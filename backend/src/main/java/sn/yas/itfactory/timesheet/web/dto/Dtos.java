package sn.yas.itfactory.timesheet.web.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import sn.yas.itfactory.timesheet.domain.*;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/** Objets échangés avec le frontend Angular. */
public final class Dtos {

    private Dtos() {
    }

    // ---- Authentification
    public record LoginRequest(@NotBlank @Email String email, @NotBlank String password) {
    }

    public record LoginResponse(String token, UserDto user) {
    }

    public record ChangePasswordRequest(@NotBlank String currentPassword, @NotBlank String newPassword) {
    }

    // ---- Utilisateurs
    /** devProfile / skills : profil (backend, frontend, fullstack) et technologies ; projects : projets auxquels il participe. */
    public record UserDto(Long id, String firstName, String lastName, String fullName, String matricule,
                          String profil, String email, Role role, Long pmId, String pmName, boolean active,
                          String gitIdentities, DevProfile devProfile, List<Skill> skills, List<ProjectRefDto> projects) {
        public static UserDto of(AppUser u) {
            return of(u, List.of());
        }

        public static UserDto of(AppUser u, List<ProjectRefDto> projects) {
            if (u == null) {
                return null;
            }
            AppUser pm = u.getPm();
            return new UserDto(u.getId(), u.getFirstName(), u.getLastName(), u.getFullName(), u.getMatricule(),
                    u.getProfil(), u.getEmail(), u.getRole(), pm == null ? null : pm.getId(),
                    pm == null ? null : pm.getFullName(), u.isActive(), u.getGitIdentities(),
                    u.getDevProfile(), u.getSkills(), projects);
        }
    }

    public record ProjectRefDto(Long id, String name) {
    }

    /** projectIds : projets dont le développeur fait partie (null : inchangés). */
    public record UserRequest(@NotBlank String firstName, @NotBlank String lastName, @NotBlank String matricule,
                              String profil, @NotBlank @Email String email, String password,
                              @NotNull Role role, Long pmId, Boolean active, String gitIdentities,
                              DevProfile devProfile, List<Skill> skills, List<Long> projectIds) {
    }

    // ---- Projets
    public record MemberDto(Long id, String fullName, String profil, String email, DevProfile devProfile, List<Skill> skills) {
        public static MemberDto of(AppUser u) {
            return u == null ? null : new MemberDto(u.getId(), u.getFullName(), u.getProfil(), u.getEmail(),
                    u.getDevProfile(), u.getSkills());
        }
    }

    /** architect : dépose les documents ; leadDev : assigne les tâches et valide les estimations ; members : développeurs. */
    public record ProjectDto(Long id, String name, String businessUnit, Long pmId, String pmName, boolean active,
                             Long architectId, String architectName, Long leadDevId, String leadDevName,
                             List<MemberDto> members) {
        public static ProjectDto of(Project p) {
            AppUser pm = p.getPm();
            AppUser architect = p.getArchitect();
            AppUser lead = p.getLeadDev();
            return new ProjectDto(p.getId(), p.getName(), p.getBusinessUnit(), pm == null ? null : pm.getId(),
                    pm == null ? null : pm.getFullName(), p.isActive(),
                    architect == null ? null : architect.getId(), architect == null ? null : architect.getFullName(),
                    lead == null ? null : lead.getId(), lead == null ? null : lead.getFullName(),
                    p.getMembers().stream().sorted(java.util.Comparator.comparing(AppUser::getLastName, String.CASE_INSENSITIVE_ORDER))
                            .map(MemberDto::of).toList());
        }
    }

    public record ProjectRequest(@NotBlank String name, String businessUnit, Long pmId, Boolean active,
                                 Long architectId, Long leadDevId, List<Long> memberIds) {
    }

    /**
     * Espace projet de l'utilisateur connecté : ses rôles (PM, ARCHITECTE, LEAD_DEV, DEV), ses droits,
     * le nombre de documents et d'estimations en attente de validation.
     */
    public record ProjectSpaceDto(ProjectDto project, List<String> myRoles, boolean canUpload, boolean canAssign,
                                  long documentCount, long pendingEstimates) {
    }

