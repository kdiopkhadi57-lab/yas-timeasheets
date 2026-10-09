import { HttpClient, HttpErrorResponse, HttpEvent, HttpParams, HttpResponse } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable, map } from 'rxjs';
import {
  Dashboard, Invoice, InvoiceProfile, InvoiceTeamRow, DocumentCategory, DocumentComment, Entry, GitActivity, GitRepo, GitStatus, GitSuggestion, Project, ProjectDocument, ProjectSpace, Report,
  ReportType, Task, TaskActivity,
  TaskGit, TaskStatus, TeamStatus, Timesheet, User,
} from './models';

type Params = Record<string, string | number | boolean | null | undefined>;

function params(p: Params): HttpParams {
  let hp = new HttpParams();
  Object.entries(p).forEach(([k, v]) => {
    if (v !== null && v !== undefined && v !== '') hp = hp.set(k, String(v));
  });
  return hp;
}

/** Message lisible depuis une erreur HTTP (ProblemDetail Spring). */
export function errorMessage(err: unknown): string {
  if (err instanceof HttpErrorResponse) {
    if (err.status === 0) return 'Serveur injoignable. Vérifiez que le backend est démarré.';
    return err.error?.detail || err.error?.message || `Erreur ${err.status}`;
  }
  return 'Erreur inattendue';
}

@Injectable({ providedIn: 'root' })
export class ApiService {
  private http = inject(HttpClient);

  // --- Référentiel
  projects(): Observable<Project[]> {
    return this.http.get<Project[]>('/api/projects');
  }
  saveProject(p: Partial<Project>): Observable<Project> {
    return p.id ? this.http.put<Project>(`/api/projects/${p.id}`, p) : this.http.post<Project>('/api/projects', p);
  }

  // --- Espaces projet et documents
  projectSpaces(): Observable<ProjectSpace[]> {
    return this.http.get<ProjectSpace[]>('/api/projects/spaces');
  }
  documents(projectId: number): Observable<ProjectDocument[]> {
    return this.http.get<ProjectDocument[]>(`/api/projects/${projectId}/documents`);
  }
  /** Envoi avec suivi de progression (événements HTTP). */
  uploadDocuments(projectId: number, files: File[], description: string, category: DocumentCategory): Observable<HttpEvent<ProjectDocument[]>> {
    const form = new FormData();
    files.forEach((f) => form.append('files', f, f.name));
    if (description.trim()) form.append('description', description.trim());
    form.append('category', category);
    return this.http.post<ProjectDocument[]>(`/api/projects/${projectId}/documents`, form, { reportProgress: true, observe: 'events' });
  }
  downloadDocument(projectId: number, documentId: number): Observable<void> {
    return this.download(`/api/projects/${projectId}/documents/${documentId}`, new HttpParams());
  }
  deleteDocument(projectId: number, documentId: number): Observable<void> {
    return this.http.delete<void>(`/api/projects/${projectId}/documents/${documentId}`);
  }
  documentComments(projectId: number, documentId: number): Observable<DocumentComment[]> {
    return this.http.get<DocumentComment[]>(`/api/projects/${projectId}/documents/${documentId}/comments`);
  }
  addDocumentComment(projectId: number, documentId: number, text: string): Observable<DocumentComment> {
    return this.http.post<DocumentComment>(`/api/projects/${projectId}/documents/${documentId}/comments`, { text });
  }
  deleteDocumentComment(projectId: number, documentId: number, commentId: number): Observable<void> {
    return this.http.delete<void>(`/api/projects/${projectId}/documents/${documentId}/comments/${commentId}`);
  }

  users(): Observable<User[]> {
    return this.http.get<User[]>('/api/users');
  }
  team(): Observable<User[]> {
    return this.http.get<User[]>('/api/users/team');
  }
  saveUser(u: Partial<User> & { password?: string }): Observable<User> {
    return u.id ? this.http.put<User>(`/api/users/${u.id}`, u) : this.http.post<User>('/api/users', u);
  }
  changePassword(currentPassword: string, newPassword: string): Observable<void> {
    return this.http.post<void>('/api/auth/password', { currentPassword, newPassword });
  }

