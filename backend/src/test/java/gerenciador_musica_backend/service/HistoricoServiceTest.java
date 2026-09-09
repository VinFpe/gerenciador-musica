package gerenciador_musica_backend.service;

import gerenciador_musica_backend.dto.HistoricoItemDTO;
import gerenciador_musica_backend.dto.PaginaResponseDTO;
import gerenciador_musica_backend.exception.MusicaNaoEncontradaException;
import gerenciador_musica_backend.model.Artista;
import gerenciador_musica_backend.model.HistoricoMusica;
import gerenciador_musica_backend.model.Musica;
import gerenciador_musica_backend.model.Role;
import gerenciador_musica_backend.model.Usuario;
import gerenciador_musica_backend.repository.HistoricoMusicaRepository;
import gerenciador_musica_backend.repository.MusicaRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.InjectMocks;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/*
 * Teste de UNIDADE do HistoricoService. Como o service descobre o
 * usuário logado através do SecurityContextHolder (não por um
 * parâmetro), simulamos a autenticação "de verdade" no
 * SecurityContextHolder antes de cada teste, seguindo o mesmo padrão
 * usado no PlaylistServiceTest.
 */
@ExtendWith(MockitoExtension.class)
class HistoricoServiceTest {

    @Mock
    private HistoricoMusicaRepository historicoMusicaRepository;

    @Mock
    private MusicaRepository musicaRepository;

    @InjectMocks
    private HistoricoService historicoService;

    private Usuario usuarioLogado;

    @BeforeEach
    void setUp() {
        usuarioLogado = new Usuario("Maria", "maria@email.com", "hash", Role.USER);
        ReflectionTestUtils.setField(usuarioLogado, "id", 1L);

        autenticarComo(usuarioLogado);
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    private void autenticarComo(Usuario usuario) {
        Authentication authentication =
                new UsernamePasswordAuthenticationToken(usuario, null, List.of());

        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(authentication);
        SecurityContextHolder.setContext(context);
    }

    private Musica montarMusica(Long id, String titulo) {
        Artista artista = new Artista("Queen", "Queen", "Banda britânica de rock.", null);
        Musica musica = new Musica(titulo, null, 354, (short) 1975, artista, null);
        musica.setIdMusica(id);

        return musica;
    }

    @Test
    void deveRegistrarVisualizacaoQuandoNaoHaHistoricoAnterior() {
        Musica musica = montarMusica(5L, "Bohemian Rhapsody");

        when(historicoMusicaRepository
                .buscarIdsDasUltimasMusicasVisualizadas(eq(1L), any(Pageable.class)))
                .thenReturn(List.of());
        when(musicaRepository.findById(5L)).thenReturn(Optional.of(musica));

        historicoService.registrarVisualizacao(5L);

        verify(historicoMusicaRepository).save(any(HistoricoMusica.class));
        verify(historicoMusicaRepository).removerExcedente(1L, 50);
    }

    @Test
    void naoDeveRegistrarQuandoUltimaMusicaVistaForAMesma() {
        when(historicoMusicaRepository
                .buscarIdsDasUltimasMusicasVisualizadas(eq(1L), any(Pageable.class)))
                .thenReturn(List.of(5L));

        historicoService.registrarVisualizacao(5L);

        verify(historicoMusicaRepository, never()).save(any());
        verify(musicaRepository, never()).findById(any());
        verify(historicoMusicaRepository, never())
                .removerExcedente(anyLong(), anyInt());
    }

    @Test
    void deveRegistrarQuandoUltimaMusicaVistaForDiferente() {
        Musica musicaAtual = montarMusica(5L, "Bohemian Rhapsody");

        when(historicoMusicaRepository
                .buscarIdsDasUltimasMusicasVisualizadas(eq(1L), any(Pageable.class)))
                .thenReturn(List.of(3L));
        when(musicaRepository.findById(5L)).thenReturn(Optional.of(musicaAtual));

        historicoService.registrarVisualizacao(5L);

        verify(historicoMusicaRepository).save(any(HistoricoMusica.class));
    }

    @Test
    void deveLancarExcecaoQuandoMusicaNaoExiste() {
        when(historicoMusicaRepository
                .buscarIdsDasUltimasMusicasVisualizadas(eq(1L), any(Pageable.class)))
                .thenReturn(List.of());
        when(musicaRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> historicoService.registrarVisualizacao(99L))
                .isInstanceOf(MusicaNaoEncontradaException.class);

        verify(historicoMusicaRepository, never()).save(any());
    }

    @Test
    void deveListarHistoricoOrdenadoDaMaisRecenteParaAMaisAntiga() {
        Musica musica = montarMusica(5L, "Bohemian Rhapsody");
        HistoricoMusica historico = new HistoricoMusica(usuarioLogado, musica);
        ReflectionTestUtils.setField(historico, "idHistorico", 10L);

        Page<HistoricoMusica> pagina = new PageImpl<>(List.of(historico));

        when(historicoMusicaRepository
                .findByUsuario_IdOrderByVisualizadoEmDesc(eq(1L), any(Pageable.class)))
                .thenReturn(pagina);

        PaginaResponseDTO<HistoricoItemDTO> resultado =
                historicoService.listarHistorico(null, null);

        assertThat(resultado.itens()).hasSize(1);
        assertThat(resultado.itens().get(0).idHistorico()).isEqualTo(10L);
        assertThat(resultado.itens().get(0).musica().getTitulo())
                .isEqualTo("Bohemian Rhapsody");
        assertThat(resultado.itens().get(0).musica().getArtista())
                .isEqualTo("Queen");
        assertThat(resultado.totalItens()).isEqualTo(1);
    }

    @Test
    void deveUsarPaginacaoPadraoQuandoParametrosNaoForemInformados() {
        when(historicoMusicaRepository
                .findByUsuario_IdOrderByVisualizadoEmDesc(eq(1L), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of()));

        historicoService.listarHistorico(null, null);

        ArgumentCaptor<Pageable> captor = ArgumentCaptor.forClass(Pageable.class);
        verify(historicoMusicaRepository)
                .findByUsuario_IdOrderByVisualizadoEmDesc(eq(1L), captor.capture());

        assertThat(captor.getValue().getPageNumber()).isZero();
        assertThat(captor.getValue().getPageSize()).isEqualTo(20);
    }

    @Test
    void deveLimitarTamanhoDaPaginaAoLimiteDoHistorico() {
        when(historicoMusicaRepository
                .findByUsuario_IdOrderByVisualizadoEmDesc(eq(1L), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of()));

        historicoService.listarHistorico(0, 999);

        ArgumentCaptor<Pageable> captor = ArgumentCaptor.forClass(Pageable.class);
        verify(historicoMusicaRepository)
                .findByUsuario_IdOrderByVisualizadoEmDesc(eq(1L), captor.capture());

        assertThat(captor.getValue().getPageSize()).isEqualTo(50);
    }
}
