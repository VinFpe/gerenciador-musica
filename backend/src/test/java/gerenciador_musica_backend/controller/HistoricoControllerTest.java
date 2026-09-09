package gerenciador_musica_backend.controller;

import gerenciador_musica_backend.config.JwtAuthenticationFilter;
import gerenciador_musica_backend.dto.HistoricoItemDTO;
import gerenciador_musica_backend.dto.MusicaResumoDTO;
import gerenciador_musica_backend.dto.PaginaResponseDTO;
import gerenciador_musica_backend.service.HistoricoService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.OffsetDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/*
 * Teste de INTEGRAÇÃO da camada web do histórico de músicas
 * (GET /api/historico). O HistoricoService é mockado; o foco aqui é o
 * contrato HTTP (status, JSON), não a regra de negócio.
 */
@WebMvcTest(HistoricoController.class)
@AutoConfigureMockMvc(addFilters = false)
class HistoricoControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private HistoricoService historicoService;

    @MockitoBean
    private JwtAuthenticationFilter jwtAuthenticationFilter;

    @Test
    void deveListarHistoricoRetornandoRespostaPaginada() throws Exception {
        HistoricoItemDTO item = new HistoricoItemDTO(
                10L,
                new MusicaResumoDTO(5L, "Bohemian Rhapsody", "Queen", null),
                OffsetDateTime.now()
        );

        when(historicoService.listarHistorico(isNull(), isNull()))
                .thenReturn(new PaginaResponseDTO<>(List.of(item), 0, 20, 1, 1));

        mockMvc.perform(get("/api/historico"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.itens[0].musica.titulo").value("Bohemian Rhapsody"))
                .andExpect(jsonPath("$.itens[0].musica.artista").value("Queen"))
                .andExpect(jsonPath("$.totalItens").value(1));
    }

    @Test
    void deveRepassarParametrosDePaginacaoDaQuery() throws Exception {
        when(historicoService.listarHistorico(1, 5))
                .thenReturn(new PaginaResponseDTO<>(List.of(), 1, 5, 0, 0));

        mockMvc.perform(get("/api/historico")
                        .param("page", "1")
                        .param("size", "5"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.itens").isEmpty());
    }

    @Test
    void deveRetornarListaVaziaQuandoNaoHaHistorico() throws Exception {
        when(historicoService.listarHistorico(isNull(), isNull()))
                .thenReturn(new PaginaResponseDTO<>(List.of(), 0, 20, 0, 0));

        mockMvc.perform(get("/api/historico"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.itens").isEmpty())
                .andExpect(jsonPath("$.totalItens").value(0));
    }
}
