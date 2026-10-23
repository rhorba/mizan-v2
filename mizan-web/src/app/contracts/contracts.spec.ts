import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { MatDialog } from '@angular/material/dialog';
import { MatSnackBar } from '@angular/material/snack-bar';
import { provideRouter } from '@angular/router';
import { of } from 'rxjs';
import { translocoTesting } from '../../testing/transloco-testing';
import { Lang, LanguageService } from '../i18n/language';
import { ConfirmDelete } from './confirm-delete';
import { ContractSummary } from './contracts-api';
import { Contracts } from './contracts';

const API = 'http://localhost:8080/api/v1';
const QUOTA_URL = `${API}/me/quota`;
const HISTORY_URL = `${API}/contracts`;

const contract = (
  n: number,
  status: 'PENDING' | 'COMPLETE' | null = 'PENDING',
): ContractSummary => ({
  id: `c-${n}`,
  fileName: `Contrat ${n}.pdf`,
  pageCount: n,
  createdAt: '2026-10-23T10:15:00Z',
  analysis: status ? { id: `a-${n}`, language: 'fr', status, requestedAt: '' } : null,
});

describe('Contracts', () => {
  let confirmAnswer: boolean;
  const dialog = { open: vi.fn(() => ({ afterClosed: () => of(confirmAnswer) })) };
  const snackBar = { open: vi.fn() };

  beforeEach(async () => {
    confirmAnswer = true;
    dialog.open.mockClear();
    snackBar.open.mockClear();
    await TestBed.configureTestingModule({
      imports: [Contracts, translocoTesting()],
      providers: [
        provideRouter([]),
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: MatDialog, useValue: dialog },
        { provide: MatSnackBar, useValue: snackBar },
      ],
    }).compileComponents();
  });

  /** Renders and answers the first history page (the pending request blocks whenStable). */
  async function render(
    lang: Lang,
    firstPage: ContractSummary[] = [],
    nextCursor: string | null = null,
  ) {
    TestBed.inject(LanguageService).use(lang);
    const fixture = TestBed.createComponent(Contracts);
    fixture.detectChanges();
    const http = TestBed.inject(HttpTestingController);
    http.expectOne(HISTORY_URL).flush({ items: firstPage, nextCursor });
    return { fixture, el: fixture.nativeElement as HTMLElement, http };
  }

  const chip = (el: HTMLElement) => el.querySelector('.quota-chip')?.textContent?.trim();
  const names = (el: HTMLElement) =>
    Array.from(el.querySelectorAll('.history .name')).map((a) => a.textContent?.trim());

  it('shows the empty history with a call to action, and a loading quota', async () => {
    const { fixture, el, http } = await render('fr');
    fixture.detectChanges();
    expect(el.querySelector('h1')?.textContent).toContain('Mes contrats');
    expect(el.querySelector('.empty h2')?.textContent).toContain('Aucun contrat');
    expect(el.querySelector('.actions a')?.getAttribute('href')).toBe('/contracts/new');
    expect(el.querySelector('.quota-loading')).not.toBeNull();
    expect(el.querySelector('.load-more')).toBeNull();
    http.expectOne(QUOTA_URL);
  });

  it.each([
    ['fr', 3, "3 analyses restantes aujourd'hui"],
    ['fr', 1, "1 analyse restante aujourd'hui"],
    ['fr', 0, "0 analyse restante aujourd'hui"],
    ['ar', 3, '3 تحليلات متبقية اليوم'],
    ['ar', 2, 'تحليلان متبقيان اليوم'],
    ['ar', 1, 'تحليل واحد متبقٍّ اليوم'],
    ['ar', 0, 'لا تحليلات متبقية اليوم'],
  ] as const)('%s with %i left uses the right plural form', async (lang, remaining, text) => {
    const { fixture, el, http } = await render(lang);
    http.expectOne(QUOTA_URL).flush({ limit: 3, used: 3 - remaining, remaining });
    await fixture.whenStable();
    expect(chip(el)).toBe(text);
  });

  it('offers a retry when the quota cannot be loaded', async () => {
    const { fixture, el, http } = await render('fr');
    http.expectOne(QUOTA_URL).flush(null, { status: 503, statusText: 'Unavailable' });
    await fixture.whenStable();
    expect(el.querySelector('.quota-error')?.textContent).toContain('Quota indisponible');

    el.querySelector<HTMLButtonElement>('.quota button')!.click();
    fixture.detectChanges();
    http.expectOne(QUOTA_URL).flush({ limit: 3, used: 0, remaining: 3 });
    await fixture.whenStable();
    expect(chip(el)).toBe("3 analyses restantes aujourd'hui");
  });

  it('lists contracts with links, dates, pages and status chips', async () => {
    const { fixture, el, http } = await render('fr', [
      contract(2),
      contract(1, 'COMPLETE'),
      contract(3, null),
    ]);
    http.expectOne(QUOTA_URL).flush({ limit: 3, used: 0, remaining: 3 });
    await fixture.whenStable();
    expect(names(el)).toEqual(['Contrat 2.pdf', 'Contrat 1.pdf', 'Contrat 3.pdf']);
    const first = el.querySelector('.history .row')!;
    expect(first.querySelector('a')?.getAttribute('href')).toBe('/contracts/c-2');
    expect(first.querySelector('.when')?.textContent).toMatch(/2026/);
    expect(first.querySelector('.pages')?.textContent).toContain('2 page(s)');
    expect(first.querySelector('app-status-chip')?.textContent).toContain('En attente');
    expect(el.querySelectorAll('app-status-chip')).toHaveLength(2);
    expect(first.querySelector('.delete')?.getAttribute('aria-label')).toBe(
      'Supprimer Contrat 2.pdf',
    );
    expect(el.querySelector('.empty')).toBeNull();
  });

  it('loads more with the cursor and stops when there is no next page', async () => {
    const { fixture, el, http } = await render('fr', [contract(1)], 'cur-1');
    http.expectOne(QUOTA_URL).flush({ limit: 3, used: 0, remaining: 3 });
    await fixture.whenStable();

    el.querySelector<HTMLButtonElement>('.load-more')!.click();
    fixture.detectChanges();
    expect(el.querySelector('.loading')).not.toBeNull();
    http.expectOne(`${HISTORY_URL}?cursor=cur-1`).flush({ items: [contract(2)], nextCursor: null });
    await fixture.whenStable();

    expect(names(el)).toEqual(['Contrat 1.pdf', 'Contrat 2.pdf']);
    expect(el.querySelector('.load-more')).toBeNull();
  });

  it('a failed first page can be retried', async () => {
    TestBed.inject(LanguageService).use('fr');
    const fixture = TestBed.createComponent(Contracts);
    fixture.detectChanges();
    const http = TestBed.inject(HttpTestingController);
    http.expectOne(HISTORY_URL).flush(null, { status: 500, statusText: 'Boom' });
    http.expectOne(QUOTA_URL).flush({ limit: 3, used: 0, remaining: 3 });
    await fixture.whenStable();
    const el = fixture.nativeElement as HTMLElement;
    expect(el.querySelector('.more .error')?.textContent).toContain('Impossible de charger');
    expect(el.querySelector('.empty')).toBeNull();

    el.querySelector<HTMLButtonElement>('.retry')!.click();
    http.expectOne(HISTORY_URL).flush({ items: [contract(1)], nextCursor: null });
    await fixture.whenStable();
    expect(names(el)).toEqual(['Contrat 1.pdf']);
  });

  it('a failed next page retries that page, keeping what is shown', async () => {
    const { fixture, el, http } = await render('fr', [contract(1)], 'cur-1');
    http.expectOne(QUOTA_URL).flush({ limit: 3, used: 0, remaining: 3 });
    await fixture.whenStable();
    el.querySelector<HTMLButtonElement>('.load-more')!.click();
    http.expectOne(`${HISTORY_URL}?cursor=cur-1`).flush(null, { status: 502, statusText: 'x' });
    await fixture.whenStable();
    expect(names(el)).toEqual(['Contrat 1.pdf']);

    el.querySelector<HTMLButtonElement>('.retry')!.click();
    http.expectOne(`${HISTORY_URL}?cursor=cur-1`).flush({ items: [contract(2)], nextCursor: null });
    await fixture.whenStable();
    expect(names(el)).toEqual(['Contrat 1.pdf', 'Contrat 2.pdf']);
  });

  it('deletes after confirmation: row removed and a snackbar', async () => {
    const { fixture, el, http } = await render('fr', [contract(1), contract(2)]);
    http.expectOne(QUOTA_URL).flush({ limit: 3, used: 0, remaining: 3 });
    await fixture.whenStable();

    el.querySelector<HTMLButtonElement>('.delete')!.click();
    expect(dialog.open).toHaveBeenCalledWith(ConfirmDelete, {
      data: { fileName: 'Contrat 1.pdf' },
      autoFocus: 'dialog',
    });
    const req = http.expectOne(`${HISTORY_URL}/c-1`);
    expect(req.request.method).toBe('DELETE');
    req.flush(null, { status: 204, statusText: 'No Content' });
    await fixture.whenStable();

    expect(names(el)).toEqual(['Contrat 2.pdf']);
    expect(snackBar.open).toHaveBeenCalledWith('Contrat supprimé.', undefined, { duration: 4000 });
  });

  it('cancelling the dialog deletes nothing', async () => {
    confirmAnswer = false;
    const { fixture, el, http } = await render('fr', [contract(1)]);
    http.expectOne(QUOTA_URL).flush({ limit: 3, used: 0, remaining: 3 });
    await fixture.whenStable();
    el.querySelector<HTMLButtonElement>('.delete')!.click();
    http.expectNone(`${HISTORY_URL}/c-1`);
    expect(names(el)).toEqual(['Contrat 1.pdf']);
  });

  it('a failed delete keeps the row and says so', async () => {
    const { fixture, el, http } = await render('ar', [contract(1)]);
    http.expectOne(QUOTA_URL).flush({ limit: 3, used: 0, remaining: 3 });
    await fixture.whenStable();
    el.querySelector<HTMLButtonElement>('.delete')!.click();
    http.expectOne(`${HISTORY_URL}/c-1`).flush(null, { status: 500, statusText: 'x' });
    await fixture.whenStable();
    expect(names(el)).toEqual(['Contrat 1.pdf']);
    expect(snackBar.open).toHaveBeenCalledWith('فشل الحذف. أعد المحاولة.', undefined, {
      duration: 4000,
    });
  });
});
