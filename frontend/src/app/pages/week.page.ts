import { Component, OnInit, computed, inject, signal } from '@angular/core';
import { forkJoin } from 'rxjs';
import { ApiService, errorMessage } from '../core/api.service';
import { AuthService } from '../core/auth.service';
import { Day, Entry, GitSuggestion, Project, TASK_STATUS_LABEL, Task, Timesheet } from '../core/models';
import { addDays, longDay, today } from '../core/dates';
import { ReportPanelComponent } from '../shared/report-panel.component';
import { EntryEditorComponent } from '../shared/entry-editor.component';

@Component({
  selector: 'app-week',
  imports: [ReportPanelComponent, EntryEditorComponent],
  template: `
    <div class="page">
      <div class="page-head">
        <div>
          <h1>Ma semaine</h1>
          <p class="sub">Après le daily, notez ce que vous faites chaque jour : le rapport du vendredi se remplit tout seul.</p>
        </div>
        <div class="period-nav">
          <button class="btn icon ghost" (click)="move(-7)" title="Semaine précédente">‹</button>
          <span class="label">{{ ts()?.periodLabel ?? '…' }}</span>
          <button class="btn icon ghost" (click)="move(7)" title="Semaine suivante">›</button>
          <button class="btn sm ghost" (click)="go(todayIso)">Aujourd'hui</button>
        </div>
      </div>

      @if (error()) { <div class="alert error" style="margin-bottom:12px">{{ error() }}</div> }

      @if (ts(); as t) {
        <app-report-panel [timesheet]="t" [date]="date()" (changed)="ts.set($event)" />

        <div class="layout" [class.single]="!auth.usesTasks()">
          <section class="days">
            <button class="btn ghost sm weekend-toggle" (click)="showWeekend.set(!showWeekend())">
              {{ showWeekend() ? 'Masquer le week-end' : 'Afficher le week-end' }}
            </button>
            @for (d of visibleDays(); track d.date) {
              <article class="card day" [class.today]="d.date === todayIso" [class.weekend]="d.weekend">
                <header>
                  <h3>{{ dayLabel(d.date) }}</h3>
                  @if (d.date === todayIso) { <span class="badge EN_COURS">Aujourd'hui</span> }
                  <span class="grow"></span>
                  @if (t.editable) {
                    @if (!d.entries.length && previousWithEntries(d)) {
                      <button class="btn sm ghost" (click)="copyPrevious(d)" [disabled]="busy()" title="Reprendre les tâches du jour précédent">↻ Comme la veille</button>
                    }
                    <button class="btn sm" (click)="openEditor(d.date)">＋ Ajouter</button>
                  }
                </header>
                @if (d.entries.length) {
                  <ol class="entries">
                    @for (e of d.entries; track e.id) {
                      <li>
                        <span class="num">{{ $index + 1 }}.</span>
                        <div class="grow">
                          <div class="desc">{{ e.description }}</div>
                          <div class="meta">
                            <span class="chip">{{ e.projectName }}</span>
                            @if (e.taskId) { <span class="muted small">Tâche : {{ e.taskTitle }}</span> }
                            @if (e.comment) { <span class="muted small">· {{ e.comment }}</span> }
                          </div>
                        </div>
                        @if (t.editable) {
                          <button class="btn icon ghost sm" (click)="openEditor(d.date, e)" title="Modifier">✎</button>
                          <button class="btn icon ghost sm" (click)="remove(e)" title="Supprimer">🗑</button>
                        }
                      </li>
                    }
                  </ol>
                } @else if (!d.weekend) {
                  <p class="muted small none">{{ d.date <= todayIso ? 'Rien de saisi pour ce jour.' : 'À venir' }}</p>
                }
                @for (s of suggestionsOf(d.date); track $index) {
                  <div class="suggestion">
                    <span class="git-ico" title="Commits GitLab pas encore dans votre fiche">⎇</span>
                    <div class="grow">
                      <div class="desc">{{ s.description }}</div>
                      <div class="meta">
                        <span class="chip">{{ s.projectName }}</span>
                        @if (s.taskRef) { <span class="muted small">{{ s.taskRef }} · {{ s.taskTitle }}</span> } @else if (auth.usesTasks()) { <span class="muted small">Sans tâche</span> }
                        <span class="muted small">· {{ s.commits.length }} commit{{ s.commits.length > 1 ? 's' : '' }} GitLab</span>
                      </div>
                    </div>
                    @if (t.editable) { <button class="btn sm" (click)="fromSuggestion(s)">＋ Ajouter</button> }
                  </div>
                }
              </article>
            }
          </section>

          @if (auth.usesTasks()) {
          <aside class="card side">
            <div class="card-pad">
              <h2>Mes tâches en cours</h2>
              <p class="muted small">Un clic pour noter que vous y avez travaillé aujourd'hui.</p>
            </div>
            @for (task of openTasks(); track task.id) {
              <div class="task">
                <div class="grow">
                  <div class="title">{{ task.title }}</div>
                  <div class="muted small">{{ task.projectName }}
                    @if (task.dueDate) { · échéance {{ task.dueDate.substring(8, 10) }}/{{ task.dueDate.substring(5, 7) }} }
                  </div>
                </div>
                <span class="badge" [class]="task.status">{{ statusLabel[task.status] }}</span>
                @if (t.editable) {
                  <button class="btn sm" (click)="openEditor(todayInPeriod(), null, task)">＋</button>
                }
              </div>
            } @empty {
              <p class="empty small">Aucune tâche ouverte.</p>
            }
          </aside>
          }
        </div>
      } @else if (!error()) {
        <p class="empty">Chargement…</p>
      }
    </div>

    @if (editor(); as ed) {
      <app-entry-editor [date]="ed.date" [entry]="ed.entry" [presetTask]="ed.task" [tasks]="tasks()" [projects]="projects()" [showTasks]="auth.usesTasks()"
        [presetDescription]="ed.description ?? null" [presetProjectId]="ed.projectId ?? null"
        (saved)="editor.set(null); reload()" (closed)="editor.set(null)" />
    }
  `,
  styles: `
    .layout { display: grid; grid-template-columns: 1fr 320px; gap: 16px; margin-top: 16px; align-items: start; }
    .layout.single { grid-template-columns: 1fr; }
    .days { display: flex; flex-direction: column; gap: 10px; }
    .day { padding: 14px 16px; }
    .day.today { border-color: var(--accent); box-shadow: 0 0 0 1px var(--accent); }
    .day.weekend { background: var(--surface-2); }
    .weekend-toggle { align-self: flex-start; }
    .day header { display: flex; align-items: center; gap: 8px; flex-wrap: wrap; }
    .entries { margin: 10px 0 0; padding: 0; list-style: none; display: flex; flex-direction: column; gap: 6px; }
    .entries li { display: flex; align-items: flex-start; gap: 8px; padding: 8px 10px; border-radius: 8px; background: var(--surface-2); }
    .num { color: var(--muted); font-weight: 600; min-width: 18px; }
    .desc { white-space: pre-line; }
    .meta { display: flex; gap: 6px; align-items: center; flex-wrap: wrap; margin-top: 3px; }
    .chip { background: var(--brand-soft); color: var(--brand-strong); border-radius: 6px; padding: 1px 7px; font-size: 12px; font-weight: 600; }
    .none { margin-top: 6px; }
    .suggestion { display: flex; align-items: flex-start; gap: 8px; margin-top: 6px; padding: 8px 10px; border-radius: 8px; border: 1px dashed var(--accent); background: var(--brand-soft); }
    .git-ico { color: var(--brand-strong); font-weight: 700; min-width: 18px; }
    .side { position: sticky; top: 16px; }
    .task { display: flex; align-items: center; gap: 8px; padding: 10px 18px; border-top: 1px solid var(--border); }
    .task .title { font-weight: 500; }
    @media (max-width: 1000px) { .layout { grid-template-columns: 1fr; } .side { position: static; } }
  `,
})
export class WeekPage implements OnInit {
  private api = inject(ApiService);
  readonly auth = inject(AuthService);

