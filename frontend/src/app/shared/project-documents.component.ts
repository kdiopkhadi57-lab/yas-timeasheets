import { Component, computed, effect, inject, input, output, signal, untracked } from '@angular/core';
import { DatePipe } from '@angular/common';
import { HttpEventType } from '@angular/common/http';
import { FormsModule } from '@angular/forms';
import { ApiService, errorMessage } from '../core/api.service';
import {
  DOCUMENT_CATEGORIES, DOCUMENT_CATEGORY_LABEL, DocumentCategory, DocumentComment, PROJECT_ROLE_LABEL, ProjectDocument,
  ProjectSpace,
} from '../core/models';

type Kind = 'word' | 'excel' | 'pdf' | 'slides' | 'code' | 'archive' | 'image' | 'other';

const KINDS: Record<Kind, string[]> = {
  word: ['doc', 'docx', 'odt', 'rtf', 'txt', 'md'],
  excel: ['xls', 'xlsx', 'xlsm', 'ods', 'csv'],
  pdf: ['pdf'],
  slides: ['ppt', 'pptx', 'odp'],
  code: ['java', 'ts', 'js', 'py', 'php', 'sql', 'xml', 'json', 'yml', 'yaml', 'html', 'css', 'sh', 'kt', 'go', 'cs', 'c', 'cpp', 'puml', 'drawio'],
  archive: ['zip', 'rar', '7z', 'tar', 'gz', 'tgz'],
  image: ['png', 'jpg', 'jpeg', 'gif', 'svg', 'webp'],
  other: [],
};
const KIND_ICON: Record<Kind, string> = {
  word: 'W', excel: 'X', pdf: 'PDF', slides: 'P', code: '</>', archive: 'ZIP', image: 'IMG', other: '•••',
};

/**
 * Documents d'un projet : l'architecte (ou le lead dev, le PM) dépose TDR, comptes rendus de réunion, specs, code… ;
 * l'équipe les télécharge et pose ses questions sous chaque document, le lead dev et l'architecte expliquent.
 */
