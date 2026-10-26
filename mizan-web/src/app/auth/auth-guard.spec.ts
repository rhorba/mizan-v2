import { TestBed } from '@angular/core/testing';
import {
  ActivatedRouteSnapshot,
  RouterStateSnapshot,
  UrlTree,
  provideRouter,
} from '@angular/router';
import { fakeAuth } from '../../testing/fake-auth';
import { adminGuard, authGuard } from './auth-guard';

describe('authGuard', () => {
  const run = (url = '/contracts/new') =>
    TestBed.runInInjectionContext(() =>
      authGuard({} as ActivatedRouteSnapshot, { url } as RouterStateSnapshot),
    );

  it('lets signed-in users through', () => {
    const { fake, provider } = fakeAuth(true);
    TestBed.configureTestingModule({ providers: [provider] });
    expect(run()).toBe(true);
    expect(fake.login).not.toHaveBeenCalled();
  });

  it('sends everyone else to Keycloak, remembering the page they asked for', () => {
    const { fake, provider } = fakeAuth(false);
    TestBed.configureTestingModule({ providers: [provider] });
    expect(run('/contracts/new')).toBe(false);
    expect(fake.login).toHaveBeenCalledWith('/contracts/new');
  });
});

describe('adminGuard', () => {
  const run = () =>
    TestBed.runInInjectionContext(() =>
      adminGuard(
        {} as ActivatedRouteSnapshot,
        { url: '/admin/dead-letters' } as RouterStateSnapshot,
      ),
    );

  it('lets admins through', () => {
    const { provider } = fakeAuth(true, true);
    TestBed.configureTestingModule({ providers: [provider, provideRouter([])] });
    expect(run()).toBe(true);
  });

  it('sends other users back to their contracts', () => {
    const { provider } = fakeAuth(true, false);
    TestBed.configureTestingModule({ providers: [provider, provideRouter([])] });
    const result = run();
    expect(result).toBeInstanceOf(UrlTree);
    expect(String(result)).toBe('/contracts');
  });
});
