import { Component, OnInit, computed, inject, input, output, signal } from '@angular/core';
import { DatePipe } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ApiService, errorMessage } from '../core/api.service';
import { AuthService } from '../core/auth.service';
import {
  ESTIMATE_STATUS_LABEL, TASK_PRIORITY_LABEL, TASK_STATUSES, TASK_STATUS_LABEL, Task, TaskActivity, TaskActivityKind, TaskStatus,
} from '../core/models';
import { shortDate, today } from '../core/dates';
import { TaskGitComponent } from './task-git.component';

/** Fiche détaillée d'une tâche, en panneau latéral : avancement, charge, fil d'activité et commentaires. */
@Component({
  selector: 'app-task-drawer',
  imports: [DatePipe, FormsModule, TaskGitComponent],
  template: `
    <div class="scrim" (click)="closed.emit()"></div>
    <aside class="drawer" role="dialog" [attr.aria-label]="task().title">
      <header>
        <div class="grow">
          <div class="row top">
            <span class="ref">{{ task().ref }}</span>
            <span class="prio" [attr.data-p]="task().priority">{{ prioLabel[task().priority] }}</span>
            @if (late()) { <span class="badge REJETE">En retard</span> }
          </div>
          <h2>{{ task().title }}</h2>
          <div class="muted small">{{ task().projectName }} · {{ task().assigneeName }}@if (task().createdByName) { · créée par {{ task().createdByName }} }</div>
        </div>
        <button type="button" class="btn icon ghost" (click)="closed.emit()" aria-label="Fermer">✕</button>
      </header>

      <div class="body">
        <div class="row">
          <select class="status-select" [ngModel]="task().status" (ngModelChange)="statusChange.emit($event)" [attr.data-s]="task().status" [disabled]="!task().active" aria-label="Statut">
            @for (s of statuses; track s) { <option [ngValue]="s">{{ label[s] }}</option> }
          </select>
          <span class="grow"></span>
          <button class="btn sm" (click)="showGit.set(true)">⎇ {{ task().commitCount }} commit{{ task().commitCount > 1 ? 's' : '' }}</button>
          @if (canEdit()) { <button class="btn sm" (click)="edit.emit(task())">Modifier</button> }
        </div>

        @if (!task().active) {
          <div class="estimate">
            <div class="row">
              <span class="badge" [class]="task().estimateStatus">{{ estLabel[task().estimateStatus] }}</span>
              <span class="muted small grow">
                @if (task().estimateStatus === 'PROPOSEE') { {{ task().assigneeName }} propose <strong>{{ task().proposedEstimateDays }} j</strong>. }
                @else { En attente de l’estimation de {{ task().assigneeName }}. }
                Active une fois accordée{{ task().leadDevName ? ' par ' + task().leadDevName : '' }}.
              </span>
            </div>
            @if (estError()) { <div class="alert error">{{ estError() }}</div> }
            @if (task().canEstimate) {
              <form class="est-form" (ngSubmit)="propose()">
                <label class="field days"><span>Durée (jours)</span><input type="number" name="days" [(ngModel)]="days" min="0.5" step="0.5" required></label>
                <label class="field grow"><span>Commentaire (facultatif)</span><input name="estComment" [(ngModel)]="estComment" placeholder="Hypothèses, découpage…"></label>
                <button class="btn primary" [disabled]="!days || busy()">{{ task().estimateStatus === 'PROPOSEE' ? 'Modifier' : 'Proposer' }}</button>
              </form>
            }
            @if (task().canApproveEstimate) {
              <form class="est-form" (ngSubmit)="approve()">
                <label class="field days"><span>Durée accordée</span><input type="number" name="grant" [(ngModel)]="days" min="0.5" step="0.5" [placeholder]="task().proposedEstimateDays + ''"></label>
                <label class="field grow"><span>Commentaire</span><input name="decision" [(ngModel)]="estComment" placeholder="Obligatoire en cas de refus"></label>
                <button type="button" class="btn danger" (click)="reject()" [disabled]="!estComment.trim() || busy()">Refuser</button>
                <button class="btn success" [disabled]="busy()">Accorder</button>
              </form>
            }
          </div>
        }

        <div class="progress-block">
          <div class="row small"><span class="muted grow">Avancement</span><strong>{{ task().progress }} %</strong></div>
          <div class="meter"><div [style.width.%]="task().progress"></div></div>
        </div>

        <dl class="facts">
          <div><dt>Début</dt><dd>{{ fmt(task().startDate) || '—' }}</dd></div>
          <div><dt>Échéance</dt><dd [class.late]="late()">{{ fmt(task().dueDate) || '—' }}</dd></div>
          <div><dt>Charge estimée</dt><dd>{{ task().estimateDays != null ? task().estimateDays + ' j' : '—' }}</dd></div>
          <div>
            <dt>Jours déclarés</dt>
            <dd [class.over]="over()">{{ task().spentDays }} j
              @if (task().estimateDays) { <span class="muted small">({{ consumed() }} % de l'estimé)</span> }
            </dd>
          </div>
          @if (task().completedAt) { <div><dt>Terminée le</dt><dd>{{ task().completedAt | date: 'dd/MM/yyyy' }}</dd></div> }
        </dl>
        @if (task().estimateDays) {
          <div class="burn" [title]="'Jours déclarés / charge estimée'">
            <div class="meter thin"><div [class.warn]="over()" [style.width.%]="min(consumed(), 100)"></div></div>
          </div>
        }

        @if (task().description) {
          <section><h3>Description</h3><p class="pre">{{ task().description }}</p></section>
        }

        <section>
          <h3>Activité <span class="muted small">({{ commentCount() }} commentaire{{ commentCount() > 1 ? 's' : '' }})</span></h3>
          @if (error()) { <div class="alert error">{{ error() }}</div> }
          <ol class="feed">
            @for (a of activity(); track a.id) {
              @if (a.kind === 'COMMENTAIRE') {
                <li class="comment">
                  <div class="avatar">{{ initials(a.authorName) }}</div>
                  <div class="bubble">
                    <div class="row small"><strong>{{ a.authorName }}</strong><span class="muted">{{ a.createdAt | date: 'dd/MM HH:mm' }}</span>
                      <span class="grow"></span>
                      @if (a.authorId === me()) { <button class="btn ghost sm del" (click)="remove(a)" title="Supprimer">✕</button> }
                    </div>
                    <p class="pre">{{ a.text }}</p>
                  </div>
                </li>
              } @else {
                <li class="event">
                  <span class="dot" [attr.data-k]="a.kind"></span>
                  <span><strong>{{ a.authorName ?? 'Système' }}</strong> · {{ kindLabel[a.kind] }} : {{ a.text }}</span>
                  <span class="muted">{{ a.createdAt | date: 'dd/MM HH:mm' }}</span>
                </li>
              }
            } @empty {
              <li class="muted small">Aucune activité.</li>
            }
          </ol>
          <form class="new" (ngSubmit)="send()">
            <textarea name="comment" [(ngModel)]="draft" rows="2" placeholder="Ajouter un commentaire… (l'assigné et le créateur sont prévenus)" (keydown.control.enter)="send()" (keydown.meta.enter)="send()"></textarea>
            <div class="row"><span class="muted small grow">Ctrl + Entrée pour envoyer</span><button class="btn primary sm" [disabled]="!draft.trim() || sending()">Commenter</button></div>
          </form>
        </section>
      </div>
    </aside>

    @if (showGit()) { <app-task-git [task]="task()" (closed)="showGit.set(false)" /> }
  `,
  styles: `
    .scrim { position: fixed; inset: 0; background: rgba(16,34,46,.35); z-index: 45; }
    .drawer { position: fixed; top: 0; right: 0; bottom: 0; width: min(520px, 100%); background: var(--surface); z-index: 46; display: flex; flex-direction: column; box-shadow: -8px 0 30px rgba(16,34,46,.18); animation: slide .18s ease-out; }
    @keyframes slide { from { transform: translateX(30px); opacity: 0; } }
    header { display: flex; gap: 10px; padding: 18px 18px 14px; border-bottom: 1px solid var(--border); }
    header h2 { font-size: 17px; margin: 6px 0 2px; }
    .top { gap: 6px; }
    .body { overflow-y: auto; padding: 16px 18px 24px; display: flex; flex-direction: column; gap: 16px; }
    .ref { font-family: ui-monospace, monospace; font-size: 12px; background: var(--brand-soft); color: var(--brand-strong); padding: 1px 6px; border-radius: 6px; }
    .prio { font-size: 11.5px; font-weight: 600; padding: 1px 8px; border-radius: 999px; background: #eef0f3; color: #475467; }
    .prio[data-p='HAUTE'] { background: var(--warn-soft); color: var(--warn); }
    .prio[data-p='CRITIQUE'] { background: var(--danger-soft); color: var(--danger); }
    .prio[data-p='BASSE'] { background: transparent; border: 1px solid var(--border); }
    .status-select { width: auto; padding: 4px 8px; font-size: 13px; font-weight: 600; border-radius: 999px; }
    .status-select[data-s='EN_COURS'] { background: var(--info-soft); color: var(--info); border-color: transparent; }
    .status-select[data-s='TERMINEE'] { background: var(--ok-soft); color: var(--ok); border-color: transparent; }
    .status-select[data-s='BLOQUEE'] { background: var(--danger-soft); color: var(--danger); border-color: transparent; }
    .meter { height: 8px; background: #eceef1; border-radius: 999px; overflow: hidden; margin-top: 6px; }
    .meter > div { height: 100%; background: var(--brand); border-radius: 999px; transition: width .2s; }
    .meter.thin { height: 4px; margin-top: 0; }
    .meter > div.warn { background: var(--warn); }
    .facts { display: grid; grid-template-columns: repeat(2, 1fr); gap: 10px 16px; margin: 0; }
    .facts dt { font-size: 12px; color: var(--muted); }
    .facts dd { margin: 2px 0 0; font-weight: 600; }
    .late { color: var(--danger); }
    .over { color: var(--warn); }
    section h3 { margin-bottom: 8px; }
    .pre { white-space: pre-line; margin: 0; }
    .feed { list-style: none; margin: 0; padding: 0; display: flex; flex-direction: column; gap: 10px; }
    .event { display: grid; grid-template-columns: 12px 1fr auto; gap: 8px; align-items: baseline; font-size: 12.5px; color: var(--muted); }
    .event strong { color: var(--text); font-weight: 600; }
    .dot { width: 8px; height: 8px; border-radius: 50%; background: #c9ced5; display: inline-block; }
    .dot[data-k='STATUT'] { background: var(--info); }
    .dot[data-k='CREATION'] { background: var(--brand); }
    .dot[data-k='ASSIGNATION'] { background: var(--warn); }
    .comment { display: flex; gap: 10px; }
    .avatar { width: 30px; height: 30px; border-radius: 50%; background: var(--brand-soft); color: var(--brand-strong); display: grid; place-items: center; font-size: 11.5px; font-weight: 700; flex: none; }
    .bubble { flex: 1; background: var(--surface-2); border: 1px solid var(--border); border-radius: 10px; padding: 8px 10px; }
    .bubble p { margin-top: 4px; }
    .del { height: 22px; padding: 0 6px; color: var(--muted); }
    .new { margin-top: 12px; display: flex; flex-direction: column; gap: 6px; }
    .estimate { border: 1px solid var(--border); background: var(--surface-2); border-radius: 10px; padding: 12px; display: flex; flex-direction: column; gap: 10px; }
    .est-form { display: flex; gap: 8px; align-items: flex-end; flex-wrap: wrap; }
    .est-form .days { width: 110px; }
    .est-form .grow { min-width: 160px; }
    .dot[data-k='ESTIMATION'] { background: var(--ok); }
  `,
})
export class TaskDrawerComponent implements OnInit {
  private api = inject(ApiService);
  private auth = inject(AuthService);