  // --- Tâches
  tasks(p: { scope?: 'mine' | 'team' | 'projects'; assigneeId?: number | null; status?: TaskStatus | null; projectId?: number | null } = {}): Observable<Task[]> {
    return this.http.get<Task[]>('/api/tasks', { params: params(p) });
  }
  saveTask(t: Partial<Task>): Observable<Task> {
    return t.id ? this.http.put<Task>(`/api/tasks/${t.id}`, t) : this.http.post<Task>('/api/tasks', t);
  }
  setTaskStatus(id: number, status: TaskStatus): Observable<Task> {
    return this.http.patch<Task>(`/api/tasks/${id}/status`, { status });
  }
  proposeEstimate(id: number, days: number, comment: string): Observable<Task> {
    return this.http.post<Task>(`/api/tasks/${id}/estimate`, { days, comment });
  }
  approveEstimate(id: number, days: number | null, comment: string): Observable<Task> {
    return this.http.post<Task>(`/api/tasks/${id}/estimate/approve`, { days, comment });
  }
  rejectEstimate(id: number, comment: string): Observable<Task> {
    return this.http.post<Task>(`/api/tasks/${id}/estimate/reject`, { comment });
  }
  deleteTask(id: number): Observable<void> {
    return this.http.delete<void>(`/api/tasks/${id}`);
  }
  taskGit(id: number): Observable<TaskGit> {
    return this.http.get<TaskGit>(`/api/tasks/${id}/git`);
  }
  taskActivity(id: number): Observable<TaskActivity[]> {
    return this.http.get<TaskActivity[]>(`/api/tasks/${id}/activity`);
  }
  addComment(id: number, text: string): Observable<TaskActivity> {
    return this.http.post<TaskActivity>(`/api/tasks/${id}/comments`, { text });
  }
  deleteComment(id: number, commentId: number): Observable<void> {
    return this.http.delete<void>(`/api/tasks/${id}/comments/${commentId}`);
  }

  // --- Tableau de bord
  dashboard(p: { projectId?: number | null; weeks?: number } = {}): Observable<Dashboard> {
    return this.http.get<Dashboard>('/api/dashboard', { params: params(p) });
  }

  // --- GitLab
  gitStatus(): Observable<GitStatus> {
    return this.http.get<GitStatus>('/api/git/status');
  }
  gitSync(): Observable<GitStatus> {
    return this.http.post<GitStatus>('/api/git/sync', null);
  }
  gitRepos(): Observable<GitRepo[]> {
    return this.http.get<GitRepo[]>('/api/git/repos');
  }
  mapGitRepo(id: number, projectId: number | null): Observable<GitRepo> {
    return this.http.put<GitRepo>(`/api/git/repos/${id}`, { projectId });
  }
  gitSuggestions(from: string, to: string, consultantId?: number | null): Observable<GitSuggestion[]> {
    return this.http.get<GitSuggestion[]>('/api/git/suggestions', { params: params({ from, to, consultantId }) });
  }
  gitActivity(type: ReportType, date: string, projectId?: number | null): Observable<GitActivity> {
    return this.http.get<GitActivity>('/api/git/activity', { params: params({ type, date, projectId }) });
  }

  // --- Saisies
  saveEntry(e: { id?: number; workDate: string; projectId?: number | null; taskId?: number | null; description: string; comment?: string | null; taskStatus?: TaskStatus | null }): Observable<Entry> {
    return e.id ? this.http.put<Entry>(`/api/entries/${e.id}`, e) : this.http.post<Entry>('/api/entries', e);
  }
  deleteEntry(id: number): Observable<void> {
    return this.http.delete<void>(`/api/entries/${id}`);
  }

