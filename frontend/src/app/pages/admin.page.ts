import { Component, OnInit, computed, inject, signal } from '@angular/core';
import { DatePipe } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute } from '@angular/router';
import { ApiService, errorMessage } from '../core/api.service';
import { AuthService } from '../core/auth.service';
import { DEV_PROFILES, DEV_PROFILE_LABEL, GitRepo, GitStatus, Project, ROLE_LABEL, Role, SKILLS, SKILL_LABEL, Skill, User } from '../core/models';

type UserDraft = Partial<User> & { password?: string; projectIds?: number[] };
type ProjectDraft = Partial<Project> & { memberIds?: number[] };

@Component({
  selector: 'app-admin',
  imports: [FormsModule, DatePipe],
  template: `
    <div class="page">
      <div class="page-head">
        <div>
          <h1>{{ auth.isAdmin() ? 'Administration' : 'Projets & équipe' }}</h1>
          <p class="sub">Consultants, managers, projets et Business Units utilisés dans les fiches de temps.</p>
        </div>
        <div class="tabs">
          <button [class.on]="tab() === 'users'" (click)="tab.set('users')">Utilisateurs ({{ users().length }})</button>
          <button [class.on]="tab() === 'projects'" (click)="tab.set('projects')">Projets ({{ projects().length }})</button>
          <button [class.on]="tab() === 'gitlab'" (click)="tab.set('gitlab')">GitLab ({{ linkedRepos() }}/{{ repos().length }})</button>
        </div>
      </div>
      @if (error()) { <div class="alert error" style="margin-bottom:12px">{{ error() }}</div> }

      @if (tab() === 'users') {
        <div class="row" style="margin-bottom:12px">
          <input [(ngModel)]="search" placeholder="Rechercher un nom, un matricule…" style="max-width:320px">
          <span class="grow"></span>
          @if (isPm()) {
            <label class="row small"><input type="checkbox" style="width:auto" [(ngModel)]="onlyMyTeam"> Mon équipe seulement</label>
          }
          <button class="btn primary" (click)="newUser()">{{ isPm() ? '＋ Nouveau développeur' : '＋ Nouvel utilisateur' }}</button>
        </div>
        <div class="card table-wrap">
          <table class="data">
            <thead><tr><th>Nom</th><th>Matricule</th><th>Profil</th><th>Rôle</th><th>Compétences</th><th>Projets</th><th>Manager</th><th>E-mail</th><th></th></tr></thead>
            <tbody>
              @for (u of filteredUsers(); track u.id) {
                <tr [class.inactive]="!u.active">
                  <td><strong>{{ u.fullName }}</strong>@if (!u.active) { <span class="muted small"> (inactif)</span> }</td>
                  <td>{{ u.matricule }}</td>
                  <td>{{ u.profil }}</td>
                  <td>{{ roleLabel[u.role] }}</td>
                  <td>
                    @if (u.devProfile) { <span class="tag profile">{{ devProfileLabel[u.devProfile] }}</span> }
                    @for (s of u.skills; track s) { <span class="tag">{{ skillLabel[s] }}</span> }
                  </td>
                  <td class="small">{{ projectNames(u) }}</td>
                  <td>{{ u.pmName }}</td>
                  <td class="small">{{ u.email }}</td>
                  <td style="text-align:right">@if (canEdit(u)) { <button class="btn sm ghost" (click)="editUser(u)">Modifier</button> }</td>
                </tr>
              }
            </tbody>
          </table>
        </div>
      } @else if (tab() === 'gitlab') {
        @if (gitStatus(); as st) {
          <div class="card card-pad" style="margin-bottom:12px">
            <div class="row">
              <div class="grow">
                @if (st.enabled) {
                  <strong>Connecté à {{ st.gitlabUrl }}</strong>
                  <div class="muted small">
                    Synchronisation automatique toutes les 15 minutes.
                    @if (st.lastRunAt) { Dernière : {{ st.lastRunAt | date: 'dd/MM à HH:mm' }} ({{ st.syncedRepos }} dépôt(s), {{ st.newCommits }} nouveau(x) commit(s)). }
                  </div>
                } @else {
                  <strong>GitLab non connecté</strong>
                  <div class="muted small">Définissez <code>GITLAB_TOKEN</code> (jeton avec le droit <code>read_api</code>) et, si besoin, <code>GITLAB_GROUPS</code> au démarrage du backend.</div>
                }
              </div>
              <button class="btn primary" (click)="syncGit()" [disabled]="!st.enabled || syncing()">{{ syncing() ? 'Synchronisation…' : '⟳ Synchroniser maintenant' }}</button>
            </div>
            @if (st.lastError) { <div class="alert error" style="margin-top:10px">{{ st.lastError }}</div> }
            <p class="muted small" style="margin:10px 0 0">
              Lien tâche ↔ commits : la référence de la tâche (<code>{{ st.taskPrefix }}-42</code>) dans le <strong>nom de branche</strong>
              (<code>feature/{{ st.taskPrefix }}-42-export-excel</code>), le titre de la merge request ou le message du commit.
              Lien consultant ↔ commits : l'e-mail de commit, ou les « Identités Git » de l'utilisateur.
            </p>
          </div>
        }
        <div class="card table-wrap">
          <table class="data">
            <thead><tr><th>Dépôt GitLab</th><th>Projet de la fiche de temps</th><th>Dernière synchro</th></tr></thead>
            <tbody>
              @for (r of repos(); track r.id) {
                <tr [class.inactive]="!r.projectId">
                  <td>@if (r.webUrl) { <a [href]="r.webUrl" target="_blank" rel="noopener">{{ r.path }}</a> } @else { {{ r.path }} }</td>
                  <td>
                    <select [ngModel]="r.projectId" (ngModelChange)="mapRepo(r, $event)" style="max-width:260px">
                      <option [ngValue]="null">— Non suivi —</option>
                      @for (p of projects(); track p.id) { <option [ngValue]="p.id">{{ p.name }}</option> }
                    </select>
                  </td>
                  <td class="small">{{ r.lastSyncedAt | date: 'dd/MM HH:mm' }}</td>
                </tr>
              } @empty {
                <tr><td colspan="3" class="empty">Aucun dépôt découvert. Les dépôts accessibles au jeton apparaissent après la première synchronisation.</td></tr>
              }
            </tbody>
          </table>
        </div>
        <p class="muted small" style="margin-top:8px">Seuls les dépôts rattachés à un projet sont synchronisés. Un projet peut avoir plusieurs dépôts (backend, backoffice…). Les dépôts d'un groupe portant le nom d'un projet (numflex → NumFlex) sont rattachés automatiquement.</p>
      } @else {
        <div class="row" style="margin-bottom:12px">
          <span class="grow"></span>
          <button class="btn primary" (click)="editProject({ active: true })">＋ Nouveau projet</button>
        </div>
        <div class="card table-wrap">
          <table class="data">
            <thead><tr><th>Projet</th><th>Business Unit</th><th>Manager</th><th>Architecte</th><th>Lead dev</th><th>Développeurs</th><th>Statut</th><th></th></tr></thead>
            <tbody>
              @for (p of projects(); track p.id) {
                <tr>
                  <td><strong>{{ p.name }}</strong></td>
                  <td>{{ p.businessUnit }}</td>
                  <td>{{ p.pmName }}</td>
                  <td>{{ p.architectName }}</td>
                  <td>{{ p.leadDevName }}</td>
                  <td class="small">{{ memberNames(p) }}</td>
                  <td><span class="badge" [class]="p.active ? 'VALIDE' : 'BROUILLON'">{{ p.active ? 'Actif' : 'Archivé' }}</span></td>
                  <td style="text-align:right"><button class="btn sm ghost" (click)="editProject(p)">Modifier</button></td>
                </tr>
              }
            </tbody>
          </table>
        </div>
      }
    </div>

    @if (userDraft(); as d) {
      <div class="modal-backdrop" (click)="userDraft.set(null)">
        <form class="card modal" (click)="$event.stopPropagation()" (ngSubmit)="saveUser(d)">
          <div class="modal-head"><h2>{{ d.id ? 'Modifier' : isPm() ? 'Nouveau développeur' : 'Nouvel utilisateur' }}</h2><button type="button" class="btn icon ghost" (click)="userDraft.set(null)">✕</button></div>
          <div class="modal-body">
            @if (formError()) { <div class="alert error">{{ formError() }}</div> }
            <div class="form-grid">
              <label class="field"><span>Prénom(s)</span><input name="firstName" [(ngModel)]="d.firstName" required></label>
              <label class="field"><span>Nom</span><input name="lastName" [(ngModel)]="d.lastName" required></label>
              <label class="field"><span>Matricule</span><input name="matricule" [(ngModel)]="d.matricule" required placeholder="C099"></label>
              <label class="field"><span>Profil</span><input name="profil" [(ngModel)]="d.profil" placeholder="Software Architect"></label>
              <label class="field"><span>E-mail</span><input type="email" name="email" [(ngModel)]="d.email" required></label>
              <label class="field"><span>{{ d.id ? 'Nouveau mot de passe (facultatif)' : 'Mot de passe' }}</span><input type="password" name="password" [(ngModel)]="d.password" autocomplete="new-password"></label>
              <label class="field"><span>Rôle</span>
                <select name="role" [(ngModel)]="d.role">@for (r of rolesFor(); track r) { <option [ngValue]="r">{{ roleLabel[r] }}</option> }</select>
              </label>
              <label class="field"><span>Manager (reçoit le rapport hebdo)</span>
                <select name="pmId" [(ngModel)]="d.pmId">
                  @if (!isPm()) { <option [ngValue]="null">—</option> }
                  @for (p of managersFor(d); track p.id) { <option [ngValue]="p.id">{{ p.fullName }} · {{ roleLabel[p.role] }}</option> }
                </select>
                <small class="muted">{{ d.role === 'ARCHITECTE' ? 'Un architecte est rattaché au Head of Office.' : 'Les managers au-dessus de lui voient aussi ses fiches.' }}</small>
              </label>
            </div>
            @if (d.role === 'CONSULTANT' || d.role === 'LEAD_DEV') {
              <div class="form-grid">
                <label class="field"><span>Profil développeur</span>
                  <select name="devProfile" [(ngModel)]="d.devProfile">
                    <option [ngValue]="null">—</option>
                    @for (p of devProfiles; track p) { <option [ngValue]="p">{{ devProfileLabel[p] }}</option> }
                  </select>
                </label>
                <div class="field"><span>Technologies</span>
                  <div class="row wrap">
                    @for (s of skills; track s) {
                      <label class="row small"><input type="checkbox" style="width:auto" [checked]="d.skills?.includes(s)" (change)="toggleSkill(d, s)"> {{ skillLabel[s] }}</label>
                    }
                  </div>
                </div>
              </div>
              <div class="field">
                <span>Projets ({{ d.projectIds?.length ?? 0 }}) — il y est ajouté comme développeur</span>
                <div class="members">
                  @for (p of activeProjects(); track p.id) {
                    <label class="row small"><input type="checkbox" style="width:auto" [checked]="d.projectIds?.includes(p.id)"
                      [disabled]="!!d.id && p.leadDevId === d.id" (change)="toggleProject(d, p.id)">
                      {{ p.name }} <span class="muted">{{ p.businessUnit }}{{ d.id && p.leadDevId === d.id ? ' · lead dev' : '' }}</span></label>
                  } @empty { <span class="muted small">Aucun projet actif.</span> }
                </div>
              </div>
            }
            <label class="field"><span>Identités Git (facultatif)</span>
              <input name="gitIdentities" [(ngModel)]="d.gitIdentities" placeholder="e-mail de commit perso, nom d'utilisateur GitLab…">
              <small class="muted">Séparées par des virgules. Utile si l'e-mail des commits n'est pas l'e-mail ci-dessus.</small>
            </label>
            <label class="row small"><input type="checkbox" name="active" [(ngModel)]="d.active" style="width:auto"> Compte actif</label>
          </div>
          <div class="modal-foot">
            <button type="button" class="btn" (click)="userDraft.set(null)">Annuler</button>
            <button class="btn primary">Enregistrer</button>
          </div>
        </form>
      </div>
    }

    @if (projectDraft(); as p) {
      <div class="modal-backdrop" (click)="projectDraft.set(null)">
        <form class="card modal" (click)="$event.stopPropagation()" (ngSubmit)="saveProject(p)">
          <div class="modal-head"><h2>{{ p.id ? 'Modifier le projet' : 'Nouveau projet' }}</h2><button type="button" class="btn icon ghost" (click)="projectDraft.set(null)">✕</button></div>
          <div class="modal-body">
            @if (formError()) { <div class="alert error">{{ formError() }}</div> }
            <label class="field"><span>Nom du projet</span><input name="name" [(ngModel)]="p.name" required></label>
            <label class="field"><span>Business Unit</span><input name="bu" [(ngModel)]="p.businessUnit"></label>
            <label class="field"><span>Manager</span>
              <select name="pmId" [(ngModel)]="p.pmId">
                <option [ngValue]="null">—</option>
                @for (u of pms(); track u.id) { <option [ngValue]="u.id">{{ u.fullName }}</option> }
              </select>
            </label>
            <div class="form-grid">
              <label class="field"><span>Architecte (dépose les documents)</span>
                <select name="architectId" [(ngModel)]="p.architectId">
                  <option [ngValue]="null">—</option>
                  @for (u of architects(); track u.id) { <option [ngValue]="u.id">{{ u.fullName }}</option> }
                </select>
                @if (!architects().length) { <small class="muted">Aucun utilisateur avec le rôle Architecte.</small> }
              </label>
              <label class="field"><span>Lead dev (assigne et valide les estimations)</span>
                <select name="leadDevId" [(ngModel)]="p.leadDevId">
                  <option [ngValue]="null">—</option>
                  @for (u of leadDevs(); track u.id) { <option [ngValue]="u.id">{{ u.fullName }}</option> }
                </select>
                @if (!leadDevs().length) { <small class="muted">Aucun utilisateur avec le rôle Lead dev.</small> }
              </label>
            </div>
            <div class="field">
              <span>Développeurs ({{ p.memberIds?.length ?? 0 }}) — accès aux documents, reçoivent les tâches du lead dev</span>
              <div class="members">
                @for (u of developers(); track u.id) {
                  @if (u.id !== p.leadDevId) {
                    <label class="row small"><input type="checkbox" style="width:auto" [checked]="p.memberIds?.includes(u.id)" (change)="toggleMember(p, u.id)">
                      {{ u.fullName }} <span class="muted">{{ u.profil }}</span></label>
                  }
                }
              </div>
            </div>
            <label class="row small"><input type="checkbox" name="active" [(ngModel)]="p.active" style="width:auto"> Projet actif</label>
          </div>
          <div class="modal-foot">
            <button type="button" class="btn" (click)="projectDraft.set(null)">Annuler</button>
            <button class="btn primary" [disabled]="!p.name">Enregistrer</button>
          </div>
        </form>
      </div>
    }
  `,
  styles: `
    tr.inactive td { opacity: .55; }
    .members { max-height: 200px; overflow-y: auto; border: 1px solid var(--border); border-radius: 8px; padding: 8px 10px; display: flex; flex-direction: column; gap: 6px; }
    .field > span { font-size: 12px; font-weight: 500; color: var(--muted); }
    .wrap { flex-wrap: wrap; gap: 6px 14px; }
    .tag { display: inline-block; background: var(--surface-2); border: 1px solid var(--border); border-radius: 6px; padding: 0 6px; margin: 1px 3px 1px 0; font-size: 12px; }
    .tag.profile { background: var(--brand-soft); color: var(--brand-strong); border-color: transparent; font-weight: 600; }
  `,
})
export class AdminPage implements OnInit {
  private api = inject(ApiService);
  readonly auth = inject(AuthService);

