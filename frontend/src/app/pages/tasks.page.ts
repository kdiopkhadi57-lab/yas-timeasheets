import { Component, OnInit, computed, inject, signal, viewChild } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute } from '@angular/router';
import { forkJoin } from 'rxjs';
import { ApiService, errorMessage } from '../core/api.service';
import { AuthService } from '../core/auth.service';
import {
  ESTIMATE_STATUS_LABEL, Project, ProjectSpace, TASK_PRIORITIES, TASK_PRIORITY_LABEL, TASK_STATUSES, TASK_STATUS_COLOR,
  TASK_STATUS_LABEL, Task, TaskPriority, TaskStatus, User,
} from '../core/models';
import { addDays, shortDate, today } from '../core/dates';
import { GanttComponent } from '../shared/gantt.component';
import { TaskDrawerComponent } from '../shared/task-drawer.component';

type View = 'board' | 'list' | 'plan';
type Scope = 'mine' | 'team' | 'projects';
type Quick = 'late' | 'blocked' | 'week' | 'unplanned' | 'estimate';
interface Person { id: number; fullName: string; }
type SortKey = 'title' | 'priority' | 'assigneeName' | 'dueDate' | 'progress' | 'status';

const PRIORITY_RANK: Record<TaskPriority, number> = { CRITIQUE: 3, HAUTE: 2, NORMALE: 1, BASSE: 0 };
const STATUS_RANK: Record<TaskStatus, number> = { BLOQUEE: 0, EN_COURS: 1, A_FAIRE: 2, TERMINEE: 3 };
const VIEW_KEY = 'yas.tasks.view';

