package gerenciador_musica_backend.service;

import gerenciador_musica_backend.dto.*;
import gerenciador_musica_backend.exception.DadosMusicaInvalidosException;
import gerenciador_musica_backend.exception.MusicaDuplicadaException;
import gerenciador_musica_backend.exception.MusicaNaoEncontradaException;
import gerenciador_musica_backend.model.Album;
import gerenciador_musica_backend.model.Artista;
import gerenciador_musica_backend.model.Genero;
import gerenciador_musica_backend.model.Musica;
import gerenciador_musica_backend.model.Usuario;
import gerenciador_musica_backend.repository.CurtidaMusicaRepository;
import gerenciador_musica_backend.repository.GeneroRepository;
import gerenciador_musica_backend.repository.MusicaRepository;
import gerenciador_musica_backend.repository.specification.MusicaSpecification;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Year;
import java.util.*;
import java.util.stream.Collectors;
import java.time.ZoneId;

@Service
public class MusicaService {

    private static final ZoneId FUSO_APLICACAO =
            ZoneId.of("America/Recife");

    private static final ArtistaResumoDTO ARTISTA_NAO_INFORMADO =
            new ArtistaResumoDTO(
                    null,
                    "Artista não informado",
                    null,
                    null,
                    null
            );

    private final MusicaRepository musicaRepository;
    private final AlbumService albumService;
    private final ArtistaService artistaService;
    private final GeneroRepository generoRepository;
    private final CurtidaMusicaRepository curtidaMusicaRepository;

    public MusicaService(
            MusicaRepository musicaRepository,
            AlbumService albumService,
            ArtistaService artistaService,
            GeneroRepository generoRepository,
            CurtidaMusicaRepository curtidaMusicaRepository
    ) {
        this.musicaRepository = musicaRepository;
        this.albumService = albumService;
        this.artistaService = artistaService;
        this.generoRepository = generoRepository;
        this.curtidaMusicaRepository = curtidaMusicaRepository;
    }

    @Transactional
    public MusicaResponseDTO cadastrarMusica(MusicaRequestDTO request) {
        String youtubeVideoId = validarRegrasDeNegocio(request);

        Artista artistaPrincipal = artistaService.buscarEntidadePorId(
                request.artistaPrincipalId()
        );

        Set<Artista> participantes = buscarParticipantes(
                request.artistasParticipantesIds(),
                artistaPrincipal
        );

        Album album = albumService.buscarAlbumDoArtista(
                request.albumId(),
                artistaPrincipal
        );

        verificarDuplicidade(
                request,
                artistaPrincipal,
                album
        );

        Set<Genero> generos = buscarOuCriarGeneros(request.generos());

        Musica musica = montarMusica(
                request,
                artistaPrincipal,
                album,
                participantes,
                generos,
                youtubeVideoId
        );

        Musica musicaSalva = musicaRepository.save(musica);

        return converterParaResponse(musicaSalva);
    }

    @Transactional
    public MusicaResponseDTO atualizarMusica(
            Long idMusica,
            MusicaRequestDTO request
    ) {
        String youtubeVideoId = validarRegrasDeNegocio(request);

        Musica musica = obterEntidadePorId(idMusica);

        Artista artistaPrincipal = artistaService.buscarEntidadePorId(
                request.artistaPrincipalId()
        );

        Set<Artista> participantes = buscarParticipantes(
                request.artistasParticipantesIds(),
                artistaPrincipal
        );

        Album album = albumService.buscarAlbumDoArtista(
                request.albumId(),
                artistaPrincipal
        );

        verificarDuplicidade(
                request,
                artistaPrincipal,
                album,
                idMusica
        );

        Set<Genero> generos = buscarOuCriarGeneros(request.generos());

        aplicarAtualizacao(
                musica,
                request,
                artistaPrincipal,
                album,
                participantes,
                generos,
                youtubeVideoId
        );

        return converterParaResponse(musica);
    }

    @Transactional
    public void excluirMusica(Long idMusica) {
        Musica musica = obterEntidadePorId(idMusica);

        /*
         * As tabelas dependentes usam ON DELETE CASCADE e o destaque
         * do perfil usa ON DELETE SET NULL. A entidade principal pode,
         * portanto, ser removida sem exclusões manuais ou órfãos.
         */
        musicaRepository.delete(musica);
    }