  private route = inject(ActivatedRoute);
  readonly tab = signal<'users' | 'projects' | 'gitlab'>(
    (['users', 'projects', 'gitlab'] as const).find((t) => t === this.route.snapshot.queryParamMap.get('tab')) ?? 'users',
  );
  readonly repos = signal<GitRepo[]>([]);
  readonly gitStatus = signal<GitStatus | null>(null);
  readonly syncing = signal(false);
  readonly linkedRepos = computed(() => this.repos().filter((r) => r.projectId).length);
  readonly users = signal<User[]>([]);
  readonly projects = signal<Project[]>([]);
  readonly error = signal<string | null>(null);
  readonly formError = signal<string | null>(null);
  readonly userDraft = signal<UserDraft | null>(null);
  readonly projectDraft = signal<ProjectDraft | null>(null);
  readonly roles: Role[] = ['CONSULTANT', 'ARCHITECTE', 'LEAD_DEV', 'PM', 'HEAD_OF_OFFICE', 'ADMIN'];
  readonly roleLabel = ROLE_LABEL;
  readonly skills = SKILLS;
  readonly skillLabel = SKILL_LABEL;
  readonly devProfiles = DEV_PROFILES;
  readonly devProfileLabel = DEV_PROFILE_LABEL;
  readonly activeProjects = computed(() => this.projects().filter((p) => p.active));
  search = '';
  onlyMyTeam = true;

