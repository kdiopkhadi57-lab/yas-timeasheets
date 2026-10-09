export type Role = 'CONSULTANT' | 'ARCHITECTE' | 'LEAD_DEV' | 'PM' | 'HEAD_OF_OFFICE' | 'ADMIN';
export type TaskStatus = 'A_FAIRE' | 'EN_COURS' | 'BLOQUEE' | 'TERMINEE';
export type TaskPriority = 'BASSE' | 'NORMALE' | 'HAUTE' | 'CRITIQUE';
export type ReportType = 'HEBDO' | 'MENSUEL';
/** Circuit d'estimation : le développeur propose une durée, le lead dev l'accorde, la tâche devient active. */
export type EstimateStatus = 'A_ESTIMER' | 'PROPOSEE' | 'ACCORDEE';
export type ProjectRole = 'PM' | 'ARCHITECTE' | 'LEAD_DEV' | 'DEV';
export type ReportStatus = 'BROUILLON' | 'SOUMIS' | 'VALIDE' | 'REJETE';
export type Skill = 'JAVA' | 'PHP' | 'TALEND' | 'PYTHON';
export type DevProfile = 'BACKEND' | 'FRONTEND' | 'FULLSTACK';

export const SKILLS: Skill[] = ['JAVA', 'PHP', 'TALEND', 'PYTHON'];
export const SKILL_LABEL: Record<Skill, string> = { JAVA: 'Java', PHP: 'PHP', TALEND: 'Talend', PYTHON: 'Python' };
export const DEV_PROFILES: DevProfile[] = ['BACKEND', 'FRONTEND', 'FULLSTACK'];
export const DEV_PROFILE_LABEL: Record<DevProfile, string> = { BACKEND: 'Backend', FRONTEND: 'Frontend', FULLSTACK: 'Fullstack' };

export interface User {
  id: number;
  firstName: string;
  lastName: string;
  fullName: string;
  matricule: string;
  profil: string | null;
  email: string;
  role: Role;
  pmId: number | null;
  pmName: string | null;
  active: boolean;
  gitIdentities: string | null;
  /** Profil du développeur (backend, frontend, fullstack) et ses technologies. */
  devProfile: DevProfile | null;
  skills: Skill[];
  /** Projets auxquels il participe (développeur, lead dev ou architecte). */
  projects: { id: number; name: string }[];
}

export interface Member {
  id: number;
  fullName: string;
  profil: string | null;
  email: string;
  devProfile: DevProfile | null;
  skills: Skill[];
}

export interface Project {
  id: number;
  name: string;
  businessUnit: string | null;
  pmId: number | null;
  pmName: string | null;
  active: boolean;
  /** Dépose les documents du projet. */
  architectId: number | null;
  architectName: string | null;
  /** Assigne les tâches aux développeurs et valide leurs estimations. */
  leadDevId: number | null;
  leadDevName: string | null;
  members: Member[];
}

/** Projet vu par l'utilisateur connecté : ses rôles et ce qu'il peut y faire. */
export interface ProjectSpace {
  project: Project;
  myRoles: ProjectRole[];
  canUpload: boolean;
  canAssign: boolean;
  documentCount: number;
  pendingEstimates: number;
}

export type DocumentCategory = 'TDR' | 'REUNION' | 'SPECIFICATION' | 'ARCHITECTURE' | 'CODE' | 'AUTRE';

export const DOCUMENT_CATEGORIES: DocumentCategory[] = ['TDR', 'REUNION', 'SPECIFICATION', 'ARCHITECTURE', 'CODE', 'AUTRE'];

export const DOCUMENT_CATEGORY_LABEL: Record<DocumentCategory, string> = {
  TDR: 'TDR',
  REUNION: 'Réunion',
  SPECIFICATION: 'Spécification',
  ARCHITECTURE: 'Architecture',
  CODE: 'Code',
  AUTRE: 'Autre',
};

export interface ProjectDocument {
  id: number;
  projectId: number;
  fileName: string;
  contentType: string | null;
  size: number;
  category: DocumentCategory;
  description: string | null;
  /** Questions des développeurs et explications du lead dev / de l'architecte. */
  commentCount: number;
  uploadedById: number | null;
  uploadedByName: string | null;
  uploadedAt: string;
  canDelete: boolean;
}

export interface DocumentComment {
  id: number;
  authorId: number;
  authorName: string;
  /** Rôle de l'auteur sur le projet : ARCHITECTE / LEAD_DEV / PM expliquent, DEV pose ses questions. */
  authorRole: ProjectRole | null;
  text: string;
  createdAt: string;
  canDelete: boolean;
}

