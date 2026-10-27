import { httpResource } from '@angular/common/http';
import { ChangeDetectionStrategy, Component, computed, inject } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { RouterLink } from '@angular/router';
import { TranslocoPipe } from '@jsverse/transloco';
import { RiskLevel } from '../contracts/contracts-api';
import { LanguageService } from '../i18n/language';
import { AdminApi, StatsOverview } from './admin-api';
import { DailyChart } from './daily-chart';

const RISKS: readonly RiskLevel[] = ['HIGH', 'MEDIUM', 'LOW'];

/** Admin overview (Story 5.2): today against the global cap, 14 days of analyses, risk split. */
@Component({
  selector: 'app-admin-stats',
  imports: [DailyChart, MatButtonModule, RouterLink, TranslocoPipe],
  templateUrl: './admin-stats.html',
  styleUrl: './admin-stats.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class AdminStats {
  private readonly api = inject(AdminApi);
  private readonly language = inject(LanguageService);

  readonly stats = httpResource<StatsOverview>(() => this.api.statsUrl);
  private readonly data = computed(() => (this.stats.hasValue() ? this.stats.value() : null));

  readonly risks = RISKS;

  readonly riskMax = computed(() => {
    const r = this.data()?.risks;
    return r ? Math.max(1, ...RISKS.map((k) => r[k])) : 1;
  });

  private readonly locale = computed(() => (this.language.current() === 'ar' ? 'ar-MA' : 'fr-MA'));
  private readonly integer = computed(() => new Intl.NumberFormat(this.locale()));
  private readonly compact = computed(
    () => new Intl.NumberFormat(this.locale(), { notation: 'compact', maximumFractionDigits: 1 }),
  );
  private readonly usd = computed(
    () =>
      new Intl.NumberFormat(this.locale(), {
        style: 'currency',
        currency: 'USD',
        maximumFractionDigits: 2,
        minimumFractionDigits: 2,
      }),
  );
  private readonly percent = computed(
    () => new Intl.NumberFormat(this.locale(), { style: 'percent', maximumFractionDigits: 1 }),
  );
  /** Share of the global cap used today, 0–1, for the meter. */
  readonly capUsed = computed(() => {
    const t = this.data()?.today;
    return t && t.cap > 0 ? Math.min(1, t.analyses / t.cap) : 0;
  });

  int(n: number): string {
    return this.integer().format(n);
  }

  tokens(n: number): string {
    return this.compact().format(n);
  }

  money(n: number): string {
    return this.usd().format(n);
  }

  rate(r: number): string {
    return this.percent().format(r);
  }

  riskWidth(count: number): number {
    return (count / this.riskMax()) * 100;
  }
}
