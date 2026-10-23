import { httpResource } from '@angular/common/http';
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
import { MatChipsModule } from '@angular/material/chips';
import { MatDialog } from '@angular/material/dialog';
import { MatSnackBar } from '@angular/material/snack-bar';
import { RouterLink } from '@angular/router';
import { TranslocoPipe, TranslocoService } from '@jsverse/transloco';
import { filter, switchMap } from 'rxjs';
import { environment } from '../../environments/environment';
import { LanguageService } from '../i18n/language';
import { ConfirmDelete } from './confirm-delete';
import { ContractSummary, ContractsApi } from './contracts-api';
import { StatusChip } from './status-chip';

export interface Quota {
  limit: number;
  used: number;
  remaining: number;
}

/** FR-6 history (keyset pages of 20, newest first), delete, and today's quota. */
@Component({
  selector: 'app-contracts',
  imports: [MatButtonModule, MatChipsModule, RouterLink, StatusChip, TranslocoPipe],
  templateUrl: './contracts.html',
  styleUrl: './contracts.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class Contracts implements OnInit {
  private readonly api = inject(ContractsApi);
  private readonly dialog = inject(MatDialog);
  private readonly snackBar = inject(MatSnackBar);
  private readonly transloco = inject(TranslocoService);
  private readonly destroyRef = inject(DestroyRef);
  private readonly language = inject(LanguageService);

  readonly quota = httpResource<Quota>(() => `${environment.apiUrl}/api/v1/me/quota`);

  readonly items = signal<ContractSummary[]>([]);
  readonly nextCursor = signal<string | null>(null);
  readonly loading = signal(false);
  readonly loaded = signal(false);
  readonly failed = signal(false);
  readonly empty = computed(() => this.loaded() && this.items().length === 0);

  /** CLDR plural category: Arabic has zero/one/two/few/many/other, French one/other. */
  readonly quotaKey = computed(() => {
    const remaining = this.quota.value()?.remaining ?? 0;
    const category = new Intl.PluralRules(this.language.current()).select(remaining);
    return `contracts.quota.${category}`;
  });

  /** Dates in the reader's language (Arabic gets Arabic month names). */
  readonly dateFormat = computed(
    () =>
      new Intl.DateTimeFormat(this.language.current(), {
        dateStyle: 'medium',
        timeStyle: 'short',
      }),
  );

  ngOnInit(): void {
    this.load(null);
  }

  loadMore(): void {
    this.load(this.nextCursor());
  }

  /** Retries whichever page failed: the first one, or the next one. */
  retry(): void {
    this.load(this.loaded() ? this.nextCursor() : null);
  }

  formatDate(iso: string): string {
    return this.dateFormat().format(new Date(iso));
  }

  delete(contract: ContractSummary): void {
    this.dialog
      .open(ConfirmDelete, { data: { fileName: contract.fileName }, autoFocus: 'dialog' })
      .afterClosed()
      .pipe(
        filter((confirmed) => confirmed === true),
        switchMap(() => this.api.delete(contract.id)),
        takeUntilDestroyed(this.destroyRef),
      )
      .subscribe({
        next: () => {
          this.items.update((list) => list.filter((c) => c.id !== contract.id));
          this.notify('contracts.deleted');
        },
        error: () => this.notify('contracts.deleteFailed'),
      });
  }

  private load(cursor: string | null): void {
    this.loading.set(true);
    this.failed.set(false);
    this.api
      .history(cursor)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (page) => {
          this.items.update((list) => (cursor ? [...list, ...page.items] : page.items));
          this.nextCursor.set(page.nextCursor);
          this.loaded.set(true);
          this.loading.set(false);
        },
        error: () => {
          this.failed.set(true);
          this.loading.set(false);
        },
      });
  }

  private notify(key: string): void {
    this.snackBar.open(this.transloco.translate(key), undefined, { duration: 4000 });
  }
}