@Component({
  selector: 'app-tasks',
  imports: [FormsModule, GanttComponent, TaskDrawerComponent],
  template: `
    <div class="page wide">
      <div class="page-head">
        <div>
          <h1>{{ auth.isManager() || leads() ? 'Tâches & assignation' : 'Mes tâches' }}</h1>
          <p class="sub">{{ auth.isManager() || leads() ? 'Planifiez, priorisez et suivez les tâches de l’équipe. Les développeurs estiment la durée ; la tâche est active une fois l’estimation accordée par le lead dev.' : 'Vos tâches : estimez celles qui vous sont assignées, puis faites-les glisser d’une colonne à l’autre pour changer leur statut.' }}</p>
        </div>
        <div class="row">
          <button class="btn" (click)="exportCsv()" [disabled]="!filtered().length">⤓ Export CSV</button>
          <button class="btn primary" (click)="edit({})">＋ Nouvelle tâche</button>
        </div>
      </div>

      <div class="summary">
        <button class="chip" [class.on]="quick() === null" (click)="quick.set(null)"><b>{{ openCount() }}</b> ouvertes</button>
        <button class="chip danger" [class.on]="quick() === 'late'" (click)="toggleQuick('late')"><b>{{ lateCount() }}</b> en retard</button>
        <button class="chip danger" [class.on]="quick() === 'blocked'" (click)="toggleQuick('blocked')"><b>{{ count('BLOQUEE') }}</b> bloquées</button>
        <button class="chip warn" [class.on]="quick() === 'week'" (click)="toggleQuick('week')"><b>{{ weekCount() }}</b> à rendre sous 7 jours</button>
        <button class="chip" [class.on]="quick() === 'unplanned'" (click)="toggleQuick('unplanned')"><b>{{ unplannedCount() }}</b> sans échéance</button>
        <button class="chip info" [class.on]="quick() === 'estimate'" (click)="toggleQuick('estimate')"><b>{{ estimateCount() }}</b> estimation{{ estimateCount() > 1 ? 's' : '' }} à traiter</button>
      </div>

      <div class="card toolbar">
        <div class="tabs">
          <button [class.on]="view() === 'board'" (click)="setView('board')">▥ Tableau</button>
          <button [class.on]="view() === 'list'" (click)="setView('list')">☰ Liste</button>
          <button [class.on]="view() === 'plan'" (click)="setView('plan')">▤ Planning</button>
        </div>
        @if (auth.isManager() || leads()) {
          <div class="tabs">
            @if (auth.isManager()) { <button [class.on]="scope() === 'team'" (click)="setScope('team')">Équipe</button> }
            @if (leads()) { <button [class.on]="scope() === 'projects'" (click)="setScope('projects')" title="Projets dont vous êtes lead dev ou architecte">Mes projets</button> }
            <button [class.on]="scope() === 'mine'" (click)="setScope('mine')">Les miennes</button>
          </div>
        }
        <input class="search" type="search" placeholder="Rechercher (titre, TS-42, description)…" [ngModel]="search()" (ngModelChange)="search.set($event)">
        @if (scope() !== 'mine') {
          <select [(ngModel)]="assigneeId" (ngModelChange)="load()" aria-label="Consultant">
            <option [ngValue]="null">Tous les développeurs</option>
            @for (u of people(); track u.id) { <option [ngValue]="u.id">{{ u.fullName }}</option> }
          </select>
        }
        <select [(ngModel)]="projectId" (ngModelChange)="load()" aria-label="Projet">
          <option [ngValue]="null">Tous les projets</option>
          @for (p of projects(); track p.id) { <option [ngValue]="p.id">{{ p.name }}</option> }
        </select>
        <select [ngModel]="priority()" (ngModelChange)="priority.set($event)" aria-label="Priorité">
          <option [ngValue]="null">Toutes priorités</option>
          @for (p of priorities; track p) { <option [ngValue]="p">{{ prioLabel[p] }}</option> }
        </select>
        @if (view() === 'list') {
          <select [ngModel]="statusFilter()" (ngModelChange)="statusFilter.set($event)" aria-label="Statut">
            <option [ngValue]="null">Tous statuts</option>
            @for (s of statuses; track s) { <option [ngValue]="s">{{ label[s] }}</option> }
          </select>
        }
        @if (view() === 'plan') {
          <select [ngModel]="groupBy()" (ngModelChange)="groupBy.set($event)" aria-label="Regrouper">
            <option value="none">Sans regroupement</option>
            <option value="assignee">Par consultant</option>
            <option value="project">Par projet</option>
          </select>
        }
        @if (hasFilters()) { <button class="btn ghost sm" (click)="reset()">Effacer les filtres</button> }
      </div>

      @if (error()) { <div class="alert error" style="margin:12px 0">{{ error() }}</div> }

      @switch (view()) {
        @case ('board') {
          <div class="board">
            @for (s of statuses; track s) {
              <section class="column" [class.over]="dragOver() === s"
                       (dragover)="$event.preventDefault(); dragOver.set(s)" (dragleave)="dragOver.set(null)" (drop)="drop($event, s)">
                <header>
                  <span class="dot" [style.background]="color[s]"></span>
                  <h3>{{ label[s] }}</h3>
                  <span class="n">{{ byStatus()[s].length }}</span>
                  @if (s === 'TERMINEE') { <span class="muted small hint">30 derniers jours</span> }
                </header>
                <div class="cards">
                  @for (t of byStatus()[s]; track t.id) {
                    <article class="tcard" draggable="true" (dragstart)="dragStart($event, t)" (dragend)="dragOver.set(null)"
                             (click)="open(t)" [class.late]="isLate(t)" tabindex="0" (keydown.enter)="open(t)">
                      <div class="row top">
                        <span class="ref">{{ t.ref }}</span>
                        <span class="prio" [attr.data-p]="t.priority">{{ prioLabel[t.priority] }}</span>
                      </div>
                      <div class="ttl">{{ t.title }}</div>
                      <div class="muted small">{{ t.projectName }}</div>
                      @if (!t.active) {
                        <div><span class="badge" [class]="t.estimateStatus">{{ estLabel[t.estimateStatus] }}@if (t.proposedEstimateDays) { · {{ t.proposedEstimateDays }} j }</span></div>
                      }
                      @if (t.status !== 'A_FAIRE' || t.progress > 0) {
                        <div class="meter" [title]="t.progress + ' %'"><div [style.width.%]="t.progress" [style.background]="color[t.status]"></div></div>
                      }
                      <div class="meta">
                                @if (scope() !== 'mine') { <span class="avatar" [title]="t.assigneeName">{{ initials(t.assigneeName) }}</span> }
                        @if (t.dueDate) { <span class="due" [class.red]="isLate(t)" [class.soon]="isSoon(t)">◷ {{ fmt(t.dueDate) }}</span> }
                        <span class="grow"></span>
                        @if (t.estimateDays) { <span title="Jours déclarés / charge estimée">{{ t.spentDays }}/{{ t.estimateDays }} j</span> }
                        @if (t.commentCount) { <span title="Commentaires">💬 {{ t.commentCount }}</span> }
                        @if (t.commitCount) { <span title="Commits GitLab">⎇ {{ t.commitCount }}</span> }
                      </div>
                    </article>
                  } @empty {
                    <div class="drop-hint">Déposez une tâche ici</div>
                  }
                </div>
              </section>
            }
          </div>
        }
        @case ('list') {
          <div class="card table-wrap" style="margin-top:12px">
            <table class="data">
              <thead><tr>
                <th class="sort" (click)="sortBy('title')">Tâche {{ arrow('title') }}</th>
                <th class="sort" (click)="sortBy('priority')">Priorité {{ arrow('priority') }}</th>
                @if (scope() !== 'mine') { <th class="sort" (click)="sortBy('assigneeName')">Assignée à {{ arrow('assigneeName') }}</th> }
                <th class="sort" (click)="sortBy('dueDate')">Échéance {{ arrow('dueDate') }}</th>
                <th class="sort" (click)="sortBy('progress')">Avancement {{ arrow('progress') }}</th>
                <th>Charge</th>
                <th class="sort" (click)="sortBy('status')">Statut {{ arrow('status') }}</th>
              </tr></thead>
              <tbody>
                @for (t of sorted(); track t.id) {
                  <tr class="click" (click)="open(t)">
                    <td>
                      <div class="title"><span class="ref">{{ t.ref }}</span>{{ t.title }}</div>
                      <div class="muted small">{{ t.projectName }}@if (t.commentCount) { · 💬 {{ t.commentCount }} }@if (t.commitCount) { · ⎇ {{ t.commitCount }} }
                        @if (!t.active) { <span class="badge" [class]="t.estimateStatus">{{ estLabel[t.estimateStatus] }}@if (t.proposedEstimateDays) { · {{ t.proposedEstimateDays }} j }</span> }</div>
                    </td>
                    <td><span class="prio" [attr.data-p]="t.priority">{{ prioLabel[t.priority] }}</span></td>
                    @if (scope() !== 'mine') { <td>{{ t.assigneeName }}</td> }
                    <td [class.late-txt]="isLate(t)">{{ fmt(t.dueDate) || '—' }}</td>
                    <td style="min-width:120px">
                      <div class="row small"><div class="meter grow"><div [style.width.%]="t.progress" [style.background]="color[t.status]"></div></div>{{ t.progress }} %</div>
                    </td>
                    <td class="small">{{ t.estimateDays ? t.spentDays + ' / ' + t.estimateDays + ' j' : t.spentDays ? t.spentDays + ' j' : '—' }}</td>
                    <td (click)="$event.stopPropagation()">
                      <select class="status-select" [ngModel]="t.status" (ngModelChange)="setStatus(t, $event)" [attr.data-s]="t.status" [disabled]="!t.active" aria-label="Statut">
                        @for (s of statuses; track s) { <option [ngValue]="s">{{ label[s] }}</option> }
                      </select>
                    </td>
                  </tr>
                } @empty {
                  <tr><td colspan="7" class="empty">Aucune tâche.</td></tr>
                }
              </tbody>
            </table>
          </div>
        }
        @case ('plan') {
          <div class="card" style="margin-top:12px; overflow:hidden">
            <app-gantt [tasks]="filtered()" [groupBy]="groupBy()" (open)="open($event)" />
          </div>
        }
      }
    </div>

    @if (selected(); as st) {
      <app-task-drawer #drawer [task]="st" [canEdit]="st.canEdit" (closed)="selectedId.set(null)" (edit)="edit($event)"
                       (statusChange)="setStatus(st, $event)" (commented)="patch(st.id, { commentCount: $event })"
                       (updated)="upsert($event); drawer.load()" />
    }

    @if (draft(); as d) {
      <div class="modal-backdrop" (click)="draft.set(null)">
        <form class="card modal" (click)="$event.stopPropagation()" (ngSubmit)="save(d)">
          <div class="modal-head"><h2>{{ d.id ? 'Modifier ' + d.ref : 'Nouvelle tâche' }}</h2><button type="button" class="btn icon ghost" (click)="draft.set(null)">✕</button></div>
          <div class="modal-body">
            @if (formError()) { <div class="alert error">{{ formError() }}</div> }
            <label class="field"><span>Titre</span><input name="title" [(ngModel)]="d.title" required autofocus></label>
            <label class="field"><span>Description</span><textarea name="description" [(ngModel)]="d.description" rows="3" placeholder="Contexte, critères d’acceptation…"></textarea></label>
            <div class="form-grid">
              <label class="field"><span>Projet</span>
                <select name="projectId" [(ngModel)]="d.projectId" required>
                  @for (p of projects(); track p.id) { <option [ngValue]="p.id">{{ p.name }}</option> }
                </select>
              </label>
              @if (assignees(d.projectId).length) {
                <label class="field"><span>Assignée à</span>
                  <select name="assigneeId" [(ngModel)]="d.assigneeId">
                    <option [ngValue]="me()">Moi</option>
                    @for (u of assignees(d.projectId); track u.id) { @if (u.id !== me()) { <option [ngValue]="u.id">{{ u.fullName }}</option> } }
                  </select>
                </label>
              }
              <label class="field"><span>Priorité</span>
                <select name="priority" [(ngModel)]="d.priority">
                  @for (p of priorities; track p) { <option [ngValue]="p">{{ prioLabel[p] }}</option> }
                </select>
              </label>
              <label class="field"><span>Statut</span>
                <select name="status" [(ngModel)]="d.status" [disabled]="needsEstimate(d)">
                  @for (s of statuses; track s) { <option [ngValue]="s">{{ label[s] }}</option> }
                </select>
              </label>
              <label class="field"><span>Début</span><input type="date" name="startDate" [(ngModel)]="d.startDate"></label>
              <label class="field"><span>Échéance</span><input type="date" name="dueDate" [(ngModel)]="d.dueDate" [min]="d.startDate ?? ''"></label>
              <label class="field"><span>{{ needsEstimate(d) ? 'Charge indicative (jours)' : 'Charge estimée (jours)' }}</span><input type="number" name="estimateDays" [(ngModel)]="d.estimateDays" min="0" step="0.5" placeholder="ex. 3"></label>
              <label class="field"><span>Avancement : {{ d.status === 'TERMINEE' ? 100 : (d.progress ?? 0) }} %</span>
                <input type="range" name="progress" [(ngModel)]="d.progress" min="0" max="100" step="5" [disabled]="d.status === 'TERMINEE'">
              </label>
            </div>
            @if (needsEstimate(d)) {
              <div class="alert info">Le développeur estimera la durée de la tâche ; elle deviendra active une fois l’estimation accordée par le lead dev{{ leadOf(d.projectId) ? ' (' + leadOf(d.projectId) + ')' : '' }}.</div>
            }
          </div>
          <div class="modal-foot">
            @if (d.id) { <button type="button" class="btn danger" (click)="remove(d)">Supprimer</button><span class="grow"></span> }
            <button type="button" class="btn" (click)="draft.set(null)">Annuler</button>
            <button class="btn primary" [disabled]="!d.title || !d.projectId">Enregistrer</button>
          </div>
        </form>
      </div>
    }
  `,
  styles: `
    .page.wide { max-width: 1480px; }
    .summary { display: flex; gap: 8px; flex-wrap: wrap; margin-bottom: 12px; }
    .chip { border: 1px solid var(--border); background: var(--surface); border-radius: 999px; padding: 5px 12px; font: inherit; font-size: 13px; color: var(--muted); cursor: pointer; }
    .chip b { color: var(--text); margin-right: 2px; }
    .chip.danger b { color: var(--danger); }
    .chip.warn b { color: var(--warn); }
    .chip.info b { color: var(--info); }
    .chip.on { border-color: var(--brand); background: var(--brand-soft); color: var(--brand-strong); }
    .toolbar { display: flex; gap: 8px; flex-wrap: wrap; align-items: center; padding: 10px 12px; }
    .toolbar select { width: auto; max-width: 200px; }
    .search { width: 260px; max-width: 100%; }

    .board { display: grid; grid-template-columns: repeat(4, minmax(250px, 1fr)); gap: 12px; margin-top: 12px; overflow-x: auto; padding-bottom: 6px; align-items: start; }
    .column { background: #eceff2; border-radius: 12px; padding: 10px; min-height: 240px; transition: background .15s, box-shadow .15s; }
    .column.over { background: var(--brand-soft); box-shadow: inset 0 0 0 2px var(--accent); }
    .column header { display: flex; align-items: center; gap: 8px; padding: 2px 4px 10px; }
    .column h3 { font-size: 13px; }
    .dot { width: 10px; height: 10px; border-radius: 3px; }
    .n { font-size: 12px; font-weight: 700; color: var(--muted); background: var(--surface); border-radius: 999px; padding: 0 8px; }
    .hint { margin-left: auto; }
    .cards { display: flex; flex-direction: column; gap: 8px; }
    .tcard { background: var(--surface); border: 1px solid var(--border); border-radius: 10px; padding: 10px 12px; cursor: grab; box-shadow: var(--shadow); display: flex; flex-direction: column; gap: 6px; transition: transform .1s, box-shadow .1s; }
    .tcard:hover { box-shadow: 0 4px 14px rgba(16,34,46,.12); transform: translateY(-1px); }
    .tcard:focus-visible { outline: 2px solid var(--accent); }
    .tcard.late { border-left: 3px solid var(--danger); }
    .tcard .top { justify-content: space-between; }
    .ttl { font-weight: 600; line-height: 1.35; }
    .meta { display: flex; align-items: center; gap: 8px; font-size: 12px; color: var(--muted); }
    .avatar { width: 24px; height: 24px; border-radius: 50%; background: var(--brand-soft); color: var(--brand-strong); display: grid; place-items: center; font-size: 10.5px; font-weight: 700; }
    .due.red { color: var(--danger); font-weight: 600; }
    .due.soon { color: var(--warn); font-weight: 600; }
    .drop-hint { border: 1.5px dashed #c9ced5; border-radius: 10px; padding: 18px; text-align: center; color: var(--muted); font-size: 12.5px; }

    .ref { font-family: ui-monospace, monospace; font-size: 12px; background: var(--brand-soft); color: var(--brand-strong); padding: 1px 6px; border-radius: 6px; margin-right: 6px; }
    .tcard .ref { margin-right: 0; font-size: 11px; }
    .prio { font-size: 11px; font-weight: 600; padding: 1px 8px; border-radius: 999px; background: #eef0f3; color: #475467; white-space: nowrap; }
    .prio[data-p='HAUTE'] { background: var(--warn-soft); color: var(--warn); }
    .prio[data-p='CRITIQUE'] { background: var(--danger-soft); color: var(--danger); }
    .prio[data-p='BASSE'] { background: transparent; border: 1px solid var(--border); }
    .meter { height: 5px; background: #eceef1; border-radius: 999px; overflow: hidden; }
    .meter > div { height: 100%; border-radius: 999px; }
    .title { font-weight: 500; }
    .sort { cursor: pointer; user-select: none; white-space: nowrap; }
    .sort:hover { color: var(--text); }
    tr.click { cursor: pointer; }
    tr.click:hover td { background: var(--surface-2); }
    .late-txt { color: var(--danger); font-weight: 600; }
    .status-select { width: auto; padding: 4px 8px; font-size: 13px; font-weight: 600; border-radius: 999px; }
    .status-select[data-s='EN_COURS'] { background: var(--info-soft); color: var(--info); border-color: transparent; }
    .status-select[data-s='TERMINEE'] { background: var(--ok-soft); color: var(--ok); border-color: transparent; }
    .status-select[data-s='BLOQUEE'] { background: var(--danger-soft); color: var(--danger); border-color: transparent; }
    @media (max-width: 720px) { .search { width: 100%; } }
  `,
})
export class TasksPage implements OnInit {
  private api = inject(ApiService);
  private route = inject(ActivatedRoute);
  readonly auth = inject(AuthService);
  private readonly drawer = viewChild<TaskDrawerComponent>('drawer');

