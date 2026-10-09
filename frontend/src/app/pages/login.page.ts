import { Component, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Router } from '@angular/router';
import { AuthService } from '../core/auth.service';
import { errorMessage } from '../core/api.service';

@Component({
  selector: 'app-login',
  imports: [FormsModule],
  template: `
    <div class="wrap">
      <section class="hero">
        <div class="logo">YAS</div>
        <h1>IT-FACTORY</h1>
        <p class="lead">Vos tâches du jour alimentent automatiquement le rapport du vendredi pour le Manager
          et la fiche de temps du 20 au 19 pour le Head of Office.</p>
        <ul>
          <li>Saisie en une minute par jour</li>
          <li>Excel au format YAS généré et envoyé automatiquement</li>
          <li>Validation en ligne par le Manager et le Head of Office</li>
        </ul>
      </section>
      <form class="card form" (ngSubmit)="submit()">
        <h2>Connexion</h2>
        <p class="muted">Utilisez votre e-mail professionnel.</p>
        @if (error()) { <div class="alert error">{{ error() }}</div> }
        <label class="field"><span>E-mail</span>
          <input type="email" name="email" [(ngModel)]="email" required autocomplete="username" autofocus>
        </label>
        <label class="field"><span>Mot de passe</span>
          <input type="password" name="password" [(ngModel)]="password" required autocomplete="current-password">
        </label>
        <button class="btn primary" type="submit" [disabled]="loading() || !email || !password">
          {{ loading() ? 'Connexion…' : 'Se connecter' }}
        </button>
        <details class="demo" open>
          <summary>Comptes de démonstration</summary>
          <p class="small muted">Mot de passe : <code>Yas&#64;2026</code></p>
          <div class="demo-list">
            @for (d of demo; track d.email) {
              <button type="button" class="btn sm" (click)="email = d.email; password = 'Yas@2026'" [title]="d.email">{{ d.label }}</button>
            }
          </div>
        </details>
      </form>
    </div>
  `,
  styles: `
    .wrap { min-height: 100vh; display: grid; grid-template-columns: 1.1fr 1fr; }
    .hero { background: var(--nav); color: #d7e3ea; padding: 64px 56px; display: flex; flex-direction: column; justify-content: center; gap: 16px; }
    .hero h1 { color: #fff; font-size: 32px; letter-spacing: .05em; }
    .logo { width: 56px; height: 56px; border-radius: 14px; background: var(--accent); color: #0b2430; font-weight: 800; display: grid; place-items: center; }
    .lead { font-size: 16px; line-height: 1.55; max-width: 460px; }
    ul { margin: 8px 0 0; padding-left: 18px; line-height: 1.9; }
    .form { align-self: center; justify-self: center; width: min(400px, calc(100% - 32px)); padding: 28px; display: flex; flex-direction: column; gap: 14px; }
    .demo summary { cursor: pointer; color: var(--muted); font-size: 13px; }
    .demo-list { display: flex; flex-wrap: wrap; gap: 6px; margin-top: 8px; }
    @media (max-width: 860px) { .wrap { grid-template-columns: 1fr; } .hero { padding: 32px 20px; } .form { margin: 24px 0; } }
  `,
})
export class LoginPage {
  private auth = inject(AuthService);
  private router = inject(Router);

  email = '';
  password = '';
  readonly loading = signal(false);
  readonly error = signal<string | null>(null);

  readonly demo = [
    { label: 'Architecte · Pape Samba NDOUR', email: 'pape@yas-itfactory.sn' },
    { label: 'Lead dev · Moussa THIOUNE', email: 'moussa@yas-itfactory.sn' },
    { label: 'Développeur · Fatou SOW', email: 'fatou@yas-itfactory.sn' },
    { label: 'Manager · Demba FALL', email: 'pm@yas-itfactory.sn' },
    { label: 'Manager de Demba FALL · Mouhamadou Moustapha BITEYE', email: 'biteye@yas-itfactory.sn' },
    { label: 'Head of Office · Toussaint BASSENE', email: 'head@yas-itfactory.sn' },
    { label: 'Admin', email: 'admin@yas-itfactory.sn' },
  ];

  submit(): void {
    this.loading.set(true);
    this.error.set(null);
    this.auth.login(this.email, this.password).subscribe({
      next: () => this.router.navigateByUrl(this.auth.home()),
      error: (e) => {
        this.error.set(errorMessage(e));
        this.loading.set(false);
      },
    });
  }
}