    private String validarRegrasDeNegocio(MusicaRequestDTO request) {
        if (request == null) {
            throw new DadosMusicaInvalidosException(
                    "Os dados da música são obrigatórios."
            );
        }

        Long artistaPrincipalId = request.artistaPrincipalId();

        if (artistaPrincipalId == null || artistaPrincipalId <= 0) {
            throw new DadosMusicaInvalidosException(
                    "O ID do artista principal deve ser válido."
            );
        }

        validarParticipantes(
                request.artistasParticipantesIds(),
                artistaPrincipalId
        );

        validarGeneros(request.generos());

        return YoutubeUrlParser.extrairVideoId(request.youtubeUrl());
    }

    private Set<Artista> buscarParticipantes(
            Set<Long> idsParticipantes,
            Artista artistaPrincipal
    ) {
        Set<Artista> participantes = new LinkedHashSet<>();

        if (idsParticipantes == null || idsParticipantes.isEmpty()) {
            return participantes;
        }

        for (Long idParticipante : idsParticipantes) {
            boolean mesmoArtistaPrincipal = idParticipante.equals(
                    artistaPrincipal.getIdArtista()
            );

            if (mesmoArtistaPrincipal) {
                throw new DadosMusicaInvalidosException(
                        "O artista principal não pode aparecer entre os participantes."
                );
            }

            Artista participante = artistaService.buscarEntidadePorId(
                    idParticipante
            );

            participantes.add(participante);
        }

        return participantes;
    }

    private void verificarDuplicidade(
            MusicaRequestDTO request,
            Artista artistaPrincipal,
            Album album
    ) {
        verificarDuplicidade(
                request,
                artistaPrincipal,
                album,
                null
        );
    }

    private void verificarDuplicidade(
            MusicaRequestDTO request,
            Artista artistaPrincipal,
            Album album,
            Long idMusicaIgnorada
    ) {
        String tituloNormalizado = normalizarTexto(request.titulo());

        boolean musicaJaExiste;

        if (album != null) {
            musicaJaExiste = verificarDuplicidadeComAlbum(
                    album,
                    tituloNormalizado,
                    idMusicaIgnorada
            );
        } else {
            musicaJaExiste = verificarDuplicidadeSemAlbum(
                    artistaPrincipal,
                    tituloNormalizado,
                    request.anoLancamento(),
                    idMusicaIgnorada
            );
        }

        if (musicaJaExiste) {
            throw new MusicaDuplicadaException(
                    "A música já está cadastrada."
            );
        }
    }

    private boolean verificarDuplicidadeComAlbum(
            Album album,
            String titulo,
            Long idMusicaIgnorada
    ) {
        if (idMusicaIgnorada == null) {
            return musicaRepository.existsByAlbumAndTituloIgnoreCase(
                    album,
                    titulo
            );
        }

        return musicaRepository
                .existsByAlbumAndTituloIgnoreCaseAndIdMusicaNot(
                        album,
                        titulo,
                        idMusicaIgnorada
                );
    }

    private boolean verificarDuplicidadeSemAlbum(
            Artista artistaPrincipal,
            String titulo,
            Short anoLancamento,
            Long idMusicaIgnorada
    ) {
        if (idMusicaIgnorada == null) {
            return musicaRepository
                    .existsByAlbumIsNullAndArtistaPrincipalAndTituloIgnoreCaseAndAnoLancamento(
                            artistaPrincipal,
                            titulo,
                            anoLancamento
                    );
        }

        return musicaRepository
                .existsByAlbumIsNullAndArtistaPrincipalAndTituloIgnoreCaseAndAnoLancamentoAndIdMusicaNot(
                        artistaPrincipal,
                        titulo,
                        anoLancamento,
                        idMusicaIgnorada
                );
    }

    private Set<Genero> buscarOuCriarGeneros(Set<String> nomesGeneros) {
        Set<Genero> generos = new LinkedHashSet<>();

        if (nomesGeneros == null || nomesGeneros.isEmpty()) {
            return generos;
        }

        for (String nomeRecebido : nomesGeneros) {
            String nomeNormalizado = normalizarTexto(nomeRecebido);

            if (nomeNormalizado.isBlank()) {
                throw new DadosMusicaInvalidosException(
                        "O nome do gênero não pode ficar vazio."
                );
            }

            Genero genero = generoRepository
                    .findByNomeIgnoreCase(nomeNormalizado)
                    .orElseGet(() -> {
                        Genero novoGenero = new Genero(nomeNormalizado);
                        return generoRepository.save(novoGenero);
                    });

            generos.add(genero);
        }

        return generos;
    }

