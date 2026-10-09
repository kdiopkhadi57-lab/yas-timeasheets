import { Component, OnInit, computed, inject, input, output, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { ApiService, errorMessage } from '../core/api.service';
import { Entry, Project, TASK_STATUS_LABEL, Task, TaskStatus } from '../core/models';
import { longDay } from '../core/dates';

/** Ajout / modification d'une ligne de travail d'une journée. */
@Component({
  selector: 'app-entry-editor',
  imports: [FormsModule],
  template: `
    <div class="modal-backdrop" (click)="closed.emit()">
      <form class="card modal" (click)="$event.stopPropagation()" (ngSubmit)="save()">
        <div class="modal-head">
          <h2>{{ entry()?.id ? 'Modifier' : 'Ajouter' }} · {{ dayLabel() }}</h2>
          <button type="button" class="btn icon ghost" (click)="closed.emit()">✕</button>
        </div>
        <div class="modal-body">
          @if (error()) { <div class="alert error">{{ error() }}</div> }
          <div class="form-grid">
            <label class="field"><span>Date</span>
              <input type="date" name="workDate" [(ngModel)]="workDate" required>
            </label>
            @if (showTasks()) {
            <label class="field"><span>Tâche assignée</span>
              <select name="taskId" [(ngModel)]="taskId" (ngModelChange)="onTask($event)">
                <option [ngValue]="null">— Travail non planifié —</option>
                @for (t of openTasks(); track t.id) {
                  <option [ngValue]="t.id">{{ t.title }} ({{ t.projectName }})</option>
                }
              </select>
            </label>
            }
          </div>
          <label class="field"><span>Projet</span>
            <select name="projectId" [(ngModel)]="projectId" required [disabled]="!!taskId">
              <option [ngValue]="null" disabled>Choisir…</option>
              @for (p of activeProjects(); track p.id) {
                <option [ngValue]="p.id">{{ p.name }}{{ p.businessUnit ? ' · ' + p.businessUnit : '' }}</option>
              }
            </select>
          </label>
          <label class="field"><span>Ce que vous avez fait</span>
            <textarea name="description" [(ngModel)]="description" required rows="3"
              placeholder="Ex. Intégration des API pour la confirmation d'une demande PORT OUT"></textarea>
          </label>
          <label class="field"><span>Commentaire (facultatif)</span>
            <input name="comment" [(ngModel)]="comment" placeholder="Bloquant, en attente, RAS…">
          </label>
          @if (taskId && showTasks()) {
            <label class="field"><span>Statut de la tâche après ce travail</span>
              <select name="taskStatus" [(ngModel)]="taskStatus">
                <option [ngValue]="null">Ne pas changer</option>
                @for (s of statuses; track s) { <option [ngValue]="s">{{ statusLabel[s] }}</option> }
              </select>
            </label>
          }
        </div>
        <div class="modal-foot">
          <button type="button" class="btn" (click)="closed.emit()">Annuler</button>
          <button type="submit" class="btn primary" [disabled]="busy() || !description.trim() || !projectId">
            {{ busy() ? 'Enregistrement…' : 'Enregistrer' }}
          </button>
        </div>
      </form>
    </div>
  `,
})
export class EntryEditorComponent implements OnInit {
  private api = inject(ApiService);

  readonly date = input.required<string>();
  readonly entry = input<Entry | null>(null);
  readonly presetTask = input<Task | null>(null);
  /** Pré-remplissage depuis une suggestion GitLab. */
  readonly presetDescription = input<string | null>(null);
  readonly presetProjectId = input<number | null>(null);
  readonly tasks = input<Task[]>([]);
  readonly projects = input<Project[]>([]);
  /** Faux pour les développeurs et le lead dev : saisie libre, sans tâche assignée. */
  readonly showTasks = input(true);
  readonly saved = output<void>();
  readonly closed = output<void>();

  readonly busy = signal(false);
  readonly error = signal<string | null>(null);
  readonly statuses: TaskStatus[] = ['EN_COURS', 'TERMINEE', 'BLOQUEE'];
  readonly statusLabel = TASK_STATUS_LABEL;

  workDate = '';
  taskId: number | null = null;
  projectId: number | null = null;
  description = '';
  comment = '';
  taskStatus: TaskStatus | null = null;

  /** Tâches ouvertes et actives (estimation accordée) : on ne déclare pas de temps sur une tâche pas encore accordée. */
  readonly openTasks = computed(() => this.tasks().filter((t) => (t.status !== 'TERMINEE' && t.active) || t.id === this.entry()?.taskId));
  readonly activeProjects = computed(() => this.projects().filter((p) => p.active || p.id === this.entry()?.projectId));
  dayLabel = () => longDay(this.workDate || this.date());

  ngOnInit(): void {
    const e = this.entry();
    const preset = this.presetTask();
    this.workDate = e?.workDate ?? this.date();
    if (e) {
      this.taskId = e.taskId;
      this.projectId = e.projectId;
      this.description = e.description;
      this.comment = e.comment ?? '';
    } else if (preset) {
      this.onTask(preset.id);
      this.taskStatus = preset.status === 'A_FAIRE' ? 'EN_COURS' : null;
    } else if (this.presetProjectId()) {
      this.projectId = this.presetProjectId();
    } else if (this.activeProjects().length === 1) {
      this.projectId = this.activeProjects()[0].id;
    }
    if (!e && this.presetDescription()) {
      this.description = this.presetDescription()!;
    }
  }

  onTask(id: number | null): void {
    this.taskId = id;
    const t = this.tasks().find((x) => x.id === id);
    if (t) {
      this.projectId = t.projectId;
      if (!this.description.trim()) this.description = t.title;
    }
  }

  save(): void {
    this.busy.set(true);
    this.error.set(null);
    this.api.saveEntry({
      id: this.entry()?.id,
      workDate: this.workDate,
      projectId: this.projectId,
      taskId: this.taskId,
      description: this.description,
      comment: this.comment,
      taskStatus: this.taskId ? this.taskStatus : null,
    }).subscribe({
      next: () => { this.busy.set(false); this.saved.emit(); },
      error: (e) => { this.error.set(errorMessage(e)); this.busy.set(false); },
    });
  }
}
