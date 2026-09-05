import { Routes } from '@angular/router';

export const routes: Routes = [
  {
    path: '',
    loadComponent: () => import('./landing/landing').then((m) => m.Landing)
  },
  {
    path: 'r/:code',
    loadComponent: () => import('./room/room').then((m) => m.Room)
  },
  { path: '**', redirectTo: '' }
];