    private Musica montarMusica(
            MusicaRequestDTO request,
            Artista artistaPrincipal,
            Album album,
            Set<Artista> artistasParticipantes,
            Set<Genero> generos,
            String youtubeVideoId
    ) {
        String tituloNormalizado = normalizarTexto(request.titulo());
        String letraNormalizada = normalizarLetra(request.letra());

        Musica musica = new Musica(
                tituloNormalizado,
                letraNormalizada,
                request.duracaoSegundos(),
                request.anoLancamento(),
                artistaPrincipal,
                album
        );

        musica.setArtistasParticipantes(artistasParticipantes);
        musica.setGeneros(generos);
        musica.setYoutubeVideoId(youtubeVideoId);

        return musica;
    }

    private void aplicarAtualizacao(
            Musica musica,
            MusicaRequestDTO request,
            Artista artistaPrincipal,
            Album album,
            Set<Artista> artistasParticipantes,
            Set<Genero> generos,
            String youtubeVideoId
    ) {
        musica.setTitulo(normalizarTexto(request.titulo()));
        musica.setLetra(normalizarLetra(request.letra()));
        musica.setDuracaoSegundos(request.duracaoSegundos());
        musica.setAnoLancamento(request.anoLancamento());
        musica.definirCreditosArtistas(
                artistaPrincipal,
                artistasParticipantes
        );
        musica.setAlbum(album);
        musica.setGeneros(generos);
        musica.setYoutubeVideoId(youtubeVideoId);
    }

    private MusicaResponseDTO converterParaResponse(Musica musica) {
        ArtistaResumoDTO artistaPrincipal = converterArtistaParaResumo(
                musica.getArtistaPrincipal()
        );

        AlbumResumoDTO album = converterAlbumParaResumo(musica.getAlbum());

        Set<ArtistaResumoDTO> participantes = musica
                .getArtistasParticipantes()
                .stream()
                .map(this::converterArtistaParaResumo)
                .collect(Collectors.toCollection(LinkedHashSet::new));

        Set<GeneroResumoDTO> generos = musica
                .getGeneros()
                .stream()
                .map(this::converterGeneroParaResumo)
                .collect(Collectors.toCollection(LinkedHashSet::new));

        return new MusicaResponseDTO(
                musica.getIdMusica(),
                musica.getTitulo(),
                musica.getLetra(),
                musica.getDuracaoSegundos(),
                musica.getAnoLancamento(),
                artistaPrincipal,
                album,
                participantes,
                generos,
                musica.getYoutubeVideoId()
        );
    }

    private String normalizarParaComparacao(String texto) {
        if (texto == null) {
            return "";
        }

        return texto
                .trim()
                .replaceAll("\\s+", " ")
                .toLowerCase(Locale.ROOT);
    }

    private String normalizarTexto(String texto) {
        return texto
                .trim()
                .replaceAll("\\s+", " ");
    }

    private String normalizarTextoOpcional(String texto) {
        if (texto == null || texto.isBlank()) {
            return null;
        }

        return normalizarTexto(texto);
    }

    private void validarParticipantes(
            Set<Long> idsParticipantes,
            Long artistaPrincipalId
    ) {
        if (idsParticipantes == null) {
            throw new DadosMusicaInvalidosException(
                    "A lista de artistas participantes é obrigatória."
            );
        }

        for (Long idParticipante : idsParticipantes) {
            if (idParticipante == null || idParticipante <= 0) {
                throw new DadosMusicaInvalidosException(
                        "A lista possui um ID de participante inválido."
                );
            }

            if (idParticipante.equals(artistaPrincipalId)) {
                throw new DadosMusicaInvalidosException(
                        "O artista principal não pode aparecer como participante."
                );
            }
        }
    }

    private void validarGeneros(Set<String> generos) {
        if (generos == null || generos.isEmpty()) {
            throw new DadosMusicaInvalidosException(
                    "A música deve possuir pelo menos um gênero."
            );
        }

        Set<String> nomesEncontrados = new HashSet<>();

        for (String genero : generos) {
            String nomeNormalizado = normalizarParaComparacao(genero);

            if (nomeNormalizado.isBlank()) {
                throw new DadosMusicaInvalidosException(
                        "O nome do gênero não pode ser vazio."
                );
            }

            boolean foiAdicionado = nomesEncontrados.add(nomeNormalizado);

            if (!foiAdicionado) {
                throw new DadosMusicaInvalidosException(
                        "O gênero está repetido: " + genero
                );
            }
        }
    }

    private String normalizarLetra(String letra) {
        if (letra == null || letra.isBlank()) {
            return null;
        }

        return letra.strip();
    }

    private ArtistaResumoDTO converterArtistaParaResumo(Artista artista) {
        if (artista == null) {
            return ARTISTA_NAO_INFORMADO;
        }

        return new ArtistaResumoDTO(
                artista.getIdArtista(),
                artista.getNome(),
                artista.getNomeCompleto(),
                artista.getDescricao(),
                artista.getFotoPerfilUrl()
        );
    }

