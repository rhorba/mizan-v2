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
import { RouterLink } from '@angular/router';
import { TranslocoPipe } from '@jsverse/transloco';
import { filter, takeWhile } from 'rxjs';
import {
  AnalysisResult,
  AnalysisStatus,
  ContractSummary,
  ContractsApi,
  problemSlug,
} from '../contracts-api';
import { AnalysisResultView } from './analysis-result';
import { AnalysisStepper } from './analysis-stepper';
import { StatusEvent, StatusStream } from './status-stream';

const FINAL: ReadonlySet<AnalysisStatus> = new Set(['COMPLETE', 'FAILED']);

/** The contract page: where the analysis is, live (Story 3.4), then its result (Story 4.1). */
@Component({
  selector: 'app-contract-detail',
  imports: [AnalysisResultView, AnalysisStepper, MatButtonModule, RouterLink, TranslocoPipe],
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
  readonly contract = httpResource<ContractSummary>(() => this.api.url(this.id()));
  readonly notFound = computed(() => problemSlug(this.contract.error()) === 'contract-not-found');

  /** Latest pushed status; wins over the loaded one for the same analysis. */
  private readonly live = signal<StatusEvent | null>(null);
  /** value() throws while the resource is in error (404, 500): read it only when present. */
  private readonly analysis = computed(() =>
    this.contract.hasValue() ? this.contract.value().analysis : null,
  );

  readonly status = computed<AnalysisStatus | null>(() => {
    const analysis = this.analysis();
    if (!analysis) {
      return null;
    }
    const pushed = this.live();
    return pushed?.analysisId === analysis.id ? pushed.status : analysis.status;
  });

  /**
   * Story 4.1: the result, loaded once the analysis is COMPLETE. Keyed on a URL string so the
   * contract reload that follows completion (a new analysis object, same id) does not fetch the
   * result a second time.
   */
  private readonly resultUrl = computed(() => {
    const analysis = this.analysis();
    return analysis && this.status() === 'COMPLETE'
      ? this.api.analysisUrl(this.id(), analysis.id)
      : undefined;
  });
  readonly result = httpResource<AnalysisResult>(() => this.resultUrl());

  private watching: string | null = null;

  constructor() {
    effect(() => {
      const analysis = this.analysis();
      if (analysis && !FINAL.has(analysis.status) && this.watching !== analysis.id) {
        untracked(() => this.watch(analysis.id));
      }
    });
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
