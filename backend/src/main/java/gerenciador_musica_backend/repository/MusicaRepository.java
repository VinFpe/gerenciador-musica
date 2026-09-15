package gerenciador_musica_backend.repository;

import gerenciador_musica_backend.model.Album;
import gerenciador_musica_backend.model.Artista;
import gerenciador_musica_backend.model.Musica;
import gerenciador_musica_backend.repository.projection.MusicaCatalogoProjection;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface MusicaRepository
        extends JpaRepository<Musica, Long>,
        JpaSpecificationExecutor<Musica> {

    @Query("""
            SELECT CASE WHEN COUNT(credito) > 0 THEN true ELSE false END
            FROM Musica musica
            JOIN musica.creditosArtistas credito
            WHERE credito.artista.idArtista = :idArtista
              AND credito.papel = gerenciador_musica_backend.model.PapelArtistaMusica.PRINCIPAL
            """)
    boolean existsByArtistaPrincipal_IdArtista(
            @Param("idArtista") Long idArtista
    );

    @Query("""
            SELECT CASE WHEN COUNT(credito) > 0 THEN true ELSE false END
            FROM Musica musica
            JOIN musica.creditosArtistas credito
            WHERE credito.artista.idArtista = :idArtista
              AND credito.papel <> gerenciador_musica_backend.model.PapelArtistaMusica.PRINCIPAL
            """)
    boolean existsByArtistasParticipantes_IdArtista(
            @Param("idArtista") Long idArtista
    );

    boolean existsByAlbum_IdAlbum(Long idAlbum);

    boolean existsByAlbumAndTituloIgnoreCase(
            Album album,
            String titulo
    );

    boolean existsByAlbumAndTituloIgnoreCaseAndIdMusicaNot(
            Album album,
            String titulo,
            Long idMusica
    );

    @Query("""
            SELECT CASE WHEN COUNT(musica) > 0 THEN true ELSE false END
            FROM Musica musica
            JOIN musica.creditosArtistas credito
            WHERE musica.album IS NULL
              AND credito.artista = :artistaPrincipal
              AND credito.papel = gerenciador_musica_backend.model.PapelArtistaMusica.PRINCIPAL
              AND LOWER(musica.titulo) = LOWER(:titulo)
              AND musica.anoLancamento = :anoLancamento
            """)
    boolean existsByAlbumIsNullAndArtistaPrincipalAndTituloIgnoreCaseAndAnoLancamento(
            @Param("artistaPrincipal") Artista artistaPrincipal,
            @Param("titulo") String titulo,
            @Param("anoLancamento") Short anoLancamento
    );

    @Query("""
            SELECT CASE WHEN COUNT(musica) > 0 THEN true ELSE false END
            FROM Musica musica
            JOIN musica.creditosArtistas credito
            WHERE musica.album IS NULL
              AND credito.artista = :artistaPrincipal
              AND credito.papel = gerenciador_musica_backend.model.PapelArtistaMusica.PRINCIPAL
              AND LOWER(musica.titulo) = LOWER(:titulo)
              AND musica.anoLancamento = :anoLancamento
              AND musica.idMusica <> :idMusica
            """)
    boolean existsByAlbumIsNullAndArtistaPrincipalAndTituloIgnoreCaseAndAnoLancamentoAndIdMusicaNot(
            @Param("artistaPrincipal") Artista artistaPrincipal,
            @Param("titulo") String titulo,
            @Param("anoLancamento") Short anoLancamento,
            @Param("idMusica") Long idMusica
    );

    @Query(value = """
            SELECT
                id_musica AS "idMusica",
                titulo,
                duracao_segundos AS "duracaoSegundos",
                ano_lancamento AS "anoLancamento",
                id_artista_principal AS "idArtistaPrincipal",
                nome_artista_principal AS "nomeArtistaPrincipal",
                id_album AS "idAlbum",
                titulo_album AS "tituloAlbum",
                capa_url AS "capaUrl",
                generos,
                papel_artista AS "papelArtista"
            FROM vw_musicas_artista_catalogo
            WHERE id_artista_contexto = :idArtista
            ORDER BY ano_lancamento DESC, titulo ASC, id_musica ASC
            """, nativeQuery = true)
    List<MusicaCatalogoProjection> buscarCatalogoPorArtista(
            @Param("idArtista") Long idArtista
    );

    @Query(value = """
            SELECT
                id_musica AS "idMusica",
                titulo,
                duracao_segundos AS "duracaoSegundos",
                ano_lancamento AS "anoLancamento",
                id_artista_principal AS "idArtistaPrincipal",
                nome_artista_principal AS "nomeArtistaPrincipal",
                id_album AS "idAlbum",
                titulo_album AS "tituloAlbum",
                capa_url AS "capaUrl",
                generos,
                papel_artista AS "papelArtista"
            FROM vw_musicas_artista_catalogo
            WHERE id_album = :idAlbum
              AND papel_artista = 'PRINCIPAL'
            ORDER BY titulo ASC, id_musica ASC
            """, nativeQuery = true)
    List<MusicaCatalogoProjection> buscarCatalogoPorAlbum(
            @Param("idAlbum") Long idAlbum
    );

    @Query("""
            SELECT DISTINCT m
            FROM Musica m
            JOIN m.creditosArtistas credito
            WHERE credito.artista.idArtista = :idArtistaPrincipal
              AND credito.papel = gerenciador_musica_backend.model.PapelArtistaMusica.PRINCIPAL
              AND m.idMusica <> :idMusicaConsultada
            ORDER BY m.titulo ASC, m.idMusica ASC
            """)
    List<Musica> buscarPorArtistaPrincipalExcluindo(
            @Param("idArtistaPrincipal") Long idArtistaPrincipal,
            @Param("idMusicaConsultada") Long idMusicaConsultada,
            org.springframework.data.domain.Pageable pageable
    );

    @Query("""
            SELECT DISTINCT m
            FROM Musica m
            JOIN m.generos g
            WHERE g.idGenero IN :idsGeneros
              AND m.idMusica NOT IN :idsIgnorados
            ORDER BY m.titulo ASC, m.idMusica ASC
            """)
    List<Musica> buscarPorGenerosExcluindo(
            @Param("idsGeneros") java.util.Set<Long> idsGeneros,
            @Param("idsIgnorados") java.util.Set<Long> idsIgnorados,
            org.springframework.data.domain.Pageable pageable
    );
}
