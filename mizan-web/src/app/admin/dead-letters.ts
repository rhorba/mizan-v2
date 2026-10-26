import { HttpErrorResponse } from '@angular/common/http';
import {
  ChangeDetectionStrategy,
  Component,
  DestroyRef,
  OnInit,
  computed,
  inject,
  signal,
} from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { MatButtonModule } from '@angular/material/button';
import { MatSnackBar } from '@angular/material/snack-bar';
import { TranslocoPipe, TranslocoService } from '@jsverse/transloco';
import { StatusChip } from '../contracts/status-chip';
import { LanguageService } from '../i18n/language';
import { AdminApi, DeadLetter } from './admin-api';

/** Admin: analysis requests that ended in the DLT, and a retry for the FAILED ones. */
@Component({
  selector: 'app-dead-letters',
  imports: [MatButtonModule, StatusChip, TranslocoPipe],
  templateUrl: './dead-letters.html',
  styleUrl: './dead-letters.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class DeadLetters implements OnInit {
  private readonly api = inject(AdminApi);
  private readonly snackBar = inject(MatSnackBar);
  private readonly transloco = inject(TranslocoService);
  private readonly language = inject(LanguageService);
  private readonly destroyRef = inject(DestroyRef);

  readonly letters = signal<DeadLetter[]>([]);
  readonly loading = signal(false);
  readonly loaded = signal(false);
  readonly failed = signal(false);
  /** Analysis ids with a retry in flight (button disabled meanwhile). */
  readonly retrying = signal<ReadonlySet<string>>(new Set());
  readonly empty = computed(() => this.loaded() && this.letters().length === 0);

  private readonly dateFormat = computed(
    () =>
      new Intl.DateTimeFormat(this.language.current(), {
        dateStyle: 'medium',
        timeStyle: 'short',
      }),
  );

  ngOnInit(): void {
    this.load();
  }

  load(): void {
    this.loading.set(true);
    this.failed.set(false);
    this.api
      .deadLetters()
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (letters) => {
          this.letters.set(letters);
          this.loaded.set(true);
          this.loading.set(false);
        },
        error: () => {
          this.failed.set(true);
          this.loading.set(false);
        },
      });
  }

  formatDate(iso: string): string {
    return this.dateFormat().format(new Date(iso));
  }

  /** First 8 characters: enough to find the row in logs, short enough for the table. */
  shortId(id: string | null): string {
    return id ? id.slice(0, 8) : '—';
  }

  canRetry(letter: DeadLetter): boolean {
    return letter.analysisId !== null && letter.status === 'FAILED';
  }

  retry(letter: DeadLetter): void {
    const id = letter.analysisId;
    if (!id) {
      return;
    }
    this.retrying.update((s) => new Set(s).add(id));
    this.api
      .retry(id)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: () => {
          this.setStatus(id, 'PENDING');
          this.done(id, 'admin.deadLetters.retried');
        },
        error: (error: unknown) => {
          const slug = this.slug(error);
          if (slug === 'analysis-not-found') {
            this.setStatus(id, null);
            this.done(id, 'admin.deadLetters.notFound');
          } else if (slug === 'analysis-not-failed') {
            this.done(id, 'admin.deadLetters.notFailed');
            this.load();
          } else {
            this.done(id, 'admin.deadLetters.retryFailed');
          }
        },
      });
  }

  private setStatus(analysisId: string, status: DeadLetter['status']): void {
    this.letters.update((all) =>
      all.map((l) => (l.analysisId === analysisId ? { ...l, status } : l)),
    );
  }

  private done(analysisId: string, messageKey: string): void {
    this.retrying.update((s) => {
      const next = new Set(s);
      next.delete(analysisId);
      return next;
    });
    this.snackBar.open(this.transloco.translate(messageKey), undefined, { duration: 4000 });
  }

  /** Admin problem slugs (analysis-not-found/-failed) are not in the user-facing list. */
  private slug(error: unknown): string | null {
    if (!(error instanceof HttpErrorResponse)) {
      return null;
    }
    const type = (error.error as { type?: unknown } | null)?.type;
    return typeof type === 'string' ? type.replace('urn:mizan:problem:', '') : null;
  }
}
