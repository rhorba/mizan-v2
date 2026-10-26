import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';
import { AuthService } from './auth';

/** Signed-in users pass; others go to Keycloak and come back to the page they asked for. */
export const authGuard: CanActivateFn = (_route, state) => {
  const auth = inject(AuthService);
  if (auth.isAuthenticated()) {
    return true;
  }
  auth.login(state.url);
  return false;
};

/** After authGuard: non-admins go back to their contracts (the API refuses them anyway). */
export const adminGuard: CanActivateFn = () =>
  inject(AuthService).isAdmin() || inject(Router).createUrlTree(['/contracts']);
