import { TestBed } from '@angular/core/testing';
import { MAT_DIALOG_DATA, MatDialogRef } from '@angular/material/dialog';
import { translocoTesting } from '../../testing/transloco-testing';
import { LanguageService } from '../i18n/language';
import { ConfirmDelete } from './confirm-delete';

describe('ConfirmDelete', () => {
  it('names the file, warns that analyses go too, and closes with the answer', async () => {
    const close = vi.fn();
    await TestBed.configureTestingModule({
      imports: [ConfirmDelete, translocoTesting()],
      providers: [
        { provide: MAT_DIALOG_DATA, useValue: { fileName: 'عقد.pdf' } },
        { provide: MatDialogRef, useValue: { close } },
      ],
    }).compileComponents();
    TestBed.inject(LanguageService).use('fr');
    const fixture = TestBed.createComponent(ConfirmDelete);
    await fixture.whenStable();
    const el = fixture.nativeElement as HTMLElement;

    expect(el.querySelector('h2')?.textContent).toContain('Supprimer ce contrat');
    expect(el.querySelector('.file')?.textContent).toBe('عقد.pdf');
    expect(el.textContent).toContain('toutes ses analyses');

    el.querySelector<HTMLButtonElement>('.cancel')!.click();
    expect(close).toHaveBeenLastCalledWith(false);
    el.querySelector<HTMLButtonElement>('.confirm')!.click();
    expect(close).toHaveBeenLastCalledWith(true);
  });
});