    private AlbumResumoDTO converterAlbumParaResumo(Album album) {
        if (album == null) {
            return null;
        }

        return new AlbumResumoDTO(
                album.getIdAlbum(),
                album.getTitulo(),
                album.getAnoLancamento(),
                album.getCapaUrl()
        );
    }

    private GeneroResumoDTO converterGeneroParaResumo(Genero genero) {
        return new GeneroResumoDTO(
                genero.getIdGenero(),
                genero.getNome()
        );
    }

    @Transactional(readOnly = true)
    public MusicaResponseDTO buscarPorId(Long id) {
        Musica musica = obterEntidadePorId(id);

        return converterParaResponse(musica);
    }

    @Transactional(readOnly = true)
    public List<MusicaListagemDTO> buscarMusicasRelacionadas(Long idMusica) {
        Musica musicaAlvo = obterEntidadePorId(idMusica);

        List<Musica> relacionadas = new ArrayList<>();
        Set<Long> idsIgnorados = new HashSet<>();
        idsIgnorados.add(musicaAlvo.getIdMusica());

        Artista artistaPrincipal = musicaAlvo.getArtistaPrincipal();
        if (artistaPrincipal != null && artistaPrincipal.getIdArtista() != null) {
            List<Musica> doArtista = musicaRepository.buscarPorArtistaPrincipalExcluindo(
                    artistaPrincipal.getIdArtista(),
                    musicaAlvo.getIdMusica(),
                    PageRequest.of(0, 5)
            );
            relacionadas.addAll(doArtista);
            doArtista.forEach(m -> idsIgnorados.add(m.getIdMusica()));
        }

        int limiteRestante = 5 - relacionadas.size();
        Set<Long> idsGeneros = musicaAlvo.getGeneros().stream()
                .map(Genero::getIdGenero)
                .collect(Collectors.toSet());

        if (limiteRestante > 0 && !idsGeneros.isEmpty()) {
            List<Musica> doGenero = musicaRepository.buscarPorGenerosExcluindo(
                    idsGeneros,
                    idsIgnorados,
                    PageRequest.of(0, limiteRestante)
            );
            relacionadas.addAll(doGenero);
        }

        List<Long> idsRelacionados = relacionadas.stream()
                .map(Musica::getIdMusica)
                .toList();

        Set<Long> idsCurtidos = obterIdsCurtidosSafely(idsRelacionados);

        return relacionadas.stream()
                .map(m -> converterParaListagem(
                        m,
                        idsCurtidos.contains(m.getIdMusica())
                ))
                .toList();
    }

    private Musica obterEntidadePorId(Long idMusica) {
        if (idMusica == null || idMusica <= 0) {
            throw new DadosMusicaInvalidosException(
                    "O ID da música deve ser positivo."
            );
        }

        return musicaRepository
                .findById(idMusica)
                .orElseThrow(
                        () -> new MusicaNaoEncontradaException(idMusica)
                );
    }

    private static final int TAMANHO_PAGINA_PADRAO = 20;
    private static final int TAMANHO_PAGINA_MAXIMO = 100;
    private static final Set<String> CAMPOS_ORDENACAO_PERMITIDOS =
            Set.of("titulo", "anoLancamento", "duracaoSegundos");

    @Transactional(readOnly = true)
    public PaginaResponseDTO<MusicaListagemDTO> pesquisarMusicas(
            MusicaFiltroDTO filtro,
            Integer pagina,
            Integer tamanhoPagina,
            String sort
    ) {
        MusicaFiltroDTO filtroNormalizado = normalizarFiltro(filtro);
        validarAno(filtroNormalizado.anoLancamento());

        Pageable pageable = PageRequest.of(
                validarPagina(pagina),
                validarTamanhoPagina(tamanhoPagina),
                resolverOrdenacao(sort)
        );

        Page<Musica> resultado = musicaRepository.findAll(
                MusicaSpecification.comFiltros(filtroNormalizado),
                pageable
        );

        List<Long> idsDaPagina = resultado.getContent()
                .stream()
                .map(Musica::getIdMusica)
                .toList();

        Set<Long> idsCurtidos = idsDaPagina.isEmpty()
                ? Set.of()
                : curtidaMusicaRepository.buscarIdsCurtidosPeloUsuario(
                        obterUsuarioAutenticado().getId(),
                        idsDaPagina
                );

        List<MusicaListagemDTO> itens = resultado
                .getContent()
                .stream()
                .map(musica -> converterParaListagem(
                        musica,
                        idsCurtidos.contains(musica.getIdMusica())
                ))
                .toList();

        return new PaginaResponseDTO<>(
                itens,
                resultado.getNumber(),
                resultado.getSize(),
                resultado.getTotalElements(),
                resultado.getTotalPages()
        );
    }