  /** Un manager crée et modifie les développeurs de son équipe. */
  isPm(): boolean { return this.auth.user()?.role === 'PM'; }
  private meId(): number | undefined { return this.auth.user()?.id; }

  /** Ce manager est-il dans la chaîne hiérarchique de l'utilisateur ? */
  private managedBy(u: Partial<User>, managerId: number | undefined): boolean {
    const byId = new Map(this.users().map((x) => [x.id, x]));
    const seen = new Set<number>();
    for (let m = u.pmId ?? null; m != null && !seen.has(m); m = byId.get(m)?.pmId ?? null) {
      if (m === managerId) return true;
      seen.add(m);
    }
    return false;
  }

  rolesFor(): Role[] {
    if (this.isPm()) return ['CONSULTANT', 'LEAD_DEV'];
    return this.auth.isAdmin() ? this.roles : this.roles.filter((r) => r !== 'ADMIN');
  }

  canEdit(u: User): boolean {
    const role = this.auth.user()?.role;
    if (role === 'ADMIN') return true;
    if (role === 'HEAD_OF_OFFICE') return u.role !== 'ADMIN';
    return role === 'PM' && (u.role === 'CONSULTANT' || u.role === 'LEAD_DEV') && this.managedBy(u, this.meId());
  }

  newUser(): void {
    this.editUser({ role: 'CONSULTANT', active: true, pmId: this.isPm() ? this.meId() ?? null : null, skills: [], projects: [] });
  }

