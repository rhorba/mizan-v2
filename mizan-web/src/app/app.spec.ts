import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { fakeAuth } from '../testing/fake-auth';
import { translocoTesting } from '../testing/transloco-testing';
import { App } from './app';
import { LanguageService } from './i18n/language';

describe('App shell', () => {
  async function render(signedIn = false) {
    localStorage.clear();
    const { fake, provider } = fakeAuth(signedIn);
    await TestBed.configureTestingModule({
      imports: [App, translocoTesting()],
      providers: [provideRouter([]), provider],
    }).compileComponents();
    const fixture = TestBed.createComponent(App);
    TestBed.inject(LanguageService).use('fr');
    await fixture.whenStable();
    return { fixture, fake, el: fixture.nativeElement as HTMLElement };
  }

  it('renders brand, skip link, outlet and the not-legal-advice footer', async () => {
    const { el } = await render();
    expect(el.querySelector('.brand')?.textContent).toContain('Mizan');
    expect(el.querySelector('.skip-link')?.getAttribute('href')).toBe('#main');
    expect(el.querySelector('router-outlet')).not.toBeNull();
    expect(el.querySelector('footer')?.textContent).toContain('pas un avis juridique');
  });

  it('switches to Arabic and right-to-left from the toolbar button', async () => {
    const { fixture, el } = await render();
    const button = el.querySelector<HTMLButtonElement>('.lang-switch')!;
    expect(button.querySelector('[lang]')?.textContent?.trim()).toBe('العربية');
    expect(button.querySelector('[lang]')?.getAttribute('lang')).toBe('ar');
    expect(button.querySelector('.visually-hidden')?.textContent).toBe('Afficher en arabe');
    expect(button.hasAttribute('aria-label')).toBe(false);

    button.click();
    await fixture.whenStable();

    expect(document.documentElement.dir).toBe('rtl');
    expect(document.documentElement.lang).toBe('ar');
    expect(el.querySelector('.shell')?.getAttribute('dir')).toBe('rtl');
    expect(el.querySelector('footer')?.textContent).toContain('ليس استشارة قانونية');
    expect(button.querySelector('[lang]')?.textContent?.trim()).toBe('Français');
    expect(button.querySelector('[lang]')?.getAttribute('lang')).toBe('fr');
  });

  it('signed out: offers login, no contracts link', async () => {
    const { el, fake } = await render(false);
    expect(el.querySelector('nav')).toBeNull();
    el.querySelector<HTMLButtonElement>('.login')!.click();
    expect(fake.login).toHaveBeenCalled();
  });

  it('signed in: contracts link and logout', async () => {
    const { el, fake } = await render(true);
    expect(el.querySelector('nav a')?.getAttribute('href')).toBe('/contracts');
    expect(el.querySelector('.login')).toBeNull();
    el.querySelector<HTMLButtonElement>('.logout')!.click();
    expect(fake.logout).toHaveBeenCalled();
  });
});