  readonly task = input.required<Task>();
  readonly canEdit = input(false);
  readonly closed = output<void>();
  readonly edit = output<Task>();
  readonly statusChange = output<TaskStatus>();
  /** Le nombre de commentaires a changé : la liste peut se rafraîchir. */
  readonly commented = output<number>();
  /** Estimation proposée, accordée ou refusée. */
  readonly updated = output<Task>();

  readonly activity = signal<TaskActivity[]>([]);
  readonly error = signal<string | null>(null);
  readonly sending = signal(false);
  readonly showGit = signal(false);
  readonly busy = signal(false);
  readonly estError = signal<string | null>(null);
  readonly estLabel = ESTIMATE_STATUS_LABEL;
  days: number | null = null;
  estComment = '';
  readonly statuses = TASK_STATUSES;
  readonly label = TASK_STATUS_LABEL;
  readonly prioLabel = TASK_PRIORITY_LABEL;
  readonly kindLabel: Record<TaskActivityKind, string> = {
    CREATION: 'création', COMMENTAIRE: 'commentaire', STATUT: 'statut', ASSIGNATION: 'assignation', MODIFICATION: 'modification',
    ESTIMATION: 'estimation',
  };
  readonly me = computed(() => this.auth.user()?.id ?? null);
  readonly late = computed(() => !!this.task().dueDate && this.task().status !== 'TERMINEE' && this.task().dueDate! < today());
  readonly consumed = computed(() => {
    const e = this.task().estimateDays;
    return e ? Math.round((this.task().spentDays / e) * 100) : 0;
  });
  readonly over = computed(() => this.consumed() > 100 || (this.consumed() > this.task().progress + 25 && this.task().status !== 'TERMINEE'));
  readonly commentCount = computed(() => this.activity().filter((a) => a.kind === 'COMMENTAIRE').length);
  draft = '';
  fmt = shortDate;
  min = Math.min;

