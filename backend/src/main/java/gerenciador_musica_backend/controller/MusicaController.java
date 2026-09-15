package gerenciador_musica_backend.controller;

import gerenciador_musica_backend.dto.MusicaFiltroDTO;
import gerenciador_musica_backend.dto.MusicaListagemDTO;
import gerenciador_musica_backend.dto.MusicaResponseDTO;
import gerenciador_musica_backend.dto.PaginaResponseDTO;
import gerenciador_musica_backend.service.HistoricoService;
import gerenciador_musica_backend.service.MusicaService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/musicas")
public class MusicaController {

    private final MusicaService musicaService;
    private final HistoricoService historicoService;

    public MusicaController(
            MusicaService musicaService,
            HistoricoService historicoService
    ) {
        this.musicaService = musicaService;
        this.historicoService = historicoService;
    }

    /**
     * GET /api/musicas — pesquisa músicas com filtros opcionais e paginação.
     * Todos os parâmetros são opcionais; quando nenhum é informado, retorna
     * o catálogo inteiro paginado, ordenado por título.
     *
     * @param titulo    busca parcial e case-insensitive no título
     * @param artistaId ID do artista (principal ou participante)
     * @param albumId   ID do álbum
     * @param generoId  ID do gênero
     * @param ano       ano de lançamento exato
     * @param page      número da página, começando em 0 (padrão: 0)
     * @param size      itens por página (padrão: 20, máximo: 100)
     * @param sort      campo e direção de ordenação no formato "campo,direcao"
     *                  (ex: "anoLancamento,desc"). Campos aceitos: titulo,
     *                  anoLancamento, duracaoSegundos. Padrão: "titulo,asc".
     * @return 200 OK com PaginaResponseDTO&lt;MusicaListagemDTO&gt;, mesmo
     *         quando nenhuma música é encontrada (lista de itens vazia)
     */
    @GetMapping
    public ResponseEntity<PaginaResponseDTO<MusicaListagemDTO>> pesquisar(
            @RequestParam(required = false) String titulo,
            @RequestParam(required = false) Long artistaId,
            @RequestParam(required = false) Long albumId,
            @RequestParam(required = false) Long generoId,
            @RequestParam(required = false) Short ano,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size,
            @RequestParam(required = false) String sort
    ) {
        MusicaFiltroDTO filtro = new MusicaFiltroDTO(
                titulo, artistaId, albumId, generoId, ano
        );

        return ResponseEntity.ok(
                musicaService.pesquisarMusicas(filtro, page, size, sort)
        );
    }

    /**
     * GET /api/musicas/{id} — detalhes completos de uma música
     * (letra, participantes, álbum, gêneros). Registra a consulta no
     * histórico de músicas acessadas do usuário autenticado (US16).
     *
     * @return 200 OK com MusicaResponseDTO, ou 404 (via
     *         MusicaNaoEncontradaException) quando o ID não existir
     */
    @GetMapping("/{id}")
    public ResponseEntity<MusicaResponseDTO> buscarPorId(
            @PathVariable Long id
    ) {
        MusicaResponseDTO musica = musicaService.buscarPorId(id);

        historicoService.registrarVisualizacao(id);

        return ResponseEntity.ok(musica);
    }
     
    @GetMapping("/{id}/relacionadas")
    public ResponseEntity<List<MusicaListagemDTO>> buscarRelacionadas(
            @PathVariable Long id
    ) {
        return ResponseEntity.ok(musicaService.buscarMusicasRelacionadas(id));
    }
}