@Component({
  selector: 'app-project-documents',
  imports: [DatePipe, FormsModule],
  template: `
    @if (error()) { <div class="alert error" style="margin-bottom:10px">{{ error() }}</div> }

    @if (space().canUpload) {
      <div class="drop" [class.over]="dragOver()" (dragover)="$event.preventDefault(); dragOver.set(true)" (dragleave)="dragOver.set(false)" (drop)="dropFiles($event)">
        @if (!pending().length) {
          <div class="drop-title">⤒ Partagez un document avec l’équipe de {{ space().project.name }}</div>
          <div>Glissez vos fichiers ici (TDR, comptes rendus de réunion, specs, Word, Excel, PDF, code…) ou
            <label class="link">parcourez<input type="file" multiple hidden (change)="pick($event)"></label></div>
          <div class="muted small">100 Mo maximum par fichier. Les développeurs et le lead dev sont prévenus par e-mail.</div>
        } @else {
          <ul class="pending">
            @for (f of pending(); track $index) {
              <li><span class="ico" [attr.data-k]="kind(f.name)">{{ icon(f.name) }}</span><span class="grow">{{ f.name }}</span><span class="muted small">{{ size(f.size) }}</span>
                <button class="btn ghost sm" (click)="unpick($index)" [disabled]="uploading() !== null" aria-label="Retirer">✕</button></li>
            }
          </ul>
          <div class="up-fields">
            <label class="field"><span>Type de document</span>
              <select [(ngModel)]="category" name="category">
                @for (c of categories; track c) { <option [ngValue]="c">{{ catLabel[c] }}</option> }
              </select>
            </label>
            <label class="field grow"><span>Description (facultatif)</span>
              <input [(ngModel)]="description" name="description" placeholder="Ex. TDR v2 validés par le client, CR réunion de lancement du 06/10…" maxlength="1000">
            </label>
          </div>
          @if (uploading() !== null) { <div class="meter"><div [style.width.%]="uploading()"></div></div> }
          <div class="row" style="justify-content:flex-end">
            <button class="btn" (click)="pending.set([])" [disabled]="uploading() !== null">Annuler</button>
            <button class="btn primary" (click)="upload()" [disabled]="uploading() !== null">⤒ Partager {{ pending().length }} fichier{{ pending().length > 1 ? 's' : '' }}</button>
          </div>
        }
      </div>
    }

    <div class="filters">
      <div class="cats">
        <button class="chip" [class.on]="cat() === null" (click)="cat.set(null)">Tous <b>{{ docs().length }}</b></button>
        @for (c of categories; track c) {
          @if (countOf(c)) { <button class="chip" [class.on]="cat() === c" (click)="cat.set(c)">{{ catLabel[c] }} <b>{{ countOf(c) }}</b></button> }
        }
      </div>
      <input type="search" placeholder="Rechercher…" [ngModel]="search()" (ngModelChange)="search.set($event)" class="search">
    </div>

    <ul class="docs">
      @for (d of filtered(); track d.id) {
        <li class="doc" [class.open]="openId() === d.id">
          <div class="doc-row">
            <span class="ico" [attr.data-k]="kind(d.fileName)">{{ icon(d.fileName) }}</span>
            <div class="grow main">
              <div class="row nowrap"><button class="fname" (click)="download(d)" [title]="'Télécharger ' + d.fileName">{{ d.fileName }}</button>
                <span class="cat" [attr.data-c]="d.category">{{ catLabel[d.category] }}</span></div>
              @if (d.description) { <div class="small desc">{{ d.description }}</div> }
              <div class="muted small">{{ d.uploadedByName }} · {{ d.uploadedAt | date: 'dd/MM/yyyy HH:mm' }} · {{ size(d.size) }}</div>
            </div>
            <div class="actions">
              <button class="btn sm" [class.has]="d.commentCount" (click)="toggle(d)" [title]="'Questions et explications'">💬 {{ openId() === d.id ? 'Fermer la discussion' : d.commentCount ? d.commentCount + ' message' + (d.commentCount > 1 ? 's' : '') : (explains() ? 'Expliquer' : 'Poser une question') }}</button>
              <button class="btn sm" (click)="download(d)" aria-label="Télécharger">⤓</button>
              @if (d.canDelete) { <button class="btn sm ghost danger" (click)="remove(d)" aria-label="Supprimer">✕</button> }
            </div>
          </div>

          @if (openId() === d.id) {
            <div class="thread">
              <ol>
                @for (c of thread(); track c.id) {
                  <li [class.explain]="c.authorRole && c.authorRole !== 'DEV'">
                    <div class="row small">
                      <strong>{{ c.authorName }}</strong>
                      @if (c.authorRole) { <span class="role" [attr.data-r]="c.authorRole">{{ roleLabel[c.authorRole] }}</span> }
                      <span class="muted">{{ c.createdAt | date: 'dd/MM HH:mm' }}</span>
                      <span class="grow"></span>
                      @if (c.canDelete) { <button class="btn ghost sm del" (click)="removeComment(d, c)" aria-label="Supprimer">✕</button> }
                    </div>
                    <p>{{ c.text }}</p>
                  </li>
                } @empty {
                  <li class="muted small empty-thread">Pas encore de question. Un point n’est pas clair ? Demandez : le lead dev{{ space().project.leadDevName ? ' (' + space().project.leadDevName + ')' : '' }} et l’architecte vous répondent.</li>
                }
              </ol>
              <form class="reply" (ngSubmit)="send(d)">
                <textarea name="reply" [(ngModel)]="reply" rows="2" [placeholder]="explains() ? 'Expliquer ce document à l’équipe…' : 'Votre question sur ce document…'"
                          (keydown.control.enter)="send(d)" (keydown.meta.enter)="send(d)"></textarea>
                <div class="row"><span class="muted small grow">Ctrl + Entrée pour envoyer · les participants sont prévenus par e-mail</span>
                  <button class="btn primary sm" [disabled]="!reply.trim() || sending()">{{ explains() ? 'Expliquer' : 'Envoyer la question' }}</button></div>
              </form>
            </div>
          }
        </li>
      } @empty {
        <li class="empty">{{ search() || cat() ? 'Aucun document ne correspond.' : (space().canUpload ? 'Aucun document partagé pour le moment : déposez vos TDR, comptes rendus de réunion…' : 'Aucun document partagé pour le moment.') }}</li>
      }
    </ul>
  `,
  styles: `
    :host { display: block; }
    .drop { border: 1.5px dashed #c9ced5; border-radius: 10px; padding: 16px; text-align: center; margin-bottom: 12px; display: flex; flex-direction: column; gap: 8px; transition: background .15s, border-color .15s; background: var(--surface); }
    .drop.over { background: var(--brand-soft); border-color: var(--accent); }
    .drop-title { font-weight: 700; color: var(--brand-strong); }
    .link { color: var(--brand); font-weight: 600; cursor: pointer; text-decoration: underline; }
    .pending { list-style: none; margin: 0; padding: 0; text-align: left; display: flex; flex-direction: column; gap: 4px; }
    .pending li { display: flex; align-items: center; gap: 8px; }
    .up-fields { display: flex; gap: 10px; flex-wrap: wrap; text-align: left; }
    .up-fields .field:first-child { width: 180px; }
    .up-fields .grow { min-width: 220px; }
    .meter { height: 6px; background: #eceef1; border-radius: 999px; overflow: hidden; }
    .meter > div { height: 100%; background: var(--brand); transition: width .2s; }
    .filters { display: flex; gap: 8px; align-items: center; flex-wrap: wrap; margin-bottom: 8px; }
    .cats { display: flex; gap: 6px; flex-wrap: wrap; flex: 1; }
    .chip { border: 1px solid var(--border); background: var(--surface); border-radius: 999px; padding: 4px 11px; font: inherit; font-size: 12.5px; color: var(--muted); cursor: pointer; }
    .chip b { color: var(--text); margin-left: 2px; }
    .chip.on { border-color: var(--brand); background: var(--brand-soft); color: var(--brand-strong); }
    .search { max-width: 220px; }
    .docs { list-style: none; margin: 0; padding: 0; display: flex; flex-direction: column; }
    .doc { border-top: 1px solid var(--border); }
    .doc:first-child { border-top: 0; }
    .doc.open { background: var(--surface-2); border-radius: 8px; }
    .doc-row { display: flex; gap: 10px; align-items: flex-start; padding: 10px 6px; }
    .main { min-width: 0; display: flex; flex-direction: column; gap: 2px; }
    .actions { display: flex; gap: 4px; flex-wrap: wrap; justify-content: flex-end; }
    .btn.has { border-color: var(--info); color: var(--info); }
    .nowrap { flex-wrap: nowrap; }
    .ico { flex: none; min-width: 34px; height: 24px; padding: 0 4px; border-radius: 6px; display: inline-grid; place-items: center; font-size: 10px; font-weight: 800; background: #eef0f3; color: #475467; font-family: ui-monospace, monospace; margin-top: 2px; }
    .ico[data-k='word'] { background: #e5edfb; color: #2457a6; }
    .ico[data-k='excel'] { background: var(--ok-soft); color: var(--ok); }
    .ico[data-k='pdf'] { background: var(--danger-soft); color: var(--danger); }
    .ico[data-k='slides'], .ico[data-k='archive'] { background: var(--warn-soft); color: var(--warn); }
    .ico[data-k='code'] { background: #10222e; color: #9fe1f0; }
    .fname { border: 0; background: none; padding: 0; font: inherit; font-weight: 600; color: var(--text); cursor: pointer; text-align: left; overflow-wrap: anywhere; }
    .fname:hover { color: var(--brand); text-decoration: underline; }
    .cat { flex: none; font-size: 11px; font-weight: 600; padding: 1px 8px; border-radius: 999px; background: #eef0f3; color: #475467; }
    .cat[data-c='TDR'] { background: var(--brand-soft); color: var(--brand-strong); }
    .cat[data-c='REUNION'] { background: var(--warn-soft); color: var(--warn); }
    .cat[data-c='SPECIFICATION'], .cat[data-c='ARCHITECTURE'] { background: var(--info-soft); color: var(--info); }
    .cat[data-c='CODE'] { background: #10222e; color: #9fe1f0; }
    .desc { color: var(--text); }
    .thread { padding: 0 10px 12px 50px; }
    .thread ol { list-style: none; margin: 0 0 10px; padding: 0; display: flex; flex-direction: column; gap: 8px; }
    .thread li:not(.empty-thread) { background: var(--surface); border: 1px solid var(--border); border-radius: 10px; padding: 8px 10px; }
    .thread li.explain { border-left: 3px solid var(--brand); }
    .thread p { margin: 4px 0 0; white-space: pre-line; }
    .role { font-size: 11px; font-weight: 600; padding: 0 7px; border-radius: 999px; background: #eef0f3; color: #475467; }
    .role[data-r='ARCHITECTE'] { background: var(--brand-soft); color: var(--brand-strong); }
    .role[data-r='LEAD_DEV'] { background: var(--info-soft); color: var(--info); }
    .del { height: 22px; padding: 0 6px; color: var(--muted); }
    .reply { display: flex; flex-direction: column; gap: 6px; }
    .empty { text-align: center; color: var(--muted); padding: 24px 12px; }
    @media (max-width: 640px) {
      .doc-row { flex-wrap: wrap; }
      .actions { width: 100%; justify-content: flex-start; padding-left: 44px; }
      .thread { padding-left: 10px; }
      .search { max-width: none; width: 100%; }
    }
  `,
})
export class ProjectDocumentsComponent {
  private api = inject(ApiService);

