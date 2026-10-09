import { Component, input, output } from '@angular/core';
import { Day } from '../core/models';
import { longDay, today } from '../core/dates';

/** Vue identique au fichier Excel : Date | Projet | Business Unit | Liste des tâches | Commentaire. */
@Component({
  selector: 'app-timesheet-table',
  template: `
    <div class="table-wrap">
      <table class="sheet">
        <thead>
          <tr><th class="c-date">Date</th><th>Projet</th><th>Business Unit</th><th class="c-tasks">Liste des tâches</th><th>Commentaire</th>
            @if (editable()) { <th class="c-act"></th> }
          </tr>
        </thead>
        <tbody>
          @for (d of days(); track d.date) {
            <tr [class.weekend]="d.weekend" [class.empty-day]="!d.weekend && !d.tasks.length && isPast(d.date)">
              <td class="c-date">{{ label(d.date) }}</td>
              <td>@for (p of d.projects; track p) { <div>{{ p }}</div> }</td>
              <td>@for (b of d.businessUnits; track b) { <div>{{ b }}</div> }</td>
              <td class="c-tasks">
                @if (d.tasks.length === 1) { {{ d.tasks[0] }} }
                @else if (d.tasks.length > 1) {
                  <ol>@for (t of d.tasks; track $index) { <li>{{ t }}</li> }</ol>
                }
              </td>
              <td>@for (c of d.comments; track c) { <div>{{ c }}</div> }</td>
              @if (editable()) {
                <td class="c-act"><button class="btn sm ghost" (click)="addFor.emit(d.date)" title="Ajouter une tâche ce jour">＋</button></td>
              }
            </tr>
          }
        </tbody>
      </table>
    </div>
  `,
  styles: `
    .sheet { width: 100%; border-collapse: collapse; font-size: 13px; }
    .sheet th { background: #4bacc6; color: #0b2430; text-align: left; padding: 8px 10px; font-weight: 700; border: 1px solid #3d97ae; position: sticky; top: 0; }
    .sheet td { border: 1px solid var(--border); padding: 7px 10px; vertical-align: middle; }
    .c-date { white-space: nowrap; width: 170px; }
    .c-tasks { min-width: 280px; }
    .c-act { width: 44px; text-align: center; }
    ol { margin: 0; padding-left: 18px; }
    tr.weekend td { background: #f3f4f6; color: var(--muted); }
    tr.empty-day td.c-date { color: var(--warn); font-weight: 600; }
  `,
})
export class TimesheetTableComponent {
  readonly days = input.required<Day[]>();
  readonly editable = input(false);
  readonly addFor = output<string>();

  label = longDay;
  private readonly todayIso = today();
  isPast(d: string): boolean {
    return d < this.todayIso;
  }
}
