import { TestBed } from '@angular/core/testing';
import { ActivatedRouteSnapshot, RouterStateSnapshot } from '@angular/router';
import { fakeAuth } from '../../testing/fake-auth';
import { authGuard } from './auth-guard';

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
