import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import {
  HttpTestingController,
  provideHttpClientTesting,
} from '@angular/common/http/testing';

import { MusicaService } from './musica';
import { MusicaListagem } from '../models/MusicaListagem';
import { MusicaResponse } from '../models/MusicaResponse';
import { PaginaResponse } from '../models/PaginaResponse';

describe('MusicaService', () => {
  let service: MusicaService;
  let httpMock: HttpTestingController;

  const apiUrl = 'http://localhost:8080/api/musicas';

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [
        MusicaService,
        provideHttpClient(),
        provideHttpClientTesting(),
      ],
    });

    service = TestBed.inject(MusicaService);
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    httpMock.verify();
  });

  it('should be created', () => {
    expect(service).toBeTruthy();
  });

  it('deve pesquisar sem enviar nenhum parâmetro quando nada é informado', () => {
    service.pesquisar().subscribe();

    const requisicao = httpMock.expectOne(apiUrl);

    expect(requisicao.request.method).toBe('GET');
    expect(requisicao.request.params.keys()).toHaveLength(0);

    requisicao.flush(paginaDeExemplo());
  });

  it('deve montar os query params apenas com os filtros preenchidos', () => {
    service.pesquisar({ titulo: 'amor', artistaId: 5 }, 1, 10, 'anoLancamento,desc').subscribe();

    const requisicao = httpMock.expectOne(
      `${apiUrl}?titulo=amor&artistaId=5&page=1&size=10&sort=anoLancamento,desc`
    );

    expect(requisicao.request.method).toBe('GET');

    requisicao.flush(paginaDeExemplo());
  });

  it('não deve enviar um filtro de título composto apenas por espaços', () => {
    service.pesquisar({ titulo: '   ' }).subscribe();

    const requisicao = httpMock.expectOne(apiUrl);

    expect(requisicao.request.params.has('titulo')).toBe(false);

    requisicao.flush(paginaDeExemplo());
  });

  it('deve devolver a página de resultados recebida do backend', () => {
    let resultado: PaginaResponse<MusicaListagem> | undefined;

    service.pesquisar().subscribe((pagina) => (resultado = pagina));

    httpMock.expectOne(apiUrl).flush(paginaDeExemplo());

    expect(resultado?.itens).toHaveLength(1);
    expect(resultado?.itens[0].titulo).toBe('Bohemian Rhapsody');
  });

  it('deve buscar os detalhes de uma música por id', () => {
    let resultado: MusicaResponse | undefined;

    service.buscarPorId(1).subscribe((musica) => (resultado = musica));

    const requisicao = httpMock.expectOne(`${apiUrl}/1`);
    expect(requisicao.request.method).toBe('GET');

    requisicao.flush(musicaDetalheDeExemplo());

    expect(resultado?.letra).toBe('Is this the real life?');
    expect(resultado?.artistasParticipantes).toHaveLength(0);
  });

  it('deve buscar músicas relacionadas do endpoint específico', () => {
    let resultado: MusicaListagem[] | undefined;

    service.buscarRelacionadas(1).subscribe((lista) => (resultado = lista));

    const requisicao = httpMock.expectOne(`${apiUrl}/1/relacionadas`);
    expect(requisicao.request.method).toBe('GET');

    requisicao.flush(paginaDeExemplo().itens);

    expect(resultado).toHaveLength(1);
    expect(resultado?.[0].titulo).toBe('Bohemian Rhapsody');
  });

  it('deve fazer fallback para pesquisa por gênero quando endpoint de relacionadas falhar', () => {
    let resultado: MusicaListagem[] | undefined;

    service.buscarRelacionadas(1, 2).subscribe((lista) => (resultado = lista));

    const reqRelacionadas = httpMock.expectOne(`${apiUrl}/1/relacionadas`);
    reqRelacionadas.flush({ message: 'Not Found' }, { status: 404, statusText: 'Not Found' });

    const reqPesquisa = httpMock.expectOne(`${apiUrl}?generoId=2&page=0&size=10`);
    expect(reqPesquisa.request.method).toBe('GET');

    reqPesquisa.flush({
      ...paginaDeExemplo(),
      itens: [
        { ...paginaDeExemplo().itens[0], id: 1 },
        { ...paginaDeExemplo().itens[0], id: 2, titulo: 'Another One Bites the Dust' }
      ]
    });

    expect(resultado).toHaveLength(1);
    expect(resultado?.[0].id).toBe(2);
    expect(resultado?.[0].titulo).toBe('Another One Bites the Dust');
  });

  function paginaDeExemplo(): PaginaResponse<MusicaListagem> {
    return {
      itens: [
        {
          id: 1,
          titulo: 'Bohemian Rhapsody',
          duracaoSegundos: 354,
          anoLancamento: 1975,
          artistaPrincipal: { id: 1, nome: 'Queen' },
          album: { id: 1, titulo: 'A Night at the Opera', anoLancamento: 1975, capaUrl: null },
          artistasParticipantes: [],
          generos: [{ id: 1, nome: 'Rock' }],
          curtida: false,
        },
      ],
      paginaAtual: 0,
      tamanhoPagina: 20,
      totalItens: 1,
      totalPaginas: 1,
    };
  }

  function musicaDetalheDeExemplo(): MusicaResponse {
    return {
      id: 1,
      titulo: 'Bohemian Rhapsody',
      letra: 'Is this the real life?',
      duracaoSegundos: 354,
      anoLancamento: 1975,
      artistaPrincipal: { id: 1, nome: 'Queen' },
      album: { id: 1, titulo: 'A Night at the Opera', anoLancamento: 1975, capaUrl: null },
      artistasParticipantes: [],
      generos: [{ id: 1, nome: 'Rock' }],
    };
  }
});
