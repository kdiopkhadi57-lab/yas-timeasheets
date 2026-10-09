import { Component, OnInit, computed, inject, signal } from '@angular/core';
import { DatePipe } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { ApiService, errorMessage } from '../core/api.service';
import { AuthService } from '../core/auth.service';
import { REPORT_STATUS_LABEL, ReportStatus, ReportType, TeamStatus } from '../core/models';
import { addDays, today } from '../core/dates';

@Component({
  selector: 'app-team',
  imports: [RouterLink, DatePipe, FormsModule],
  template: `
    <div class="page">
      <div class="page-head">
        <div>
          <h1>Suivi de l’équipe</h1>
          <p class="sub">{{ type() === 'HEBDO' ? 'Rapports du vendredi' : 'Fiches de temps du 20 au 19' }} de
            {{ auth.hasRole('PM') ? 'votre équipe' : 'tous les consultants' }}.</p>
        </div>
        <div class="row">
          <div class="tabs">
            <button [class.on]="type() === 'HEBDO'" (click)="setType('HEBDO')">Hebdomadaire</button>
            <button [class.on]="type() === 'MENSUEL'" (click)="setType('MENSUEL')">Mensuel</button>
          </div>
          <div class="period-nav">
            <button class="btn icon ghost" (click)="shift(-1)">‹</button>
            <span class="label">{{ periodLabel() }}</span>
            <button class="btn icon ghost" (click)="shift(1)">›</button>
          </div>
          <button class="btn primary" (click)="zip()" [disabled]="busy() || !rows().length">⬇ Tout télécharger (ZIP)</button>
        </div>
      </div>

      @if (error()) { <div class="alert error" style="margin-bottom:12px">{{ error() }}</div> }
      @if (notice()) { <div class="alert ok" style="margin-bottom:12px">{{ notice() }}</div> }

      <div class="stats">
        <div class="card stat"><div class="l">Consultants</div><div class="v">{{ rows().length }}</div></div>
        <div class="card stat"><div class="l">À valider</div><div class="v" style="color:var(--info)">{{ count('SOUMIS') }}</div></div>
        <div class="card stat"><div class="l">Validés</div><div class="v" style="color:var(--ok)">{{ count('VALIDE') }}</div></div>
        <div class="card stat"><div class="l">Non soumis</div><div class="v" style="color:var(--warn)">{{ count('BROUILLON') }}</div></div>
        <div class="card stat"><div class="l">À corriger</div><div class="v" style="color:var(--danger)">{{ count('REJETE') }}</div></div>
      </div>

      <div class="card table-wrap">
        <table class="data">
          <thead><tr><th>Consultant</th><th>Profil</th><th>Jours / tâches</th><th>Statut</th><th>Soumis le</th><th></th></tr></thead>
          <tbody>
            @for (r of rows(); track r.consultant.id) {
              @let st = r.report?.status ?? 'BROUILLON';
              <tr>
                <td>
                  <a [routerLink]="['/equipe', r.consultant.id]" [queryParams]="{ type: type(), date: date() }" class="name">{{ r.consultant.fullName }}</a>
                  <div class="muted small">{{ r.consultant.matricule }}{{ r.consultant.pmName ? ' · Manager ' + r.consultant.pmName : '' }}</div>
                </td>
                <td>{{ r.consultant.profil }}</td>
                <td>{{ r.workedDays }} j · {{ r.entryCount }} tâche(s)</td>
                <td>
                  <span class="badge" [class]="st">{{ statusLabel[st] }}</span>
                  @if (r.report?.consultantComment) { <div class="muted small comment">« {{ r.report?.consultantComment }} »</div> }
                </td>
                <td>{{ r.report?.submittedAt | date: 'dd/MM à HH:mm' }}</td>
                <td class="actions">
                  <a class="btn sm ghost" [routerLink]="['/equipe', r.consultant.id]" [queryParams]="{ type: type(), date: date() }">Voir</a>
                  <button class="btn sm ghost" (click)="excel(r)">⬇ Excel</button>
                  @if (st === 'SOUMIS' && canReview()) {
                    <button class="btn sm success" (click)="approve(r)" [disabled]="busy()">Valider</button>
                    <button class="btn sm danger" (click)="rejecting.set(r); rejectComment = ''">Renvoyer</button>
                  }
                </td>
              </tr>
            } @empty {
              <tr><td colspan="6" class="empty">Aucun consultant rattaché.</td></tr>
            }
          </tbody>
        </table>
      </div>
      <p class="muted small" style="margin-top:12px">
        Envoi automatique : chaque vendredi à 18h, chaque Manager reçoit le ZIP des rapports de son équipe (sous-équipes comprises) ; le 20 à 8h, le Head of Office reçoit toutes les fiches de temps de la période écoulée. Semaine du samedi au vendredi. Relances aux retardataires le jeudi 16h, le vendredi 10h et les 18 et 19 du mois.
      </p>
    </div>

    @if (rejecting(); as r) {
      <div class="modal-backdrop" (click)="rejecting.set(null)">
        <div class="card modal" (click)="$event.stopPropagation()">
          <div class="modal-head"><h2>Renvoyer à {{ r.consultant.fullName }}</h2><button class="btn icon ghost" (click)="rejecting.set(null)">✕</button></div>
          <div class="modal-body">
            <label class="field"><span>Ce qu’il faut corriger</span>
              <textarea [(ngModel)]="rejectComment" rows="4" placeholder="Ex. Le 24/09 est vide, merci de compléter."></textarea>
            </label>
          </div>
          <div class="modal-foot">
            <button class="btn" (click)="rejecting.set(null)">Annuler</button>
            <button class="btn primary" (click)="reject(r)" [disabled]="!rejectComment.trim() || busy()">Renvoyer pour correction</button>
          </div>
        </div>
      </div>
    }
  `,
  styles: `
    .name { font-weight: 600; }
    .actions { text-align: right; white-space: nowrap; }
    .actions .btn { margin-left: 2px; }
    .comment { margin-top: 4px; max-width: 260px; }
  `,
})
export class TeamPage implements OnInit {
  private api = inject(ApiService);
  readonly auth = inject(AuthService);

