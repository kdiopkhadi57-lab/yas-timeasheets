import { Component, OnInit, computed, inject, signal } from '@angular/core';
import { DatePipe } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ApiService, errorMessage } from '../core/api.service';
import { AuthService } from '../core/auth.service';
import { INVOICE_STATUS_LABEL, Invoice, InvoiceProfile, InvoiceTeamRow } from '../core/models';
import { shortDate, today } from '../core/dates';
import { SignaturePadComponent } from '../shared/signature-pad.component';

const MONTHS = ['janvier', 'février', 'mars', 'avril', 'mai', 'juin', 'juillet', 'août', 'septembre', 'octobre', 'novembre', 'décembre'];

/** « 2026-09 » ± n mois. */
function shift(month: string, n: number): string {
  const [y, m] = month.split('-').map(Number);
  const d = new Date(y, m - 1 + n, 1);
  return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}`;
}

/**
 * Factures mensuelles. Consultant : saisit ses honoraires nets du mois (dates, numéro et période calculés),
 * signe et envoie le PDF au Head of Office. Head of Office / Admin : suit, télécharge, valide ou renvoie.
 */
@Component({
  selector: 'app-invoices',
  imports: [DatePipe, FormsModule, SignaturePadComponent],
  template: `
    <div class="page">
      <div class="page-head">
        <div>
          <h1>{{ head() ? 'Factures des consultants' : 'Ma facture' }}</h1>
          <p class="sub">{{ head() ? 'Factures mensuelles reçues en PDF : téléchargez, validez ou renvoyez-les pour correction.' : 'Saisissez le montant du mois : dates, numéro et calculs sont faits pour vous. La facture signée part en PDF au Head of Office.' }}</p>
        </div>
        <div class="period-nav">
          <button class="btn icon ghost" (click)="go(-1)" aria-label="Mois précédent">‹</button>
          <span class="label">{{ monthLabel() }}</span>
          <button class="btn icon ghost" (click)="go(1)" aria-label="Mois suivant">›</button>
        </div>
      </div>

      @if (error()) { <div class="alert error" style="margin-bottom:12px">{{ error() }}</div> }
      @if (notice()) { <div class="alert ok" style="margin-bottom:12px">{{ notice() }}</div> }

      @if (head()) {
        <div class="stats">
          <div class="card stat"><div class="l">Factures reçues</div><div class="v">{{ received() }} / {{ rows().length }}</div></div>
          <div class="card stat"><div class="l">À traiter</div><div class="v">{{ pendingCount() }}</div></div>
          <div class="card stat"><div class="l">Total net à payer</div><div class="v">{{ money(totalNet()) }} <small>FCFA</small></div></div>
        </div>
        <div class="card table-wrap">
          <table class="data">
            <thead><tr><th>Consultant</th><th>Numéro</th><th>Objet</th><th class="num">Net à payer</th><th>Statut</th><th></th></tr></thead>
            <tbody>
              @for (r of rows(); track r.consultant.id) {
                <tr>
                  <td><strong>{{ r.consultant.fullName }}</strong><div class="muted small">{{ r.consultant.matricule }} · {{ r.consultant.profil }}</div></td>
                  @if (r.invoice; as i) {
                    <td class="small mono">{{ i.number }}</td>
                    <td class="small">{{ i.objectLine }}<div class="muted">Date : {{ fmt(i.invoiceDate) }}@if (i.submittedAt) { · envoyée le {{ i.submittedAt | date: 'dd/MM HH:mm' }} }</div></td>
                    <td class="num"><strong>{{ money(i.netToPay) }}</strong></td>
                    <td><span class="badge" [class]="i.status">{{ statusLabel[i.status] }}</span>
                      @if (i.reviewComment) { <div class="muted small">{{ i.reviewComment }}</div> }</td>
                    <td class="actions">
                      @if (i.status !== 'BROUILLON') { <button class="btn sm" (click)="preview(i)">Voir le PDF</button> }
                      @if (i.status === 'SOUMISE') {
                        <button class="btn sm success" (click)="approve(i)">Valider</button>
                        <button class="btn sm danger" (click)="reject(i)">Renvoyer</button>
                      }
                    </td>
                  } @else {
                    <td colspan="3" class="muted small">Pas encore de facture pour ce mois</td>
                    <td><span class="badge BROUILLON">Manquante</span></td><td></td>
                  }
                </tr>
              } @empty {
                <tr><td colspan="6" class="empty">Aucun consultant.</td></tr>
              }
            </tbody>
          </table>
        </div>
      } @else {
        <div class="layout">
          @if (inv(); as i) {
            <form class="card card-pad invoice" (ngSubmit)="save()">
              <div class="row head">
                <div class="grow">
                  <div class="muted small">Numéro</div>
                  <div class="number">{{ i.number }}</div>
                </div>
                <span class="badge" [class]="i.id ? i.status : 'BROUILLON'">{{ i.id ? statusLabel[i.status] : 'Nouvelle' }}</span>
              </div>
              @if (i.status === 'REJETEE' && i.reviewComment) {
                <div class="alert warn">À corriger ({{ i.reviewerName }}) : {{ i.reviewComment }}</div>
              }
              @if (i.status === 'SOUMISE') { <div class="alert info">Envoyée au Head of Office le {{ i.submittedAt | date: 'dd/MM/yyyy à HH:mm' }}. En attente de validation.</div> }
              @if (i.status === 'VALIDEE') { <div class="alert ok">Validée par {{ i.reviewerName }} le {{ i.reviewedAt | date: 'dd/MM/yyyy' }}.</div> }

              <div class="form-grid">
                <label class="field"><span>Période facturée</span><input [value]="'Du ' + fmt(i.periodStart) + ' au ' + fmt(i.periodEnd)" disabled></label>
                <label class="field"><span>Date de la facture</span><input type="date" name="invoiceDate" [(ngModel)]="form.invoiceDate" [disabled]="!editable()" required></label>
              </div>
              <label class="field"><span>Intitulé (objet)</span>
                <input name="title" [(ngModel)]="form.title" [disabled]="!editable()" required placeholder="Consultant Développeur Java">
                <small class="muted">Sur la facture : « {{ form.title || '…' }} - {{ periodText() }} »</small>
              </label>
              <div class="form-grid">
                <label class="field"><span>Honoraires nets (FCFA)</span>
                  <input type="number" name="netAmount" [(ngModel)]="form.netAmount" [disabled]="!editable()" min="1" step="1000" required placeholder="800000" class="amount">
                </label>
                <label class="field"><span>Retenue ordinateur à reverser à STAM</span>
                  <input type="number" name="computerDeduction" [(ngModel)]="form.computerDeduction" [disabled]="!editable()" min="0" step="1000" class="amount">
                </label>
              </div>

              <table class="calc">
                <tr><td>Honoraires bruts</td><td>{{ money(gross()) }}</td></tr>
                <tr><td>Retenue ({{ i.withholdingPercent }} %)</td><td>-{{ money(gross() - net()) }}</td></tr>
                <tr class="b"><td>Honoraires nets</td><td>{{ money(net()) }}</td></tr>
                <tr><td>Retenue ordinateur à reverser à STAM</td><td>{{ money(deduction()) }}</td></tr>
                <tr class="total"><td>Net à payer</td><td>{{ money(net() - deduction()) }} FCFA</td></tr>
              </table>
              <div class="muted small">Facturé à {{ i.client }}</div>

              <div class="row foot">
                @if (i.id) { <button type="button" class="btn" (click)="preview(i)">Aperçu PDF</button> }
                <span class="grow"></span>
                @if (editable()) {
                  <button class="btn" [disabled]="busy() || !valid()">Enregistrer</button>
                  <button type="button" class="btn primary" [disabled]="busy() || !valid()" (click)="send()">✉ Envoyer au Head of Office</button>
                }
              </div>
              @if (editable() && !ready()) {
                <p class="muted small" style="margin:0">Pour envoyer : renseignez votre téléphone, votre adresse et votre signature (ci-contre).</p>
              }
            </form>
          }

          <aside class="side">
            <section class="card card-pad">
              <h3>Mes coordonnées <span class="muted small">(imprimées sur la facture)</span></h3>
              <form class="col" (ngSubmit)="saveProfile()">
                <label class="field"><span>Téléphone</span><input name="phone" [(ngModel)]="contact.phone" placeholder="77 000 00 00"></label>
                <label class="field"><span>E-mail</span><input type="email" name="invoiceEmail" [(ngModel)]="contact.invoiceEmail"></label>
                <label class="field"><span>Adresse</span><input name="address" [(ngModel)]="contact.address" placeholder="Yoff / Sicap Layenne"></label>
                <div class="row"><span class="grow"></span><button class="btn sm">Enregistrer</button></div>
              </form>
            </section>
            <section class="card card-pad">
              <h3>Ma signature</h3>
              @if (signatureUrl() && !resign()) {
                <img [src]="signatureUrl()" alt="Signature" class="sig">
                <div class="row"><button class="btn sm" (click)="resign.set(true)">Refaire</button>
                  <button class="btn sm ghost danger" (click)="removeSignature()">Supprimer</button></div>
              } @else {
                <app-signature-pad (saved)="saveSignature($event)" />
              }
            </section>
            <section class="card card-pad">
              <h3>Mes factures</h3>
              <ul class="history">
                @for (h of history(); track h.id) {
                  <li>
                    <button class="link" (click)="month.set(h.month); load()">{{ h.number }}</button>
                    <span class="badge" [class]="h.status">{{ statusLabel[h.status] }}</span>
                    <span class="grow"></span>
                    <span class="small">{{ money(h.netToPay) }}</span>
                    <button class="btn ghost sm" (click)="download(h)" aria-label="Télécharger">⤓</button>
                  </li>
                } @empty { <li class="muted small">Aucune facture enregistrée.</li> }
              </ul>
            </section>
          </aside>
        </div>
      }
    </div>
  `,
  styles: `
    .layout { display: grid; grid-template-columns: minmax(0, 1fr) 360px; gap: 16px; align-items: start; }
    .invoice { display: flex; flex-direction: column; gap: 14px; }
    .head { align-items: flex-start; }
    .number { font-family: ui-monospace, monospace; font-size: 18px; font-weight: 700; }
    .amount { font-variant-numeric: tabular-nums; font-size: 16px; font-weight: 600; }
    .calc { width: 100%; border-collapse: collapse; font-variant-numeric: tabular-nums; }
    .calc td { padding: 6px 4px; border-bottom: 1px solid var(--border); }
    .calc td:last-child { text-align: right; width: 40%; }
    .calc tr.b td { font-weight: 700; }
    .calc tr.total td { font-weight: 800; font-size: 16px; border-top: 2px solid var(--text); border-bottom: 2px solid var(--text); }
    .foot { gap: 8px; }
    .side { display: flex; flex-direction: column; gap: 16px; }
    .side h3 { margin-bottom: 10px; }
    .col { display: flex; flex-direction: column; gap: 10px; }
    .sig { max-width: 100%; max-height: 120px; display: block; margin-bottom: 8px; border: 1px solid var(--border); border-radius: 8px; background: #fff; }
    .history { list-style: none; margin: 0; padding: 0; display: flex; flex-direction: column; gap: 6px; }
    .history li { display: flex; align-items: center; gap: 8px; }
    .link { border: 0; background: none; padding: 0; font: inherit; font-family: ui-monospace, monospace; font-size: 12.5px; color: var(--brand); cursor: pointer; text-align: left; }
    .num { text-align: right; font-variant-numeric: tabular-nums; white-space: nowrap; }
    .mono { font-family: ui-monospace, monospace; }
    .actions { text-align: right; white-space: nowrap; }
    .actions .btn { margin-left: 4px; }
    .stat small { font-size: 12px; color: var(--muted); font-weight: 500; }
    @media (max-width: 960px) { .layout { grid-template-columns: 1fr; } }
  `,
})
export class InvoicesPage implements OnInit {
  private api = inject(ApiService);
  readonly auth = inject(AuthService);

  readonly head = computed(() => this.auth.hasRole('HEAD_OF_OFFICE', 'ADMIN'));
  readonly month = signal(today().substring(0, 7));
  readonly inv = signal<Invoice | null>(null);
  readonly history = signal<Invoice[]>([]);
  readonly rows = signal<InvoiceTeamRow[]>([]);
  readonly profile = signal<InvoiceProfile | null>(null);
  readonly signatureUrl = signal<string | null>(null);
  readonly resign = signal(false);
  readonly busy = signal(false);
  readonly error = signal<string | null>(null);
  readonly notice = signal<string | null>(null);
  readonly statusLabel = INVOICE_STATUS_LABEL;
  readonly fmt = shortDate;
  /** Champs saisis ; les montants calculés suivent en direct. */
  form = { invoiceDate: '', title: '', netAmount: 0 as number | null, computerDeduction: 0 as number | null };
  contact = { phone: '' as string | null, invoiceEmail: '' as string | null, address: '' as string | null };

  readonly monthLabel = computed(() => {
    const [y, m] = this.month().split('-').map(Number);
    const name = MONTHS[m - 1];
    return name.charAt(0).toUpperCase() + name.slice(1) + ' ' + y;
  });
  readonly editable = computed(() => this.inv()?.id == null || !!this.inv()?.editable);
  readonly ready = computed(() => !!this.profile()?.phone && !!this.profile()?.address && !!this.profile()?.hasSignature);
  readonly received = computed(() => this.rows().filter((r) => r.invoice && r.invoice.status !== 'BROUILLON').length);
  readonly pendingCount = computed(() => this.rows().filter((r) => r.invoice?.status === 'SOUMISE').length);
  readonly totalNet = computed(() => this.rows().reduce((s, r) => s + (r.invoice && r.invoice.status !== 'BROUILLON' ? r.invoice.netToPay : 0), 0));

  ngOnInit(): void {
    if (!this.head()) {
      this.api.invoiceProfile().subscribe((p) => this.setProfile(p));
      this.loadHistory();
    }
    this.load();
  }

  go(n: number): void {
    this.month.set(shift(this.month(), n));
    this.load();
  }

  load(): void {
    this.error.set(null);
    this.notice.set(null);
    if (this.head()) {
      this.api.teamInvoices(this.month()).subscribe({ next: (r) => this.rows.set(r), error: (e) => this.error.set(errorMessage(e)) });
      return;
    }
    this.api.invoiceForMonth(this.month()).subscribe({ next: (i) => this.setInvoice(i), error: (e) => this.error.set(errorMessage(e)) });
  }

  // --- Calculs en direct (mêmes règles que le serveur)
  net(): number { return Math.max(0, Number(this.form.netAmount) || 0); }
  deduction(): number { return Math.max(0, Number(this.form.computerDeduction) || 0); }
  gross(): number {
    const rate = (this.inv()?.withholdingPercent ?? 5) / 100;
    return Math.round(this.net() / (1 - rate));
  }
  valid(): boolean { return !!this.form.invoiceDate && !!this.form.title.trim() && this.net() > 0 && this.deduction() <= this.net(); }
  periodText(): string {
    const i = this.inv();
    if (!i) return '';
    const [y, m] = i.month.split('-').map(Number);
    const name = MONTHS[m - 1];
    return `Periode du 01 au ${i.periodEnd.substring(8)} ${name.charAt(0).toUpperCase() + name.slice(1)} ${y}`;
  }
  money(v: number): string { return new Intl.NumberFormat('fr-FR').format(v); }

  save(after?: (i: Invoice) => void): void {
    if (!this.valid()) return;
    this.busy.set(true);
    this.error.set(null);
    this.api.saveInvoice({
      month: this.month(), invoiceDate: this.form.invoiceDate, title: this.form.title.trim(),
      netAmount: this.net(), computerDeduction: this.deduction(),
    }).subscribe({
      next: (i) => {
        this.setInvoice(i);
        this.loadHistory();
        if (after) after(i);
        else { this.busy.set(false); this.notice.set('Facture enregistrée.'); }
      },
      error: (e) => { this.busy.set(false); this.error.set(errorMessage(e)); },
    });
  }

  send(): void {
    if (!confirm('Envoyer cette facture signée au Head of Office ? Elle ne sera plus modifiable, sauf si elle vous est renvoyée.')) return;
    this.save((saved) => {
      this.api.submitInvoice(saved.id!).subscribe({
        next: (i) => {
          this.busy.set(false);
          this.setInvoice(i);
          this.loadHistory();
          this.notice.set(`Facture ${i.number} envoyée en PDF au Head of Office.`);
        },
        error: (e) => { this.busy.set(false); this.error.set(errorMessage(e)); },
      });
    });
  }

  preview(i: Invoice): void {
    if (!i.id) return;
    const tab = window.open('', '_blank');
    this.api.invoicePdf(i.id).subscribe({
      next: (blob) => {
        const url = URL.createObjectURL(blob);
        if (tab) tab.location.href = url;
        else window.location.href = url;
        setTimeout(() => URL.revokeObjectURL(url), 60_000);
      },
      error: (e) => { tab?.close(); this.error.set(errorMessage(e)); },
    });
  }

  download(i: Invoice): void {
    if (i.id) this.api.downloadInvoice(i.id).subscribe({ error: (e) => this.error.set(errorMessage(e)) });
  }

  approve(i: Invoice): void {
    this.api.approveInvoice(i.id!).subscribe({ next: () => this.load(), error: (e) => this.error.set(errorMessage(e)) });
  }

  reject(i: Invoice): void {
    const why = prompt(`Que doit corriger ${i.consultantName} ?`);
    if (!why?.trim()) return;
    this.api.rejectInvoice(i.id!, why).subscribe({ next: () => this.load(), error: (e) => this.error.set(errorMessage(e)) });
  }

  saveProfile(): void {
    this.api.saveInvoiceProfile(this.contact).subscribe({
      next: (p) => { this.setProfile(p); this.notice.set('Coordonnées enregistrées.'); },
      error: (e) => this.error.set(errorMessage(e)),
    });
  }

  saveSignature(dataUrl: string): void {
    this.api.saveSignature(dataUrl).subscribe({
      next: (p) => { this.resign.set(false); this.setProfile(p); this.notice.set('Signature enregistrée : elle sera apposée sur vos factures.'); },
      error: (e) => this.error.set(errorMessage(e)),
    });
  }

  removeSignature(): void {
    if (!confirm('Supprimer votre signature ?')) return;
    this.api.deleteSignature().subscribe({ next: (p) => this.setProfile(p), error: (e) => this.error.set(errorMessage(e)) });
  }

  private setInvoice(i: Invoice): void {
    this.inv.set(i);
    this.form = { invoiceDate: i.invoiceDate, title: i.title, netAmount: i.netAmount || null, computerDeduction: i.computerDeduction };
  }

  private setProfile(p: InvoiceProfile): void {
    this.profile.set(p);
    this.contact = { phone: p.phone, invoiceEmail: p.invoiceEmail, address: p.address };
    const old = this.signatureUrl();
    if (old) URL.revokeObjectURL(old);
    this.signatureUrl.set(null);
    if (p.hasSignature) this.api.signature().subscribe((b) => this.signatureUrl.set(URL.createObjectURL(b)));
  }

  private loadHistory(): void {
    this.api.myInvoices().subscribe({ next: (h) => this.history.set(h), error: () => undefined });
  }
}
