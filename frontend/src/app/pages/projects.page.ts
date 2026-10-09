import { Component, OnInit, computed, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { ApiService, errorMessage } from '../core/api.service';
import { AuthService } from '../core/auth.service';
import {
  ESTIMATE_STATUS_LABEL, PROJECT_ROLE_LABEL, ProjectSpace, TASK_PRIORITY_LABEL, Task,
} from '../core/models';
import { shortDate } from '../core/dates';
import { ProjectDocumentsComponent } from '../shared/project-documents.component';

/** Mes projets : équipe, documents partagés par l'architecte et estimations à traiter. */
@Component({
  selector: 'app-projects',
  imports: [FormsModule, RouterLink, ProjectDocumentsComponent],
  template: `
    <div class="page wide">
      <div class="page-head">
        <div>
          <h1>Mes projets</h1>
          <p class="sub">Documents partagés par l’architecte (TDR, réunions, specs, code…), questions de l’équipe et explications du lead dev{{ auth.usesTasks() ? ', estimations des tâches' : '' }}.</p>
        </div>
      </div>
      @if (error()) { <div class="alert error" style="margin-bottom:12px">{{ error() }}</div> }

      @if (!spaces().length && loaded()) {
        <div class="card empty">Vous ne faites partie d’aucune équipe projet. Le Manager ou l’administrateur vous ajoute à un projet (architecte, lead dev ou développeur).</div>
      }

      <div class="layout">
        <nav class="list">
          @for (s of spaces(); track s.project.id) {
            <button class="card pcard" [class.on]="s.project.id === selectedId()" (click)="select(s.project.id)">
              <div class="row"><strong class="grow">{{ s.project.name }}</strong>
                @if (s.pendingEstimates && auth.usesTasks()) { <span class="badge PROPOSEE" title="Estimations à valider">{{ s.pendingEstimates }}</span> }
              </div>
              <div class="muted small">{{ s.project.businessUnit || '—' }} · {{ s.documentCount }} document{{ s.documentCount > 1 ? 's' : '' }}</div>
              @if (s.myRoles.length) {
                <div class="row roles">@for (r of s.myRoles; track r) { <span class="role" [attr.data-r]="r">{{ roleLabel[r] }}</span> }</div>
              }
            </button>
          }
        </nav>

        @if (current(); as s) {
          <div class="detail">
            <section class="card card-pad team">
              <div class="row">
                <h2 class="grow">{{ s.project.name }}</h2>
                @if (auth.usesTasks()) { <a class="btn sm" routerLink="/taches" [queryParams]="{ project: s.project.id }">☑ Voir les tâches</a> }
              </div>
              <dl>
                <div><dt>Manager</dt><dd>{{ s.project.pmName || '—' }}</dd></div>
                <div><dt>Architecte</dt><dd>{{ s.project.architectName || '—' }}</dd></div>
                <div><dt>Lead dev</dt><dd>{{ s.project.leadDevName || '—' }}</dd></div>
                <div class="wide"><dt>Développeurs</dt>
                  <dd>@for (m of s.project.members; track m.id) { <span class="member" [title]="m.profil ?? ''">{{ m.fullName }}</span> } @empty { — }</dd>
                </div>
              </dl>
            </section>

            @if (auth.usesTasks() && (toApprove().length || toEstimate().length)) {
              <section class="card card-pad">
                <h3>Estimations</h3>
                <p class="muted small" style="margin:2px 0 10px">Le développeur estime la durée de la tâche ; une fois l’estimation accordée par le lead dev, la tâche est active.</p>
                @for (t of toApprove(); track t.id) {
                  <div class="est">
                    <div class="grow">
                      <div><span class="ref">{{ t.ref }}</span><strong>{{ t.title }}</strong></div>
                      <div class="muted small">{{ t.assigneeName }} propose <strong>{{ t.proposedEstimateDays }} j</strong> · priorité {{ prioLabel[t.priority] }}@if (t.dueDate) { · échéance {{ fmt(t.dueDate) }} }</div>
                    </div>
                    <input type="number" min="0.5" step="0.5" [(ngModel)]="grant[t.id]" [placeholder]="t.proposedEstimateDays + ''" aria-label="Durée accordée (jours)" class="days">
                    <button class="btn success sm" (click)="approve(t)">Accorder</button>
                    <button class="btn sm danger" (click)="reject(t)">Refuser</button>
                  </div>
                }
                @for (t of toEstimate(); track t.id) {
                  <div class="est">
                    <div class="grow">
                      <div><span class="ref">{{ t.ref }}</span><strong>{{ t.title }}</strong> <span class="badge" [class]="t.estimateStatus">{{ estLabel[t.estimateStatus] }}</span></div>
                      <div class="muted small">Assignée par {{ t.createdByName }}@if (t.proposedEstimateDays) { · vous avez proposé {{ t.proposedEstimateDays }} j }</div>
                    </div>
                    <input type="number" min="0.5" step="0.5" [(ngModel)]="propose[t.id]" placeholder="jours" aria-label="Durée estimée (jours)" class="days">
                    <button class="btn primary sm" [disabled]="!propose[t.id]" (click)="sendEstimate(t)">{{ t.estimateStatus === 'PROPOSEE' ? 'Modifier' : 'Proposer' }}</button>
                  </div>
                }
              </section>
            }

            <section class="card card-pad">
              <h3 style="margin-bottom:10px">Documents du projet <span class="muted small">TDR, réunions, spécifications, code… · questions et explications sous chaque document</span></h3>
              <app-project-documents [space]="s" (countChange)="setCount(s.project.id, $event)" />
            </section>
          </div>
        }
      </div>
    </div>
  `,
  styles: `
    .page.wide { max-width: 1360px; }
    .layout { display: grid; grid-template-columns: 280px 1fr; gap: 16px; align-items: start; }
    .list { display: flex; flex-direction: column; gap: 8px; }
    .pcard { text-align: left; padding: 12px 14px; font: inherit; color: inherit; cursor: pointer; display: flex; flex-direction: column; gap: 4px; }
    .pcard:hover { border-color: #cfd5db; }
    .pcard.on { border-color: var(--brand); box-shadow: inset 3px 0 0 var(--brand), var(--shadow); }
    .roles { gap: 4px; margin-top: 2px; }
    .role { font-size: 11px; font-weight: 600; padding: 1px 7px; border-radius: 999px; background: #eef0f3; color: #475467; }
    .role[data-r='ARCHITECTE'] { background: var(--brand-soft); color: var(--brand-strong); }
    .role[data-r='LEAD_DEV'] { background: var(--info-soft); color: var(--info); }
    .detail { display: flex; flex-direction: column; gap: 16px; min-width: 0; }
    .team h2 { font-size: 18px; }
    dl { display: grid; grid-template-columns: repeat(3, 1fr); gap: 10px 16px; margin: 12px 0 0; }
    dl .wide { grid-column: 1 / -1; }
    dt { font-size: 12px; color: var(--muted); }
    dd { margin: 2px 0 0; font-weight: 600; display: flex; flex-wrap: wrap; gap: 6px; }
    .member { font-weight: 500; background: var(--surface-2); border: 1px solid var(--border); border-radius: 999px; padding: 1px 10px; font-size: 13px; }
    .est { display: flex; align-items: center; gap: 8px; padding: 10px 0; border-top: 1px solid var(--border); flex-wrap: wrap; }
    .est .days { width: 90px; }
    .ref { font-family: ui-monospace, monospace; font-size: 12px; background: var(--brand-soft); color: var(--brand-strong); padding: 1px 6px; border-radius: 6px; margin-right: 6px; }
    @media (max-width: 900px) {
      .layout { grid-template-columns: 1fr; }
      dl { grid-template-columns: 1fr 1fr; }
    }
  `,
})
export class ProjectsPage implements OnInit {
  private api = inject(ApiService);
  private route = inject(ActivatedRoute);
  readonly auth = inject(AuthService);

  readonly spaces = signal<ProjectSpace[]>([]);
  readonly loaded = signal(false);
  readonly selectedId = signal<number | null>(null);
  readonly tasks = signal<Task[]>([]);
  readonly error = signal<string | null>(null);
  readonly roleLabel = PROJECT_ROLE_LABEL;
  readonly estLabel = ESTIMATE_STATUS_LABEL;
  readonly prioLabel = TASK_PRIORITY_LABEL;
  readonly fmt = shortDate;
  grant: Record<number, number | null> = {};
  propose: Record<number, number | null> = {};

  readonly current = computed(() => this.spaces().find((s) => s.project.id === this.selectedId()) ?? null);
  readonly toApprove = computed(() => this.tasks().filter((t) => t.canApproveEstimate));
  readonly toEstimate = computed(() => this.tasks().filter((t) => t.canEstimate));

  ngOnInit(): void {
    this.api.projectSpaces().subscribe({
      next: (s) => {
        this.spaces.set(s);
        this.loaded.set(true);
        const wanted = Number(this.route.snapshot.queryParamMap.get('project'));
        const first = s.find((x) => x.project.id === wanted) ?? s[0];
        if (first) this.select(first.project.id);
      },
      error: (e) => this.error.set(errorMessage(e)),
    });
  }

  select(id: number): void {
    this.selectedId.set(id);
    this.tasks.set([]);
    this.loadTasks(id);
  }

  private loadTasks(projectId: number): void {
    const s = this.spaces().find((x) => x.project.id === projectId);
    const leads = !!s && (s.myRoles.includes('LEAD_DEV') || s.myRoles.includes('ARCHITECTE'));
    const scope = leads ? 'projects' : this.auth.isManager() ? 'team' : 'mine';
    this.api.tasks({ scope, projectId }).subscribe({ next: (t) => this.tasks.set(t), error: (e) => this.error.set(errorMessage(e)) });
  }

  sendEstimate(t: Task): void {
    const days = this.propose[t.id];
    if (!days) return;
    this.api.proposeEstimate(t.id, days, '').subscribe({ next: (u) => this.replace(u), error: (e) => this.error.set(errorMessage(e)) });
  }

  approve(t: Task): void {
    this.api.approveEstimate(t.id, this.grant[t.id] || null, '').subscribe({
      next: (u) => { this.replace(u); this.bumpPending(t.projectId); },
      error: (e) => this.error.set(errorMessage(e)),
    });
  }

  reject(t: Task): void {
    const why = prompt(`Pourquoi refuser l’estimation de ${t.proposedEstimateDays} j ?`);
    if (!why?.trim()) return;
    this.api.rejectEstimate(t.id, why).subscribe({
      next: (u) => { this.replace(u); this.bumpPending(t.projectId); },
      error: (e) => this.error.set(errorMessage(e)),
    });
  }

  private replace(t: Task): void { this.tasks.update((l) => l.map((x) => (x.id === t.id ? t : x))); }

  setCount(projectId: number, n: number): void {
    this.spaces.update((l) => l.map((s) => (s.project.id === projectId ? { ...s, documentCount: n } : s)));
  }

  private bumpPending(projectId: number): void {
    const n = this.tasks().filter((t) => t.estimateStatus === 'PROPOSEE').length;
    this.spaces.update((l) => l.map((s) => (s.project.id === projectId ? { ...s, pendingEstimates: n } : s)));
  }
}