  toggleSkill(d: UserDraft, s: Skill): void {
    const list = d.skills ?? [];
    d.skills = list.includes(s) ? list.filter((x) => x !== s) : [...list, s];
  }

  toggleProject(d: UserDraft, id: number): void {
    const ids = d.projectIds ?? [];
    d.projectIds = ids.includes(id) ? ids.filter((x) => x !== id) : [...ids, id];
  }

  projectNames(u: User): string {
    return (u.projects ?? []).map((p) => p.name).join(', ');
  }

  readonly architects = computed(() => this.users().filter((u) => u.active && u.role === 'ARCHITECTE'));
  readonly leadDevs = computed(() => this.users().filter((u) => u.active && u.role === 'LEAD_DEV'));
  /** Développeurs : consultants, et lead devs (qui peuvent développer sur un autre projet). */
  readonly developers = computed(() => this.users().filter((u) => u.active && (u.role === 'CONSULTANT' || u.role === 'LEAD_DEV')));
  readonly pms = computed(() => this.users().filter((u) => u.active && (u.role === 'PM' || u.role === 'HEAD_OF_OFFICE')));

  /**
   * Managers possibles : le Head of Office pour un architecte, sinon un manager ou le Head of Office.
   * Ni l'utilisateur lui-même, ni quelqu'un qui est déjà sous sa responsabilité (pas de boucle).
   */
  managersFor(d: UserDraft): User[] {
    const byId = new Map(this.users().map((u) => [u.id, u]));
    const under = (u: User): boolean => {
      const seen = new Set<number>();
      for (let m = u.pmId; m != null && !seen.has(m); m = byId.get(m)?.pmId ?? null) {
        if (m === d.id) return true;
        seen.add(m);
      }
      return false;
    };
    return this.pms().filter((u) => u.id !== d.id && (d.role !== 'ARCHITECTE' || u.role === 'HEAD_OF_OFFICE') && !(d.id && under(u))
      && (!this.isPm() || u.id === this.meId() || this.managedBy(u, this.meId())));
  }