    private MusicaFiltroDTO normalizarFiltro(MusicaFiltroDTO filtro) {
        if (filtro == null) {
            return new MusicaFiltroDTO(null, null, null, null, null);
        }

        return new MusicaFiltroDTO(
                normalizarTextoOpcional(filtro.titulo()),
                filtro.artistaId(),
                filtro.albumId(),
                filtro.generoId(),
                filtro.anoLancamento()
        );
    }

    private void validarAno(Short anoLancamento) {
        if (anoLancamento == null) {
            return;
        }

        int anoAtual = Year.now(FUSO_APLICACAO).getValue();

        if (anoLancamento <= 0 || anoLancamento > anoAtual) {
            throw new DadosMusicaInvalidosException(
                    "O ano de lançamento informado para o filtro é inválido."
            );
        }
    }

    private Sort resolverOrdenacao(String sort) {
        if (sort == null || sort.isBlank()) {
            return Sort.by(Sort.Direction.ASC, "titulo");
        }

        String[] partes = sort.split(",");
        String campo = partes[0].trim();

        if (!CAMPOS_ORDENACAO_PERMITIDOS.contains(campo)) {
            throw new DadosMusicaInvalidosException(
                    "Campo de ordenação inválido: " + campo
            );
        }

        boolean descendente = partes.length > 1
                && partes[1].trim().equalsIgnoreCase("desc");

        Sort.Direction direcao = descendente
                ? Sort.Direction.DESC
                : Sort.Direction.ASC;

        return Sort.by(direcao, campo);
    }

    private int validarPagina(Integer pagina) {
        if (pagina == null) {
            return 0;
        }

        if (pagina < 0) {
            throw new DadosMusicaInvalidosException(
                    "O número da página não pode ser negativo."
            );
        }

        return pagina;
    }

    private int validarTamanhoPagina(Integer tamanhoPagina) {
        if (tamanhoPagina == null) {
            return TAMANHO_PAGINA_PADRAO;
        }

        if (tamanhoPagina <= 0) {
            throw new DadosMusicaInvalidosException(
                    "O tamanho da página deve ser maior que zero."
            );
        }

        return Math.min(tamanhoPagina, TAMANHO_PAGINA_MAXIMO);
    }

    private MusicaListagemDTO converterParaListagem(
            Musica musica,
            boolean curtida
    ) {
        ArtistaResumoDTO artistaPrincipal = converterArtistaParaResumo(
                musica.getArtistaPrincipal()
        );

        AlbumResumoDTO album = converterAlbumParaResumo(musica.getAlbum());

        Set<ArtistaResumoDTO> participantes = musica
                .getArtistasParticipantes()
                .stream()
                .map(this::converterArtistaParaResumo)
                .collect(Collectors.toCollection(LinkedHashSet::new));

        Set<GeneroResumoDTO> generos = musica
                .getGeneros()
                .stream()
                .map(this::converterGeneroParaResumo)
                .collect(Collectors.toCollection(LinkedHashSet::new));

        return new MusicaListagemDTO(
                musica.getIdMusica(),
                musica.getTitulo(),
                musica.getDuracaoSegundos(),
                musica.getAnoLancamento(),
                artistaPrincipal,
                album,
                participantes,
                generos,
                curtida
        );
    }

    private Usuario obterUsuarioAutenticado() {
        Authentication authentication = SecurityContextHolder
                .getContext()
                .getAuthentication();

        if (authentication != null
                && authentication.isAuthenticated()
                && authentication.getPrincipal() instanceof Usuario usuario) {
            return usuario;
        }

        throw new IllegalStateException(
                "Usuário autenticado não encontrado."
        );
    }

    private Set<Long> obterIdsCurtidosSafely(List<Long> idsMusicas) {
        if (idsMusicas == null || idsMusicas.isEmpty()) {
            return Set.of();
        }

        try {
            Authentication authentication = SecurityContextHolder
                    .getContext()
                    .getAuthentication();

            if (authentication != null
                    && authentication.isAuthenticated()
                    && authentication.getPrincipal() instanceof Usuario usuario) {
                return curtidaMusicaRepository.buscarIdsCurtidosPeloUsuario(
                        usuario.getId(),
                        idsMusicas
                );
            }
        } catch (Exception ignored) {
        }

        return Set.of();
    }
}
