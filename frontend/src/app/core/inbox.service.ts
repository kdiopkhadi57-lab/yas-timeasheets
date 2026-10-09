import { Injectable, computed, effect, inject, signal } from '@angular/core';
import { NavigationEnd, Router } from '@angular/router';
import { filter } from 'rxjs';
import { ApiService } from './api.service';
import { AuthService } from './auth.service';
import { Report } from './models';

/**
 * Fiches reçues par le manager ou le Head of Office et en attente de validation (cloche de notification).
 * Rafraîchies à chaque navigation et toutes les minutes ; le compteur baisse quand une fiche est validée ou renvoyée.
 */
@Injectable({ providedIn: 'root' })
export class InboxService {
  private api = inject(ApiService);
  private auth = inject(AuthService);

  readonly reports = signal<Report[]>([]);
  readonly count = computed(() => this.reports().length);

  constructor() {
    inject(Router).events.pipe(filter((e) => e instanceof NavigationEnd)).subscribe(() => this.refresh());
    setInterval(() => this.refresh(), 60_000);
    effect(() => {
      if (!this.auth.isManager()) this.reports.set([]);
    });
  }

  refresh(): void {
    if (!this.auth.isManager()) return;
    this.api.inbox().subscribe({ next: (r) => this.reports.set(r), error: () => {} });
  }
}
