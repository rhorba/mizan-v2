import { ChangeDetectionStrategy, Component, computed, input } from '@angular/core';
import { TranslocoPipe } from '@jsverse/transloco';
import { AnalysisStatus } from '../contracts-api';

type StepState = 'done' | 'current' | 'todo';

/**
 * Story 3.4: where an analysis is. Words and shapes carry the state, never colour alone
 * (docs/ui §2); the current step has aria-current="step" and the list announces changes.
 */
@Component({
  selector: 'app-analysis-stepper',
  imports: [TranslocoPipe],
  template: `
    <ol class="stepper" [attr.aria-label]="'detail.stepper.label' | transloco" aria-live="polite">
      @for (step of steps(); track step.key) {
        <li
          [class]="'step ' + step.state + ' ' + step.key"
          [attr.aria-current]="step.state === 'current' ? 'step' : null"
        >
          <span class="mark" aria-hidden="true"></span>
          <span class="name">{{ step.label | transloco }}</span>
        </li>
      }
    </ol>
  `,
  styles: `
    .stepper {
      display: flex;
      flex-wrap: wrap;
      gap: var(--mz-sp-md);
      margin: var(--mz-sp-md) 0;
      padding: 0;
      list-style: none;
    }
    .step {
      display: flex;
      align-items: center;
      gap: var(--mz-sp-xs);
      color: var(--mz-text-muted);
      font-size: var(--mz-fs-sm);
    }
    .mark {
      inline-size: 12px;
      block-size: 12px;
      border: 2px solid currentColor;
      border-radius: 50%;
    }
    .done {
      color: var(--mz-primary);
    }
    .done .mark {
      background: currentColor;
    }
    .current {
      color: var(--mz-text);
      font-weight: 700;
    }
    .current .mark {
      border-width: 4px;
    }
    .current.analyzing .mark {
      animation: pulse 1.2s ease-in-out infinite;
    }
    .failed {
      color: var(--mz-error);
    }
    .failed .mark {
      border-radius: 2px;
      background: currentColor;
    }
    @keyframes pulse {
      50% {
        opacity: 0.35;
      }
    }
    @media (prefers-reduced-motion: reduce) {
      .current.analyzing .mark {
        animation: none;
      }
    }
  `,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class AnalysisStepper {
  readonly status = input.required<AnalysisStatus>();

  readonly steps = computed(() => {
    const status = this.status();
    const at = status === 'PENDING' ? 0 : status === 'ANALYZING' ? 1 : 2;
    const last = status === 'FAILED' ? 'failed' : 'complete';
    return ['pending', 'analyzing', last].map((key, i) => ({
      key,
      state: (i < at ? 'done' : i === at ? 'current' : 'todo') as StepState,
      label: `detail.stepper.${key}`,
    }));
  });
}
