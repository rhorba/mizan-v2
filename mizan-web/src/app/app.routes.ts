import { Routes } from '@angular/router';
import { authGuard } from './auth/auth-guard';

export const routes: Routes = [
  {
    path: '',
    pathMatch: 'full',
    loadComponent: () => import('./landing/landing').then((m) => m.Landing),
  },
  {
    path: 'contracts',
    canActivate: [authGuard],
    loadComponent: () => import('./contracts/contracts').then((m) => m.Contracts),
  },
  { path: '**', redirectTo: '' },
];
