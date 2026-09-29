import { Routes } from '@angular/router';

export const routes: Routes = [
  {
    path: '',
    title: 'QuickDot',
    loadComponent: () => import('./landing/landing').then((m) => m.Landing)
  },
  {
    path: 'r/:code',
    title: 'Room · QuickDot',
    loadComponent: () => import('./room/room').then((m) => m.Room)
  },
  {
    path: 'privacy',
    title: 'Privacy Policy · QuickDot',
    data: { doc: 'privacy' },
    loadComponent: () => import('./legal/legal-page').then((m) => m.LegalPage)
  },
  {
    path: 'terms',
    title: 'Terms of Service · QuickDot',
    data: { doc: 'terms' },
    loadComponent: () => import('./legal/legal-page').then((m) => m.LegalPage)
  },
  { path: '**', redirectTo: '' }
];
