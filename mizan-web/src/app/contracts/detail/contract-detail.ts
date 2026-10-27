import { NgTemplateOutlet } from '@angular/common';
import { httpResource } from '@angular/common/http';
import {
  ChangeDetectionStrategy,
  Component,
  DestroyRef,
  computed,
  effect,
  inject,
  input,
  signal,
  untracked,
} from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { MatButtonModule } from '@angular/material/button';
import { MatMenuModule } from '@angular/material/menu';
import { MatTabsModule } from '@angular/material/tabs';
import { RouterLink } from '@angular/router';
import { TranslocoPipe } from '@jsverse/transloco';
import { filter, takeWhile } from 'rxjs';
import {
  AnalysisLanguage,
  AnalysisResult,
  AnalysisStatus,
  AnalysisSummary,
  ContractWithAnalyses,
  ContractsApi,
  problemSlug,
} from '../contracts-api';
import { AnalysisResultView } from './analysis-result';
import { AnalysisStepper } from './analysis-stepper';
import { StatusEvent, StatusStream } from './status-stream';

const FINAL: ReadonlySet<AnalysisStatus> = new Set(['COMPLETE', 'FAILED']);
const LANGUAGES: readonly AnalysisLanguage[] = ['fr', 'ar', 'ary'];

/**
 * The contract page: where the analysis is, live (Story 3.4), then its result (Story 4.1), with
 * one tab per language and a new analysis in another language on request (Story 4.3).
 */
@Component({
  selector: 'app-contract-detail',
  imports: [
    AnalysisResultView,
    AnalysisStepper,
    MatButtonModule,
    MatMenuModule,
    MatTabsModule,
    NgTemplateOutlet,
    RouterLink,
    TranslocoPipe,
  ],
  templateUrl: './contract-detail.html',
  styleUrl: './contract-detail.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class ContractDetail {
  private readonly api = inject(ContractsApi);
  private readonly stream = inject(StatusStream);
  private readonly destroyRef = inject(DestroyRef);

  /** Bound from the :id route parameter (withComponentInputBinding). */
  readonly id = input.required<string>();
  readonly contract = httpResource<ContractWithAnalyses>(() => this.api.url(this.id()));
  readonly notFound = computed(() => problemSlug(this.contract.error()) === 'contract-not-found');

  /** Latest pushed status; wins over the loaded one for the same analysis. */
  private readonly live = signal<StatusEvent | null>(null);
  /** value() throws while the resource is in error (404, 500): read it only when present. */
  private readonly analysis = computed(() =>
    this.contract.hasValue() ? this.contract.value().analysis : null,
  );

  /**
   * One tab per language: its newest result, else its newest analysis (a later failed retry never
   * hides a result). Oldest language first, so a new tab goes last.
   */
  readonly tabs = computed<AnalysisSummary[]>(() => {
    const newestFirst = this.contract.hasValue() ? this.contract.value().analyses : [];
    const byLanguage = new Map<AnalysisLanguage, AnalysisSummary>();
    for (const a of newestFirst) {
      const kept = byLanguage.get(a.language);
      if (!kept || (kept.status !== 'COMPLETE' && a.status === 'COMPLETE')) {
        byLanguage.set(a.language, a);
      }
    }
    return [...byLanguage.values()].sort((x, y) => x.requestedAt.localeCompare(y.requestedAt));
  });

  /** The tab the user picked; null follows the newest analysis. */
  private readonly picked = signal<string | null>(null);
  readonly selected = computed(
    () => this.tabs().find((a) => a.id === this.picked()) ?? this.analysis(),
  );
  readonly selectedIndex = computed(() =>
    Math.max(
      0,
      this.tabs().findIndex((a) => a.id === this.selected()?.id),
    ),
  );

  readonly status = computed<AnalysisStatus | null>(() => {
    const analysis = this.selected();
    return analysis ? this.statusOf(analysis) : null;
  });

  /**
   * Story 4.1: the result, loaded once the analysis is COMPLETE. Keyed on a URL string so the
   * contract reload that follows completion (a new analysis object, same id) does not fetch the
   * result a second time.
   */
  private readonly resultUrl = computed(() => {
    const analysis = this.selected();
    return analysis && this.status() === 'COMPLETE'
      ? this.api.analysisUrl(this.id(), analysis.id)
      : undefined;
  });
  readonly result = httpResource<AnalysisResult>(() => this.resultUrl());

  /** Story 4.3: languages still without a result, offered once nothing is running. */
  readonly offered = computed<AnalysisLanguage[]>(() => {
    const latest = this.analysis();
    const tabs = this.tabs();
    if (!latest || !FINAL.has(this.statusOf(latest)) || !tabs.some((a) => this.isDone(a))) {
      return [];
    }
    return LANGUAGES.filter((l) => !tabs.some((a) => a.language === l && this.isDone(a)));
  });
  readonly requesting = signal(false);
  /** i18n key of the last refused request. */
  readonly requestError = signal<string | null>(null);

  private watching: string | null = null;

  constructor() {
    effect(() => {
      const analysis = this.analysis();
      if (analysis && !FINAL.has(analysis.status) && this.watching !== analysis.id) {
        untracked(() => this.watch(analysis.id));
      }
    });
  }

  statusOf(analysis: AnalysisSummary): AnalysisStatus {
    const pushed = this.live();
    return pushed?.analysisId === analysis.id ? pushed.status : analysis.status;
  }

  select(index: number): void {
    this.picked.set(this.tabs()[index]?.id ?? null);
  }

  reanalyze(language: AnalysisLanguage): void {
    this.requesting.set(true);
    this.requestError.set(null);
    this.api
      .reanalyze(this.id(), language)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (analysis) => {
          this.requesting.set(false);
          this.picked.set(analysis.id);
          this.contract.reload(); // the new tab, then its live status
        },
        error: (error: unknown) => {
          this.requesting.set(false);
          const slug = problemSlug(error);
          this.requestError.set(slug ? `upload.errors.${slug}` : 'detail.reanalyze.error');
        },
      });
  }

  private isDone(analysis: AnalysisSummary): boolean {
    return this.statusOf(analysis) === 'COMPLETE';
  }

  private watch(analysisId: string): void {
    this.watching = analysisId;
    this.stream
      .watch(this.api.url(this.id()))
      .pipe(
        filter((event) => event.analysisId === analysisId),
        takeWhile((event) => !FINAL.has(event.status), true),
        takeUntilDestroyed(this.destroyRef),
      )
      .subscribe((event) => {
        this.live.set(event);
        if (FINAL.has(event.status)) {
          this.contract.reload(); // the final status, and with it the result (Story 4.1)
        }
      });
  }
}
