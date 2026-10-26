import { ChangeDetectionStrategy, Component, computed, inject, input, signal } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { MatChipsModule } from '@angular/material/chips';
import { MatExpansionModule } from '@angular/material/expansion';
import { MatSnackBar } from '@angular/material/snack-bar';
import { TranslocoPipe, TranslocoService } from '@jsverse/transloco';
import { AnalysisResult, ClauseFlag, RiskLevel } from '../contracts-api';

export type RiskFilter = 'ALL' | RiskLevel;

const RISK_ORDER: Record<RiskLevel, number> = { HIGH: 0, MEDIUM: 1, LOW: 2 };

/** Arabic and Darija results read right to left whatever the interface language is. */
const RTL_LANGUAGES = new Set(['ar', 'ary']);

/**
 * Story 4.1: summary and flags of a COMPLETE analysis. Flags are sorted HIGH → LOW (model order
 * within a level), filterable by level, expandable, with a Copy button for the suggested wording.
 */
@Component({
  selector: 'app-analysis-result',
  imports: [MatButtonModule, MatChipsModule, MatExpansionModule, TranslocoPipe],
  templateUrl: './analysis-result.html',
  styleUrl: './analysis-result.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class AnalysisResultView {
  private readonly snackBar = inject(MatSnackBar);
  private readonly transloco = inject(TranslocoService);

  readonly result = input.required<AnalysisResult>();
  readonly filter = signal<RiskFilter>('ALL');

  readonly dir = computed(() => (RTL_LANGUAGES.has(this.result().language) ? 'rtl' : 'ltr'));

  readonly sorted = computed(() =>
    [...this.result().flags].sort(
      (a, b) => RISK_ORDER[a.riskLevel] - RISK_ORDER[b.riskLevel] || a.position - b.position,
    ),
  );

  readonly counts = computed(() => {
    const counts: Record<RiskFilter, number> = { ALL: 0, HIGH: 0, MEDIUM: 0, LOW: 0 };
    for (const flag of this.result().flags) {
      counts[flag.riskLevel]++;
      counts.ALL++;
    }
    return counts;
  });

  /** "All" first, then only the levels that have flags. */
  readonly filters = computed(() =>
    (['ALL', 'HIGH', 'MEDIUM', 'LOW'] as const).filter((f) => f === 'ALL' || this.counts()[f] > 0),
  );

  readonly visible = computed(() => {
    const filter = this.filter();
    return filter === 'ALL' ? this.sorted() : this.sorted().filter((f) => f.riskLevel === filter);
  });

  select(filter: RiskFilter | undefined): void {
    // A listbox can be deselected by clicking the selected chip: fall back to "All".
    this.filter.set(filter ?? 'ALL');
  }

  async copy(flag: ClauseFlag): Promise<void> {
    if (!flag.suggestedCorrection) {
      return;
    }
    let key = 'result.copied';
    try {
      await navigator.clipboard.writeText(flag.suggestedCorrection);
    } catch {
      key = 'result.copyFailed';
    }
    this.snackBar.open(this.transloco.translate(key), undefined, { duration: 3000 });
  }
}