  readonly type = signal<ReportType>('HEBDO');
  readonly date = signal(today());
  readonly rows = signal<TeamStatus[]>([]);
  readonly error = signal<string | null>(null);
  readonly notice = signal<string | null>(null);
  readonly busy = signal(false);
  readonly rejecting = signal<TeamStatus | null>(null);
  readonly statusLabel = REPORT_STATUS_LABEL;
  rejectComment = '';

  readonly periodLabel = computed(() => {
    const r = this.rows()[0];
    return this.label() ?? (r?.report?.periodLabel ?? '…');
  });
  private readonly label = signal<string | null>(null);
  private period: { start: string; end: string } | null = null;

  /** Le mensuel est validé par le Head of Office ; l'hebdo par le PM (ou le Head of Office). */
  readonly canReview = computed(() => this.type() === 'HEBDO' || this.auth.hasRole('HEAD_OF_OFFICE', 'ADMIN'));

  ngOnInit(): void { this.load(); }

  load(): void {
    this.api.teamStatus(this.type(), this.date()).subscribe({
      next: (rows) => { this.rows.set(rows); this.error.set(null); },
      error: (e) => this.error.set(errorMessage(e)),
    });
    // Libellé et bornes de la période, même si personne n'a encore soumis.
    this.api.timesheet(this.type(), this.date()).subscribe({
      next: (ts) => { this.label.set(ts.periodLabel); this.period = { start: ts.periodStart, end: ts.periodEnd }; },
    });
  }

  setType(t: ReportType): void { this.type.set(t); this.load(); }

  shift(dir: number): void {
    if (!this.period) return;
    this.date.set(dir < 0 ? addDays(this.period.start, -1) : addDays(this.period.end, 1));
    this.load();
  }

  count(s: ReportStatus): number {
    return this.rows().filter((r) => (r.report?.status ?? 'BROUILLON') === s).length;
  }

  approve(r: TeamStatus): void {
    this.busy.set(true);
    this.api.approve(r.report!.id).subscribe({
      next: () => { this.busy.set(false); this.notice.set(`Rapport de ${r.consultant.fullName} validé.`); this.load(); },
      error: (e) => { this.busy.set(false); this.error.set(errorMessage(e)); },
    });
  }

  reject(r: TeamStatus): void {
    this.busy.set(true);
    this.api.reject(r.report!.id, this.rejectComment).subscribe({
      next: () => { this.busy.set(false); this.rejecting.set(null); this.notice.set(`Rapport renvoyé à ${r.consultant.fullName}.`); this.load(); },
      error: (e) => { this.busy.set(false); this.error.set(errorMessage(e)); },
    });
  }

  excel(r: TeamStatus): void {
    this.api.downloadExcel(this.type(), this.date(), r.consultant.id).subscribe({ error: (e) => this.error.set(errorMessage(e)) });
  }

  zip(): void {
    this.busy.set(true);
    this.api.downloadTeamZip(this.type(), this.date()).subscribe({
      next: () => this.busy.set(false),
      error: (e) => { this.busy.set(false); this.error.set(errorMessage(e)); },
    });
  }
}
