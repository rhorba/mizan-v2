import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { translocoTesting } from '../../../testing/transloco-testing';
import { LanguageService } from '../../i18n/language';
import { ContractDetail } from './contract-detail';

const URL = 'http://localhost:8080/api/v1/contracts/c-1';

describe('ContractDetail', () => {
  async function render() {
    await TestBed.configureTestingModule({
      imports: [ContractDetail, translocoTesting()],
      providers: [provideRouter([]), provideHttpClient(), provideHttpClientTesting()],
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
    expect(el.querySelector('app-status-chip')?.textContent).toContain('En attente');
  });

  it('a contract without analysis shows no chip', async () => {
    const { fixture, el, http } = await render();
    http
      .expectOne(URL)
      .flush({ id: 'c-1', fileName: 'a.pdf', pageCount: 1, createdAt: '', analysis: null });
    await fixture.whenStable();
    expect(el.querySelector('app-status-chip')).toBeNull();
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