  readonly todayIso = today();
  readonly date = signal(this.todayIso);
  readonly ts = signal<Timesheet | null>(null);
  readonly tasks = signal<Task[]>([]);
  readonly projects = signal<Project[]>([]);
  readonly error = signal<string | null>(null);
  readonly busy = signal(false);
  readonly showWeekend = signal(true);
  readonly editor = signal<{ date: string; entry: Entry | null; task: Task | null; description?: string; projectId?: number } | null>(null);
  readonly suggestions = signal<GitSuggestion[]>([]);
  readonly statusLabel = TASK_STATUS_LABEL;

  /** La semaine va du samedi au vendredi : le week-end précédent s'affiche avant le lundi. */
  readonly visibleDays = computed(() =>
    (this.ts()?.days ?? []).filter((d) => !d.weekend || this.showWeekend() || d.entries.length > 0 || this.suggestionsOf(d.date).length > 0),
  );
  readonly openTasks = computed(() => this.tasks().filter((t) => t.status !== 'TERMINEE'));

  dayLabel = longDay;

  ngOnInit(): void {
    forkJoin({ tasks: this.api.tasks(), projects: this.api.projects() }).subscribe({
      next: ({ tasks, projects }) => { this.tasks.set(tasks); this.projects.set(projects); },
      error: (e) => this.error.set(errorMessage(e)),
    });
    this.reload();
  }