    /** commentCount : questions des développeurs et explications du lead dev / de l'architecte. */
    public record ProjectDocumentDto(Long id, Long projectId, String fileName, String contentType, long size,
                                     DocumentCategory category, String description, Long uploadedById,
                                     String uploadedByName, Instant uploadedAt, long commentCount, boolean canDelete) {
        public static ProjectDocumentDto of(ProjectDocument d, long commentCount, boolean canDelete) {
            AppUser u = d.getUploadedBy();
            return new ProjectDocumentDto(d.getId(), d.getProject().getId(), d.getFileName(), d.getContentType(),
                    d.getSize(), d.getCategory(), d.getDescription(), u == null ? null : u.getId(),
                    u == null ? null : u.getFullName(), d.getUploadedAt(), commentCount, canDelete);
        }
    }

    /** authorRole : rôle de l'auteur sur le projet (ARCHITECTE, LEAD_DEV, PM, DEV) pour distinguer les explications. */
    public record DocumentCommentDto(Long id, Long authorId, String authorName, String authorRole, String text,
                                     Instant createdAt, boolean canDelete) {
    }

    // ---- Tâches
    /** Droits de l'utilisateur connecté sur une tâche. */
    public record TaskRights(boolean canEdit, boolean canEstimate, boolean canApproveEstimate) {
        public static final TaskRights NONE = new TaskRights(false, false, false);
    }

    /**
     * ref : référence à mettre dans la branche / MR / commit (TS-42) ; commitCount : commits GitLab liés ;
     * spentDays : jours où une saisie de la fiche de temps est rattachée à la tâche ;
     * estimateStatus : circuit d'estimation (A_ESTIMER → PROPOSEE → ACCORDEE), la tâche n'est active qu'une fois accordée.
     */
    public record TaskDto(Long id, String ref, String title, String description, Long projectId, String projectName,
                          Long assigneeId, String assigneeName, String createdByName, TaskStatus status,
                          TaskPriority priority, LocalDate startDate, LocalDate dueDate, Double estimateDays,
                          int progress, long spentDays, long commentCount, Instant createdAt, Instant completedAt,
                          long commitCount, Instant lastCommitAt, EstimateStatus estimateStatus,
                          Double proposedEstimateDays, String leadDevName, boolean active,
                          boolean canEdit, boolean canEstimate, boolean canApproveEstimate) {
        public static TaskDto of(Task t, String ref, long commitCount, Instant lastCommitAt, long spentDays, long commentCount,
                                 TaskRights rights) {
            AppUser lead = t.getProject().getLeadDev();
            return new TaskDto(t.getId(), ref, t.getTitle(), t.getDescription(), t.getProject().getId(),
                    t.getProject().getName(), t.getAssignee().getId(), t.getAssignee().getFullName(),
                    t.getCreatedBy() == null ? null : t.getCreatedBy().getFullName(), t.getStatus(),
                    t.getPriority(), t.getStartDate(), t.getDueDate(), t.getEstimateDays(), t.getProgress(),
                    spentDays, commentCount, t.getCreatedAt(), t.getCompletedAt(), commitCount, lastCommitAt,
                    t.getEstimateStatus(), t.getProposedEstimateDays(), lead == null ? null : lead.getFullName(),
                    t.isActive(), rights.canEdit(), rights.canEstimate(), rights.canApproveEstimate());
        }
    }

    public record TaskRequest(@NotBlank String title, String description, @NotNull Long projectId,
                              Long assigneeId, LocalDate startDate, LocalDate dueDate, TaskStatus status,
                              TaskPriority priority, @PositiveOrZero Double estimateDays,
                              @Min(0) @Max(100) Integer progress) {
    }

    public record TaskStatusRequest(@NotNull TaskStatus status) {
    }

    /** Durée proposée par le développeur (jours). */
    public record EstimateRequest(@NotNull @Positive Double days, @Size(max = 2000) String comment) {
    }

    /** Décision du lead dev : days peut ajuster la durée accordée ; comment obligatoire pour un refus. */
    public record EstimateDecisionRequest(@Positive Double days, @Size(max = 2000) String comment) {
    }

    public record TaskActivityDto(Long id, TaskActivityKind kind, String text, Long authorId, String authorName,
                                  Instant createdAt) {
        public static TaskActivityDto of(TaskActivity a) {
            AppUser u = a.getAuthor();
            return new TaskActivityDto(a.getId(), a.getKind(), a.getText(), u == null ? null : u.getId(),
                    u == null ? null : u.getFullName(), a.getCreatedAt());
        }
    }

    public record CommentRequest(@NotBlank @Size(max = 4000) String text) {
    }

