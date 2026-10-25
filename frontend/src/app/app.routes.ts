import { Routes } from '@angular/router';
import { authGuard } from './core/auth.guard';

export const routes: Routes = [
  { path: 'login', loadComponent: () => import('./pages/login.page').then(m => m.LoginPage) },
  {
    path: '',
    canActivate: [authGuard],
    children: [
      { path: '', pathMatch: 'full', redirectTo: 'dashboard' },
      { path: 'dashboard', loadComponent: () => import('./pages/dashboard.page').then(m => m.DashboardPage) },
      { path: 'shipments', loadComponent: () => import('./pages/shipments.page').then(m => m.ShipmentsPage) },
      { path: 'docks', loadComponent: () => import('./pages/docks.page').then(m => m.DocksPage) },
      { path: 'schedule', loadComponent: () => import('./pages/schedule.page').then(m => m.SchedulePage) },
      { path: 'disruptions', loadComponent: () => import('./pages/disruptions.page').then(m => m.DisruptionsPage) },
      { path: 'disruptions/:id', loadComponent: () => import('./pages/disruption-detail.page').then(m => m.DisruptionDetailPage) },
      { path: 'assistant', loadComponent: () => import('./pages/assistant.page').then(m => m.AssistantPage) },
    ],
  },
  { path: '**', redirectTo: '' },
];
