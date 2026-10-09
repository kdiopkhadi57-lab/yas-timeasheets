import { Component, OnInit, computed, inject, signal } from '@angular/core';
import { DatePipe } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { ApiService, errorMessage } from '../core/api.service';
import { AuthService } from '../core/auth.service';
import { GitActivity, GitStatus, Project, ReportType, TASK_STATUS_LABEL, Task } from '../core/models';
import { addDays, shortDate, today } from '../core/dates';
import { TaskGitComponent } from '../shared/task-git.component';

/** Vue globale PM / Head of Office : commits GitLab par consultant et projet, et les écarts avec les fiches. */
@Component({
  selector: 'app-git-activity',
  imports: [DatePipe, FormsModule, RouterLink, TaskGitComponent],
  template: `
    <div class="page">
      <div class="page-head">
        <div>
          <h1>Activité GitLab</h1>
          <p class="sub">Les commits de {{ auth.hasRole('PM') ? 'votre équipe' : 'tous les consultants' }}, rapprochés des tâches et des fiches de temps.</p>
        </div>
        <div class="row">
          <select [(ngModel)]="projectId" (ngModelChange)="load()" style="max-width:200px">
            <option [ngValue]="null">Tous les projets</option>
            @for (p of projects(); track p.id) { <option [ngValue]="p.id">{{ p.name }}</option> }
          </select>
          <div class="tabs">
            <button [class.on]="type() === 'HEBDO'" (click)="type.set('HEBDO'); load()">Semaine</button>
            <button [class.on]="type() === 'MENSUEL'" (click)="type.set('MENSUEL'); load()">Mois (20 → 19)</button>
          </div>
          <div class="period-nav">
            <button class="btn icon ghost" (click)="shift(-1)">‹</button>
            <span class="label">{{ data()?.periodLabel ?? '…' }}</span>
            <button class="btn icon ghost" (click)="shift(1)">›</button>
          </div>
        </div>
      </div>

      @if (status(); as st) {
        @if (!st.enabled) {
          <div class="alert warn" style="margin-bottom:12px">
            GitLab n'est pas encore connecté : renseignez <code>GITLAB_TOKEN</code> côté serveur (jeton <code>read_api</code>), puis rattachez les dépôts dans
            <a routerLink="/admin" [queryParams]="{ tab: 'gitlab' }">{{ auth.isAdmin() ? 'Administration' : 'Projets & équipe' }} › GitLab</a>. Les données ci-dessous sont celles de démonstration.
          </div>
        } @else if (st.lastError) {
          <div class="alert error" style="margin-bottom:12px">Dernière synchronisation incomplète : {{ st.lastError }}</div>
        }
      }
      @if (error()) { <div class="alert error" style="margin-bottom:12px">{{ error() }}</div> }

      @if (data(); as d) {
        <div class="stats">
          <div class="card stat"><div class="l">Commits</div><div class="v">{{ total().commits }}</div></div>
          <div class="card stat"><div class="l">Liés à une tâche</div><div class="v" style="color:var(--ok)">{{ total().linkedPct }} %</div></div>
          <div class="card stat"><div class="l">Tâches en cours sans commit</div><div class="v" style="color:var(--warn)">{{ d.tasksWithoutCommits.length }}</div></div>
          <div class="card stat"><div class="l">Jours codés non déclarés</div><div class="v" style="color:var(--danger)">{{ total().undeclared }}</div></div>
        </div>

        <div class="card table-wrap">
          <table class="data">
            <thead><tr><th>Consultant</th><th>Projet</th><th>Commits</th><th>Liés</th><th>Tâches</th><th>Jours avec commits</th><th>Jours déclarés (projet)</th><th>Dernier commit</th></tr></thead>
            <tbody>
              @for (r of d.rows; track r.consultantId + '-' + r.projectId) {
                <tr>
                  <td><a class="name" [routerLink]="['/equipe', r.consultantId]" [queryParams]="{ type: type(), date: date() }">{{ r.consultantName }}</a></td>
                  <td>{{ r.projectName }}</td>
                  <td>{{ r.commits }}</td>
                  <td [class.low]="r.linkedCommits < r.commits / 2">{{ r.linkedCommits }}</td>
                  <td>{{ r.tasks }}</td>
                  <td>{{ r.commitDays }}</td>
                  <td>
                    {{ r.declaredDays }}
                    @if (r.undeclaredDays) { <span class="badge REJETE" title="Jours avec des commits mais aucune ligne dans la fiche de temps">{{ r.undeclaredDays }} j non déclaré{{ r.undeclaredDays > 1 ? 's' : '' }}</span> }
                  </td>
                  <td class="small">{{ r.lastCommitAt | date: 'dd/MM HH:mm' }}</td>
                </tr>
              } @empty {
                <tr><td colspan="8" class="empty">Aucun commit sur la période.</td></tr>
              }
            </tbody>
          </table>
        </div>

        <div class="grid2">
          <section class="card">
            <div class="card-pad"><h2>Tâches « En cours » sans commit</h2>
              <p class="muted small">Sur les projets suivis dans GitLab. Tâche bloquée, mal nommée côté Git, ou travail hors code ?</p></div>
            @for (t of d.tasksWithoutCommits; track t.id) {
              <div class="line">
                <div class="grow"><span class="ref">{{ t.ref }}</span>{{ t.title }}
                  <div class="muted small">{{ t.assigneeName }} · {{ t.projectName }}@if (t.dueDate) { · échéance {{ fmt(t.dueDate) }} }</div></div>
                <button class="btn sm ghost" (click)="gitTask.set(t)">⎇</button>
              </div>
            } @empty { <p class="empty small">Rien à signaler.</p> }
          </section>

          <section class="card">
            <div class="card-pad"><h2>Commits sans tâche ({{ d.unlinkedCommits.length }})</h2>
              <p class="muted small">Travail non planifié, ou référence <code>{{ status()?.taskPrefix ?? 'TS' }}-…</code> oubliée dans la branche.</p></div>
            @for (c of d.unlinkedCommits; track c.id) {
              <div class="line">
                <code class="sha">{{ c.shortSha }}</code>
                <div class="grow">
                  @if (c.webUrl) { <a [href]="c.webUrl" target="_blank" rel="noopener">{{ c.title }}</a> } @else { {{ c.title }} }
                  <div class="muted small">{{ c.consultantName }} · {{ c.repo }} · {{ fmt(c.workDate) }}</div>
                </div>
              </div>
            } @empty { <p class="empty small">Tous les commits sont liés à une tâche.</p> }
          </section>
        </div>

        @if (d.unknownAuthors.length) {
          <section class="card" style="margin-top:16px">
            <div class="card-pad"><h2>Auteurs GitLab non reconnus</h2>
              <p class="muted small">Ajoutez ces e-mails dans « Identités Git » de l'utilisateur concerné : ses commits lui seront rattachés.</p></div>
            @for (a of d.unknownAuthors; track a.email) {
              <div class="line"><div class="grow"><strong>{{ a.name }}</strong> <span class="muted">&lt;{{ a.email }}&gt;</span></div><span class="muted small">{{ a.commits }} commit(s)</span></div>
            }
          </section>
        }
      } @else if (!error()) {
        <p class="empty">Chargement…</p>
      }
    </div>

    @if (gitTask(); as gt) { <app-task-git [task]="gt" (closed)="gitTask.set(null)" /> }
  `,
  styles: `
    .name { font-weight: 600; }
    .low { color: var(--warn); font-weight: 600; }
    td .badge { margin-left: 6px; }
    .grid2 { display: grid; grid-template-columns: 1fr 1fr; gap: 16px; margin-top: 16px; align-items: start; }
    .line { display: flex; gap: 10px; align-items: flex-start; padding: 8px 18px; border-top: 1px solid var(--border); }
    .ref { font-family: ui-monospace, monospace; font-size: 12px; background: var(--brand-soft); color: var(--brand-strong); padding: 1px 6px; border-radius: 6px; margin-right: 6px; }
    .sha { color: var(--muted); font-size: 12px; padding-top: 2px; }
    code { font-family: ui-monospace, monospace; }
    @media (max-width: 1000px) { .grid2 { grid-template-columns: 1fr; } }
  `,
})
export class GitActivityPage implements OnInit {
  private api = inject(ApiService);
  readonly auth = inject(AuthService);