  readonly tasks = signal<Task[]>([]);
  readonly projects = signal<Project[]>([]);
  readonly team = signal<User[]>([]);
  readonly spaces = signal<ProjectSpace[]>([]);
  readonly scope = signal<Scope>(this.auth.isManager() ? 'team' : 'mine');
  readonly view = signal<View>(readView());
  readonly statusFilter = signal<TaskStatus | null>(null);
  readonly priority = signal<TaskPriority | null>(null);
  readonly search = signal('');
  readonly quick = signal<Quick | null>(null);
  readonly groupBy = signal<'assignee' | 'project' | 'none'>(this.auth.isManager() ? 'assignee' : 'project');
  readonly sort = signal<{ key: SortKey; dir: 1 | -1 }>({ key: 'priority', dir: -1 });
  readonly error = signal<string | null>(null);
  readonly formError = signal<string | null>(null);
  readonly draft = signal<Partial<Task> | null>(null);
  readonly selectedId = signal<number | null>(null);
  readonly dragOver = signal<TaskStatus | null>(null);
  readonly statuses = TASK_STATUSES;
  readonly priorities = TASK_PRIORITIES;
  readonly label = TASK_STATUS_LABEL;
  readonly prioLabel = TASK_PRIORITY_LABEL;
  readonly color = TASK_STATUS_COLOR;
  readonly estLabel = ESTIMATE_STATUS_LABEL;
  readonly me = computed(() => this.auth.user()?.id ?? null);
  assigneeId: number | null = null;
  projectId: number | null = null;
  fmt = shortDate;
  private dragged: Task | null = null;