  filteredUsers(): User[] {
    const q = this.search.trim().toLowerCase();
    const list = this.isPm() && this.onlyMyTeam ? this.users().filter((u) => this.managedBy(u, this.meId())) : this.users();
    return q ? list.filter((u) => (u.fullName + ' ' + u.matricule + ' ' + u.email + ' ' + u.skills.join(' ') + ' ' + (u.devProfile ?? '')).toLowerCase().includes(q)) : list;
  }

  ngOnInit(): void { this.load(); }

  load(): void {
    this.api.users().subscribe({ next: (u) => this.users.set(u), error: (e) => this.error.set(errorMessage(e)) });
    this.api.projects().subscribe({ next: (p) => this.projects.set(p), error: (e) => this.error.set(errorMessage(e)) });
    this.api.gitRepos().subscribe({ next: (r) => this.repos.set(r), error: (e) => this.error.set(errorMessage(e)) });
    this.api.gitStatus().subscribe((s) => this.gitStatus.set(s));
  }

  syncGit(): void {
    this.syncing.set(true);
    this.api.gitSync().subscribe({
      next: (s) => { this.syncing.set(false); this.gitStatus.set(s); this.load(); },
      error: (e) => { this.syncing.set(false); this.error.set(errorMessage(e)); },
    });
  }

