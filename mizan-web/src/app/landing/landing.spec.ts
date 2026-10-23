import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { TranslocoService } from '@jsverse/transloco';
import { fakeAuth } from '../../testing/fake-auth';
import { translocoTesting } from '../../testing/transloco-testing';
import { Landing } from './landing';

describe('Landing', () => {
  async function render(lang = 'fr', signedIn = false) {
    const { fake, provider } = fakeAuth(signedIn);
    await TestBed.configureTestingModule({
      imports: [Landing, translocoTesting()],
      providers: [provideRouter([]), provider],
    }).compileComponents();
    TestBed.inject(TranslocoService).setActiveLang(lang);
    const fixture = TestBed.createComponent(Landing);
    await fixture.whenStable();
    return { fake, el: fixture.nativeElement as HTMLElement };
  }

  it('states the promise, three steps and the legal notice (FR-10)', async () => {
    const { el } = await render();
    expect(el.querySelector('h1')?.textContent).toContain('avant de le signer');
    expect(el.querySelectorAll('.how li')).toHaveLength(3);
    expect(el.querySelector('[role="note"]')?.textContent).toContain('Pas un avis juridique');
  });

  it('renders the Arabic copy', async () => {
    const { el } = await render('ar');
    expect(el.querySelector('h1')?.textContent).toContain('قبل أن توقّعه');
  });

  it('signed out: "Try it" opens Keycloak registration, the second button opens login', async () => {
    const { el, fake } = await render();
    el.querySelector<HTMLButtonElement>('.try-it')!.click();
    el.querySelector<HTMLButtonElement>('.sign-in')!.click();
    expect(fake.register).toHaveBeenCalledOnce();
    expect(fake.login).toHaveBeenCalledOnce();
  });

  it('signed in: links straight to the contracts', async () => {
    const { el } = await render('fr', true);
    expect(el.querySelector('.try-it')).toBeNull();
    expect(el.querySelector('.actions a')?.getAttribute('href')).toBe('/contracts');
  });
});
