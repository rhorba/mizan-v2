import { DOCUMENT } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { TranslocoService } from '@jsverse/transloco';
import { translocoTesting } from '../../testing/transloco-testing';
import { LanguageService } from './language';

describe('LanguageService', () => {
  let navigatorLanguage: string;

  beforeEach(() => {
    localStorage.clear();
    navigatorLanguage = 'fr-FR';
    vi.spyOn(navigator, 'language', 'get').mockImplementation(() => navigatorLanguage);
    TestBed.configureTestingModule({ imports: [translocoTesting()] });
  });

  afterEach(() => vi.restoreAllMocks());

  const service = () => TestBed.inject(LanguageService);
  const html = () => TestBed.inject(DOCUMENT).documentElement;

  it('defaults to French, left-to-right', () => {
    service().init();
    expect(service().current()).toBe('fr');
    expect(html().lang).toBe('fr');
    expect(html().dir).toBe('ltr');
  });

  it('follows an Arabic browser', () => {
    navigatorLanguage = 'ar-MA';
    service().init();
    expect(service().current()).toBe('ar');
    expect(service().dir()).toBe('rtl');
    expect(html().dir).toBe('rtl');
  });

  it('prefers the stored choice over the browser and remembers toggles', () => {
    navigatorLanguage = 'ar-MA';
    localStorage.setItem('mizan.lang', 'fr');
    service().init();
    expect(service().current()).toBe('fr');

    service().toggle();
    expect(service().current()).toBe('ar');
    expect(TestBed.inject(TranslocoService).getActiveLang()).toBe('ar');
    expect(localStorage.getItem('mizan.lang')).toBe('ar');
    expect(service().other()).toBe('fr');
  });

  it('ignores unknown stored values', () => {
    localStorage.setItem('mizan.lang', 'xx');
    service().init();
    expect(service().current()).toBe('fr');
  });

  it('still works when storage is unavailable', () => {
    vi.spyOn(Storage.prototype, 'getItem').mockImplementation(() => {
      throw new Error('blocked');
    });
    vi.spyOn(Storage.prototype, 'setItem').mockImplementation(() => {
      throw new Error('blocked');
    });
    service().init();
    service().use('ar');
    expect(service().current()).toBe('ar');
  });
});
