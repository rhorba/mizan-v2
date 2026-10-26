import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { MatSnackBar } from '@angular/material/snack-bar';
import { translocoTesting } from '../../testing/transloco-testing';
import { LanguageService } from '../i18n/language';
import { DeadLetter } from './admin-api';
import { DeadLetters } from './dead-letters';

const API = 'http://localhost:8080/api/v1/admin';

const letter = (n: number, status: DeadLetter['status'] = 'FAILED'): DeadLetter => ({
  partition: 0,
  offset: n,
  failedAt: '2026-10-26T10:15:00Z',
  analysisId: `a${n}aaaaaaa-0000-0000-0000-000000000000`,
  contractId: `c-${n}`,
  language: 'ar',
  error: 'IllegalStateException: simulated timeout',
  status,
});

describe('DeadLetters', () => {
  const snackBar = { open: vi.fn() };

  beforeEach(async () => {
    snackBar.open.mockClear();
    await TestBed.configureTestingModule({
      imports: [DeadLetters, translocoTesting()],
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: MatSnackBar, useValue: snackBar },
      ],
    }).compileComponents();
    TestBed.inject(LanguageService).use('fr');
  });

  async function render(letters: DeadLetter[]) {
    const fixture = TestBed.createComponent(DeadLetters);
    fixture.detectChanges();
    const http = TestBed.inject(HttpTestingController);
    http.expectOne(`${API}/dead-letters`).flush(letters);
    await fixture.whenStable();
    fixture.detectChanges();
    const el = fixture.nativeElement as HTMLElement;
    const rows = () => Array.from(el.querySelectorAll('tbody tr'));
    const buttons = () => Array.from(el.querySelectorAll<HTMLButtonElement>('tbody button'));
    return { fixture, el, http, rows, buttons };
  }

  it('says so when nothing failed', async () => {
    const { el } = await render([]);
    expect(el.querySelector('h1')?.textContent).toContain('Analyses en échec');
    expect(el.querySelector('.empty')?.textContent).toContain('Aucune analyse en échec');
    expect(el.querySelector('table')).toBeNull();
  });

  it('lists dead letters with error and status, and offers retry only for FAILED ones', async () => {
    const unreadable: DeadLetter = {
      ...letter(3),
      analysisId: null,
      contractId: null,
      language: null,
      status: null,
    };
    const { rows, buttons } = await render([letter(1), letter(2, 'PENDING'), unreadable]);

    expect(rows()).toHaveLength(3);
    expect(rows()[0].textContent).toContain('a1aaaaaa');
    expect(rows()[0].textContent).toContain('IllegalStateException: simulated timeout');
    expect(rows()[0].textContent).toContain('Échec');
    expect(rows()[1].textContent).toContain('En attente');
    expect(rows()[2].textContent).toContain('Message illisible');
    expect(buttons()).toHaveLength(1);
    expect(buttons()[0].textContent).toContain('Relancer');
  });

  it('retries, then shows the analysis as pending without the button', async () => {
    const { fixture, http, rows, buttons } = await render([letter(1)]);

    buttons()[0].click();
    fixture.detectChanges();
    expect(buttons()[0].disabled).toBe(true);
    const req = http.expectOne(`${API}/analyses/a1aaaaaaa-0000-0000-0000-000000000000/retry`);
    expect(req.request.method).toBe('POST');
    req.flush(null, { status: 202, statusText: 'Accepted' });
    await fixture.whenStable();
    fixture.detectChanges();

    expect(rows()[0].textContent).toContain('En attente');
    expect(buttons()).toHaveLength(0);
    expect(snackBar.open).toHaveBeenCalledWith(
      'Analyse relancée : elle repasse en attente.',
      undefined,
      { duration: 4000 },
    );
  });

  it('marks the contract as deleted when the analysis is gone', async () => {
    const { fixture, http, rows } = await render([letter(1)]);

    (fixture.nativeElement as HTMLElement)
      .querySelector<HTMLButtonElement>('tbody button')!
      .click();
    http
      .expectOne(`${API}/analyses/a1aaaaaaa-0000-0000-0000-000000000000/retry`)
      .flush(
        { type: 'urn:mizan:problem:analysis-not-found', status: 404 },
        { status: 404, statusText: 'Not Found' },
      );
    await fixture.whenStable();
    fixture.detectChanges();

    expect(rows()[0].textContent).toContain('Contrat supprimé');
    expect(snackBar.open).toHaveBeenCalledWith(
      "Cette analyse n'existe plus (contrat supprimé).",
      undefined,
      { duration: 4000 },
    );
  });

  it('reloads when the analysis is no longer failed', async () => {
    const { fixture, http } = await render([letter(1)]);

    (fixture.nativeElement as HTMLElement)
      .querySelector<HTMLButtonElement>('tbody button')!
      .click();
    http
      .expectOne(`${API}/analyses/a1aaaaaaa-0000-0000-0000-000000000000/retry`)
      .flush(
        { type: 'urn:mizan:problem:analysis-not-failed', status: 409 },
        { status: 409, statusText: 'Conflict' },
      );
    http.expectOne(`${API}/dead-letters`).flush([letter(1, 'COMPLETE')]);
    await fixture.whenStable();
    fixture.detectChanges();

    expect((fixture.nativeElement as HTMLElement).textContent).toContain('Terminée');
  });

  it('shows a generic message when the retry fails otherwise, and an error when loading fails', async () => {
    const { fixture, http } = await render([letter(1)]);
    (fixture.nativeElement as HTMLElement)
      .querySelector<HTMLButtonElement>('tbody button')!
      .click();
    http
      .expectOne(`${API}/analyses/a1aaaaaaa-0000-0000-0000-000000000000/retry`)
      .flush(null, { status: 500, statusText: 'Server Error' });
    expect(snackBar.open).toHaveBeenCalledWith('La relance a échoué. Réessayez.', undefined, {
      duration: 4000,
    });

    fixture.componentInstance.load();
    http.expectOne(`${API}/dead-letters`).flush(null, { status: 403, statusText: 'Forbidden' });
    await fixture.whenStable();
    fixture.detectChanges();
    expect(
      (fixture.nativeElement as HTMLElement).querySelector('[role=alert]')?.textContent,
    ).toContain('Impossible de lire');
  });
});
