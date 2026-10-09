import { Component, OnInit, computed, inject, signal } from '@angular/core';
import { DatePipe } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { ApiService, errorMessage } from '../core/api.service';
import { AuthService } from '../core/auth.service';
import {
  Dashboard, MemberRow, PROJECT_ROLE_LABEL, Project, ProjectSpace, REPORT_STATUS_LABEL, ROLE_LABEL, Report,
} from '../core/models';
import { reportSpan, shortDate } from '../core/dates';
import { BarListComponent, BarRow, Column, ColumnChartComponent } from '../shared/charts';
import { ProjectDocumentsComponent } from '../shared/project-documents.component';

type Filter = 'all' | 'submitted' | 'pending' | 'reviewed' | 'missing' | 'days';
const FILTER_LABEL: Record<Filter, string> = {
  all: 'Tous', submitted: 'Ont soumis', pending: 'Fiches à valider', reviewed: 'Validées / à corriger',
  missing: 'Rapports manquants', days: 'Jours déclarés',
};

/** Couleurs des séries (ordre catégoriel fixe, validé daltonisme). */
const SERIES = { blue: '#2a78d6', orange: '#eb6834' };

/**
 * Tableau de bord des managers (leurs développeurs et lead devs) et du Head of Office (les architectes) :
 * fiches soumises par personne ; un clic sur une personne liste ses fiches, un clic sur une fiche ouvre son détail.
 */
