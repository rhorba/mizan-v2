import { ChangeDetectionStrategy, Component, inject } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { MAT_DIALOG_DATA, MatDialogModule } from '@angular/material/dialog';
import { TranslocoPipe } from '@jsverse/transloco';

/** Flow 3: deleting removes the text and every analysis, so it is confirmed first. */
@Component({
  selector: 'app-confirm-delete',
  imports: [MatButtonModule, MatDialogModule, TranslocoPipe],
  template: `
    <h2 mat-dialog-title>{{ 'contracts.confirm.title' | transloco }}</h2>
    <mat-dialog-content>
      <p dir="auto" class="file">{{ data.fileName }}</p>
      <p>{{ 'contracts.confirm.body' | transloco }}</p>
    </mat-dialog-content>
    <mat-dialog-actions align="end">
      <button mat-button type="button" class="cancel" [mat-dialog-close]="false">
        {{ 'contracts.confirm.cancel' | transloco }}
      </button>
      <button mat-flat-button type="button" class="confirm" [mat-dialog-close]="true">
        {{ 'contracts.confirm.delete' | transloco }}
      </button>
    </mat-dialog-actions>
  `,
  styles: `
    .file {
      font-weight: 600;
      overflow-wrap: anywhere;
    }
    .confirm {
      --mat-button-filled-container-color: var(--mz-error);
      --mat-button-filled-label-text-color: #ffffff;
    }
  `,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class ConfirmDelete {
  readonly data = inject<{ fileName: string }>(MAT_DIALOG_DATA);
}
