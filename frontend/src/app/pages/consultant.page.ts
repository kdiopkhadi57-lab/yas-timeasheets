import { Component, OnInit, computed, inject, input, signal } from '@angular/core';
import { DatePipe } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { ApiService, errorMessage } from '../core/api.service';
import { AuthService } from '../core/auth.service';
import { InboxService } from '../core/inbox.service';
import { REPORT_STATUS_LABEL, ReportType, Timesheet } from '../core/models';
import { addDays, today } from '../core/dates';
import { TimesheetTableComponent } from '../shared/timesheet-table.component';

/** Fiche d'un consultant vue par son PM ou le Head of Office, avec validation. */
@Component({
  selector: 'app-consultant',
  imports: [TimesheetTableComponent, RouterLink, DatePipe, FormsModule],
  template: `
    <div class="page">
      <a routerLink="/equipe" class="small">‹ Retour au suivi de l’équipe</a>
      @if (ts(); as t) {
        @let st = t.report?.status ?? 'BROUILLON';
        <div class="page-head" style="margin-top:8px">
          <div>
            <h1>{{ t.consultant.fullName }}</h1>
            <p class="sub">{{ t.consultant.matricule }} · {{ t.consultant.profil }} · {{ t.consultant.email }}</p>
          </div>
          <div class="row">
            <div class="tabs">
              <button [class.on]="kind() === 'HEBDO'" (click)="setType('HEBDO')">Hebdomadaire</button>
              <button [class.on]="kind() === 'MENSUEL'" (click)="setType('MENSUEL')">Mensuel</button>
            </div>
            <div class="period-nav">
              <button class="btn icon ghost" (click)="shift(-1)">‹</button>
              <span class="label">{{ t.periodLabel }}</span>
              <button class="btn icon ghost" (click)="shift(1)">›</button>
            </div>
          </div>
        </div>

        @if (error()) { <div class="alert error" style="margin-bottom:12px">{{ error() }}</div> }

        <div class="card card-pad review">
          <div class="row">
            <span class="badge" [class]="st">{{ statusLabel[st] }}</span>
            <span class="muted">
              @if (t.report?.submittedAt) { Soumis le {{ t.report?.submittedAt | date: 'dd/MM/yyyy à HH:mm' }} · }
              {{ t.workedDays }} jour(s), {{ t.entryCount }} tâche(s)
            </span>
            <span class="grow"></span>
            <button class="btn" (click)="excel()">⬇ Excel</button>
            @if (st === 'SOUMIS' && canReview()) {
              <button class="btn danger" (click)="rejecting.set(true)">Renvoyer</button>
              <button class="btn success" (click)="approve()" [disabled]="busy()">Valider</button>
            }
          </div>
          @if (t.report?.consultantComment) { <div class="alert info" style="margin-top:10px"><strong>Commentaire du consultant :</strong> {{ t.report?.consultantComment }}</div> }
          @if (t.report?.reviewComment) { <div class="alert" [class.error]="st === 'REJETE'" [class.ok]="st === 'VALIDE'" style="margin-top:10px"><strong>{{ t.report?.reviewerName }} :</strong> {{ t.report?.reviewComment }}</div> }
          @if (rejecting()) {
            <div style="margin-top:12px" class="row">
              <input class="grow" [(ngModel)]="comment" placeholder="Ce qu'il faut corriger…" style="flex:1">
              <button class="btn" (click)="rejecting.set(false)">Annuler</button>
              <button class="btn primary" (click)="reject()" [disabled]="!comment.trim() || busy()">Envoyer</button>
            </div>
          }
        </div>

        <div class="card" style="margin-top:16px; overflow:hidden">
          <app-timesheet-table [days]="t.days" />
        </div>
      } @else if (error()) {
        <div class="alert error" style="margin-top:12px">{{ error() }}</div>
      }
    </div>
  `,
})
export class ConsultantPage implements OnInit {
  private api = inject(ApiService);
  private auth = inject(AuthService);
  private inbox = inject(InboxService);

  readonly consultantId = input.required<string>();
  readonly type = input<ReportType>();
  readonly date = input<string>();

  readonly kind = signal<ReportType>('HEBDO');
  readonly day = signal(today());
  readonly ts = signal<Timesheet | null>(null);
  readonly error = signal<string | null>(null);
  readonly busy = signal(false);
  readonly rejecting = signal(false);
  readonly statusLabel = REPORT_STATUS_LABEL;
  comment = '';

  readonly canReview = computed(() => this.kind() === 'HEBDO' || this.auth.hasRole('HEAD_OF_OFFICE', 'ADMIN'));

  ngOnInit(): void {
    if (this.type()) this.kind.set(this.type()!);
    if (this.date()) this.day.set(this.date()!);
    this.load();
  }

  load(): void {
    this.api.timesheet(this.kind(), this.day(), +this.consultantId()).subscribe({
      next: (t) => { this.ts.set(t); this.error.set(null); },
      error: (e) => this.error.set(errorMessage(e)),
    });
  }

  setType(t: ReportType): void { this.kind.set(t); this.load(); }

  shift(dir: number): void {
    const t = this.ts();
    if (!t) return;
    this.day.set(dir < 0 ? addDays(t.periodStart, -1) : addDays(t.periodEnd, 1));
    this.load();
  }

  approve(): void {
    this.busy.set(true);
    this.api.approve(this.ts()!.report!.id).subscribe({
      next: () => { this.busy.set(false); this.load(); this.inbox.refresh(); },
      error: (e) => { this.busy.set(false); this.error.set(errorMessage(e)); },
    });
  }

  reject(): void {
    this.busy.set(true);
    this.api.reject(this.ts()!.report!.id, this.comment).subscribe({
      next: () => { this.busy.set(false); this.rejecting.set(false); this.comment = ''; this.load(); this.inbox.refresh(); },
      error: (e) => { this.busy.set(false); this.error.set(errorMessage(e)); },
    });
  }

  excel(): void {
    this.api.downloadExcel(this.kind(), this.day(), +this.consultantId()).subscribe({ error: (e) => this.error.set(errorMessage(e)) });
  }
}
