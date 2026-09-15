import { Injectable } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable, catchError, map, of } from 'rxjs';

import { MusicaFiltro } from '../models/MusicaFiltro';
import { MusicaListagem } from '../models/MusicaListagem';
import { MusicaResponse } from '../models/MusicaResponse';
import { PaginaResponse } from '../models/PaginaResponse';
import { environment } from '../../environments/environment';

@Injectable({
  providedIn: 'root'
})
export class MusicaService {

  private readonly apiUrl = `${environment.apiUrl}/api/musicas`;

  constructor(private readonly http: HttpClient) {}

  pesquisar(
    filtro: MusicaFiltro = {},
    pagina?: number,
    tamanho?: number,
    sort?: string
  ): Observable<PaginaResponse<MusicaListagem>> {
    const params = this.montarParametros(filtro, pagina, tamanho, sort);

    return this.http.get<PaginaResponse<MusicaListagem>>(this.apiUrl, { params });
  }

  buscarPorId(id: number): Observable<MusicaResponse> {
    return this.http.get<MusicaResponse>(`${this.apiUrl}/${id}`);
  }

  buscarRelacionadas(id: number, generoId?: number): Observable<MusicaListagem[]> {
    return this.http.get<MusicaListagem[]>(`${this.apiUrl}/${id}/relacionadas`).pipe(
      catchError(() => {
        if (generoId) {
          return this.pesquisar({ generoId }, 0, 10).pipe(
            map((pagina) => pagina.itens.filter((m) => m.id !== id).slice(0, 5))
          );
        }
        return of([]);
      })
    );
  }

  private montarParametros(
    filtro: MusicaFiltro,
    pagina?: number,
    tamanho?: number,
    sort?: string
  ): HttpParams {
    let params = new HttpParams();

    params = this.adicionarSeNaoVazio(params, 'titulo', filtro.titulo);
    params = this.adicionarSeNaoVazio(params, 'artistaId', filtro.artistaId);
    params = this.adicionarSeNaoVazio(params, 'albumId', filtro.albumId);
    params = this.adicionarSeNaoVazio(params, 'generoId', filtro.generoId);
    params = this.adicionarSeNaoVazio(params, 'ano', filtro.ano);
    params = this.adicionarSeNaoVazio(params, 'page', pagina);
    params = this.adicionarSeNaoVazio(params, 'size', tamanho);
    params = this.adicionarSeNaoVazio(params, 'sort', sort);

    return params;
  }

  private adicionarSeNaoVazio(
    params: HttpParams,
    nome: string,
    valor: string | number | undefined
  ): HttpParams {
    if (valor === undefined || valor === null) {
      return params;
    }

    if (typeof valor === 'string' && valor.trim() === '') {
      return params;
    }

    return params.set(nome, valor);
  }
}