@Component({
  selector: 'app-dashboard',
  imports: [FormsModule, RouterLink, DatePipe, BarListComponent, ColumnChartComponent, ProjectDocumentsComponent],
  template: `
    <div class="page wide">
      <div class="page-head">
        <div>
          <h1>Tableau de bord</h1>
          <p class="sub">
            @if (d(); as d) { Du {{ fmt(d.from) }} au {{ fmt(d.to) }} · }
            {{ audienceText() }}
          </p>
        </div>
        <div class="row">
          <select [(ngModel)]="projectId" (ngModelChange)="load()" aria-label="Projet">
            <option [ngValue]="null">Tous les projets</option>
            @for (p of projects(); track p.id) { <option [ngValue]="p.id">{{ p.name }}</option> }
          </select>
          <select [(ngModel)]="weeks" (ngModelChange)="load()" aria-label="Période">
            <option [ngValue]="4">4 semaines</option>
            <option [ngValue]="8">8 semaines</option>
            <option [ngValue]="12">12 semaines</option>
            <option [ngValue]="26">6 mois</option>
            <option [ngValue]="52">12 mois</option>
          </select>
        </div>
      </div>

      @if (error()) { <div class="alert error" style="margin-bottom:12px">{{ error() }}</div> }

      @if (d(); as d) {
        <div class="kpis">
          <button class="card kpi" type="button" [class.on]="filter() === 'all'" (click)="applyFilter('all')"><div class="l">{{ people() }} suivis</div><div class="v">{{ d.kpis.members }}</div><div class="s">{{ d.audience === 'ARCHITECTE' ? 'rattachés au Head of Office' : d.audience === 'DEV' ? 'dans votre équipe' : 'remplissent une fiche' }}</div></button>
          <button class="card kpi" type="button" [class.on]="filter() === 'submitted'" (click)="applyFilter('submitted')"><div class="l">Fiches soumises</div><div class="v">{{ d.kpis.submitted }}</div><div class="s">sur {{ weeks }} semaines (hebdo + mensuelles)</div></button>
          <button class="card kpi" type="button" [class.on]="filter() === 'pending'" (click)="applyFilter('pending')"><div class="l">À valider</div><div class="v" [class.warn]="d.kpis.pending">{{ d.kpis.pending }}</div><div class="s">fiches reçues en attente</div></button>
          <button class="card kpi" type="button" [class.on]="filter() === 'reviewed'" (click)="applyFilter('reviewed')"><div class="l">Validées</div><div class="v good">{{ d.kpis.validated }}</div><div class="s">{{ d.kpis.rejected }} renvoyée(s) à corriger</div></button>
          <button class="card kpi" type="button" [class.on]="filter() === 'missing'" (click)="applyFilter('missing')"><div class="l">Rapports manquants</div><div class="v" [class.bad]="totalMissing()">{{ totalMissing() }}</div><div class="s">dont {{ d.kpis.missingLastWeek }} la dernière semaine échue</div></button>
          <button class="card kpi" type="button" [class.on]="filter() === 'days'" (click)="applyFilter('days')"><div class="l">Jours déclarés</div><div class="v">{{ num(d.kpis.declaredDays) }}<small> j</small></div><div class="s">ponctualité {{ d.kpis.onTimeRate ?? '—' }}@if (d.kpis.onTimeRate !== null) { % }</div></button>
        </div>

        <div class="grid">
          <section class="card panel span-2" id="members">
            <div class="ph">
              <div class="ph-row">
                <h2>{{ people() }}</h2>
                @if (filter() !== 'all') {
                  <span class="chip">{{ filterLabel[filter()] }} · {{ visibleMembers().length }}<button type="button" (click)="applyFilter('all')" aria-label="Retirer le filtre">×</button></span>
                }
              </div>
              <span class="muted small">Nombre de fiches soumises sur la période · cliquez sur une personne pour voir ses fiches</span>
            </div>
            <div class="table-wrap">
              <table class="data compact">
                <thead>
                  <tr>
                    <th>{{ person() }}</th>
                    <th class="n">Soumises</th>
                    <th class="n">À valider</th>
                    <th class="n">Validées</th>
                    <th class="n">À corriger</th>
                    <th class="n">Retards</th>
                    <th class="n">Manquants</th>
                    <th class="n">Jours</th>
                    <th>Dernière soumission</th>
                  </tr>
                </thead>
                <tbody>
                  @for (m of visibleMembers(); track m.id) {
                    <tr class="click" [class.sel]="selected()?.id === m.id" (click)="select(m)">
                      <td>
                        <div class="name">{{ m.fullName }}</div>
                        <div class="muted small">{{ m.matricule }} · {{ roleLabel[m.role] }}@if (m.profil) { · {{ m.profil }} }</div>
                      </td>
                      <td class="n"><span class="pill">{{ m.submitted }}</span><div class="muted small">{{ m.weekly }} hebdo · {{ m.monthly }} mens.</div></td>
                      <td class="n" [class.warn]="m.pending">{{ m.pending }}</td>
                      <td class="n">{{ m.validated }}</td>
                      <td class="n" [class.bad]="m.rejected">{{ m.rejected }}</td>
                      <td class="n" [class.warn]="m.late">{{ m.late }}</td>
                      <td class="n" [class.bad]="m.missing">{{ m.missing }}</td>
                      <td class="n">{{ num(m.declaredDays) }} j</td>
                      <td class="small">{{ m.lastSubmittedAt ? (m.lastSubmittedAt | date: 'dd/MM/yyyy HH:mm') : '—' }}</td>
                    </tr>
                  } @empty {
                    <tr><td colspan="9" class="empty">{{ filter() === 'all' ? 'Aucun ' + person().toLowerCase() + ' à suivre.' : 'Personne dans cette catégorie.' }}</td></tr>
                  }
                </tbody>
              </table>
            </div>
          </section>

          <section class="card panel reports">
            @if (selected(); as m) {
              <div class="ph">
                <h2>Fiches de {{ m.fullName }}</h2>
                <span class="muted small">
                  @if (filter() === 'missing') { {{ missingWeeks().length }} rapport(s) hebdo non soumis depuis le {{ fmt(d.from) }} · cliquez pour voir la semaine }
                  @else { {{ memberReports().length }} fiche(s) {{ reportsWord() }} depuis le {{ fmt(d.from) }} · cliquez pour voir le détail }
                </span>
              </div>
              @if (loadingReports()) {
                <p class="empty">Chargement…</p>
              } @else if (filter() === 'missing') {
                <ul class="rlist">
                  @for (w of missingWeeks(); track w) {
                    <li>
                      <a [routerLink]="['/equipe', m.id]" [queryParams]="{ type: 'HEBDO', date: w }">
                        <div class="row1">
                          <span class="span">{{ span({ type: 'HEBDO', periodStart: w, periodEnd: w }) }}</span>
                          <span class="badge BROUILLON">Non soumis</span>
                        </div>
                        <div class="muted small"><span class="tag">Rapport hebdo</span> attendu avant le vendredi 18 h</div>
                      </a>
                    </li>
                  } @empty {
                    <li class="empty">Aucun rapport manquant sur la période.</li>
                  }
                </ul>
              } @else {
                <ul class="rlist">
                  @for (r of memberReports(); track r.id) {
                    <li>
                      <a [routerLink]="['/equipe', r.consultantId]" [queryParams]="{ type: r.type, date: r.periodStart }">
                        <div class="row1">
                          <span class="span">{{ span(r) }}</span>
                          <span class="badge" [class]="r.status">{{ statusLabel[r.status] }}</span>
                        </div>
                        <div class="muted small">
                          <span class="tag" [class.m]="r.type === 'MENSUEL'">{{ r.type === 'HEBDO' ? 'Rapport hebdo' : 'Fiche mensuelle' }}</span>
                          @if (r.submittedAt) { Soumise le {{ r.submittedAt | date: 'dd/MM/yyyy à HH:mm' }} }
                        </div>
                      </a>
                    </li>
                  } @empty {
                    <li class="empty">Aucune fiche {{ reportsWord() }} sur la période.</li>
                  }
                </ul>
              }
            } @else {
              <div class="ph"><h2>Fiches rendues</h2></div>
              <p class="empty">Cliquez sur un {{ person().toLowerCase() }} pour voir la liste de ses fiches (du lundi au vendredi), puis sur une fiche pour l’ouvrir.</p>
            }
          </section>

          <section class="card panel span-2">
            <div class="ph"><h2>Ponctualité des rapports hebdo</h2><span class="muted small">Soumis avant vendredi 18 h · en retard · manquants</span></div>
            <app-column-chart [columns]="complianceColumns()" emptyText="Aucune semaine échue sur la période." />
          </section>

          <section class="card panel" id="projects" [class.hl]="filter() === 'days'">
            <div class="ph"><h2>Jours déclarés par projet</h2><span class="muted small">Jours-homme (journée partagée = répartie)</span></div>
            <app-bar-list [rows]="projectRows()" unit=" j" emptyText="Aucune saisie sur la période." />
          </section>
        </div>
      } @else if (!error()) {
        <p class="empty">Chargement…</p>
      }

      @if (docSpace(); as ds) {
        <section class="card panel docs-panel">
          <div class="ph docs-head">
            <div class="grow">
              <h2>Documents du projet</h2>
              <span class="muted small">Documents partagés par l’architecte et discussions de l’équipe.</span>
            </div>
            @if (docSpaces().length > 1) {
              <div class="tabs">
                @for (s of docSpaces(); track s.project.id) {
                  <button [class.on]="s.project.id === ds.project.id" (click)="docProjectId.set(s.project.id)">{{ s.project.name }}</button>
                }
              </div>
            } @else {
              <span class="pname">{{ ds.project.name }} · {{ myRoles(ds) }}</span>
            }
            <a class="btn sm" routerLink="/projets" [queryParams]="{ project: ds.project.id }">Équipe du projet →</a>
          </div>
          <app-project-documents [space]="ds" />
        </section>
      }
    </div>
  `,
  styles: `
    .page.wide { max-width: 1480px; }
    .page-head select { width: auto; max-width: 210px; }
    .docs-panel { margin-top: 16px; }
    .ph.docs-head { flex-direction: row; gap: 12px; align-items: center; flex-wrap: wrap; }
    .ph.docs-head .grow { flex: 1 1 380px; display: flex; flex-direction: column; gap: 2px; min-width: 260px; }
    .docs-head .tabs { flex-wrap: wrap; }
    .pname { font-weight: 600; color: var(--brand-strong); background: var(--brand-soft); border-radius: 999px; padding: 3px 12px; font-size: 13px; }
    .kpis { display: grid; grid-template-columns: repeat(6, minmax(0, 1fr)); gap: 12px; margin-bottom: 14px; }
    .kpi { padding: 14px 16px; color: inherit; display: block; text-align: left; font: inherit; }
    button.kpi { cursor: pointer; }
    button.kpi:hover { border-color: var(--accent); }
    button.kpi.on { border-color: var(--accent); box-shadow: inset 0 0 0 1px var(--accent); background: var(--brand-soft); }
    .ph-row { display: flex; align-items: center; gap: 10px; flex-wrap: wrap; }
    .chip { display: inline-flex; align-items: center; gap: 6px; font-size: 12px; font-weight: 600; background: var(--brand-soft); color: var(--brand-strong); border-radius: 999px; padding: 2px 4px 2px 10px; }
    .chip button { border: 0; background: transparent; color: inherit; cursor: pointer; font-size: 15px; line-height: 1; padding: 0 4px; }
    .panel.hl { border-color: var(--accent); box-shadow: inset 0 0 0 1px var(--accent); }
    .kpi .l { font-size: 12px; color: var(--muted); font-weight: 500; }
    .kpi .v { font-size: 28px; font-weight: 700; margin-top: 2px; letter-spacing: -.02em; }
    .kpi .v small { font-size: 15px; font-weight: 600; color: var(--muted); }
    .kpi .s { font-size: 12px; color: var(--muted); margin-top: 2px; }
    .bad { color: var(--danger); }
    .warn { color: var(--warn); }
    .good { color: var(--ok); }
    .grid { display: grid; grid-template-columns: repeat(3, minmax(0, 1fr)); gap: 14px; }
    .panel { padding: 16px 18px; min-width: 0; }
    .span-2 { grid-column: span 2; }
    .ph { display: flex; flex-direction: column; gap: 2px; margin-bottom: 14px; }
    table.compact td, table.compact th { padding: 8px 10px; }
    .n { text-align: right; font-variant-numeric: tabular-nums; }
    tr.click { cursor: pointer; }
    tr.click:hover td { background: var(--surface-2); }
    tr.sel td { background: var(--brand-soft); }
    .name { font-weight: 600; }
    .pill { display: inline-block; min-width: 28px; text-align: center; font-weight: 700; background: var(--brand-soft); color: var(--brand-strong); border-radius: 999px; padding: 1px 8px; }
    .reports { align-self: start; position: sticky; top: 16px; max-height: calc(100vh - 32px); overflow: auto; }
    .rlist { list-style: none; margin: 0; padding: 0; display: flex; flex-direction: column; gap: 8px; }
    .rlist a { display: block; color: inherit; border: 1px solid var(--border); border-radius: 10px; padding: 10px 12px; }
    .rlist a:hover { border-color: var(--accent); background: var(--surface-2); }
    .row1 { display: flex; justify-content: space-between; gap: 8px; align-items: center; margin-bottom: 4px; }
    .span { font-weight: 600; }
    .tag { font-size: 11px; font-weight: 600; padding: 1px 8px; border-radius: 999px; background: var(--brand-soft); color: var(--brand-strong); margin-right: 6px; }
    .tag.m { background: var(--warn-soft); color: var(--warn); }
    @media (max-width: 1280px) { .kpis { grid-template-columns: repeat(3, minmax(0, 1fr)); } }
    @media (max-width: 720px) { .kpis { grid-template-columns: repeat(2, minmax(0, 1fr)); } }
    @media (max-width: 1100px) {
      .grid { grid-template-columns: 1fr; }
      .span-2 { grid-column: auto; }
      .reports { position: static; max-height: none; }
    }
  `,
})
export class DashboardPage implements OnInit {
  private api = inject(ApiService);
  readonly auth = inject(AuthService);