  mapRepo(r: GitRepo, projectId: number | null): void {
    this.api.mapGitRepo(r.id, projectId).subscribe({
      next: (updated) => this.repos.update((list) => list.map((x) => (x.id === r.id ? updated : x))),
      error: (e) => this.error.set(errorMessage(e)),
    });
  }

  editUser(u: UserDraft): void {
    this.formError.set(null);
    // Projets cochés : ceux où il est développeur ou lead dev (l'architecte se gère sur le projet).
    const projectIds = this.projects().filter((p) => p.members.some((m) => m.id === u.id) || (!!u.id && p.leadDevId === u.id)).map((p) => p.id);
    this.userDraft.set({ devProfile: null, ...u, skills: [...(u.skills ?? [])], projectIds, password: '' });
  }
  editProject(p: Partial<Project>): void {
    this.formError.set(null);
    this.projectDraft.set({ architectId: null, leadDevId: null, ...p, memberIds: (p.members ?? []).map((m) => m.id) });
  }

  toggleMember(p: ProjectDraft, id: number): void {
    const ids = p.memberIds ?? [];
    p.memberIds = ids.includes(id) ? ids.filter((x) => x !== id) : [...ids, id];
  }

  memberNames(p: Project): string {
    return p.members.map((m) => m.fullName).join(', ');
  }

  saveUser(d: UserDraft): void {
    this.api.saveUser(d).subscribe({
      next: () => { this.userDraft.set(null); this.load(); },
      error: (e) => this.formError.set(errorMessage(e)),
    });
  }

  saveProject(p: ProjectDraft): void {
    // Le lead dev et l'architecte ne sont pas comptés deux fois parmi les développeurs.
    const memberIds = (p.memberIds ?? []).filter((id) => id !== p.leadDevId && id !== p.architectId);
    this.api.saveProject({ ...p, memberIds } as Partial<Project>).subscribe({
      next: () => { this.projectDraft.set(null); this.load(); },
      error: (e) => this.formError.set(errorMessage(e)),
    });
  }
}