  // --- Factures
  myInvoices(): Observable<Invoice[]> {
    return this.http.get<Invoice[]>('/api/invoices/mine');
  }
  invoiceForMonth(month: string): Observable<Invoice> {
    return this.http.get<Invoice>(`/api/invoices/month/${month}`);
  }
  saveInvoice(i: { month: string; invoiceDate: string; title: string; netAmount: number; computerDeduction: number }): Observable<Invoice> {
    return this.http.put<Invoice>('/api/invoices', i);
  }
  submitInvoice(id: number): Observable<Invoice> {
    return this.http.post<Invoice>(`/api/invoices/${id}/submit`, null);
  }
  teamInvoices(month: string): Observable<InvoiceTeamRow[]> {
    return this.http.get<InvoiceTeamRow[]>('/api/invoices/team', { params: params({ month }) });
  }
  approveInvoice(id: number, comment?: string): Observable<Invoice> {
    return this.http.post<Invoice>(`/api/invoices/${id}/approve`, { comment });
  }
  rejectInvoice(id: number, comment: string): Observable<Invoice> {
    return this.http.post<Invoice>(`/api/invoices/${id}/reject`, { comment });
  }
  downloadInvoice(id: number): Observable<void> {
    return this.download(`/api/invoices/${id}/pdf`, new HttpParams());
  }
  /** PDF ouvert dans un nouvel onglet (aperçu). */
  invoicePdf(id: number): Observable<Blob> {
    return this.http.get(`/api/invoices/${id}/pdf`, { responseType: 'blob' });
  }
  invoiceProfile(): Observable<InvoiceProfile> {
    return this.http.get<InvoiceProfile>('/api/invoices/profile');
  }
  saveInvoiceProfile(p: { phone: string | null; invoiceEmail: string | null; address: string | null }): Observable<InvoiceProfile> {
    return this.http.put<InvoiceProfile>('/api/invoices/profile', p);
  }
  signature(): Observable<Blob> {
    return this.http.get('/api/invoices/profile/signature', { responseType: 'blob' });
  }
  saveSignature(dataUrl: string): Observable<InvoiceProfile> {
    return this.http.put<InvoiceProfile>('/api/invoices/profile/signature', { dataUrl });
  }
  deleteSignature(): Observable<InvoiceProfile> {
    return this.http.delete<InvoiceProfile>('/api/invoices/profile/signature');
  }

  // --- Rapports
  timesheet(type: ReportType, date: string, consultantId?: number | null): Observable<Timesheet> {
    return this.http.get<Timesheet>('/api/reports/timesheet', { params: params({ type, date, consultantId }) });
  }
  submit(type: ReportType, date: string, comment: string): Observable<Timesheet> {
    return this.http.post<Timesheet>('/api/reports/submit', { comment }, { params: params({ type, date }) });
  }
  withdraw(type: ReportType, date: string): Observable<Timesheet> {
    return this.http.post<Timesheet>('/api/reports/withdraw', null, { params: params({ type, date }) });
  }
  approve(id: number, comment?: string): Observable<Report> {
    return this.http.post<Report>(`/api/reports/${id}/approve`, { comment });
  }
  reject(id: number, comment: string): Observable<Report> {
    return this.http.post<Report>(`/api/reports/${id}/reject`, { comment });
  }
  teamStatus(type: ReportType, date: string): Observable<TeamStatus[]> {
    return this.http.get<TeamStatus[]>('/api/reports/team', { params: params({ type, date }) });
  }
  /** Fiches soumises reçues, en attente de validation (cloche de notification). */
  inbox(): Observable<Report[]> {
    return this.http.get<Report[]>('/api/reports/inbox');
  }
  history(consultantId?: number): Observable<Report[]> {
    return this.http.get<Report[]>('/api/reports/history', { params: params({ consultantId }) });
  }

  downloadExcel(type: ReportType, date: string, consultantId?: number | null): Observable<void> {
    return this.download('/api/reports/excel', params({ type, date, consultantId }));
  }
  downloadTeamZip(type: ReportType, date: string): Observable<void> {
    return this.download('/api/reports/team/zip', params({ type, date }));
  }

  private download(url: string, p: HttpParams): Observable<void> {
    return this.http.get(url, { params: p, responseType: 'blob', observe: 'response' }).pipe(
      map((res: HttpResponse<Blob>) => {
        const name = fileName(res.headers.get('Content-Disposition')) ?? 'telechargement';
        const link = document.createElement('a');
        link.href = URL.createObjectURL(res.body!);
        link.download = name;
        link.click();
        setTimeout(() => URL.revokeObjectURL(link.href), 1000);
      }),
    );
  }
}

function fileName(header: string | null): string | null {
  if (!header) return null;
  const star = /filename\*=UTF-8''([^;]+)/i.exec(header);
  if (star) return decodeURIComponent(star[1]);
  const plain = /filename="?([^";]+)"?/i.exec(header);
  return plain ? plain[1] : null;
}
