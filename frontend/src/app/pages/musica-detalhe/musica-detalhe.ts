import { Component, OnInit, signal } from '@angular/core';
import { ActivatedRoute, Params, RouterLink } from '@angular/router';
import { HttpErrorResponse } from '@angular/common/http';
import { catchError, finalize, of } from 'rxjs';

import { MusicaService } from '../../services/musica';
import { MusicaResponse } from '../../models/MusicaResponse';
import { MusicaListagem } from '../../models/MusicaListagem';
import { formatarDuracao } from '../../shared/formatar-duracao';
import { YoutubePlayer } from '../../shared/youtube-player/youtube-player';
import type { Review } from '../../models/Review';
import { ReviewService } from '../../services/review';
import { ReviewCard } from '../../shared/review-card/review-card';
import { CurtirBotao } from '../../shared/curtir-botao/curtir-botao';

const TAMANHO_LISTA_REVIEWS = 8;

@Component({
  selector: 'app-musica-detalhe',
  imports: [RouterLink, YoutubePlayer, ReviewCard, CurtirBotao],
  templateUrl: './musica-detalhe.html',
  styleUrls: ['./musica-detalhe.css']
})
export class MusicaDetalhe implements OnInit {

  readonly formatarDuracao = formatarDuracao;

  musica = signal<MusicaResponse | null>(null);
  carregando = signal(false);
  mensagemErro = signal('');

  // Músicas relacionadas do mesmo gênero / artista
  musicasRelacionadas = signal<MusicaListagem[]>([]);
  carregandoRelacionadas = signal(false);

  // Minha review desta música, se existir — usada para trocar o botão
  // "Avaliar" por "Ver minha review" e evitar tentar criar duplicada.
  minhaReview = signal<Review | null>(null);

  // Reviews mais recentes desta música, exibidas na barra lateral.
  reviewsDaMusica = signal<Review[]>([]);

  // Filtros/página da pesquisa que trouxe o usuário até aqui, recebidos via
  // router state (não vão pra URL). Se a página for aberta direto (sem vir
  // da pesquisa), fica vazio e o link de volta cai numa pesquisa em branco.
  voltarQueryParams = signal<Params>({});

  constructor(
    private readonly musicaService: MusicaService,
    private readonly reviewService: ReviewService,
    private readonly route: ActivatedRoute
  ) {}

  ngOnInit(): void {
    this.route.paramMap.subscribe((params) => {
      const id = Number(params.get('id'));
      if (id) {
        this.voltarQueryParams.set(window.history.state?.queryParams ?? {});
        this.carregarMusica(id);
        this.carregarReviews(id);
      }
    });
  }

  private carregarMusica(id: number): void {
    this.carregando.set(true);
    this.mensagemErro.set('');

    this.musicaService
      .buscarPorId(id)
      .pipe(finalize(() => this.carregando.set(false)))
      .subscribe({
        next: (musica) => {
          this.musica.set(musica);
          const generoId = musica.generos?.[0]?.id;
          this.carregarRelacionadas(id, generoId);
        },
        error: (erro: HttpErrorResponse) => {
          console.error(erro);
          this.mensagemErro.set(this.mensagemDeErroPara(erro));
        }
      });
  }

  private carregarRelacionadas(id: number, generoId?: number): void {
    this.carregandoRelacionadas.set(true);
    this.musicaService
      .buscarRelacionadas(id, generoId)
      .pipe(finalize(() => this.carregandoRelacionadas.set(false)))
      .subscribe({
        next: (relacionadas) => this.musicasRelacionadas.set(relacionadas),
        error: () => this.musicasRelacionadas.set([])
      });
  }

  private carregarReviews(id: number): void {
    this.reviewService.listarPorMusica(id, 0, TAMANHO_LISTA_REVIEWS)
      .pipe(catchError(() => of(null)))
      .subscribe(pagina => {
        this.reviewsDaMusica.set(pagina?.itens ?? []);

        const minha = pagina?.itens.find(review => review.minhaReview);
        this.minhaReview.set(minha ?? null);
      });
  }

  private mensagemDeErroPara(erro: HttpErrorResponse): string {
    switch (erro.status) {
      case 0:
        return 'Não foi possível conectar ao servidor. Verifique sua conexão e tente novamente.';
      case 404:
        return 'Música não encontrada.';
      case 401:
        return 'Sua sessão expirou. Faça login novamente.';
      case 403:
        return 'Você não tem permissão para ver essa música.';
      case 500:
        return 'Ocorreu um erro no servidor. Tente novamente mais tarde.';
      default:
        return 'Não foi possível carregar os detalhes da música. Tente novamente.';
    }
  }
}