  ngOnInit(): void { this.load(); }

  /** Rechargé aussi par la page après un changement de statut ou une modification. */
  load(): void {
    this.api.taskActivity(this.task().id).subscribe({
      next: (a) => this.activity.set(a),
      error: (e) => this.error.set(errorMessage(e)),
    });
  }

  send(): void {
    const text = this.draft.trim();
    if (!text || this.sending()) return;
    this.sending.set(true);
    this.api.addComment(this.task().id, text).subscribe({
      next: (a) => {
        this.activity.update((l) => [...l, a]);
        this.draft = '';
        this.sending.set(false);
        this.commented.emit(this.commentCount());
      },
      error: (e) => { this.error.set(errorMessage(e)); this.sending.set(false); },
    });
  }

  remove(a: TaskActivity): void {
    if (!confirm('Supprimer ce commentaire ?')) return;
    this.api.deleteComment(this.task().id, a.id).subscribe({
      next: () => {
        this.activity.update((l) => l.filter((x) => x.id !== a.id));
        this.commented.emit(this.commentCount());
      },
      error: (e) => this.error.set(errorMessage(e)),
    });
  }

  propose(): void {
    if (!this.days) return;
    this.decide(this.api.proposeEstimate(this.task().id, this.days, this.estComment));
  }

  approve(): void {
    this.decide(this.api.approveEstimate(this.task().id, this.days || null, this.estComment));
  }

  reject(): void {
    if (!this.estComment.trim()) return;
    this.decide(this.api.rejectEstimate(this.task().id, this.estComment));
  }

  private decide(call: ReturnType<ApiService['approveEstimate']>): void {
    this.busy.set(true);
    this.estError.set(null);
    call.subscribe({
      next: (t) => {
        this.busy.set(false);
        this.days = null;
        this.estComment = '';
        this.updated.emit(t);
      },
      error: (e) => { this.busy.set(false); this.estError.set(errorMessage(e)); },
    });
  }

  initials(name: string | null): string {
    return (name ?? '?').split(' ').map((p) => p[0]).slice(0, 2).join('').toUpperCase();
  }
}
