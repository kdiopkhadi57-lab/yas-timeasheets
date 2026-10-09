import { Component, computed, input, output, signal } from '@angular/core';
import { TASK_PRIORITY_LABEL, TASK_STATUS_COLOR, TASK_STATUS_LABEL, Task, TaskStatus } from '../core/models';
import { addDays, parse, shortDate, today } from '../core/dates';

interface GanttBar { task: Task; left: number; width: number; clippedStart: boolean; clippedEnd: boolean; late: boolean; estimated: boolean; }
interface GanttGroup { name: string; bars: GanttBar[]; }

const DAY_MS = 86_400_000;
const MONTH_FMT = new Intl.DateTimeFormat('fr-FR', { day: 'numeric', month: 'short' });

function diffDays(a: string, b: string): number {
  return Math.round((parse(b).getTime() - parse(a).getTime()) / DAY_MS);
}

/** Planning des tâches (diagramme de Gantt) : une barre du début à l'échéance, remplie selon l'avancement. */
@Component({
  selector: 'app-gantt',
  template: `
    <div class="toolbar">
      <div class="period-nav">
        <button class="btn icon ghost" (click)="shift(-4)" aria-label="Plus tôt">‹</button>
        <span class="label">{{ rangeLabel() }}</span>
        <button class="btn icon ghost" (click)="shift(4)" aria-label="Plus tard">›</button>
      </div>
      <button class="btn sm" (click)="offset.set(0)">Aujourd'hui</button>
      @if (hidden()) { <span class="muted small">{{ hidden() }} tâche{{ hidden() > 1 ? 's' : '' }} hors de la période affichée</span> }
      <span class="grow"></span>
      <ul class="legend">
        @for (s of legendStatuses; track s) { <li><span class="sw" [style.background]="color[s]"></span>{{ label[s] }}</li> }
        <li><span class="sw hatch"></span>Dates estimées</li>
      </ul>
    </div>

    <div class="gantt">
      <div class="head">
        <div class="lbl-col">Tâche</div>
        <div class="timeline">
          @for (w of weeks(); track w.date) {
            <div class="week" [style.left.%]="w.left" [style.width.%]="w.width">{{ w.label }}</div>
          }
        </div>
      </div>
      <div class="rows">
        <div class="bg">
          <div class="lbl-col"></div>
          <div class="timeline">
            @for (d of weekends(); track d) { <div class="weekend" [style.left.%]="d" [style.width.%]="dayPct()"></div> }
            @for (w of weeks(); track w.date) { <div class="vline" [style.left.%]="w.left"></div> }
            @if (todayPct() !== null) { <div class="today" [style.left.%]="todayPct()"><span>Aujourd'hui</span></div> }
          </div>
        </div>
        @for (g of groups(); track g.name) {
          @if (groupBy() !== 'none') {
            <div class="group"><span>{{ g.name }}</span><span class="muted">{{ g.bars.length }}</span></div>
          }
          @for (b of g.bars; track b.task.id) {
            <div class="line" (click)="open.emit(b.task)">
              <div class="lbl-col">
                <span class="ref">{{ b.task.ref }}</span>
                <span class="t" [title]="b.task.title">{{ b.task.title }}</span>
              </div>
              <div class="timeline">
                <div class="bar" [class.est]="b.estimated" [class.late]="b.late" [class.cs]="b.clippedStart" [class.ce]="b.clippedEnd"
                       [style.left.%]="b.left" [style.width.%]="b.width" [style.--c]="color[b.task.status]"
                       [title]="tip(b)">
                    <div class="fill" [style.width.%]="b.task.progress"></div>
                  </div>
                @if (b.late) { <span class="flag" [style.left.%]="b.left + b.width" title="Échéance dépassée">!</span> }
              </div>
            </div>
          }
        } @empty {
          <div class="empty">Aucune tâche sur cette période.</div>
        }
      </div>
    </div>
  `,
  styles: `
    :host { display: block; }
    .toolbar { display: flex; align-items: center; gap: 10px; flex-wrap: wrap; padding: 12px 14px; border-bottom: 1px solid var(--border); }
    .grow { flex: 1; }
    .legend { list-style: none; display: flex; flex-wrap: wrap; gap: 12px; margin: 0; padding: 0; font-size: 12px; color: var(--muted); }
    .legend li { display: flex; align-items: center; gap: 6px; }
    .sw { width: 10px; height: 10px; border-radius: 3px; display: inline-block; }
    .sw.hatch { background: repeating-linear-gradient(45deg, #98a2b3 0 2px, transparent 2px 5px); border: 1px solid #98a2b3; }
    .gantt { overflow-x: auto; }
    .head, .line, .bg, .group { display: grid; grid-template-columns: 260px 1fr; min-width: 860px; }
    .head { border-bottom: 1px solid var(--border); background: var(--surface-2); font-size: 12px; color: var(--muted); font-weight: 600; }
    .head .lbl-col { padding: 8px 12px; }
    .head .timeline { position: relative; height: 32px; }
    .week { position: absolute; top: 0; bottom: 0; display: flex; align-items: center; padding-left: 6px; border-left: 1px solid var(--border); white-space: nowrap; overflow: hidden; }
    .rows { position: relative; }
    .bg { position: absolute; inset: 0; pointer-events: none; }
    .bg .timeline { position: relative; }
    .weekend { position: absolute; top: 0; bottom: 0; background: rgba(16,34,46,.035); }
    .vline { position: absolute; top: 0; bottom: 0; border-left: 1px solid #eef0f2; }
    .today { position: absolute; top: 0; bottom: 0; border-left: 2px solid var(--brand); z-index: 2; }
    .today span { position: absolute; top: 2px; left: 4px; font-size: 10.5px; font-weight: 600; color: var(--brand); background: var(--surface); padding: 0 4px; border-radius: 4px; white-space: nowrap; }
    .group { position: relative; background: var(--surface-2); border-top: 1px solid var(--border); border-bottom: 1px solid var(--border); font-size: 12px; font-weight: 600; }
    .group span:first-child { padding: 6px 12px; }
    .group .muted { padding: 6px 12px; font-weight: 500; }
    .line { position: relative; cursor: pointer; border-bottom: 1px solid #f1f3f5; }
    .line:hover { background: rgba(15,110,133,.04); }
    .line .lbl-col { display: flex; align-items: center; gap: 6px; padding: 8px 12px; min-width: 0; }
    .ref { font-family: ui-monospace, monospace; font-size: 11px; color: var(--brand-strong); background: var(--brand-soft); padding: 0 5px; border-radius: 5px; flex: none; }
    .t { font-size: 13px; white-space: nowrap; overflow: hidden; text-overflow: ellipsis; }
    .line .timeline { position: relative; height: 36px; }
    .bar { position: absolute; top: 11px; height: 14px; border-radius: 4px; background: color-mix(in srgb, var(--c) 28%, white); overflow: hidden; z-index: 1; }
    .bar.est { background: repeating-linear-gradient(45deg, color-mix(in srgb, var(--c) 30%, white) 0 3px, white 3px 6px); outline: 1px dashed color-mix(in srgb, var(--c) 60%, white); outline-offset: -1px; }
    .bar.cs { border-top-left-radius: 0; border-bottom-left-radius: 0; }
    .bar.ce { border-top-right-radius: 0; border-bottom-right-radius: 0; }
    .fill { height: 100%; background: var(--c); }
    .flag { position: absolute; top: 10px; margin-left: 4px; width: 16px; height: 16px; border-radius: 50%; background: var(--danger); color: #fff; font-size: 11px; font-weight: 800; display: grid; place-items: center; z-index: 1; }
    .empty { padding: 32px; text-align: center; color: var(--muted); position: relative; }
    @media (max-width: 720px) { .head, .line, .bg, .group { grid-template-columns: 170px 1fr; } }
  `,
})
export class GanttComponent {
  readonly tasks = input.required<Task[]>();
  readonly groupBy = input<'assignee' | 'project' | 'none'>('none');
  readonly open = output<Task>();