  readonly selected = computed(() => this.tasks().find((t) => t.id === this.selectedId()) ?? null);

  /** Projets dont je suis lead dev ou architecte : j'en vois toutes les tâches, le lead dev y assigne. */
  readonly ledSpaces = computed(() => this.spaces().filter((s) => s.myRoles.includes('LEAD_DEV') || s.myRoles.includes('ARCHITECTE')));
  readonly leads = computed(() => this.ledSpaces().length > 0);

  /** Filtre « développeur » : l'équipe du PM, ou celle des projets que je mène. */
  readonly people = computed<Person[]>(() => {
    if (this.scope() === 'team') return this.team();
    const out = new Map<number, Person>();
    this.ledSpaces().forEach((s) => this.projectTeam(s.project).forEach((p) => out.set(p.id, p)));
    return [...out.values()].sort((a, b) => a.fullName.localeCompare(b.fullName));
  });

  readonly filtered = computed(() => {
    const q = this.search().trim().toLowerCase();
    const p = this.priority();
    const quick = this.quick();
    const soon = addDays(today(), 7);
    return this.tasks().filter((t) =>
      (!q || t.title.toLowerCase().includes(q) || t.ref.toLowerCase().includes(q) || (t.description ?? '').toLowerCase().includes(q))
      && (!p || t.priority === p)
      && (quick !== 'late' || this.isLate(t))
      && (quick !== 'blocked' || t.status === 'BLOQUEE')
      && (quick !== 'week' || (t.status !== 'TERMINEE' && !!t.dueDate && t.dueDate >= today() && t.dueDate <= soon))
      && (quick !== 'unplanned' || (t.status !== 'TERMINEE' && !t.dueDate))
      && (quick !== 'estimate' || t.canEstimate || t.canApproveEstimate));
  });

