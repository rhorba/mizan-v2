import { ChangeDetectionStrategy, Component, input } from '@angular/core';
import { TranslocoPipe } from '@jsverse/transloco';
import { AnalysisStatus } from './contracts-api';

/** Status as words plus a shape, never colour alone (docs/ui §2). */
@Component({
  selector: 'app-status-chip',
  imports: [TranslocoPipe],
  template: `<span class="chip" [class]="'chip ' + status().toLowerCase()"
    ><span class="dot" aria-hidden="true"></span>{{ 'status.' + status() | transloco }}</span
  >`,
  styles: `
    .chip {
      display: inline-flex;
      align-items: center;
      gap: 6px;
      padding: 2px 10px;
      border: 1px solid currentColor;
      border-radius: 999px;
      font-size: var(--mz-fs-sm);
      font-weight: 600;
      color: var(--mz-text-muted);
    }
    .dot {
      inline-size: 8px;
      block-size: 8px;
      border-radius: 50%;
      border: 2px solid currentColor;
    }
    .analyzing .dot {
      background: currentColor;
    }
    .complete {
      color: var(--mz-risk-low);
    }
    .complete .dot {
      background: currentColor;
      border-radius: 2px;
    }
    .failed {
      color: var(--mz-error);
    }
    .failed .dot {
      border-radius: 0;
      transform: rotate(45deg);
    }
  `,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class StatusChip {
  readonly status = input.required<AnalysisStatus>();
}