    // ---- Factures
    /**
     * Facture d'un mois. id null : proposition pas encore enregistrée (dates du mois, montant de la facture
     * précédente). objectLine : libellé complet imprimé (intitulé + période).
     */
    public record InvoiceDto(Long id, Long consultantId, String consultantName, String matricule, String number,
                             String month, LocalDate periodStart, LocalDate periodEnd, LocalDate invoiceDate,
                             String title, String objectLine, String client, long netAmount, long grossAmount,
                             long withholding, int withholdingPercent, long computerDeduction, long netToPay,
                             InvoiceStatus status, String reviewComment, String reviewerName, Instant submittedAt,
                             Instant reviewedAt, boolean editable) {
    }

    /** month : AAAA-MM. */
    public record InvoiceRequest(@NotBlank String month, @NotNull LocalDate invoiceDate,
                                 @NotBlank @Size(max = 300) String title, @NotNull @Positive Long netAmount,
                                 @PositiveOrZero Long computerDeduction) {
    }

    /** Coordonnées imprimées sur les factures du consultant. */
    public record InvoiceProfileDto(String fullName, String phone, String invoiceEmail, String address,
                                    boolean hasSignature) {
    }

    public record InvoiceProfileRequest(@Size(max = 40) String phone, @Size(max = 200) String invoiceEmail,
                                        @Size(max = 500) String address) {
    }

    /** Image PNG en data URL (data:image/png;base64,…). */
    public record SignatureRequest(@NotBlank String dataUrl) {
    }

    /** Suivi Head of Office : chaque consultant et sa facture du mois (null si pas encore créée). */
    public record InvoiceTeamRowDto(UserDto consultant, InvoiceDto invoice) {
    }

    // ---- Tableau de bord (managers et Head of Office)
    /**
     * Indicateurs sur les fiches des personnes suivies (développeurs pour un manager, architectes pour le Head of Office),
     * sur la plage de semaines demandée. pending : fiches soumises en attente de validation, quelle que soit leur date.
     */
    public record DashboardKpis(int members, int submitted, int pending, int validated, int rejected,
                                int missingLastWeek, Integer onTimeRate, double declaredDays) {
    }

    /**
     * Une personne suivie et ses fiches sur la période : soumissions (hebdo + mensuelles), en attente, validées,
     * à corriger, rapports hebdo en retard ou manquants, jours déclarés.
     */
    public record MemberRowDto(Long id, String fullName, String matricule, Role role, String profil, int submitted,
                               int weekly, int monthly, int pending, int validated, int rejected, int late,
                               int missing, double declaredDays, Instant lastSubmittedAt) {
    }

    /** declaredDays : jours-homme, une journée partagée entre N projets compte 1/N pour chacun. */
    public record ProjectRowDto(Long projectId, String projectName, String businessUnit, int people,
                                double declaredDays) {
    }

    /** Rapports hebdo : attendus, soumis, et soumis avant l'échéance (vendredi 18 h). */
    public record ComplianceWeekDto(LocalDate weekStart, String label, int expected, int submitted, int onTime) {
    }

    /** audience : DEV (manager), ARCHITECTE (Head of Office) ou ALL (Admin). */
    public record DashboardDto(LocalDate from, LocalDate to, String audience, DashboardKpis kpis,
                               List<MemberRowDto> members, List<ProjectRowDto> projects,
                               List<ComplianceWeekDto> compliance) {
    }

    // ---- Saisies journalières
    public record EntryDto(Long id, LocalDate workDate, Long projectId, String projectName, String businessUnit,
                           Long taskId, String taskTitle, String description, String comment) {
        public static EntryDto of(WorkEntry e) {
            return new EntryDto(e.getId(), e.getWorkDate(), e.getProject().getId(), e.getProject().getName(),
                    e.getProject().getBusinessUnit(), e.getTask() == null ? null : e.getTask().getId(),
                    e.getTask() == null ? null : e.getTask().getTitle(), e.getDescription(), e.getComment());
        }
    }

    /** taskStatus : permet de faire avancer la tâche liée en même temps que la saisie. */
    public record EntryRequest(@NotNull LocalDate workDate, Long projectId, Long taskId,
                               @NotBlank String description, String comment, TaskStatus taskStatus) {
    }

    // ---- Fiche de temps
    public record DayDto(LocalDate date, boolean weekend, List<String> projects, List<String> businessUnits,
                         List<String> tasks, List<String> comments, List<EntryDto> entries) {
    }

