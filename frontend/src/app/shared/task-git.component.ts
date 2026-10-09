import { Component, OnInit, inject, input, output, signal } from '@angular/core';
import { DatePipe } from '@angular/common';
import { ApiService, errorMessage } from '../core/api.service';
import { GIT_LINK_LABEL, Task, TaskGit } from '../core/models';

/** Commits et merge requests GitLab liés à une tâche, avec le nom de branche à créer. */
@Component({
  selector: 'app-task-git',
  imports: [DatePipe],
  template: `
    <div class="modal-backdrop" (click)="closed.emit()">
      <div class="card modal wide" (click)="$event.stopPropagation()">
        <div class="modal-head">
          <div>
            <h2><span class="ref">{{ task().ref }}</span> {{ task().title }}</h2>
            <div class="muted small">{{ task().projectName }} · {{ task().assigneeName }}</div>
          </div>
          <button type="button" class="btn icon ghost" (click)="closed.emit()">✕</button>
        </div>
        <div class="modal-body">
          @if (error()) { <div class="alert error">{{ error() }}</div> }
          @if (git(); as g) {
            <div class="howto">
              <div class="small muted">Pour lier son travail à cette tâche, le consultant met <strong>{{ g.ref }}</strong> dans le nom de branche (recommandé), le titre de la MR ou le message de commit :</div>
              <div class="row">
                <code class="grow">{{ g.branchName }}</code>
                <button class="btn sm" (click)="copy(g.branchName)">{{ copied() ? '✓ Copié' : 'Copier' }}</button>
              </div>
              <code class="small muted">git checkout -b {{ g.branchName }}</code>
            </div>

            <h3>Merge requests ({{ g.mergeRequests.length }})</h3>
            @for (m of g.mergeRequests; track m.id) {
              <div class="line">
                <span class="badge" [class]="mrClass(m.state)">{{ mrLabel(m.state) }}</span>
                <div class="grow">
                  <a [href]="m.webUrl" target="_blank" rel="noopener">!{{ m.iid }} {{ m.title }}</a>
                  <div class="muted small">{{ m.repo }} · {{ m.sourceBranch }} · {{ m.authorName }}</div>
                </div>
                <span class="muted small">{{ (m.mergedAt ?? m.updatedAt) | date: 'dd/MM HH:mm' }}</span>
              </div>
            } @empty { <p class="muted small">Aucune merge request.</p> }

            <h3>Commits ({{ g.commits.length }})</h3>
            @for (c of g.commits; track c.id) {
              <div class="line">
                <code class="sha">{{ c.shortSha }}</code>
                <div class="grow">
                  @if (c.webUrl) { <a [href]="c.webUrl" target="_blank" rel="noopener">{{ c.title }}</a> } @else { {{ c.title }} }
                  <div class="muted small">{{ c.consultantName ?? c.authorName }} · {{ c.repo }}@if (c.linkSource) { · lié par {{ linkLabel[c.linkSource] }} }</div>
                </div>
                <span class="muted small">{{ c.authoredAt | date: 'dd/MM HH:mm' }}</span>
              </div>
            } @empty { <p class="muted small">Aucun commit lié pour l'instant.</p> }
          } @else if (!error()) {
            <p class="empty">Chargement…</p>
          }
        </div>
      </div>
    </div>
  `,
  styles: `
    .wide { width: min(760px, 100%); }
    .ref { font-family: ui-monospace, monospace; font-size: .85em; background: var(--brand-soft); color: var(--brand-strong); padding: 1px 7px; border-radius: 6px; margin-right: 4px; }
    .howto { background: var(--surface-2); border: 1px solid var(--border); border-radius: 8px; padding: 10px 12px; display: flex; flex-direction: column; gap: 6px; }
    code { font-family: ui-monospace, monospace; font-size: 13px; overflow-wrap: anywhere; }
    h3 { font-size: 14px; margin: 6px 0 0; }
    .line { display: flex; gap: 10px; align-items: flex-start; padding: 6px 0; border-top: 1px solid var(--border); }
    .sha { color: var(--muted); padding-top: 2px; }
    .badge.merged { background: var(--ok-soft); color: var(--ok); }
    .badge.opened { background: var(--info-soft); color: var(--info); }
    .badge.closed { background: #eef0f3; color: #475467; }
  `,
})
export class TaskGitComponent implements OnInit {
  private api = inject(ApiService);

  readonly task = input.required<Task>();
  readonly closed = output<void>();

  readonly git = signal<TaskGit | null>(null);
  readonly error = signal<string | null>(null);
  readonly copied = signal(false);
  readonly linkLabel = GIT_LINK_LABEL;

  ngOnInit(): void {
    this.api.taskGit(this.task().id).subscribe({
      next: (g) => this.git.set(g),
      error: (e) => this.error.set(errorMessage(e)),
    });
  }

  copy(text: string): void {
    navigator.clipboard?.writeText(text).then(() => {
      this.copied.set(true);
      setTimeout(() => this.copied.set(false), 1500);
    });
  }

  mrClass(state: string | null): string { return state ?? 'closed'; }
  mrLabel(state: string | null): string {
    return state === 'merged' ? 'Fusionnée' : state === 'opened' ? 'Ouverte' : 'Fermée';
  }
}
