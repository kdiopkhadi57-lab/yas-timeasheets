import { Component, HostListener, ElementRef, inject, input, signal } from '@angular/core';
import { DatePipe } from '@angular/common';
import { Router, RouterLink } from '@angular/router';
import { InboxService } from '../core/inbox.service';
import { Report } from '../core/models';
import { reportSpan } from '../core/dates';

/** Cloche : nombre de fiches reçues en attente de validation, et leur liste au clic. */
@Component({
  selector: 'app-inbox-bell',
  imports: [DatePipe, RouterLink],
  template: `
    <button class="bell" [class.dark]="dark()" (click)="toggle()" [attr.aria-label]="inbox.count() + ' fiche(s) reçue(s) à valider'"
            [title]="inbox.count() ? inbox.count() + ' fiche(s) reçue(s) à valider' : 'Aucune fiche en attente'">
      <svg viewBox="0 0 24 24" width="18" height="18" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true">
        <path d="M18 8a6 6 0 0 0-12 0c0 7-3 9-3 9h18s-3-2-3-9"/><path d="M13.73 21a2 2 0 0 1-3.46 0"/>
      </svg>
      @if (inbox.count()) { <span class="count">{{ inbox.count() > 99 ? '99+' : inbox.count() }}</span> }
    </button>
    @if (open()) {
      <div class="pop card" [class.right]="align() === 'right'" role="dialog" aria-label="Fiches reçues">
        <div class="head">
          <strong>Fiches reçues</strong>
          <span class="muted small">{{ inbox.count() }} à valider</span>
        </div>
        <ul>
          @for (r of inbox.reports(); track r.id) {
            <li (click)="openReport(r)">
              <div class="who">{{ r.consultantName }} <span class="tag" [class.m]="r.type === 'MENSUEL'">{{ r.type === 'HEBDO' ? 'Hebdo' : 'Mensuelle' }}</span></div>
              <div class="small">{{ span(r) }}</div>
              @if (r.submittedAt) { <div class="muted small">Reçue le {{ r.submittedAt | date: 'dd/MM/yyyy à HH:mm' }}</div> }
            </li>
          } @empty {
            <li class="empty">Aucune fiche en attente.</li>
          }
        </ul>
        <a class="all" routerLink="/tableau-de-bord" (click)="open.set(false)">Voir le tableau de bord →</a>
      </div>
    }
  `,
  styles: `
    :host { position: relative; display: inline-block; }
    .bell { position: relative; width: 36px; height: 36px; border-radius: 10px; border: 1px solid var(--border); background: var(--surface); color: var(--text); display: grid; place-items: center; cursor: pointer; }
    .bell.dark { background: rgba(255,255,255,.06); border-color: rgba(255,255,255,.12); color: #fff; }
    .bell:hover { border-color: var(--accent); }
    .count { position: absolute; top: -6px; right: -6px; min-width: 18px; height: 18px; padding: 0 5px; border-radius: 999px; background: var(--danger); color: #fff; font-size: 11px; font-weight: 700; display: grid; place-items: center; line-height: 1; }
    .pop { position: absolute; top: 0; left: calc(100% + 10px); width: 340px; max-width: calc(100vw - 32px); z-index: 60; color: var(--text); box-shadow: 0 12px 32px rgba(0,0,0,.18); }
    .pop.right { left: auto; right: 0; top: calc(100% + 8px); }
    .head { display: flex; justify-content: space-between; align-items: baseline; padding: 12px 14px; border-bottom: 1px solid var(--border); }
    ul { list-style: none; margin: 0; padding: 0; max-height: 360px; overflow: auto; }
    li { padding: 10px 14px; border-bottom: 1px solid var(--border); cursor: pointer; }
    li:hover { background: var(--surface-2); }
    li.empty { cursor: default; color: var(--muted); text-align: center; }
    .who { font-weight: 600; display: flex; gap: 6px; align-items: center; }
    .tag { font-size: 11px; font-weight: 600; padding: 1px 8px; border-radius: 999px; background: var(--brand-soft); color: var(--brand-strong); }
    .tag.m { background: var(--warn-soft); color: var(--warn); }
    .all { display: block; padding: 10px 14px; font-weight: 500; }
  `,
})
export class InboxBellComponent {
  readonly inbox = inject(InboxService);
  private router = inject(Router);
  private host = inject(ElementRef<HTMLElement>);

  /** Fond sombre (menu latéral) ; align=right : la liste s'ouvre sous la cloche, alignée à droite. */
  readonly dark = input(false);
  readonly align = input<'side' | 'right'>('side');
  readonly open = signal(false);
  readonly span = reportSpan;

  toggle(): void {
    this.open.update((o) => !o);
    if (this.open()) this.inbox.refresh();
  }

  openReport(r: Report): void {
    this.open.set(false);
    this.router.navigate(['/equipe', r.consultantId], { queryParams: { type: r.type, date: r.periodStart } });
  }

  @HostListener('document:click', ['$event'])
  close(e: Event): void {
    if (this.open() && !this.host.nativeElement.contains(e.target as Node)) this.open.set(false);
  }

  @HostListener('document:keydown.escape')
  escape(): void { this.open.set(false); }
}
