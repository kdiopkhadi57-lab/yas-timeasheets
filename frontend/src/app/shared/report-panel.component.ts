import { Component, computed, inject, input, output, signal } from '@angular/core';
import { DatePipe } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ApiService, errorMessage } from '../core/api.service';
import { REPORT_STATUS_LABEL, Timesheet } from '../core/models';
import { today } from '../core/dates';

/** Statut du rapport du consultant connecté + actions Soumettre / Retirer / Télécharger. */
@Component({
  selector: 'app-report-panel',
  imports: [FormsModule, DatePipe],
  template: `
    @let ts = timesheet();
    @let r = ts.report;
    <div class="card card-pad panel" [class]="'st-' + status()">
      <div class="row">
        <div class="grow">
          <div class="row">
            <h2>{{ ts.type === 'HEBDO' ? 'Rapport hebdo' : 'Fiche de temps' }} · {{ ts.periodLabel }}</h2>
            <span class="badge" [class]="status()">{{ statusLabel() }}</span>
          </div>
          <p class="muted small" style="margin-top:4px">
            @switch (status()) {
              @case ('BROUILLON') { Destinataire : {{ recipient() }}. {{ ts.entryCount }} tâche(s) sur {{ ts.workedDays }} jour(s). }
              @case ('SOUMIS') { Envoyé le {{ r?.submittedAt | date: 'dd/MM/yyyy à HH:mm' }} à {{ recipient() }}. En attente de validation. }
              @case ('VALIDE') { Validé par {{ r?.reviewerName }} le {{ r?.reviewedAt | date: 'dd/MM/yyyy à HH:mm' }}. }
              @case ('REJETE') { {{ r?.reviewerName }} demande une correction. Modifiez vos saisies puis soumettez à nouveau. }
            }
          </p>
        </div>
        <button class="btn" (click)="download()" [disabled]="busy()">⬇ Excel</button>
        @if (status() === 'SOUMIS') {
          <button class="btn" (click)="withdraw()" [disabled]="busy()">Retirer</button>
        }
        @if (status() === 'BROUILLON' || status() === 'REJETE') {
          <button class="btn primary" (click)="confirming.set(true)" [disabled]="busy() || !ts.entryCount">
            Soumettre {{ ts.type === 'HEBDO' ? 'au Manager' : 'au Head of Office' }}
          </button>
        }
      </div>
      @if (r?.reviewComment) {
        <div class="alert" [class.error]="status() === 'REJETE'" [class.ok]="status() === 'VALIDE'" style="margin-top:12px">
          <strong>{{ r?.reviewerName }} :</strong> {{ r?.reviewComment }}
        </div>
      }
      @if (error()) { <div class="alert error" style="margin-top:12px">{{ error() }}</div> }
    </div>

    @if (confirming()) {
      <div class="modal-backdrop" (click)="confirming.set(false)">
        <div class="card modal" (click)="$event.stopPropagation()">
          <div class="modal-head"><h2>Soumettre {{ ts.periodLabel }}</h2><button class="btn icon ghost" (click)="confirming.set(false)">✕</button></div>
          <div class="modal-body">
            <p>Le fichier Excel sera généré au format YAS et envoyé à <strong>{{ recipient() }}</strong>.
              Vos saisies de la période seront verrouillées jusqu'à la validation.</p>
            @if (emptyWorkdays() > 0) {
              <div class="alert warn">{{ emptyWorkdays() }} jour(s) ouvré(s) passé(s) sans aucune tâche saisie.</div>
            }
            <label class="field"><span>Commentaire pour {{ ts.type === 'HEBDO' ? 'le Manager' : 'le Head of Office' }} (facultatif)</span>
              <textarea [(ngModel)]="comment" rows="3" placeholder="Points bloquants, congés, remarques…"></textarea>
            </label>
          </div>
          <div class="modal-foot">
            <button class="btn" (click)="confirming.set(false)">Annuler</button>
            <button class="btn primary" (click)="submit()" [disabled]="busy()">{{ busy() ? 'Envoi…' : 'Soumettre et envoyer' }}</button>
          </div>
        </div>
      </div>
    }
  `,
  styles: `
    .panel { border-left: 4px solid #cbd2d9; }
    .panel.st-SOUMIS { border-left-color: var(--info); }
    .panel.st-VALIDE { border-left-color: var(--ok); }
    .panel.st-REJETE { border-left-color: var(--danger); }
  `,
})
export class ReportPanelComponent {
  private api = inject(ApiService);

  readonly timesheet = input.required<Timesheet>();
  readonly date = input.required<string>();
  readonly changed = output<Timesheet>();

  readonly busy = signal(false);
  readonly error = signal<string | null>(null);
  readonly confirming = signal(false);
  comment = '';

  readonly status = computed(() => this.timesheet().report?.status ?? 'BROUILLON');
  readonly statusLabel = computed(() => REPORT_STATUS_LABEL[this.status()]);
  readonly recipient = computed(() => {
    const ts = this.timesheet();
    if (ts.type === 'MENSUEL') return 'Head of Office' + (ts.consultant.pmName ? ` (copie ${ts.consultant.pmName})` : '');
    return ts.consultant.pmName ?? 'Head of Office';
  });
  readonly emptyWorkdays = computed(() => {
    const todayIso = today();
    return this.timesheet().days.filter((d) => !d.weekend && d.date <= todayIso && !d.tasks.length).length;
  });

  submit(): void {
    this.run(this.api.submit(this.timesheet().type, this.date(), this.comment), () => {
      this.confirming.set(false);
      this.comment = '';
    });
  }

  withdraw(): void {
    this.run(this.api.withdraw(this.timesheet().type, this.date()));
  }

  download(): void {
    this.busy.set(true);
    this.api.downloadExcel(this.timesheet().type, this.date()).subscribe({
      next: () => this.busy.set(false),
      error: (e) => { this.error.set(errorMessage(e)); this.busy.set(false); },
    });
  }

  private run(obs: ReturnType<ApiService['submit']>, after?: () => void): void {
    this.busy.set(true);
    this.error.set(null);
    obs.subscribe({
      next: (ts) => { this.busy.set(false); after?.(); this.changed.emit(ts); },
      error: (e) => { this.error.set(errorMessage(e)); this.busy.set(false); },
    });
  }
}
