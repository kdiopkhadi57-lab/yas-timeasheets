import { Component, computed, inject, signal } from '@angular/core';
import { RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';
import { AuthService } from './core/auth.service';
import { ROLE_LABEL } from './core/models';
import { InboxBellComponent } from './shared/inbox-bell.component';

interface NavItem { path: string; label: string; icon: string; }

@Component({
  selector: 'app-shell',
  imports: [RouterOutlet, RouterLink, RouterLinkActive, InboxBellComponent],
  template: `
    <div class="layout" [class.open]="menuOpen()">
      <aside class="nav">
        <div class="brand">
          <div class="logo">YAS</div>
          <div>
            <div class="brand-name">IT-FACTORY</div>
            <div class="brand-sub">Fiches de temps</div>
          </div>
          @if (auth.isManager()) { <app-inbox-bell class="brand-bell" [dark]="true" /> }
        </div>
        <nav>
          @for (item of items(); track item.path) {
            <a [routerLink]="item.path" routerLinkActive="active" (click)="menuOpen.set(false)">
              <span class="ico" [innerHTML]="item.icon"></span>{{ item.label }}
            </a>
          }
        </nav>
        <div class="me">
          <a routerLink="/compte" class="who" (click)="menuOpen.set(false)">
            <div class="avatar">{{ initials() }}</div>
            <div class="grow">
              <div class="name">{{ auth.user()?.fullName }}</div>
              <div class="role">{{ roleLabel() }} · {{ auth.user()?.matricule }}</div>
            </div>
          </a>
          <button class="btn ghost sm logout" (click)="auth.logout()">Déconnexion</button>
        </div>
      </aside>
      <div class="scrim" (click)="menuOpen.set(false)"></div>
      <main>
        <header class="topbar">
          <button class="btn icon ghost" (click)="menuOpen.set(true)" aria-label="Menu">☰</button>
          <strong>YAS IT-FACTORY</strong>
          @if (auth.isManager()) { <app-inbox-bell class="top-bell" align="right" /> }
        </header>
        <router-outlet />
      </main>
    </div>
  `,
  styles: `
    .layout { display: grid; grid-template-columns: 248px 1fr; min-height: 100vh; }
    .nav { background: var(--nav); color: var(--nav-text); display: flex; flex-direction: column; position: sticky; top: 0; height: 100vh; }
    .brand { display: flex; align-items: center; gap: 10px; padding: 20px 18px; border-bottom: 1px solid rgba(255,255,255,.07); }
    .logo { width: 40px; height: 40px; border-radius: 10px; background: var(--accent); color: #0b2430; font-weight: 800; display: grid; place-items: center; font-size: 13px; letter-spacing: .02em; }
    .brand-name { color: #fff; font-weight: 700; letter-spacing: .04em; }
    .brand-sub { font-size: 12px; opacity: .7; }
    .brand-bell, .top-bell { margin-left: auto; }
    nav { padding: 12px 10px; display: flex; flex-direction: column; gap: 2px; flex: 1; }
    nav a { display: flex; align-items: center; gap: 10px; padding: 9px 12px; border-radius: 8px; color: var(--nav-text); font-weight: 500; }
    nav a:hover { background: rgba(255,255,255,.06); color: #fff; }
    nav a.active { background: rgba(75,172,198,.18); color: #fff; }
    .ico { width: 18px; display: inline-grid; place-items: center; opacity: .9; }
    .me { padding: 12px; border-top: 1px solid rgba(255,255,255,.07); }
    .who { display: flex; gap: 10px; align-items: center; color: inherit; padding: 6px; border-radius: 8px; }
    .who:hover { background: rgba(255,255,255,.06); }
    .avatar { width: 34px; height: 34px; border-radius: 50%; background: #23404f; color: #fff; display: grid; place-items: center; font-weight: 600; font-size: 13px; }
    .name { color: #fff; font-weight: 600; font-size: 13px; }
    .role { font-size: 11.5px; opacity: .7; }
    .logout { color: var(--nav-text); width: 100%; margin-top: 6px; justify-content: flex-start; }
    .logout:hover { background: rgba(255,255,255,.06) !important; color: #fff; }
    main { min-width: 0; }
    .topbar { display: none; }
    .scrim { display: none; }
    @media (max-width: 900px) {
      .layout { grid-template-columns: 1fr; }
      .nav { position: fixed; left: 0; top: 0; bottom: 0; width: 260px; z-index: 40; transform: translateX(-100%); transition: transform .2s; }
      .layout.open .nav { transform: none; }
      .layout.open .scrim { display: block; position: fixed; inset: 0; background: rgba(0,0,0,.35); z-index: 30; }
      .topbar { display: flex; align-items: center; gap: 8px; padding: 8px 12px; background: var(--surface); border-bottom: 1px solid var(--border); position: sticky; top: 0; z-index: 20; }
    }
  `,
})
export class ShellComponent {
  readonly auth = inject(AuthService);
  readonly menuOpen = signal(false);

  readonly roleLabel = computed(() => {
    const r = this.auth.user()?.role;
    return r ? ROLE_LABEL[r] : '';
  });

  readonly initials = computed(() => {
    const u = this.auth.user();
    return u ? (u.firstName[0] + u.lastName[0]).toUpperCase() : '';
  });

  readonly items = computed<NavItem[]>(() => {
    const items: NavItem[] = [];
    // Tableau de bord réservé aux managers et au Head of Office.
    if (this.auth.isManager()) {
      items.push({ path: '/tableau-de-bord', label: 'Tableau de bord', icon: '◔' });
      items.push({ path: '/equipe', label: 'Suivi de l’équipe', icon: '◫' });
      items.push({ path: '/activite-gitlab', label: 'Activité GitLab', icon: '⎇' });
    }
    items.push(
      { path: '/semaine', label: 'Ma semaine', icon: '▤' },
      { path: '/mois', label: 'Ma fiche de temps', icon: '▦' },
      ...(this.auth.usesTasks() ? [{ path: '/taches', label: this.auth.isManager() ? 'Tâches & assignation' : 'Mes tâches', icon: '☑' }] : []),
      { path: '/projets', label: 'Mes projets', icon: '▣' },
      { path: '/factures', label: this.auth.hasRole('HEAD_OF_OFFICE', 'ADMIN') ? 'Factures' : 'Ma facture', icon: '₣' },
    );
    if (this.auth.isManager()) {
      items.push({ path: '/admin', label: this.auth.isAdmin() ? 'Administration' : 'Projets & équipe', icon: '⚙' });
    }
    return items;
  });
}
