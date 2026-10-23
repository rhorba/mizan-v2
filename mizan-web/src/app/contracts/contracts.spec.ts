import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { translocoTesting } from '../../testing/transloco-testing';
import { Lang, LanguageService } from '../i18n/language';
import { Contracts } from './contracts';

const QUOTA_URL = 'http://localhost:8080/api/v1/me/quota';

describe('Contracts', () => {
  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [Contracts, translocoTesting()],
      providers: [provideHttpClient(), provideHttpClientTesting()],
    }).compileComponents();
  });

  async function render(lang: Lang) {
    TestBed.inject(LanguageService).use(lang);
    const fixture = TestBed.createComponent(Contracts);
    // Not whenStable(): the pending quota request keeps the fixture unstable until flushed.
    fixture.detectChanges();
    return {
      fixture,
      el: fixture.nativeElement as HTMLElement,
      http: TestBed.inject(HttpTestingController),
    };
  }

  const chip = (el: HTMLElement) => el.querySelector('.quota-chip')?.textContent?.trim();

  it('shows the empty history and a loading state first', async () => {
    const { el, http } = await render('fr');
    expect(el.querySelector('h1')?.textContent).toContain('Mes contrats');
    expect(el.querySelector('.empty h2')?.textContent).toContain('Aucun contrat');
    expect(el.querySelector('.quota-loading')).not.toBeNull();
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
});