export interface Task {
  id: number;
  /** Référence à mettre dans la branche, la MR ou le commit : TS-42. */
  ref: string;
  title: string;
  description: string | null;
  projectId: number;
  projectName: string;
  assigneeId: number;
  assigneeName: string;
  createdByName: string | null;
  status: TaskStatus;
  priority: TaskPriority;
  startDate: string | null;
  dueDate: string | null;
  /** Charge estimée en jours. */
  estimateDays: number | null;
  /** Avancement 0–100 (100 si terminée). */
  progress: number;
  /** Jours de la fiche de temps rattachés à la tâche. */
  spentDays: number;
  commentCount: number;
  createdAt: string;
  completedAt: string | null;
  commitCount: number;
  lastCommitAt: string | null;
  estimateStatus: EstimateStatus;
  /** Durée proposée par le développeur, en attente du lead dev. */
  proposedEstimateDays: number | null;
  leadDevName: string | null;
  /** Estimation accordée : la tâche peut être démarrée et déclarée dans la fiche de temps. */
  active: boolean;
  canEdit: boolean;
  canEstimate: boolean;
  canApproveEstimate: boolean;
}

export type TaskActivityKind = 'CREATION' | 'COMMENTAIRE' | 'STATUT' | 'ASSIGNATION' | 'MODIFICATION' | 'ESTIMATION';

export interface TaskActivity {
  id: number;
  kind: TaskActivityKind;
  text: string;
  authorId: number | null;
  authorName: string | null;
  createdAt: string;
}

// --- Tableau de bord
export interface DashboardKpis {
  members: number;
  submitted: number;
  pending: number;
  validated: number;
  rejected: number;
  missingLastWeek: number;
  onTimeRate: number | null;
  declaredDays: number;
}

/** Développeur (vu par son manager) ou architecte (vu par le Head of Office) et ses fiches sur la période. */
export interface MemberRow {
  id: number;
  fullName: string;
  matricule: string;
  role: Role;
  profil: string | null;
  submitted: number;
  weekly: number;
  monthly: number;
  pending: number;
  validated: number;
  rejected: number;
  late: number;
  missing: number;
  declaredDays: number;
  lastSubmittedAt: string | null;
}

export interface ProjectRow {
  projectId: number;
  projectName: string;
  businessUnit: string | null;
  people: number;
  declaredDays: number;
}

export interface ComplianceWeek { weekStart: string; label: string; expected: number; submitted: number; onTime: number; }

export interface Dashboard {
  from: string;
  to: string;
  audience: 'DEV' | 'ARCHITECTE' | 'ALL';
  kpis: DashboardKpis;
  members: MemberRow[];
  projects: ProjectRow[];
  compliance: ComplianceWeek[];
}

export interface Entry {
  id: number;
  workDate: string;
  projectId: number;
  projectName: string;
  businessUnit: string | null;
  taskId: number | null;
  taskTitle: string | null;
  description: string;
  comment: string | null;
}

export interface Day {
  date: string;
  weekend: boolean;
  projects: string[];
  businessUnits: string[];
  tasks: string[];
  comments: string[];
  entries: Entry[];
}

export interface Report {
  id: number;
  type: ReportType;
  consultantId: number;
  consultantName: string;
  matricule: string;
  periodStart: string;
  periodEnd: string;
  periodLabel: string;
  status: ReportStatus;
  consultantComment: string | null;
  reviewComment: string | null;
  reviewerName: string | null;
  submittedAt: string | null;
  reviewedAt: string | null;
  sentAt: string | null;
}

export interface Timesheet {
  consultant: User;
  type: ReportType;
  periodStart: string;
  periodEnd: string;
  periodLabel: string;
  report: Report | null;
  days: Day[];
  workedDays: number;
  entryCount: number;
  editable: boolean;
}

export interface TeamStatus {
  consultant: User;
  report: Report | null;
  workedDays: number;
  entryCount: number;
}

// --- GitLab
export type GitLinkSource = 'MESSAGE' | 'MERGE_REQUEST' | 'BRANCHE';

export interface GitStatus {
  enabled: boolean;
  gitlabUrl: string;
  taskPrefix: string;
  lastRunAt: string | null;
  lastError: string | null;
  syncedRepos: number;
  newCommits: number;
}

export interface GitRepo {
  id: number;
  path: string;
  webUrl: string | null;
  projectId: number | null;
  projectName: string | null;
  lastSyncedAt: string | null;
}

export interface GitCommit {
  id: number;
  shortSha: string;
  title: string;
  repo: string;
  webUrl: string | null;
  authoredAt: string;
  workDate: string;
  authorName: string | null;
  authorEmail: string | null;
  consultantId: number | null;
  consultantName: string | null;
  taskId: number | null;
  taskRef: string | null;
  taskTitle: string | null;
  linkSource: GitLinkSource | null;
}

export interface GitMergeRequest {
  id: number;
  iid: number;
  title: string;
  repo: string;
  sourceBranch: string | null;
  state: string | null;
  webUrl: string | null;
  authorName: string | null;
  updatedAt: string | null;
  mergedAt: string | null;
}