  readonly d = signal<Dashboard | null>(null);
  readonly error = signal<string | null>(null);
  readonly projects = signal<Project[]>([]);
  readonly selected = signal<MemberRow | null>(null);
  readonly history = signal<Report[]>([]);
  readonly loadingReports = signal(false);
  readonly spaces = signal<ProjectSpace[]>([]);
  readonly docProjectId = signal<number | null>(null);
  /** Projets dont je fais partie de l'équipe (PM d'un projet, par exemple). */
  readonly docSpaces = computed(() => this.spaces().filter((s) => s.myRoles.length > 0));
  readonly docSpace = computed(() => this.docSpaces().find((s) => s.project.id === this.docProjectId()) ?? this.docSpaces()[0] ?? null);
  readonly statusLabel = REPORT_STATUS_LABEL;
  readonly roleLabel = ROLE_LABEL;
  projectId: number | null = null;
  weeks = 12;
  fmt = shortDate;
  span = reportSpan;

  readonly people = computed(() => {
    const a = this.d()?.audience;
    return a === 'ARCHITECTE' ? 'Architectes' : a === 'DEV' ? 'Développeurs' : 'Consultants';
  });
  readonly person = computed(() => {
    const a = this.d()?.audience;
    return a === 'ARCHITECTE' ? 'Architecte' : a === 'DEV' ? 'Développeur' : 'Consultant';
  });
  readonly audienceText = computed(() => {
    if (this.auth.hasRole('HEAD_OF_OFFICE')) return 'fiches rendues par les architectes';
    if (this.auth.hasRole('PM')) return 'fiches rendues par vos développeurs';
    return 'fiches rendues par tous les consultants';
  });