  readonly space = input.required<ProjectSpace>();
  /** Nombre de documents modifié (dépôt / suppression). */
  readonly countChange = output<number>();

  readonly docs = signal<ProjectDocument[]>([]);
  readonly thread = signal<DocumentComment[]>([]);
  readonly openId = signal<number | null>(null);
  readonly cat = signal<DocumentCategory | null>(null);
  readonly search = signal('');
  readonly pending = signal<File[]>([]);
  readonly uploading = signal<number | null>(null);
  readonly dragOver = signal(false);
  readonly sending = signal(false);
  readonly error = signal<string | null>(null);
  readonly categories = DOCUMENT_CATEGORIES;
  readonly catLabel = DOCUMENT_CATEGORY_LABEL;
  readonly roleLabel = PROJECT_ROLE_LABEL;
  category: DocumentCategory = 'TDR';
  description = '';
  reply = '';

  /** Architecte, lead dev ou PM : ils expliquent ; les développeurs posent leurs questions. */
  readonly explains = computed(() => this.space().myRoles.some((r) => r !== 'DEV'));

  readonly filtered = computed(() => {
    const q = this.search().trim().toLowerCase();
    const c = this.cat();
    return this.docs().filter((d) => (!c || d.category === c)
      && (!q || (d.fileName + ' ' + (d.description ?? '') + ' ' + (d.uploadedByName ?? '')).toLowerCase().includes(q)));
  });