export interface TaskGit {
  ref: string;
  branchName: string;
  commits: GitCommit[];
  mergeRequests: GitMergeRequest[];
}

export interface GitSuggestion {
  date: string;
  projectId: number;
  projectName: string;
  taskId: number | null;
  taskRef: string | null;
  taskTitle: string | null;
  description: string;
  commits: GitCommit[];
}

export interface GitActivityRow {
  consultantId: number;
  consultantName: string;
  projectId: number | null;
  projectName: string;
  commits: number;
  linkedCommits: number;
  tasks: number;
  commitDays: number;
  declaredDays: number;
  undeclaredDays: number;
  lastCommitAt: string | null;
}

export interface GitActivity {
  from: string;
  to: string;
  periodLabel: string;
  rows: GitActivityRow[];
  tasksWithoutCommits: Task[];
  unlinkedCommits: GitCommit[];
  unknownAuthors: { email: string; name: string | null; commits: number }[];
}

export const GIT_LINK_LABEL: Record<GitLinkSource, string> = {
  MESSAGE: 'message du commit',
  MERGE_REQUEST: 'merge request',
  BRANCHE: 'branche',
};

export const TASK_STATUS_LABEL: Record<TaskStatus, string> = {
  A_FAIRE: 'À faire',
  EN_COURS: 'En cours',
  BLOQUEE: 'Bloquée',
  TERMINEE: 'Terminée',
};

export const TASK_STATUSES: TaskStatus[] = ['A_FAIRE', 'EN_COURS', 'BLOQUEE', 'TERMINEE'];
export const TASK_PRIORITIES: TaskPriority[] = ['CRITIQUE', 'HAUTE', 'NORMALE', 'BASSE'];

export const TASK_PRIORITY_LABEL: Record<TaskPriority, string> = {
  BASSE: 'Basse',
  NORMALE: 'Normale',
  HAUTE: 'Haute',
  CRITIQUE: 'Critique',
};

/** Couleurs des statuts dans les graphiques (texte toujours en encre neutre à côté). */
export const TASK_STATUS_COLOR: Record<TaskStatus, string> = {
  A_FAIRE: '#98a2b3',
  EN_COURS: '#2a78d6',
  BLOQUEE: '#d03b3b',
  TERMINEE: '#0f7b3f',
};

// --- Factures
export type InvoiceStatus = 'BROUILLON' | 'SOUMISE' | 'VALIDEE' | 'REJETEE';

export const INVOICE_STATUS_LABEL: Record<InvoiceStatus, string> = {
  BROUILLON: 'Brouillon',
  SOUMISE: 'Envoyée',
  VALIDEE: 'Validée',
  REJETEE: 'À corriger',
};

/** Facture d'un mois ; id null = proposition pas encore enregistrée. Montants en FCFA. */
export interface Invoice {
  id: number | null;
  consultantId: number;
  consultantName: string;
  matricule: string;
  number: string;
  /** AAAA-MM */
  month: string;
  periodStart: string;
  periodEnd: string;
  invoiceDate: string;
  /** Intitulé, ex. « Consultant Développeur Java ». */
  title: string;
  /** Ligne « Objet » imprimée : intitulé + période. */
  objectLine: string;
  client: string;
  netAmount: number;
  grossAmount: number;
  withholding: number;
  withholdingPercent: number;
  computerDeduction: number;
  netToPay: number;
  status: InvoiceStatus;
  reviewComment: string | null;
  reviewerName: string | null;
  submittedAt: string | null;
  reviewedAt: string | null;
  editable: boolean;
}

export interface InvoiceProfile {
  fullName: string;
  phone: string | null;
  invoiceEmail: string | null;
  address: string | null;
  hasSignature: boolean;
}

export interface InvoiceTeamRow {
  consultant: User;
  invoice: Invoice | null;
}

export const ESTIMATE_STATUS_LABEL: Record<EstimateStatus, string> = {
  A_ESTIMER: 'À estimer',
  PROPOSEE: 'Estimation à valider',
  ACCORDEE: 'Estimation accordée',
};

export const PROJECT_ROLE_LABEL: Record<ProjectRole, string> = {
  PM: 'Manager',
  ARCHITECTE: 'Architecte',
  LEAD_DEV: 'Lead dev',
  DEV: 'Développeur',
};

export const REPORT_STATUS_LABEL: Record<ReportStatus, string> = {
  BROUILLON: 'Non soumis',
  SOUMIS: 'Soumis',
  VALIDE: 'Validé',
  REJETE: 'À corriger',
};

export const ROLE_LABEL: Record<Role, string> = {
  CONSULTANT: 'Consultant (développeur)',
  ARCHITECTE: 'Architecte',
  LEAD_DEV: 'Lead dev',
  PM: 'Manager',
  HEAD_OF_OFFICE: 'Head of Office',
  ADMIN: 'Administrateur',
};