  readonly byStatus = computed(() => {
    const cutoff = addDays(today(), -30);
    const out = { A_FAIRE: [], EN_COURS: [], BLOQUEE: [], TERMINEE: [] } as Record<TaskStatus, Task[]>;
    this.filtered().forEach((t) => {
      if (t.status === 'TERMINEE' && (t.completedAt ?? '').substring(0, 10) < cutoff) return;
      out[t.status].push(t);
    });
    out.TERMINEE.sort((a, b) => (b.completedAt ?? '').localeCompare(a.completedAt ?? ''));
    return out;
  });

  readonly sorted = computed(() => {
    const { key, dir } = this.sort();
    const s = this.statusFilter();
    const list = this.filtered().filter((t) => !s || t.status === s);
    const val = (t: Task): string | number => {
      switch (key) {
        case 'priority': return PRIORITY_RANK[t.priority];
        case 'status': return STATUS_RANK[t.status];
        case 'progress': return t.progress;
        case 'dueDate': return t.dueDate ?? (dir === 1 ? '9999' : '0000');
        default: return (t[key] ?? '').toString().toLowerCase();
      }
    };
    return [...list].sort((a, b) => {
      const va = val(a);
      const vb = val(b);
      return (va < vb ? -1 : va > vb ? 1 : 0) * dir || (a.dueDate ?? '9999').localeCompare(b.dueDate ?? '9999');
    });
  });