  constructor() {
    effect(() => {
      const id = this.space().project.id;
      untracked(() => this.load(id));
    });
  }

  private load(projectId: number): void {
    this.docs.set([]);
    this.openId.set(null);
    this.pending.set([]);
    this.cat.set(null);
    this.search.set('');
    this.api.documents(projectId).subscribe({ next: (d) => this.docs.set(d), error: (e) => this.error.set(errorMessage(e)) });
  }

  countOf(c: DocumentCategory): number { return this.docs().filter((d) => d.category === c).length; }

  pick(ev: Event): void {
    const input = ev.target as HTMLInputElement;
    this.pending.update((l) => [...l, ...Array.from(input.files ?? [])]);
    input.value = '';
  }

  dropFiles(ev: DragEvent): void {
    ev.preventDefault();
    this.dragOver.set(false);
    this.pending.update((l) => [...l, ...Array.from(ev.dataTransfer?.files ?? [])]);
  }

  unpick(i: number): void { this.pending.update((l) => l.filter((_, j) => j !== i)); }

  upload(): void {
    const files = this.pending();
    if (!files.length) return;
    this.error.set(null);
    this.uploading.set(0);
    this.api.uploadDocuments(this.space().project.id, files, this.description, this.category).subscribe({
      next: (ev) => {
        if (ev.type === HttpEventType.UploadProgress && ev.total) this.uploading.set(Math.round((ev.loaded / ev.total) * 100));
        if (ev.type === HttpEventType.Response) {
          this.docs.update((l) => [...(ev.body ?? []), ...l]);
          this.countChange.emit(this.docs().length);
          this.pending.set([]);
          this.description = '';
          this.uploading.set(null);
        }
      },
      error: (e) => { this.uploading.set(null); this.error.set(errorMessage(e)); },
    });
  }

