import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { Subject } from 'rxjs';
import { translocoTesting } from '../../../testing/transloco-testing';
import { LanguageService } from '../../i18n/language';
import { ContractDetail } from './contract-detail';
import { StatusEvent, StatusStream } from './status-stream';

const URL = 'http://localhost:8080/api/v1/contracts/c-1';

describe('ContractDetail', () => {
  let events: Subject<StatusEvent>;
  let stream: { watch: ReturnType<typeof vi.fn> };

  async function render() {
    events = new Subject<StatusEvent>();
    stream = { watch: vi.fn(() => events) };
    await TestBed.configureTestingModule({
      imports: [ContractDetail, translocoTesting()],
      providers: [
        provideRouter([]),
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: StatusStream, useValue: stream },
      ],
    }).compileComponents();
    TestBed.inject(LanguageService).use('fr');
    const fixture = TestBed.createComponent(ContractDetail);
    fixture.componentRef.setInput('id', 'c-1');
    fixture.detectChanges();
    return {
      fixture,
      el: fixture.nativeElement as HTMLElement,
      http: TestBed.inject(HttpTestingController),
    };
  }

  const contract = (status: string | null) => {
    const analysis = status ? { id: 'a-1', language: 'fr', status, requestedAt: '' } : null;
    return {
      id: 'c-1',
      fileName: 'a.pdf',
      pageCount: 1,
      createdAt: '',
      analysis,
      analyses: analysis ? [analysis] : [],
    };
  };
  const current = (el: HTMLElement) => el.querySelector('[aria-current=step]')?.textContent?.trim();

  it('shows file, pages, language and the pending status', async () => {
    const { fixture, el, http } = await render();
    expect(el.querySelector('.loading')).not.toBeNull();
    http.expectOne(URL).flush({
      id: 'c-1',
      fileName: 'عقد كراء.pdf',
      pageCount: 3,
      createdAt: '2026-10-23T10:00:00Z',
      analysis: { id: 'a-1', language: 'ary', status: 'PENDING', requestedAt: '' },
      analyses: [{ id: 'a-1', language: 'ary', status: 'PENDING', requestedAt: '' }],
    });
    await fixture.whenStable();
    expect(el.querySelector('h1')?.textContent).toBe('عقد كراء.pdf');
    expect(el.querySelector('h1')?.getAttribute('dir')).toBe('auto');
    expect(el.querySelector('.meta')?.textContent).toContain('3 page(s)');
    expect(el.querySelector('.meta')?.textContent).toContain('Darija');
    expect(current(el)).toBe('En attente');
    expect(el.querySelector('.next')?.textContent).toContain("file d'attente");
  });

  it('follows the live status until the analysis is final, then reloads the contract', async () => {
    const { fixture, el, http } = await render();
    http.expectOne(URL).flush(contract('PENDING'));
    await fixture.whenStable();
    expect(stream.watch).toHaveBeenCalledWith(URL);

    events.next({ analysisId: 'a-other', status: 'COMPLETE' }); // another analysis: ignored
    events.next({ analysisId: 'a-1', status: 'ANALYZING' });
    fixture.detectChanges();
    expect(current(el)).toBe('Analyse en cours');
    expect(el.querySelector('.next')?.textContent).toContain('Claude lit votre contrat');

    events.next({ analysisId: 'a-1', status: 'COMPLETE' });
    fixture.detectChanges();
    // Both start at once: the contract reload and (Story 4.1) the result of that analysis.
    http.expectOne(URL).flush(contract('COMPLETE'));
    http.expectOne(`${URL}/analyses/a-1`).flush({
      id: 'a-1',
      language: 'fr',
      status: 'COMPLETE',
      summary: 'Résumé du contrat.',
      failureReason: null,
      requestedAt: '',
      completedAt: '',
      flags: [],
    });
    // Resources publish their value a tick after the response (whenStable hangs here instead).
    await new Promise((resolve) => setTimeout(resolve));
    fixture.detectChanges();
    http.verify(); // nothing else was requested
    expect(current(el)).toBe('Terminée');
    expect(el.querySelector('app-analysis-result .summary')?.textContent).toContain(
      'Résumé du contrat.',
    );
    expect(events.observed).toBe(false); // stopped listening
    expect(stream.watch).toHaveBeenCalledTimes(1);
  });

  it('shows a failure in words and does not watch a final analysis', async () => {
    const { fixture, el, http } = await render();
    http.expectOne(URL).flush(contract('FAILED'));
    await fixture.whenStable();
    expect(current(el)).toBe('Échec');
    expect(el.querySelector('.next.failed')?.textContent).toContain("L'analyse a échoué");
    expect(stream.watch).not.toHaveBeenCalled();
  });

  it('a contract without analysis shows no stepper and opens no stream', async () => {
    const { fixture, el, http } = await render();
    http.expectOne(URL).flush(contract(null));
    await fixture.whenStable();
    expect(el.querySelector('app-analysis-stepper')).toBeNull();
    expect(stream.watch).not.toHaveBeenCalled();
  });

  describe('other languages (Story 4.3)', () => {
    const a = (id: string, language: string, status: string, minute: number) => ({
      id,
      language,
      status,
      requestedAt: `2026-10-27T10:0${minute}:00Z`,
    });
    const withAnalyses = (...newestFirst: ReturnType<typeof a>[]) => ({
      ...contract(null),
      analysis: newestFirst[0],
      analyses: newestFirst,
    });
    const result = (id: string, summary: string) => ({
      id,
      language: 'fr',
      status: 'COMPLETE',
      summary,
      failureReason: null,
      requestedAt: '',
      completedAt: '',
      flags: [],
    });
    const tick = () => new Promise((resolve) => setTimeout(resolve));
    /** whenStable() would wait for the pending result request: one tick, then render. */
    const settle = async (fixture: { detectChanges(): void }) => {
      fixture.detectChanges();
      await tick();
      fixture.detectChanges();
    };
    const tabLabels = (el: HTMLElement) =>
      [...el.querySelectorAll('[role=tab]')].map((t) => t.textContent?.replace(/\s+/g, ' ').trim());
    const menuItems = () => [...document.querySelectorAll<HTMLButtonElement>('[mat-menu-item]')];

    it('shows one tab per language, newest selected, and loads the picked tab’s result', async () => {
      const { fixture, el, http } = await render();
      http
        .expectOne(URL)
        .flush(
          withAnalyses(
            a('a-2', 'ar', 'COMPLETE', 3),
            a('a-retry', 'fr', 'FAILED', 2),
            a('a-1', 'fr', 'COMPLETE', 1),
          ),
        );
      await settle(fixture);
      http.expectOne(`${URL}/analyses/a-2`).flush(result('a-2', 'ملخص'));
      await tick();
      fixture.detectChanges();

      // A later failed French retry (admin) never hides the French result; oldest language first.
      expect(tabLabels(el)).toEqual(['Français', 'العربية']);
      expect(el.querySelector('[role=tab][aria-selected=true]')?.textContent).toContain('العربية');
      expect(el.querySelector('.meta')?.textContent).not.toContain('Français');
      expect(el.querySelector('app-analysis-result .summary')?.textContent).toContain('ملخص');

      el.querySelectorAll<HTMLElement>('[role=tab]')[0].click();
      fixture.detectChanges();
      await settle(fixture);
      http.expectOne(`${URL}/analyses/a-1`).flush(result('a-1', 'Résumé'));
      await tick();
      fixture.detectChanges();
      expect(el.querySelector('app-analysis-result .summary')?.textContent).toContain('Résumé');
    });

    it('offers the missing languages, starts one, then follows it in a new tab', async () => {
      const { fixture, el, http } = await render();
      http.expectOne(URL).flush(contract('COMPLETE'));
      await settle(fixture);
      http.expectOne(`${URL}/analyses/a-1`).flush(result('a-1', 'Résumé'));
      await tick();
      fixture.detectChanges();

      el.querySelector<HTMLButtonElement>('.reanalyze button')!.click();
      fixture.detectChanges();
      expect(menuItems().map((b) => b.textContent?.trim())).toEqual([
        'Analyser en arabe',
        'Analyser en darija',
      ]);
      menuItems()[0].click();

      const post = http.expectOne({ method: 'POST', url: `${URL}/analyses` });
      expect(post.request.body).toEqual({ language: 'ar' });
      post.flush(a('a-2', 'ar', 'PENDING', 2));
      await settle(fixture);
      http
        .expectOne(URL)
        .flush(withAnalyses(a('a-2', 'ar', 'PENDING', 2), a('a-1', 'fr', 'COMPLETE', 1)));
      await tick();
      fixture.detectChanges();

      expect(tabLabels(el)).toEqual(['Français', 'العربية · En attente']);
      expect(el.querySelector('[role=tab][aria-selected=true]')?.textContent).toContain('العربية');
      expect(current(el)).toBe('En attente');
      expect(stream.watch).toHaveBeenCalledWith(URL);
      // Nothing more to offer while it runs.
      expect(el.querySelector('.reanalyze')).toBeNull();

      events.next({ analysisId: 'a-2', status: 'ANALYZING' });
      fixture.detectChanges();
      expect(tabLabels(el)[1]).toBe('العربية · Analyse en cours');
    });

    it('shows why a request was refused', async () => {
      const { fixture, el, http } = await render();
      http.expectOne(URL).flush(contract('COMPLETE'));
      await settle(fixture);
      http.expectOne(`${URL}/analyses/a-1`).flush(result('a-1', 'Résumé'));
      await tick();
      fixture.detectChanges();

      el.querySelector<HTMLButtonElement>('.reanalyze button')!.click();
      fixture.detectChanges();
      menuItems()[1].click();
      http
        .expectOne({ method: 'POST', url: `${URL}/analyses` })
        .flush(
          { type: 'urn:mizan:problem:quota-exceeded', status: 429 },
          { status: 429, statusText: 'Too Many Requests' },
        );
      fixture.detectChanges();
      expect(el.querySelector('[role=alert]')?.textContent).toContain('analyses du jour');
      expect(el.querySelector<HTMLButtonElement>('.reanalyze button')!.disabled).toBe(false);

      el.querySelector<HTMLButtonElement>('.reanalyze button')!.click();
      fixture.detectChanges();
      menuItems()[1].click();
      http
        .expectOne({ method: 'POST', url: `${URL}/analyses` })
        .flush(null, { status: 0, statusText: 'Unknown Error' });
      fixture.detectChanges();
      expect(el.querySelector('[role=alert]')?.textContent).toContain('La demande a échoué');
    });

    it('offers nothing before a first result or once every language is done', async () => {
      const { fixture, el, http } = await render();
      http.expectOne(URL).flush(contract('FAILED'));
      await settle(fixture);
      expect(el.querySelector('.reanalyze')).toBeNull();

      fixture.componentInstance.contract.reload();
      await settle(fixture);
      http
        .expectOne(URL)
        .flush(
          withAnalyses(
            a('a-3', 'ary', 'COMPLETE', 3),
            a('a-2', 'ar', 'COMPLETE', 2),
            a('a-1', 'fr', 'COMPLETE', 1),
          ),
        );
      await settle(fixture);
      http.expectOne(`${URL}/analyses/a-3`).flush(result('a-3', 'x'));
      await tick();
      fixture.detectChanges();
      expect(tabLabels(el)).toHaveLength(3);
      expect(el.querySelector('.reanalyze')).toBeNull();
    });
  });

  it('says "not found" for 404 (foreign or deleted)', async () => {
    const { fixture, el, http } = await render();
    http
      .expectOne(URL)
      .flush(
        { type: 'urn:mizan:problem:contract-not-found', status: 404 },
        { status: 404, statusText: 'Not Found' },
      );
    await fixture.whenStable();
    expect(el.querySelector('h1')?.textContent).toContain('Contrat introuvable');
  });

  it('offers a retry on other errors', async () => {
    const { fixture, el, http } = await render();
    http.expectOne(URL).flush(null, { status: 500, statusText: 'Boom' });
    await fixture.whenStable();
    expect(el.querySelector('.error')?.textContent).toContain('Impossible de charger');
    el.querySelector<HTMLButtonElement>('button')!.click();
    fixture.detectChanges();
    http.expectOne(URL).flush(contract(null));
    await fixture.whenStable();
    expect(el.querySelector('h1')?.textContent).toBe('a.pdf');
  });
});
