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

  const contract = (status: string | null) => ({
    id: 'c-1',
    fileName: 'a.pdf',
    pageCount: 1,
    createdAt: '',
    analysis: status ? { id: 'a-1', language: 'fr', status, requestedAt: '' } : null,
  });
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
    fixture.detectChanges(); // the reload request is pending now (whenStable would wait on it)
    http.expectOne(URL).flush(contract('COMPLETE'));
    await fixture.whenStable();
    expect(current(el)).toBe('Terminée');
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
    http
      .expectOne(URL)
      .flush({ id: 'c-1', fileName: 'a.pdf', pageCount: 1, createdAt: '', analysis: null });
    await fixture.whenStable();
    expect(el.querySelector('h1')?.textContent).toBe('a.pdf');
  });
});
