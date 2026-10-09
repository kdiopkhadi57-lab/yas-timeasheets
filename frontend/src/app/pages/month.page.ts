import { Component, OnInit, inject, signal } from '@angular/core';
import { forkJoin } from 'rxjs';
import { ApiService, errorMessage } from '../core/api.service';
import { AuthService } from '../core/auth.service';
import { Project, Task, Timesheet } from '../core/models';
import { addDays, shortDate, today } from '../core/dates';
import { ReportPanelComponent } from '../shared/report-panel.component';
import { TimesheetTableComponent } from '../shared/timesheet-table.component';
import { EntryEditorComponent } from '../shared/entry-editor.component';

@Component({
  selector: 'app-month',
  imports: [ReportPanelComponent, TimesheetTableComponent, EntryEditorComponent],
  template: `
    <div class="page">
      <div class="page-head">
        <div>
          <h1>Ma fiche de temps</h1>
          <p class="sub">Période du 20 au 19, au format du fichier Excel envoyé au Head of Office.</p>
        </div>
        <div class="period-nav">
          <button class="btn icon ghost" (click)="shift(-1)" title="Période précédente">‹</button>
          <span class="label">{{ ts()?.periodLabel ?? '…' }}</span>
          <button class="btn icon ghost" (click)="shift(1)" title="Période suivante">›</button>
          <button class="btn sm ghost" (click)="go(todayIso)">Aujourd'hui</button>
        </div>
      </div>
      @if (error()) { <div class="alert error" style="margin-bottom:12px">{{ error() }}</div> }

      @if (ts(); as t) {
        <app-report-panel [timesheet]="t" [date]="date()" (changed)="ts.set($event)" />

        <div class="stats" style="margin-top:16px">
          <div class="card stat"><div class="l">Période</div><div class="v small-v">{{ fmt(t.periodStart) }} → {{ fmt(t.periodEnd) }}</div></div>
          <div class="card stat"><div class="l">Jours travaillés</div><div class="v">{{ t.workedDays }}</div></div>
          <div class="card stat"><div class="l">Tâches saisies</div><div class="v">{{ t.entryCount }}</div></div>
          <div class="card stat"><div class="l">Jours ouvrés sans saisie</div><div class="v" [style.color]="missing(t) ? 'var(--warn)' : ''">{{ missing(t) }}</div></div>
        </div>

        <div class="card sheet-card">
          <div class="head">
            <div><strong>Fiche de temps</strong> · {{ t.consultant.fullName }} · {{ t.consultant.matricule }} · {{ t.consultant.profil }}</div>
          </div>
          <app-timesheet-table [days]="t.days" [editable]="t.editable" (addFor)="editorDate.set($event)" />
        </div>
      }
    </div>
    @if (editorDate(); as d) {
      <app-entry-editor [date]="d" [tasks]="tasks()" [projects]="projects()" [showTasks]="auth.usesTasks()" (saved)="editorDate.set(null); reload()" (closed)="editorDate.set(null)" />
    }
  `,
  styles: `
    .small-v { font-size: 16px !important; padding-top: 6px; }
    .sheet-card { overflow: hidden; }
    .sheet-card .head { padding: 12px 16px; border-bottom: 1px solid var(--border); }
  `,
})
export class MonthPage implements OnInit {
  private api = inject(ApiService);
  readonly auth = inject(AuthService);
  readonly todayIso = today();
  readonly date = signal(this.todayIso);
  readonly ts = signal<Timesheet | null>(null);
  readonly error = signal<string | null>(null);
  readonly editorDate = signal<string | null>(null);
  readonly tasks = signal<Task[]>([]);
  readonly projects = signal<Project[]>([]);
  fmt = shortDate;

  ngOnInit(): void {
    forkJoin({ tasks: this.api.tasks(), projects: this.api.projects() }).subscribe(({ tasks, projects }) => {
      this.tasks.set(tasks);
      this.projects.set(projects);
    });
    this.reload();
  }

  reload(): void {
    this.api.timesheet('MENSUEL', this.date()).subscribe({
      next: (t) => { this.ts.set(t); this.error.set(null); },
      error: (e) => this.error.set(errorMessage(e)),
    });
  }

  shift(dir: number): void {
    const t = this.ts();
    if (!t) return;
    this.go(dir < 0 ? addDays(t.periodStart, -1) : addDays(t.periodEnd, 1));
  }

  go(d: string): void { this.date.set(d); this.reload(); }

  missing(t: Timesheet): number {
    return t.days.filter((d) => !d.weekend && d.date <= this.todayIso && !d.tasks.length).length;
  }
}
