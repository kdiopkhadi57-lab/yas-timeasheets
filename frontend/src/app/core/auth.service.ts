import { HttpClient } from '@angular/common/http';
import { Injectable, computed, inject, signal } from '@angular/core';
import { Router } from '@angular/router';
import { Observable, tap } from 'rxjs';
import { Role, User } from './models';

const TOKEN_KEY = 'yas.token';
const USER_KEY = 'yas.user';

@Injectable({ providedIn: 'root' })
export class AuthService {
  private http = inject(HttpClient);
  private router = inject(Router);

  readonly user = signal<User | null>(readStored());
  readonly isManager = computed(() => {
    const r = this.user()?.role;
    return r === 'PM' || r === 'HEAD_OF_OFFICE' || r === 'ADMIN';
  });
  readonly isAdmin = computed(() => this.user()?.role === 'ADMIN');
  /**
   * Pas de tâches assignées pour l'instant : après le daily sur Teams, les développeurs et le lead dev
   * saisissent directement leur journée (projet + ce qu'ils ont fait). Le menu, le panneau et le champ
   * « Tâche » leur sont masqués ; il suffit de changer cette règle pour les réafficher.
   */
  readonly usesTasks = computed(() => {
    const r = this.user()?.role;
    return r !== 'CONSULTANT' && r !== 'LEAD_DEV';
  });

  get token(): string | null {
    return safeGet(TOKEN_KEY);
  }

  login(email: string, password: string): Observable<{ token: string; user: User }> {
    return this.http.post<{ token: string; user: User }>('/api/auth/login', { email, password }).pipe(
      tap((res) => {
        safeSet(TOKEN_KEY, res.token);
        safeSet(USER_KEY, JSON.stringify(res.user));
        this.user.set(res.user);
      }),
    );
  }

  logout(): void {
    safeRemove(TOKEN_KEY);
    safeRemove(USER_KEY);
    this.user.set(null);
    this.router.navigate(['/login']);
  }

  hasRole(...roles: Role[]): boolean {
    const r = this.user()?.role;
    return !!r && roles.includes(r);
  }

  /** Page d'accueil selon le rôle. */
  home(): string {
    if (this.isManager()) return '/equipe';
    return this.user()?.role === 'ARCHITECTE' ? '/projets' : '/semaine';
  }
}

function readStored(): User | null {
  const raw = safeGet(USER_KEY);
  try {
    return raw ? (JSON.parse(raw) as User) : null;
  } catch {
    return null;
  }
}

function safeGet(k: string): string | null {
  try {
    return localStorage.getItem(k);
  } catch {
    return null;
  }
}

function safeSet(k: string, v: string): void {
  try {
    localStorage.setItem(k, v);
  } catch {
    /* stockage indisponible */
  }
}

function safeRemove(k: string): void {
  try {
    localStorage.removeItem(k);
  } catch {
    /* stockage indisponible */
  }
}
