import { TestBed } from '@angular/core/testing';
import { ActivatedRouteSnapshot, RouterStateSnapshot } from '@angular/router';
import { fakeAuth } from '../../testing/fake-auth';
import { authGuard } from './auth-guard';

describe('authGuard', () => {
  const run = () =>
    TestBed.runInInjectionContext(() =>
      authGuard({} as ActivatedRouteSnapshot, {} as RouterStateSnapshot),
    );

  it('lets signed-in users through', () => {
    const { fake, provider } = fakeAuth(true);
    TestBed.configureTestingModule({ providers: [provider] });
    expect(run()).toBe(true);
    expect(fake.login).not.toHaveBeenCalled();
  });

  it('sends everyone else to Keycloak', () => {
    const { fake, provider } = fakeAuth(false);
    TestBed.configureTestingModule({ providers: [provider] });
    expect(run()).toBe(false);
    expect(fake.login).toHaveBeenCalled();
  });
});