  download(d: ProjectDocument): void {
    this.api.downloadDocument(d.projectId, d.id).subscribe({ error: (e) => this.error.set(errorMessage(e)) });
  }

  remove(d: ProjectDocument): void {
    if (!confirm(`Supprimer « ${d.fileName} » et sa discussion pour toute l’équipe ?`)) return;
    this.api.deleteDocument(d.projectId, d.id).subscribe({
      next: () => {
        this.docs.update((l) => l.filter((x) => x.id !== d.id));
        this.countChange.emit(this.docs().length);
      },
      error: (e) => this.error.set(errorMessage(e)),
    });
  }

  toggle(d: ProjectDocument): void {
    if (this.openId() === d.id) {
      this.openId.set(null);
      return;
    }
    this.openId.set(d.id);
    this.thread.set([]);
    this.reply = '';
    this.api.documentComments(d.projectId, d.id).subscribe({ next: (c) => this.thread.set(c), error: (e) => this.error.set(errorMessage(e)) });
  }

  send(d: ProjectDocument): void {
    const text = this.reply.trim();
    if (!text || this.sending()) return;
    this.sending.set(true);
    this.api.addDocumentComment(d.projectId, d.id, text).subscribe({
      next: (c) => {
        this.thread.update((l) => [...l, c]);
        this.setCount(d.id, this.thread().length);
        this.reply = '';
        this.sending.set(false);
      },
      error: (e) => { this.sending.set(false); this.error.set(errorMessage(e)); },
    });
  }

  removeComment(d: ProjectDocument, c: DocumentComment): void {
    if (!confirm('Supprimer ce message ?')) return;
    this.api.deleteDocumentComment(d.projectId, d.id, c.id).subscribe({
      next: () => {
        this.thread.update((l) => l.filter((x) => x.id !== c.id));
        this.setCount(d.id, this.thread().length);
      },
      error: (e) => this.error.set(errorMessage(e)),
    });
  }

  kind(name: string): Kind {
    const ext = name.includes('.') ? name.split('.').pop()!.toLowerCase() : '';
    return (Object.keys(KINDS) as Kind[]).find((k) => KINDS[k].includes(ext)) ?? 'other';
  }
  icon(name: string): string { return KIND_ICON[this.kind(name)]; }

  size(bytes: number): string {
    if (bytes < 1024) return bytes + ' o';
    if (bytes < 1024 * 1024) return Math.round(bytes / 1024) + ' Ko';
    return (bytes / 1024 / 1024).toFixed(1).replace('.', ',') + ' Mo';
  }

  private setCount(id: number, n: number): void {
    this.docs.update((l) => l.map((x) => (x.id === id ? { ...x, commentCount: n } : x)));
  }
}
