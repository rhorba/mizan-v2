import { Routes } from '@angular/router';
import { adminGuard, authGuard } from './auth/auth-guard';

export const routes: Routes = [
  {
    path: '',
    pathMatch: 'full',
    loadComponent: () => import('./landing/landing').then((m) => m.Landing),
  },
  {
    path: 'contracts',
    canActivate: [authGuard],
    children: [
      {
        path: '',
        loadComponent: () => import('./contracts/contracts').then((m) => m.Contracts),
      },
      {
        path: 'new',
        loadComponent: () => import('./contracts/upload/upload').then((m) => m.Upload),
      },
      {
        path: ':id',
        loadComponent: () =>
          import('./contracts/detail/contract-detail').then((m) => m.ContractDetail),
      },
    ],
  },
  {
    path: 'admin',
    canActivate: [authGuard, adminGuard],
    loadComponent: () => import('./admin/admin-stats').then((m) => m.AdminStats),
  },
  {
    path: 'admin/dead-letters',
    canActivate: [authGuard, adminGuard],
    loadComponent: () => import('./admin/dead-letters').then((m) => m.DeadLetters),
  },
  { path: '**', redirectTo: '' },
];
