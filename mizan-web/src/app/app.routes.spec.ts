import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { provideRouter, Router, withComponentInputBinding } from '@angular/router';
import { RouterTestingHarness } from '@angular/router/testing';
import { fakeAuth } from '../testing/fake-auth';
import { translocoTesting } from '../testing/transloco-testing';
import { routes } from './app.routes';
import { Contracts } from './contracts/contracts';
import { ContractDetail } from './contracts/detail/contract-detail';
import { Upload } from './contracts/upload/upload';

const API = 'http://localhost:8080/api/v1';
import { Landing } from './landing/landing';

describe('routes', () => {
  function setup(signedIn: boolean) {
    const { fake, provider } = fakeAuth(signedIn);
    TestBed.configureTestingModule({
      imports: [translocoTesting()],
      providers: [
        provideRouter(routes, withComponentInputBinding()),
        provideHttpClient(),
        provideHttpClientTesting(),
        provider,
      ],
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

  it('opens /contracts, /contracts/new and /contracts/:id for signed-in users', async () => {
    setup(true);
    const harness = await RouterTestingHarness.create();
    const http = TestBed.inject(HttpTestingController);
    // The pages load data on arrival; flush it so the harness can become stable.
    const list = harness.navigateByUrl('/contracts', Contracts);
    await vi.waitFor(() =>
      http.expectOne(`${API}/me/quota`).flush({ limit: 3, used: 0, remaining: 3 }),
    );
    expect(await list).toBeInstanceOf(Contracts);

    expect(await harness.navigateByUrl('/contracts/new', Upload)).toBeInstanceOf(Upload);

    const detail = harness.navigateByUrl('/contracts/c-1', ContractDetail);
    await vi.waitFor(() =>
      http
        .expectOne(`${API}/contracts/c-1`)
        .flush({ id: 'c-1', fileName: 'a.pdf', pageCount: 1, createdAt: '', analysis: null }),
    );
    expect((await detail).id()).toBe('c-1');
  });

  it('guards /contracts: signed-out users go to Keycloak', async () => {
    const fake = setup(false);
    const harness = await RouterTestingHarness.create();
    await harness.navigateByUrl('/contracts');
    expect(fake.login).toHaveBeenCalled();
    expect(TestBed.inject(Router).url).not.toBe('/contracts');
  });
});
