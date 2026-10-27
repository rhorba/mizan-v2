import {
  ChangeDetectionStrategy,
  Component,
  ElementRef,
  computed,
  effect,
  inject,
  input,
  signal,
  viewChild,
} from '@angular/core';
import { TranslocoPipe } from '@jsverse/transloco';
import { LanguageService } from '../i18n/language';
import { StatsOverview } from './admin-api';

type Day = StatsOverview['days'][number];

/** Stack order, bottom to top: red and green never touch (validated palette, decisions.md). */
export const SERIES = ['done', 'running', 'failed'] as const;
export type Series = (typeof SERIES)[number];

/**
 * Chart box in CSS pixels: the width follows the container (ResizeObserver) so text stays at its
 * real size at any width; 640 until measured (and in tests, which have no layout).
 */
const DEFAULT_WIDTH = 640;
const H = 220;
const PAD = { top: 12, right: 8, bottom: 28, left: 36 } as const;
const MAX_BAR = 24;
const GAP = 2;
const RADIUS = 4;
/** Room one day label needs ("27/10" at 11px plus air), to space them without collisions. */
const LABEL_ROOM = 44;

export interface Segment {
  series: Series;
  y: number;
  height: number;
  /** Path for the topmost segment (rounded data end); null for a plain rect. */
  path: string | null;
}

export interface Column {
  day: string;
  x: number;
  counts: Record<Series, number>;
  total: number;
  segments: Segment[];
}

/** Clean axis maximum: 1, 2 or 5 × 10ⁿ steps, four of them, at least 4. */
export function niceMax(value: number): { max: number; step: number } {
  const raw = Math.max(value, 4) / 4;
  const power = 10 ** Math.floor(Math.log10(raw));
  const step = [1, 2, 5, 10].map((m) => m * power).find((s) => s >= raw)!;
  return { max: step * 4, step };
}

/** A bar whose top corners are rounded and whose base stays square on the baseline. */
export function roundedTop(x: number, y: number, w: number, h: number, r: number): string {
  const rr = Math.min(r, h, w / 2);
  return (
    `M${x},${y + h}V${y + rr}Q${x},${y} ${x + rr},${y}` +
    `H${x + w - rr}Q${x + w},${y} ${x + w},${y + rr}V${y + h}Z`
  );
}

/**
 * Story 5.2: analyses per day as stacked columns (done, in progress, failed), with a legend of
 * totals, a hover/focus tooltip per day and the same numbers as a table.
 */
@Component({
  selector: 'app-daily-chart',
  imports: [TranslocoPipe],
  templateUrl: './daily-chart.html',
  styleUrl: './daily-chart.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class DailyChart {
  private readonly language = inject(LanguageService);

  /** Oldest first, every day present. */
  readonly days = input.required<Day[]>();

  readonly series = SERIES;
  readonly baseline = H - PAD.bottom;

  private readonly plot = viewChild<ElementRef<HTMLElement>>('plot');
  private readonly width = signal(DEFAULT_WIDTH);
  readonly box = computed(() => ({ w: this.width(), h: H, pad: PAD }));

  /** Index of the column under the pointer or keyboard focus. */
  readonly hovered = signal<number | null>(null);

  readonly totals = computed(() => {
    const totals: Record<Series, number> = { done: 0, running: 0, failed: 0 };
    this.columns().forEach((c) => SERIES.forEach((k) => (totals[k] += c.counts[k])));
    return totals;
  });

  readonly scale = computed(() =>
    niceMax(Math.max(0, ...this.days().map((d) => this.total(d.statuses)))),
  );

  readonly ticks = computed(() => {
    const { max, step } = this.scale();
    return [0, 1, 2, 3, 4].map((i) => ({ value: i * step, y: this.y(i * step, max) }));
  });

  readonly slot = computed(
    () => (this.width() - PAD.left - PAD.right) / Math.max(1, this.days().length),
  );
  /** Thin marks: at most 24px, and never more than 60% of a day's slot. */
  readonly bar = computed(() => Math.min(MAX_BAR, Math.floor(this.slot() * 0.6)));

  readonly columns = computed<Column[]>(() => {
    const days = this.days();
    const { max } = this.scale();
    const slot = this.slot();
    const bar = this.bar();
    return days.map((d, i) => {
      const counts: Record<Series, number> = {
        done: d.statuses.COMPLETE,
        running: d.statuses.PENDING + d.statuses.ANALYZING,
        failed: d.statuses.FAILED,
      };
      const x = PAD.left + i * slot + (slot - bar) / 2;
      const present = SERIES.filter((s) => counts[s] > 0);
      const segments: Segment[] = [];
      let top = this.baseline;
      present.forEach((series, k) => {
        const full = this.baseline - this.y(counts[series], max);
        // The 2px surface gap comes out of the segment below it, never out of the total height.
        const height = Math.max(1, full - (k < present.length - 1 ? GAP : 0));
        const y = top - full + (k < present.length - 1 ? GAP : 0);
        const last = k === present.length - 1;
        segments.push({
          series,
          y,
          height,
          path: last ? roundedTop(x, y, bar, height, RADIUS) : null,
        });
        top -= full;
      });
      return { day: d.day, x, counts, total: this.total(d.statuses), segments };
    });
  });

  readonly hoveredColumn = computed(() => {
    const i = this.hovered();
    return i === null ? null : (this.columns()[i] ?? null);
  });

  /** As many day labels as fit without colliding, always ending on today. */
  readonly labelled = computed(() => {
    const n = this.columns().length;
    const stride = Math.max(1, Math.ceil(LABEL_ROOM / this.slot()));
    return new Set(this.columns().flatMap((_, i) => ((n - 1 - i) % stride === 0 ? [i] : [])));
  });

  constructor() {
    effect((onCleanup) => {
      const el = this.plot()?.nativeElement;
      if (!el || typeof ResizeObserver === 'undefined') {
        return;
      }
      const observer = new ResizeObserver(([entry]) => {
        const measured = Math.round(entry.contentRect.width);
        if (measured > 0) {
          this.width.set(measured);
        }
      });
      observer.observe(el);
      onCleanup(() => observer.disconnect());
    });
  }

  private readonly locale = computed(() => (this.language.current() === 'ar' ? 'ar-MA' : 'fr-MA'));
  private readonly integer = computed(() => new Intl.NumberFormat(this.locale()));
  // Days are plain dates: format them in UTC so no zone shifts them.
  private readonly dayShort = computed(
    () =>
      new Intl.DateTimeFormat(this.locale(), { day: 'numeric', month: 'numeric', timeZone: 'UTC' }),
  );
  private readonly dayLong = computed(
    () =>
      new Intl.DateTimeFormat(this.locale(), {
        weekday: 'short',
        day: 'numeric',
        month: 'long',
        timeZone: 'UTC',
      }),
  );

  int(n: number): string {
    return this.integer().format(n);
  }

  shortDay(day: string): string {
    return this.dayShort().format(new Date(`${day}T00:00:00Z`));
  }

  longDay(day: string): string {
    return this.dayLong().format(new Date(`${day}T00:00:00Z`));
  }

  private y(value: number, max: number): number {
    return this.baseline - (value / max) * (this.baseline - PAD.top);
  }

  private total(s: Day['statuses']): number {
    return s.PENDING + s.ANALYZING + s.COMPLETE + s.FAILED;
  }
}