    public record ReportDto(Long id, ReportType type, Long consultantId, String consultantName, String matricule,
                            LocalDate periodStart, LocalDate periodEnd, String periodLabel, ReportStatus status,
                            String consultantComment, String reviewComment, String reviewerName,
                            Instant submittedAt, Instant reviewedAt, Instant sentAt) {
    }

    public record TimesheetDto(UserDto consultant, ReportType type, LocalDate periodStart, LocalDate periodEnd,
                               String periodLabel, ReportDto report, List<DayDto> days, int workedDays,
                               int entryCount, boolean editable) {
    }

    public record SubmitRequest(String comment) {
    }

    public record ReviewRequest(String comment) {
    }

    /** Ligne du tableau de suivi PM / Head of Office : qui a soumis, qui manque. */
    public record TeamStatusDto(UserDto consultant, ReportDto report, int workedDays, int entryCount) {
    }

    // ---- GitLab
    public record GitStatusDto(boolean enabled, String gitlabUrl, String taskPrefix, Instant lastRunAt,
                               String lastError, int syncedRepos, int newCommits) {
    }

    public record GitRepoDto(Long id, String path, String webUrl, Long projectId, String projectName, Instant lastSyncedAt) {
        public static GitRepoDto of(GitRepo r) {
            Project p = r.getProject();
            return new GitRepoDto(r.getId(), r.getPathWithNamespace(), r.getWebUrl(), p == null ? null : p.getId(),
                    p == null ? null : p.getName(), r.getLastSyncedAt());
        }
    }

    public record GitRepoRequest(Long projectId) {
    }

    public record GitCommitDto(Long id, String shortSha, String title, String repo, String webUrl, Instant authoredAt,
                               LocalDate workDate, String authorName, String authorEmail, Long consultantId,
                               String consultantName, Long taskId, String taskRef, String taskTitle, GitLinkSource linkSource) {
        public static GitCommitDto of(GitCommit c, String taskRef) {
            AppUser u = c.getConsultant();
            Task t = c.getTask();
            return new GitCommitDto(c.getId(), c.getSha().substring(0, Math.min(8, c.getSha().length())), c.getTitle(),
                    c.getRepo().getPathWithNamespace(), c.getWebUrl(), c.getAuthoredAt(), c.getWorkDate(),
                    c.getAuthorName(), c.getAuthorEmail(), u == null ? null : u.getId(), u == null ? null : u.getFullName(),
                    t == null ? null : t.getId(), taskRef, t == null ? null : t.getTitle(), c.getLinkSource());
        }
    }

    public record GitMergeRequestDto(Long id, long iid, String title, String repo, String sourceBranch, String state,
                                     String webUrl, String authorName, Instant updatedAt, Instant mergedAt) {
        public static GitMergeRequestDto of(GitMergeRequest m) {
            return new GitMergeRequestDto(m.getId(), m.getIid(), m.getTitle(), m.getRepo().getPathWithNamespace(),
                    m.getSourceBranch(), m.getState(), m.getWebUrl(),
                    m.getConsultant() == null ? m.getAuthorUsername() : m.getConsultant().getFullName(),
                    m.getUpdatedAt(), m.getMergedAt());
        }
    }

    /** Activité GitLab d'une tâche ; branchName : nom de branche proposé au consultant. */
    public record TaskGitDto(String ref, String branchName, List<GitCommitDto> commits, List<GitMergeRequestDto> mergeRequests) {
    }

    /** Commits d'une journée pas encore reportés dans la fiche de temps, proposés en une ligne. */
    public record GitSuggestionDto(LocalDate date, Long projectId, String projectName, Long taskId, String taskRef,
                                   String taskTitle, String description, List<GitCommitDto> commits) {
    }

    /** Ligne consultant × projet du tableau d'activité. */
    public record GitActivityRowDto(Long consultantId, String consultantName, Long projectId, String projectName,
                                    int commits, int linkedCommits, int tasks, int commitDays, int declaredDays,
                                    int undeclaredDays, Instant lastCommitAt) {
    }

    public record GitAuthorDto(String email, String name, int commits) {
    }

    public record GitActivityDto(LocalDate from, LocalDate to, String periodLabel, List<GitActivityRowDto> rows,
                                 List<TaskDto> tasksWithoutCommits, List<GitCommitDto> unlinkedCommits,
                                 List<GitAuthorDto> unknownAuthors) {
    }
}
