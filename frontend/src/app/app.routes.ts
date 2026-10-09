import { inject } from '@angular/core';
import { Router, Routes } from '@angular/router';
import { AuthService } from './core/auth.service';
import { authGuard, roleGuard } from './core/auth.guard';
import { ShellComponent } from './shell.component';

export const routes: Routes = [
  { path: 'login', loadComponent: () => import('./pages/login.page').then((m) => m.LoginPage) },
  {
    path: '',
    component: ShellComponent,
    canActivate: [authGuard],
    children: [
      {
        path: 'tableau-de-bord',
        // Réservé aux managers et au Head of Office (et à l'Admin) : suivi des fiches des développeurs / architectes.
        canActivate: [roleGuard('PM', 'HEAD_OF_OFFICE', 'ADMIN')],
        loadComponent: () => import('./pages/dashboard.page').then((m) => m.DashboardPage),
      },
      { path: 'semaine', loadComponent: () => import('./pages/week.page').then((m) => m.WeekPage) },
      { path: 'mois', loadComponent: () => import('./pages/month.page').then((m) => m.MonthPage) },
      { path: 'factures', loadComponent: () => import('./pages/invoices.page').then((m) => m.InvoicesPage) },
      { path: 'projets', loadComponent: () => import('./pages/projects.page').then((m) => m.ProjectsPage) },
      {
        path: 'taches',
        // Développeurs et lead dev : pas de tâches assignées pour l'instant.
        canActivate: [() => inject(AuthService).usesTasks() || inject(Router).createUrlTree([inject(AuthService).home()])],
        loadComponent: () => import('./pages/tasks.page').then((m) => m.TasksPage),
      },
      {
        path: 'equipe',
        canActivate: [roleGuard('PM', 'HEAD_OF_OFFICE', 'ADMIN')],
        loadComponent: () => import('./pages/team.page').then((m) => m.TeamPage),
      },
      {
        path: 'equipe/:consultantId',
        canActivate: [roleGuard('PM', 'HEAD_OF_OFFICE', 'ADMIN')],
        loadComponent: () => import('./pages/consultant.page').then((m) => m.ConsultantPage),
      },
      {
        path: 'activite-gitlab',
        canActivate: [roleGuard('PM', 'HEAD_OF_OFFICE', 'ADMIN')],
        loadComponent: () => import('./pages/git-activity.page').then((m) => m.GitActivityPage),
      },
      {
        path: 'admin',
        canActivate: [roleGuard('ADMIN', 'HEAD_OF_OFFICE', 'PM')],
        loadComponent: () => import('./pages/admin.page').then((m) => m.AdminPage),
      },
      { path: 'compte', loadComponent: () => import('./pages/account.page').then((m) => m.AccountPage) },
      // L'architecte arrive sur ses projets (documents à partager), les autres sur leur semaine.
      { path: '', pathMatch: 'full', redirectTo: () => (inject(AuthService).hasRole('ARCHITECTE') ? 'projets' : 'semaine') },
    ],
  },
  { path: '**', redirectTo: '' },
];
