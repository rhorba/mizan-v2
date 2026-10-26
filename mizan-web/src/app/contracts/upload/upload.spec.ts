import { HttpEventType, HttpHeaders, provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { Router, provideRouter } from '@angular/router';
import { translocoTesting } from '../../../testing/transloco-testing';
import { Lang, LanguageService } from '../../i18n/language';
import { Upload } from './upload';

const URL = 'http://localhost:8080/api/v1/contracts';
const pdf = (name = 'bail.pdf', size = 1000) =>
  new File([new Uint8Array(size)], name, { type: 'application/pdf' });

describe('Upload', () => {
  async function render(lang: Lang = 'fr') {
    await TestBed.configureTestingModule({
      imports: [Upload, translocoTesting()],
      providers: [provideRouter([]), provideHttpClient(), provideHttpClientTesting()],
    }).compileComponents();
    TestBed.inject(LanguageService).use(lang);
    const fixture = TestBed.createComponent(Upload);
    fixture.detectChanges();
    const el = fixture.nativeElement as HTMLElement;
    return {
      fixture,
      el,
      component: fixture.componentInstance,
      http: TestBed.inject(HttpTestingController),
      router: TestBed.inject(Router),
      // Rendered text, whitespace collapsed as the browser displays it.
      error: () => (el.querySelector('.error')?.textContent ?? '').replace(/\s+/g, ' ').trim(),
      submitButton: () => el.querySelector<HTMLButtonElement>('button[type=submit]')!,
    };
  }

  function choose(el: HTMLElement, file: File) {
    const input = el.querySelector<HTMLInputElement>('input[type=file]')!;
    Object.defineProperty(input, 'files', { value: [file], configurable: true });
    input.dispatchEvent(new Event('change'));
  }

  it('shows the drop zone, the limits and a disabled Analyze button', async () => {
    const { el, submitButton } = await render();
    expect(el.querySelector('.prompt')?.textContent).toContain('Déposez un PDF ici');
    expect(el.querySelector('#upload-limits')?.textContent).toContain('10 Mo');
    expect(el.querySelector('#upload-limits')?.textContent).toContain('30 pages');
    expect(submitButton().disabled).toBe(true);
    expect(el.querySelector('input[type=file]')?.getAttribute('aria-describedby')).toContain(
      'upload-limits',
    );
  });

  it('defaults the analysis language to the UI language', async () => {
    expect((await render('ar')).component.language()).toBe('ar');
  });

  it('rejects obvious non-PDFs and oversized files before uploading, keeping the selection', async () => {
    const { el, fixture, error, submitButton, http } = await render();
    choose(el, new File(['x'], 'photo.png', { type: 'image/png' }));
    await fixture.whenStable();
    expect(error()).toBe("Ce fichier n'est pas un PDF.");
    expect(el.querySelector('.chosen')?.textContent).toBe('photo.png');
    expect(submitButton().disabled).toBe(true);

    choose(el, pdf('big.pdf', 11 * 1024 * 1024));
    await fixture.whenStable();
    expect(error()).toBe('Le fichier dépasse 10 Mo.');
    expect(el.querySelector('input')?.getAttribute('aria-invalid')).toBe('true');
    http.expectNone(URL);
  });

  it('accepts a dropped PDF', async () => {
    const { el, fixture, component } = await render();
    const zone = el.querySelector('.dropzone')!;
    const over = new Event('dragover', { cancelable: true });
    zone.dispatchEvent(over);
    await fixture.whenStable();
    expect(zone.classList).toContain('dragging');
    zone.dispatchEvent(new Event('dragleave'));
    expect(component.dragging()).toBe(false);

    const drop = new Event('drop', { cancelable: true }) as DragEvent;
    Object.defineProperty(drop, 'dataTransfer', { value: { files: [pdf('dropped.pdf')] } });
    zone.dispatchEvent(drop);
    await fixture.whenStable();
    expect(over.defaultPrevented).toBe(true);
    expect(drop.defaultPrevented).toBe(true);
    expect(el.querySelector('.chosen')?.textContent).toBe('dropped.pdf');
    expect(el.querySelector('.meta')?.textContent).toContain('1 Ko');
  });

  it('uploads with progress and opens the contract page', async () => {
    const { el, fixture, component, http, router, submitButton } = await render();
    const navigate = vi.spyOn(router, 'navigate').mockResolvedValue(true);
    choose(el, pdf());
    component.language.set('ary');
    await fixture.whenStable();
    submitButton().click();
    await fixture.whenStable();

    const req = http.expectOne(URL);
    const body = req.request.body as FormData;
    expect(body.get('language')).toBe('ary');
    expect((body.get('file') as File).name).toBe('bail.pdf');
    expect(submitButton().textContent).toContain('Lecture du PDF');

    req.event({ type: HttpEventType.UploadProgress, loaded: 500, total: 1000 });
    await fixture.whenStable();
    expect(component.progress()).toBe(50);
    expect(el.querySelector('mat-progress-bar')).not.toBeNull();

    req.flush({ id: 'c-1', fileName: 'bail.pdf', pageCount: 3, createdAt: '', analysis: null });
    expect(navigate).toHaveBeenCalledWith(['/contracts', 'c-1']);
  });

  it.each([
    ['too-many-pages', 400, 'Le PDF dépasse 30 pages.'],
    [
      'no-text-layer',
      400,
      'Ce PDF ne contient pas de texte (document scanné). Utilisez un PDF texte.',
    ],
    ['encrypted', 400, 'Ce PDF est protégé par un mot de passe.'],
    ['malware-detected', 422, "L'analyse antivirus a bloqué ce fichier."],
    ['budget-exceeded', 429, "Le budget d'analyse du jour est épuisé. Réessayez demain."],
  ])('maps the %s problem to its message and keeps the file', async (slug, status, message) => {
    const { el, fixture, http, error, component, submitButton } = await render();
    choose(el, pdf());
    await fixture.whenStable();
    submitButton().click();
    http
      .expectOne(URL)
      .flush({ type: `urn:mizan:problem:${slug}`, status }, { status, statusText: 'x' });
    await fixture.whenStable();
    expect(error()).toBe(message);
    expect(component.file()?.name).toBe('bail.pdf');
    expect(component.uploading()).toBe(false);
  });

  it('tells how long to wait when rate limited', async () => {
    const { el, fixture, http, error, submitButton } = await render();
    choose(el, pdf());
    await fixture.whenStable();
    submitButton().click();
    http.expectOne(URL).flush(
      { type: 'urn:mizan:problem:rate-limited', status: 429 },
      {
        status: 429,
        statusText: 'Too Many Requests',
        headers: new HttpHeaders({ 'Retry-After': '7' }),
      },
    );
    await fixture.whenStable();
    expect(error()).toBe("Trop d'envois en peu de temps. Réessayez dans 7 s.");
  });

  it('shows megabytes above 1 MB', async () => {
    const { el, fixture } = await render();
    choose(el, pdf('big.pdf', 2.5 * 1024 * 1024));
    await fixture.whenStable();
    expect(el.querySelector('.meta')?.textContent).toContain('2.5 Mo');
  });

  it('allows retrying the same file after a retryable error, not after a final one', async () => {
    const { el, fixture, http, submitButton, error } = await render();
    choose(el, pdf());
    await fixture.whenStable();
    submitButton().click();
    http
      .expectOne(URL)
      .flush({ type: 'urn:mizan:problem:scanner-unavailable' }, { status: 503, statusText: 'x' });
    await fixture.whenStable();
    expect(submitButton().disabled).toBe(false);

    submitButton().click();
    await fixture.whenStable();
    expect(error()).toBe('');
    http
      .expectOne(URL)
      .flush({ type: 'urn:mizan:problem:encrypted' }, { status: 400, statusText: 'x' });
    await fixture.whenStable();
    expect(submitButton().disabled).toBe(true);
  });

  it('falls back to a generic message for unknown failures', async () => {
    const { el, fixture, http, error, submitButton } = await render('ar');
    choose(el, pdf());
    await fixture.whenStable();
    submitButton().click();
    http.expectOne(URL).error(new ProgressEvent('error'));
    await fixture.whenStable();
    expect(error()).toBe('فشل الإرسال. تحقّق من الاتصال وأعد المحاولة.');
  });

  it('ignores submit without a valid file and clears the selection', async () => {
    const { component, http } = await render();
    component.submit();
    component.select(null);
    expect(component.file()).toBeNull();
    http.expectNone(URL);
  });
});