  readonly openCount = computed(() => this.tasks().filter((t) => t.status !== 'TERMINEE').length);
  readonly lateCount = computed(() => this.tasks().filter((t) => this.isLate(t)).length);
  readonly weekCount = computed(() => {
    const soon = addDays(today(), 7);
    return this.tasks().filter((t) => t.status !== 'TERMINEE' && !!t.dueDate && t.dueDate >= today() && t.dueDate <= soon).length;
  });
  readonly estimateCount = computed(() => this.tasks().filter((t) => t.canEstimate || t.canApproveEstimate).length);
  readonly unplannedCount = computed(() => this.tasks().filter((t) => t.status !== 'TERMINEE' && !t.dueDate).length);
  readonly hasFilters = computed(() => !!this.search() || !!this.priority() || !!this.quick() || !!this.statusFilter()
    || this.projectId !== null || this.assigneeId !== null);

  ngOnInit(): void {
    const q = this.route.snapshot.queryParamMap;
    if (q.get('task')) this.selectedId.set(Number(q.get('task')));
    if (q.get('project')) this.projectId = Number(q.get('project'));
    if (q.get('filter') === 'late' || q.get('filter') === 'blocked' || q.get('filter') === 'estimate') this.quick.set(q.get('filter') as Quick);
    forkJoin({ projects: this.api.projects(), team: this.api.team(), spaces: this.api.projectSpaces() }).subscribe(({ projects, team, spaces }) => {
      this.projects.set(projects.filter((p) => p.active));
      this.team.set(team);
      this.spaces.set(spaces);
      // Lead dev ou architecte (hors managers) : on ouvre sur les tâches de ses projets.
      if (!this.auth.isManager() && this.leads()) this.scope.set('projects');
      this.load();
    });
  }