  reload(): void {
    this.api.timesheet('HEBDO', this.date()).subscribe({
      next: (t) => {
        this.ts.set(t);
        this.error.set(null);
        this.api.gitSuggestions(t.periodStart, t.periodEnd).subscribe({
          next: (s) => this.suggestions.set(s),
          error: () => this.suggestions.set([]),
        });
      },
      error: (e) => this.error.set(errorMessage(e)),
    });
    this.api.tasks().subscribe((t) => this.tasks.set(t));
  }

  move(days: number): void { this.go(addDays(this.date(), days)); }
  go(d: string): void { this.date.set(d); this.reload(); }

  todayInPeriod(): string {
    const t = this.ts();
    if (!t) return this.todayIso;
    return this.todayIso >= t.periodStart && this.todayIso <= t.periodEnd ? this.todayIso : t.periodStart;
  }

  openEditor(date: string, entry: Entry | null = null, task: Task | null = null): void {
    this.editor.set({ date, entry, task });
  }

  suggestionsOf(date: string): GitSuggestion[] {
    return this.suggestions().filter((s) => s.date === date);
  }

  fromSuggestion(s: GitSuggestion): void {
    const task = this.auth.usesTasks() ? this.tasks().find((t) => t.id === s.taskId) ?? null : null;
    this.editor.set({ date: s.date, entry: null, task, description: s.description, projectId: s.projectId });
  }

  previousWithEntries(d: Day): Day | undefined {
    const days = this.ts()?.days ?? [];
    const idx = days.findIndex((x) => x.date === d.date);
    for (let i = idx - 1; i >= 0; i--) {
      if (days[i].entries.length) return days[i];
    }
    return undefined;
  }

  copyPrevious(d: Day): void {
    const prev = this.previousWithEntries(d);
    if (!prev) return;
    this.busy.set(true);
    forkJoin(prev.entries.map((e) => this.api.saveEntry({
      workDate: d.date, projectId: e.projectId, taskId: e.taskId, description: e.description, comment: e.comment,
    }))).subscribe({
      next: () => { this.busy.set(false); this.reload(); },
      error: (e) => { this.busy.set(false); this.error.set(errorMessage(e)); },
    });
  }

  remove(e: Entry): void {
    if (!confirm('Supprimer cette ligne ?')) return;
    this.api.deleteEntry(e.id).subscribe({
      next: () => this.reload(),
      error: (err) => this.error.set(errorMessage(err)),
    });
  }
}