  readonly type = signal<ReportType>('HEBDO');
  readonly date = signal(today());
  readonly data = signal<GitActivity | null>(null);
  readonly status = signal<GitStatus | null>(null);
  readonly projects = signal<Project[]>([]);
  readonly error = signal<string | null>(null);
  readonly gitTask = signal<Task | null>(null);
  readonly statusLabel = TASK_STATUS_LABEL;
  projectId: number | null = null;
  fmt = shortDate;

  readonly total = computed(() => {
    const rows = this.data()?.rows ?? [];
    const commits = rows.reduce((n, r) => n + r.commits, 0);
    const linked = rows.reduce((n, r) => n + r.linkedCommits, 0);
    // Un même jour non déclaré peut apparaître sur plusieurs projets : on compte par consultant.
    const undeclared = new Map<number, number>();
    rows.forEach((r) => undeclared.set(r.consultantId, Math.max(undeclared.get(r.consultantId) ?? 0, r.undeclaredDays)));
    return {
      commits,
      linkedPct: commits ? Math.round((linked * 100) / commits) : 0,
      undeclared: [...undeclared.values()].reduce((a, b) => a + b, 0),
    };
  });

  ngOnInit(): void {
    this.api.gitStatus().subscribe((s) => this.status.set(s));
    this.api.projects().subscribe((p) => this.projects.set(p.filter((x) => x.active)));
    this.load();
  }

  load(): void {
    this.api.gitActivity(this.type(), this.date(), this.projectId).subscribe({
      next: (d) => { this.data.set(d); this.error.set(null); },
      error: (e) => this.error.set(errorMessage(e)),
    });
  }

  shift(dir: number): void {
    const d = this.data();
    if (!d) return;
    this.date.set(dir < 0 ? addDays(d.from, -1) : addDays(d.to, 1));
    this.load();
  }
}
