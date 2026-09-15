package gerenciador_musica_backend.controller;

import gerenciador_musica_backend.config.JwtAuthenticationFilter;
import gerenciador_musica_backend.dto.ArtistaResumoDTO;
import gerenciador_musica_backend.dto.MusicaFiltroDTO;
import gerenciador_musica_backend.dto.MusicaListagemDTO;
import gerenciador_musica_backend.dto.MusicaResponseDTO;
import gerenciador_musica_backend.dto.PaginaResponseDTO;
import gerenciador_musica_backend.exception.DadosMusicaInvalidosException;
import gerenciador_musica_backend.exception.MusicaNaoEncontradaException;
import gerenciador_musica_backend.service.HistoricoService;
import gerenciador_musica_backend.service.MusicaService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Set;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/*
 * Teste de INTEGRAÇÃO da camada web do catálogo público de músicas
 * (GET /api/musicas). O MusicaService é mockado; o foco aqui é o
 * contrato HTTP (status, JSON, erros), não a regra de negócio.
 */
@WebMvcTest(MusicaController.class)
@AutoConfigureMockMvc(addFilters = false)
class MusicaControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private MusicaService musicaService;

    @MockitoBean
    private HistoricoService historicoService;

    @MockitoBean
    private JwtAuthenticationFilter jwtAuthenticationFilter;

    private MusicaResponseDTO montarMusicaResposta() {
        return new MusicaResponseDTO(
                1L,
                "Bohemian Rhapsody",
                null,
                354,
                (short) 1975,
                new ArtistaResumoDTO(1L, "Queen", "Queen", null, null),
                null,
                Set.of(),
                Set.of()
        );
    }

    @Test
    void devePesquisarMusicasRetornandoRespostaPaginada() throws Exception {
        MusicaListagemDTO item = new MusicaListagemDTO(
                1L, "Bohemian Rhapsody", 354, (short) 1975,
                new ArtistaResumoDTO(1L, "Queen", "Queen", null, null),
                null,
                Set.of(new ArtistaResumoDTO(
                        2L,
                        "Artista Participante",
                        "Nome Completo",
                        null,
                        null
                )),
                Set.of(),
                false
        );

        when(musicaService.pesquisarMusicas(any(MusicaFiltroDTO.class), isNull(), isNull(), isNull()))
                .thenReturn(new PaginaResponseDTO<>(List.of(item), 0, 20, 1, 1));

        mockMvc.perform(get("/api/musicas"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.itens[0].titulo").value("Bohemian Rhapsody"))
                .andExpect(jsonPath("$.itens[0].artistaPrincipal.nome").value("Queen"))
                .andExpect(jsonPath("$.itens[0].artistasParticipantes[0].nome")
                        .value("Artista Participante"))
                .andExpect(jsonPath("$.totalItens").value(1));
    }

    @Test
    void devePesquisarMusicasRepassandoFiltrosDaQuery() throws Exception {
        when(musicaService.pesquisarMusicas(any(MusicaFiltroDTO.class), eq(1), eq(5), eq("anoLancamento,desc")))
                .thenReturn(new PaginaResponseDTO<>(List.of(), 1, 5, 0, 0));

        mockMvc.perform(get("/api/musicas")
                        .param("titulo", "amor")
                        .param("page", "1")
                        .param("size", "5")
                        .param("sort", "anoLancamento,desc"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.itens").isEmpty());
    }

    @Test
    void deveRetornarListaVaziaQuandoNenhumaMusicaForEncontrada() throws Exception {
        when(musicaService.pesquisarMusicas(any(MusicaFiltroDTO.class), isNull(), isNull(), isNull()))
                .thenReturn(new PaginaResponseDTO<>(List.of(), 0, 20, 0, 0));

        mockMvc.perform(get("/api/musicas"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.itens").isEmpty())
                .andExpect(jsonPath("$.totalItens").value(0));
    }

    @Test
    void deveRetornar400QuandoFiltroForInvalido() throws Exception {
        when(musicaService.pesquisarMusicas(any(MusicaFiltroDTO.class), isNull(), isNull(), isNull()))
                .thenThrow(new DadosMusicaInvalidosException("O ano de lançamento informado para o filtro é inválido."));

        mockMvc.perform(get("/api/musicas"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void deveRetornar400QuandoParametroDeQueryTiverTipoInvalido() throws Exception {
        mockMvc.perform(get("/api/musicas").param("artistaId", "abc"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void deveBuscarMusicaPorId() throws Exception {
        when(musicaService.buscarPorId(1L)).thenReturn(montarMusicaResposta());

        mockMvc.perform(get("/api/musicas/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.titulo").value("Bohemian Rhapsody"));
    }

    @Test
    void deveRegistrarVisualizacaoNoHistoricoAoBuscarMusicaPorId() throws Exception {
        when(musicaService.buscarPorId(1L)).thenReturn(montarMusicaResposta());

        mockMvc.perform(get("/api/musicas/1"))
                .andExpect(status().isOk());

        verify(historicoService).registrarVisualizacao(1L);
    }

    @Test
    void deveRetornar404QuandoMusicaNaoEncontrada() throws Exception {
        when(musicaService.buscarPorId(99L))
                .thenThrow(new MusicaNaoEncontradaException(99L));

        mockMvc.perform(get("/api/musicas/99"))
                .andExpect(status().isNotFound());

        verify(historicoService, never()).registrarVisualizacao(any());
    }

    @Test
    void deveBuscarMusicasRelacionadas() throws Exception {
        MusicaListagemDTO itemRelacionado = new MusicaListagemDTO(
                2L, "Love of My Life", 219, (short) 1975,
                new ArtistaResumoDTO(1L, "Queen", "Queen", null, null),
                null,
                Set.of(),
                Set.of(),
                false
        );

        when(musicaService.buscarMusicasRelacionadas(1L))
                .thenReturn(List.of(itemRelacionado));

        mockMvc.perform(get("/api/musicas/1/relacionadas"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(2))
                .andExpect(jsonPath("$[0].titulo").value("Love of My Life"))
                .andExpect(jsonPath("$[0].artistaPrincipal.nome").value("Queen"));
    }

    @Test
    void deveRetornar404QuandoBuscarRelacionadasDeMusicaInexistente() throws Exception {
        when(musicaService.buscarMusicasRelacionadas(99L))
                .thenThrow(new MusicaNaoEncontradaException(99L));

        mockMvc.perform(get("/api/musicas/99/relacionadas"))
                .andExpect(status().isNotFound());
    }
}
