import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { provideRouter, Router } from '@angular/router';
import { RouterTestingHarness } from '@angular/router/testing';
import { fakeAuth } from '../testing/fake-auth';
import { translocoTesting } from '../testing/transloco-testing';
import { routes } from './app.routes';
import { Contracts } from './contracts/contracts';
import { Landing } from './landing/landing';

describe('routes', () => {
  function setup(signedIn: boolean) {
    const { fake, provider } = fakeAuth(signedIn);
    TestBed.configureTestingModule({
      imports: [translocoTesting()],
      providers: [provideRouter(routes), provideHttpClient(), provideHttpClientTesting(), provider],
    });
    return fake;
  }

  it('shows the landing page at /', async () => {
    setup(false);
    const harness = await RouterTestingHarness.create();
    expect(await harness.navigateByUrl('/', Landing)).toBeInstanceOf(Landing);
  });

  it('sends unknown paths back to the landing page', async () => {
    setup(false);
    const harness = await RouterTestingHarness.create();
    await harness.navigateByUrl('/nope');
    expect(TestBed.inject(Router).url).toBe('/');
  });

  it('opens /contracts for signed-in users', async () => {
    setup(true);
    const harness = await RouterTestingHarness.create();
    expect(await harness.navigateByUrl('/contracts', Contracts)).toBeInstanceOf(Contracts);
  });

  it('guards /contracts: signed-out users go to Keycloak', async () => {
    const fake = setup(false);
    const harness = await RouterTestingHarness.create();
    await harness.navigateByUrl('/contracts');
    expect(fake.login).toHaveBeenCalled();
    expect(TestBed.inject(Router).url).not.toBe('/contracts');
  });
});