  /** Décalage de la fenêtre, en semaines ; la fenêtre couvre 2 semaines passées et 8 à venir. */
  readonly offset = signal(0);
  readonly span = 10;
  readonly color = TASK_STATUS_COLOR;
  readonly label = TASK_STATUS_LABEL;
  readonly legendStatuses: TaskStatus[] = ['A_FAIRE', 'EN_COURS', 'BLOQUEE', 'TERMINEE'];

  readonly from = computed(() => {
    const t = parse(today());
    const monday = addDays(today(), -((t.getDay() + 6) % 7));
    return addDays(monday, (this.offset() - 2) * 7);
  });
  readonly days = computed(() => this.span * 7);
  readonly dayPct = computed(() => 100 / this.days());
  readonly to = computed(() => addDays(this.from(), this.days() - 1));
  readonly rangeLabel = computed(() => `${MONTH_FMT.format(parse(this.from()))} → ${MONTH_FMT.format(parse(this.to()))}`);

  readonly weeks = computed(() => Array.from({ length: this.span }, (_, i) => {
    const date = addDays(this.from(), i * 7);
    return { date, left: i * 7 * this.dayPct(), width: 7 * this.dayPct(), label: MONTH_FMT.format(parse(date)) };
  }));
  readonly weekends = computed(() => {
    const out: number[] = [];
    for (let i = 0; i < this.days(); i++) {
      if (i % 7 === 5 || i % 7 === 6) out.push(i * this.dayPct());
    }
    return out;
  });
  readonly todayPct = computed(() => {
    const d = diffDays(this.from(), today());
    return d >= 0 && d < this.days() ? (d + 0.5) * this.dayPct() : null;
  });

