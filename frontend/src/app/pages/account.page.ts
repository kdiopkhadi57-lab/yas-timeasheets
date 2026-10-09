import { Component, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { ApiService, errorMessage } from '../core/api.service';
import { AuthService } from '../core/auth.service';
import { ROLE_LABEL } from '../core/models';

@Component({
  selector: 'app-account',
  imports: [FormsModule],
  template: `
    <div class="page" style="max-width:720px">
      <div class="page-head"><h1>Mon compte</h1></div>
      @if (auth.user(); as u) {
        <div class="card card-pad info">
          <div><span class="muted small">Prénom(s) & Nom</span><div>{{ u.fullName }}</div></div>
          <div><span class="muted small">Matricule</span><div>{{ u.matricule }}</div></div>
          <div><span class="muted small">Profil</span><div>{{ u.profil }}</div></div>
          <div><span class="muted small">Rôle</span><div>{{ roleLabel[u.role] }}</div></div>
          <div><span class="muted small">Manager</span><div>{{ u.pmName ?? '—' }}</div></div>
          <div><span class="muted small">E-mail</span><div>{{ u.email }}</div></div>
        </div>
      }
      <form class="card card-pad pwd" (ngSubmit)="change()">
        <h2>Changer de mot de passe</h2>
        @if (msg()) { <div class="alert" [class.ok]="ok()" [class.error]="!ok()">{{ msg() }}</div> }
        <label class="field"><span>Mot de passe actuel</span><input type="password" name="cur" [(ngModel)]="current" required></label>
        <label class="field"><span>Nouveau mot de passe (6 caractères min.)</span><input type="password" name="new" [(ngModel)]="next" required minlength="6"></label>
        <div><button class="btn primary" [disabled]="!current || next.length < 6">Mettre à jour</button></div>
      </form>
    </div>
  `,
  styles: `
    .info { display: grid; grid-template-columns: repeat(auto-fit, minmax(200px, 1fr)); gap: 14px; margin-bottom: 16px; }
    .pwd { display: flex; flex-direction: column; gap: 12px; }
  `,
})
export class AccountPage {
  private api = inject(ApiService);
  readonly auth = inject(AuthService);
  readonly roleLabel = ROLE_LABEL;
  readonly msg = signal<string | null>(null);
  readonly ok = signal(false);
  current = '';
  next = '';

  change(): void {
    this.api.changePassword(this.current, this.next).subscribe({
      next: () => { this.ok.set(true); this.msg.set('Mot de passe mis à jour.'); this.current = ''; this.next = ''; },
      error: (e) => { this.ok.set(false); this.msg.set(errorMessage(e)); },
    });
  }
}