  /** Indicateur cliqué : filtre la liste des personnes et leurs fiches. */
  readonly filter = signal<Filter>('all');
  readonly filterLabel = FILTER_LABEL;

  readonly visibleMembers = computed(() => {
    const members = this.d()?.members ?? [];
    switch (this.filter()) {
      case 'submitted': return [...members.filter((m) => m.submitted > 0)].sort((a, b) => b.submitted - a.submitted);
      case 'pending': return members.filter((m) => m.pending > 0);
      case 'reviewed': return members.filter((m) => m.validated + m.rejected > 0);
      case 'missing': return [...members.filter((m) => m.missing > 0)].sort((a, b) => b.missing - a.missing);
      case 'days': return [...members.filter((m) => m.declaredDays > 0)].sort((a, b) => b.declaredDays - a.declaredDays);
      default: return members;
    }
  });

  readonly totalMissing = computed(() => (this.d()?.members ?? []).reduce((s, m) => s + m.missing, 0));

  readonly reportsWord = computed(() => {
    switch (this.filter()) {
      case 'pending': return 'en attente de validation';
      case 'reviewed': return 'validée(s) ou renvoyée(s)';
      default: return 'soumise(s)';
    }
  });

  /** Fiches de la personne sélectionnée sur la période affichée (selon l'indicateur choisi), les plus récentes d'abord. */
  readonly memberReports = computed(() => {
    const from = this.d()?.from ?? '';
    const f = this.filter();
    return this.history()
      .filter((r) => r.status !== 'BROUILLON' && r.submittedAt && r.periodStart >= from)
      .filter((r) => f === 'pending' ? r.status === 'SOUMIS' : f === 'reviewed' ? r.status === 'VALIDE' || r.status === 'REJETE' : true)
      .sort((a, b) => b.periodStart.localeCompare(a.periodStart) || a.type.localeCompare(b.type));
  });