  load(): void {
    this.api.tasks({ scope: this.scope(), assigneeId: this.scope() !== 'mine' ? this.assigneeId : null, projectId: this.projectId })
      .subscribe({ next: (t) => this.tasks.set(t), error: (e) => this.error.set(errorMessage(e)) });
  }

  setScope(s: Scope): void {
    this.scope.set(s);
    this.assigneeId = null;
    this.load();
  }

  /** Équipe d'un projet : lead dev, architecte et développeurs. */
  projectTeam(p: Project): Person[] {
    const out: Person[] = [];
    if (p.leadDevId) out.push({ id: p.leadDevId, fullName: p.leadDevName ?? '' });
    if (p.architectId) out.push({ id: p.architectId, fullName: p.architectName ?? '' });
    p.members.forEach((m) => out.push({ id: m.id, fullName: m.fullName }));
    return out;
  }

  /** À qui assigner une tâche de ce projet : l'équipe du projet si j'en suis lead dev, mon équipe si je suis manager. */
  assignees(projectId: number | undefined): Person[] {
    const out = new Map<number, Person>();
    const led = this.ledSpaces().find((s) => s.project.id === projectId && s.myRoles.includes('LEAD_DEV'));
    if (led) this.projectTeam(led.project).forEach((p) => out.set(p.id, p));
    if (this.auth.isManager()) this.team().forEach((u) => out.set(u.id, { id: u.id, fullName: u.fullName }));
    return [...out.values()];
  }

  leadOf(projectId: number | undefined): string | null {
    return this.projects().find((p) => p.id === projectId)?.leadDevName ?? null;
  }

  /** La tâche passera (ou est) par le circuit d'estimation : projet avec un lead dev, assignée à quelqu'un d'autre. */
  needsEstimate(d: Partial<Task>): boolean {
    if (d.id) return d.active === false;
    const p = this.projects().find((x) => x.id === d.projectId);
    const assignee = d.assigneeId ?? this.me();
    return !!p?.leadDevId && assignee !== p.leadDevId;
  }

  setView(v: View): void {
    this.view.set(v);
    try { localStorage.setItem(VIEW_KEY, v); } catch { /* stockage indisponible */ }
  }

  toggleQuick(q: Quick): void { this.quick.set(this.quick() === q ? null : q); }

  reset(): void {
    this.search.set('');
    this.priority.set(null);
    this.quick.set(null);
    this.statusFilter.set(null);
    this.projectId = null;
    this.assigneeId = null;
    this.load();
  }

  count(s: TaskStatus): number { return this.tasks().filter((t) => t.status === s).length; }
  isLate(t: Task): boolean { return !!t.dueDate && t.status !== 'TERMINEE' && t.dueDate < today(); }
  isSoon(t: Task): boolean { return !this.isLate(t) && t.status !== 'TERMINEE' && !!t.dueDate && t.dueDate <= addDays(today(), 2); }
  initials(name: string): string { return name.split(' ').map((p) => p[0]).slice(0, 2).join('').toUpperCase(); }

  sortBy(key: SortKey): void {
    const s = this.sort();
    this.sort.set({ key, dir: s.key === key ? (s.dir === 1 ? -1 : 1) : key === 'priority' ? -1 : 1 });
  }
  arrow(key: SortKey): string { return this.sort().key === key ? (this.sort().dir === 1 ? '↑' : '↓') : ''; }

