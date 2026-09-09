package gerenciador_musica_backend.service;

import gerenciador_musica_backend.dto.HistoricoItemDTO;
import gerenciador_musica_backend.dto.MusicaResumoDTO;
import gerenciador_musica_backend.dto.PaginaResponseDTO;
import gerenciador_musica_backend.exception.MusicaNaoEncontradaException;
import gerenciador_musica_backend.model.Album;
import gerenciador_musica_backend.model.Artista;
import gerenciador_musica_backend.model.HistoricoMusica;
import gerenciador_musica_backend.model.Musica;
import gerenciador_musica_backend.model.Usuario;
import gerenciador_musica_backend.repository.HistoricoMusicaRepository;
import gerenciador_musica_backend.repository.MusicaRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class HistoricoService {

    // Quantidade máxima de registros de histórico mantidos por usuário;
    // ao ultrapassar o limite, os mais antigos são descartados.
    private static final int LIMITE_HISTORICO = 50;
    private static final int TAMANHO_PAGINA_PADRAO = 20;

    private final HistoricoMusicaRepository historicoMusicaRepository;
    private final MusicaRepository musicaRepository;

    public HistoricoService(
            HistoricoMusicaRepository historicoMusicaRepository,
            MusicaRepository musicaRepository
    ) {
        this.historicoMusicaRepository = historicoMusicaRepository;
        this.musicaRepository = musicaRepository;
    }

    /*
     * Chamado ao consultar os detalhes de uma música (US16). Visualizar
     * a mesma música várias vezes seguidas não gera entradas repetidas
     * consecutivas no histórico.
     */
    @Transactional
    public void registrarVisualizacao(Long musicaId) {
        Usuario usuario = obterUsuarioAutenticado();

        List<Long> ultimasVisualizadas = historicoMusicaRepository
                .buscarIdsDasUltimasMusicasVisualizadas(
                        usuario.getId(),
                        PageRequest.of(0, 1)
                );

        boolean repeticaoConsecutiva = !ultimasVisualizadas.isEmpty()
                && ultimasVisualizadas.get(0).equals(musicaId);

        if (repeticaoConsecutiva) {
            return;
        }

        Musica musica = musicaRepository.findById(musicaId)
                .orElseThrow(() -> new MusicaNaoEncontradaException(musicaId));

        historicoMusicaRepository.save(new HistoricoMusica(usuario, musica));
        historicoMusicaRepository.removerExcedente(
                usuario.getId(),
                LIMITE_HISTORICO
        );
    }

    @Transactional(readOnly = true)
    public PaginaResponseDTO<HistoricoItemDTO> listarHistorico(
            Integer pagina,
            Integer tamanhoPagina
    ) {
        Usuario usuario = obterUsuarioAutenticado();

        Pageable pageable = PageRequest.of(
                validarPagina(pagina),
                validarTamanhoPagina(tamanhoPagina)
        );

        Page<HistoricoMusica> resultado = historicoMusicaRepository
                .findByUsuario_IdOrderByVisualizadoEmDesc(
                        usuario.getId(),
                        pageable
                );

        List<HistoricoItemDTO> itens = resultado.getContent()
                .stream()
                .map(this::converterParaItem)
                .toList();

        return new PaginaResponseDTO<>(
                itens,
                resultado.getNumber(),
                resultado.getSize(),
                resultado.getTotalElements(),
                resultado.getTotalPages()
        );
    }

    private HistoricoItemDTO converterParaItem(HistoricoMusica historico) {
        Musica musica = historico.getMusica();
        Artista artistaPrincipal = musica.getArtistaPrincipal();
        Album album = musica.getAlbum();

        MusicaResumoDTO musicaResumo = new MusicaResumoDTO(
                musica.getIdMusica(),
                musica.getTitulo(),
                artistaPrincipal != null ? artistaPrincipal.getNome() : null,
                album != null ? album.getCapaUrl() : null
        );

        return new HistoricoItemDTO(
                historico.getIdHistorico(),
                musicaResumo,
                historico.getVisualizadoEm()
        );
    }

    private int validarPagina(Integer pagina) {
        return (pagina == null || pagina < 0) ? 0 : pagina;
    }

    private int validarTamanhoPagina(Integer tamanhoPagina) {
        if (tamanhoPagina == null || tamanhoPagina <= 0) {
            return TAMANHO_PAGINA_PADRAO;
        }

        return Math.min(tamanhoPagina, LIMITE_HISTORICO);
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
}