  private readonly allBars = computed(() => this.tasks().map((t) => this.bar(t)));
  readonly hidden = computed(() => this.allBars().filter((b) => b.width === 0).length);

  readonly groups = computed<GanttGroup[]>(() => {
    const bars = this.allBars().filter((b) => b.width > 0)
      .sort((a, b) => (a.task.startDate ?? a.task.createdAt).localeCompare(b.task.startDate ?? b.task.createdAt));
    const by = this.groupBy();
    if (by === 'none') return bars.length ? [{ name: '', bars }] : [];
    const map = new Map<string, GanttBar[]>();
    bars.forEach((b) => {
      const k = by === 'assignee' ? b.task.assigneeName : b.task.projectName;
      map.set(k, [...(map.get(k) ?? []), b]);
    });
    return [...map.entries()].sort(([a], [b]) => a.localeCompare(b)).map(([name, list]) => ({ name, bars: list }));
  });

  private bar(t: Task): GanttBar {
    const start = t.startDate ?? t.createdAt.substring(0, 10);
    let end = t.dueDate ?? t.completedAt?.substring(0, 10)
      ?? addDays(start, Math.max(0, Math.ceil(t.estimateDays ?? 1) - 1));
    if (end < start) end = start;
    const s = diffDays(this.from(), start);
    const e = diffDays(this.from(), end) + 1;
    const cs = Math.max(0, s);
    const ce = Math.min(this.days(), e);
    const visible = ce > cs;
    return {
      task: t,
      left: visible ? cs * this.dayPct() : 0,
      width: visible ? (ce - cs) * this.dayPct() : 0,
      clippedStart: s < 0,
      clippedEnd: e > this.days(),
      late: t.status !== 'TERMINEE' && !!t.dueDate && t.dueDate < today(),
      estimated: !t.startDate || !t.dueDate,
    };
  }

  shift(weeks: number): void { this.offset.update((o) => o + weeks); }

  tip(b: GanttBar): string {
    const t = b.task;
    return `${t.ref} · ${t.title}\n${TASK_STATUS_LABEL[t.status]} · ${t.progress} % · priorité ${TASK_PRIORITY_LABEL[t.priority].toLowerCase()}\n`
      + `${shortDate(t.startDate) || 'début non renseigné'} → ${shortDate(t.dueDate) || 'échéance non renseignée'}`
      + (t.estimateDays ? `\nCharge : ${t.estimateDays} j · déclarés : ${t.spentDays} j` : '');
  }
}