  open(t: Task): void { this.selectedId.set(t.id); }

  edit(t: Partial<Task>): void {
    this.formError.set(null);
    this.draft.set(t.id ? { ...t } : {
      status: 'A_FAIRE', priority: 'NORMALE', progress: 0, startDate: today(),
      assigneeId: this.assigneeId ?? this.me() ?? undefined, projectId: this.projectId ?? this.projects()[0]?.id,
    });
  }

  save(d: Partial<Task>): void {
    this.api.saveTask(d).subscribe({
      next: (saved) => {
        this.draft.set(null);
        this.upsert(saved);
        this.drawer()?.load();
      },
      error: (e) => this.formError.set(errorMessage(e)),
    });
  }

  remove(d: Partial<Task>): void {
    if (!d.id || !confirm(`Supprimer ${d.ref} ? Les saisies de la fiche de temps sont conservées.`)) return;
    this.api.deleteTask(d.id).subscribe({
      next: () => {
        this.draft.set(null);
        this.selectedId.set(null);
        this.tasks.update((l) => l.filter((x) => x.id !== d.id));
      },
      error: (e) => this.formError.set(errorMessage(e)),
    });
  }

  setStatus(t: Task, s: TaskStatus): void {
    if (t.status === s) return;
    if (!t.active && s !== 'A_FAIRE') {
      this.error.set(`${t.ref} n’est pas encore active : ${t.canEstimate ? 'proposez d’abord votre estimation' : 'l’estimation doit être accordée par le lead dev'}.`);
      return;
    }
    const before = t;
    this.patch(t.id, { status: s, progress: s === 'TERMINEE' ? 100 : t.progress });
    this.api.setTaskStatus(t.id, s).subscribe({
      next: (updated) => { this.upsert(updated); this.drawer()?.load(); },
      error: (e) => { this.upsert(before); this.error.set(errorMessage(e)); },
    });
  }

  patch(id: number, changes: Partial<Task>): void {
    this.tasks.update((list) => list.map((x) => (x.id === id ? { ...x, ...changes } : x)));
  }

  upsert(t: Task): void {
    this.tasks.update((list) => (list.some((x) => x.id === t.id) ? list.map((x) => (x.id === t.id ? t : x)) : [t, ...list]));
  }

  dragStart(ev: DragEvent, t: Task): void {
    this.dragged = t;
    ev.dataTransfer?.setData('text/plain', String(t.id));
    if (ev.dataTransfer) ev.dataTransfer.effectAllowed = 'move';
  }

  drop(ev: DragEvent, s: TaskStatus): void {
    ev.preventDefault();
    this.dragOver.set(null);
    if (this.dragged) this.setStatus(this.dragged, s);
    this.dragged = null;
  }

  /** Les tâches affichées (filtres appliqués), pour Excel : séparateur « ; » et BOM UTF-8. */
  exportCsv(): void {
    const head = ['Référence', 'Tâche', 'Projet', 'Assignée à', 'Priorité', 'Statut', 'Estimation', 'Début', 'Échéance', 'Charge (j)',
      'Jours déclarés', 'Avancement (%)', 'Commits', 'Terminée le'];
    const cell = (v: unknown) => `"${String(v ?? '').replace(/"/g, '""')}"`;
    const rows = this.sorted().map((t) => [t.ref, t.title, t.projectName, t.assigneeName, this.prioLabel[t.priority],
      this.label[t.status], this.estLabel[t.estimateStatus], shortDate(t.startDate), shortDate(t.dueDate), t.estimateDays ?? '', t.spentDays, t.progress,
      t.commitCount, shortDate(t.completedAt)]);
    const csv = '﻿' + [head, ...rows].map((r) => r.map(cell).join(';')).join('\r\n');
    const link = document.createElement('a');
    link.href = URL.createObjectURL(new Blob([csv], { type: 'text/csv;charset=utf-8' }));
    link.download = `Taches-${today()}.csv`;
    link.click();
    setTimeout(() => URL.revokeObjectURL(link.href), 1000);
  }
}

function readView(): View {
  try {
    const v = localStorage.getItem(VIEW_KEY);
    return v === 'list' || v === 'plan' ? v : 'board';
  } catch {
    return 'board';
  }
}
