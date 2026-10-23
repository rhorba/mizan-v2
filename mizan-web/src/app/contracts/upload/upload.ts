import { HttpErrorResponse, HttpEventType } from '@angular/common/http';
import {
  ChangeDetectionStrategy,
  Component,
  DestroyRef,
  computed,
  inject,
  signal,
} from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { FormsModule } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { MatSelectModule } from '@angular/material/select';
import { Router, RouterLink } from '@angular/router';
import { TranslocoPipe } from '@jsverse/transloco';
import { LanguageService } from '../../i18n/language';
import {
  AnalysisLanguage,
  ContractsApi,
  ProblemSlug,
  UPLOAD_LIMITS,
  problemSlug,
} from '../contracts-api';

type UploadError = ProblemSlug | 'generic';

/** Worth retrying with the same file; the others need a different file. */
const RETRYABLE: ReadonlySet<UploadError> = new Set([
  'rate-limited',
  'scanner-unavailable',
  'generic',
]);

/** Story 2.2: drop or choose a PDF, pick the analysis language, upload with progress. */
@Component({
  selector: 'app-upload',
  imports: [
    FormsModule,
    MatButtonModule,
    MatProgressBarModule,
    MatSelectModule,
    RouterLink,
    TranslocoPipe,
  ],
  templateUrl: './upload.html',
  styleUrl: './upload.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class Upload {
  private readonly api = inject(ContractsApi);
  private readonly router = inject(Router);
  private readonly destroyRef = inject(DestroyRef);

  readonly limits = UPLOAD_LIMITS;
  readonly languages: AnalysisLanguage[] = ['fr', 'ar', 'ary'];

  readonly file = signal<File | null>(null);
  readonly language = signal<AnalysisLanguage>(inject(LanguageService).current());
  readonly dragging = signal(false);
  readonly progress = signal<number | null>(null);
  readonly error = signal<UploadError | null>(null);
  readonly retryAfter = signal<number | null>(null);
  readonly uploading = computed(() => this.progress() !== null);
  readonly canSubmit = computed(() => {
    const error = this.error();
    return !!this.file() && !this.uploading() && (!error || RETRYABLE.has(error));
  });
  /** Size in the unit a person expects: KB under 1 MB, MB with one decimal above. */
  readonly size = computed(() => {
    const bytes = this.file()?.size ?? 0;
    return bytes < 1024 * 1024
      ? { key: 'upload.sizeKb', value: Math.max(1, Math.round(bytes / 1024)) }
      : { key: 'upload.sizeMb', value: (bytes / (1024 * 1024)).toFixed(1) };
  });

  onFileInput(event: Event): void {
    const input = event.target as HTMLInputElement;
    this.select(input.files?.[0] ?? null);
    input.value = ''; // choosing the same file again still fires "change"
  }

  onDragOver(event: DragEvent): void {
    event.preventDefault();
    this.dragging.set(true);
  }

  onDragLeave(): void {
    this.dragging.set(false);
  }

  onDrop(event: DragEvent): void {
    event.preventDefault();
    this.dragging.set(false);
    this.select(event.dataTransfer?.files[0] ?? null);
  }

  /** Obvious problems are reported at once; the server stays the authority. */
  select(file: File | null): void {
    this.error.set(null);
    this.retryAfter.set(null);
    this.file.set(file);
    if (!file) {
      return;
    }
    const looksLikePdf = file.type === 'application/pdf' || /\.pdf$/i.test(file.name);
    if (!looksLikePdf) {
      this.error.set('not-a-pdf');
    } else if (file.size > this.limits.maxBytes) {
      this.error.set('too-large');
    }
  }

  submit(): void {
    const file = this.file();
    if (!file || !this.canSubmit()) {
      return;
    }
    this.error.set(null);
    this.retryAfter.set(null);
    this.progress.set(0);
    this.api
      .upload(file, this.language())
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (event) => {
          if (event.type === HttpEventType.UploadProgress && event.total) {
            this.progress.set(Math.round((100 * event.loaded) / event.total));
          } else if (event.type === HttpEventType.Response && event.body) {
            void this.router.navigate(['/contracts', event.body.id]);
          }
        },
        error: (err: unknown) => {
          this.progress.set(null);
          this.error.set(problemSlug(err) ?? 'generic');
          if (err instanceof HttpErrorResponse && err.status === 429) {
            this.retryAfter.set(Number(err.headers.get('Retry-After')) || null);
          }
        },
      });
  }
}
