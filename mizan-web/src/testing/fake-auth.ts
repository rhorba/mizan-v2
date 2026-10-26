import { signal } from '@angular/core';
import { AuthService } from '../app/auth/auth';

/** Stand-in for AuthService: no Keycloak, controllable sign-in state. */
export function fakeAuth(signedIn = false, admin = false) {
  const fake = {
    isAuthenticated: signal(signedIn),
    email: signal<string | null>(signedIn ? 'karim@mizan.test' : null),
    isAdmin: signal(signedIn && admin),
    init: vi.fn(async () => undefined),
    login: vi.fn(),
    register: vi.fn(),
    logout: vi.fn(),
  };
  return { fake, provider: { provide: AuthService, useValue: fake } };
}