  /** Semaines échues de la période sans rapport hebdo soumis par la personne sélectionnée. */
  readonly missingWeeks = computed(() => {
    const sent = new Set(this.history()
      .filter((r) => r.type === 'HEBDO' && r.status !== 'BROUILLON' && r.submittedAt).map((r) => r.periodStart));
    return (this.d()?.compliance ?? []).map((w) => w.weekStart).filter((w) => !sent.has(w)).reverse();
  });

  readonly projectRows = computed<BarRow[]>(() => (this.d()?.projects ?? []).map((p) => ({
    id: p.projectId,
    label: p.projectName,
    sub: `${p.people} personne${p.people > 1 ? 's' : ''}${p.businessUnit ? ' · ' + p.businessUnit : ''}`,
    segments: [{ key: 'days', label: 'Jours déclarés', value: p.declaredDays, color: SERIES.blue }],
  })));

  readonly complianceColumns = computed<Column[]>(() => (this.d()?.compliance ?? []).map((w) => ({
    label: w.label,
    title: `${w.label} · semaine du ${shortDate(w.weekStart)}`,
    total: w.expected,
    segments: [
      { key: 'ontime', label: 'À l’heure', value: w.onTime, color: SERIES.blue },
      { key: 'late', label: 'En retard', value: w.submitted - w.onTime, color: SERIES.orange },
      { key: 'missing', label: 'Manquant', value: Math.max(0, w.expected - w.submitted), color: '#d0d5dd' },
    ],
  })));

  ngOnInit(): void {
    this.api.projects().subscribe((p) => this.projects.set(p));
    this.api.projectSpaces().subscribe({ next: (s) => this.spaces.set(s), error: () => {} });
    this.load();
  }

  load(): void {
    this.error.set(null);
    this.api.dashboard({ projectId: this.projectId, weeks: this.weeks }).subscribe({
      next: (d) => {
        this.d.set(d);
        const sel = this.selected();
        this.selected.set(sel ? d.members.find((m) => m.id === sel.id) ?? null : null);
      },
      error: (e) => this.error.set(errorMessage(e)),
    });
  }

  select(m: MemberRow): void {
    this.selected.set(m);
    this.history.set([]);
    this.loadingReports.set(true);
    this.api.history(m.id).subscribe({
      next: (h) => { if (this.selected()?.id === m.id) { this.history.set(h); this.loadingReports.set(false); } },
      error: (e) => { this.loadingReports.set(false); this.error.set(errorMessage(e)); },
    });
  }

  /** Clic sur un indicateur : filtre la liste, sélectionne la première personne concernée et y amène l'écran. */
  applyFilter(f: Filter): void {
    this.filter.set(f);
    const first = this.visibleMembers()[0];
    if (first && this.selected()?.id !== first.id && !this.visibleMembers().some((m) => m.id === this.selected()?.id)) {
      this.select(first);
    } else if (!first) {
      this.selected.set(null);
    }
    document.getElementById(f === 'days' ? 'projects' : 'members')?.scrollIntoView({ behavior: 'smooth', block: 'start' });
  }

  myRoles(s: ProjectSpace): string {
    return s.myRoles.map((r) => PROJECT_ROLE_LABEL[r]).join(', ');
  }

  num(v: number): string { return v.toLocaleString('fr-FR', { maximumFractionDigits: 1 }); }
}
