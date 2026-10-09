package sn.yas.itfactory.timesheet.service;

import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import sn.yas.itfactory.timesheet.domain.*;
import sn.yas.itfactory.timesheet.repository.AppUserRepository;
import sn.yas.itfactory.timesheet.repository.GitCommitRepository;
import sn.yas.itfactory.timesheet.repository.GitMergeRequestRepository;
import sn.yas.itfactory.timesheet.repository.ProjectRepository;
import sn.yas.itfactory.timesheet.repository.TaskActivityRepository;
import sn.yas.itfactory.timesheet.repository.TaskRepository;
import sn.yas.itfactory.timesheet.repository.WorkEntryRepository;
import sn.yas.itfactory.timesheet.security.UserPrincipal;
import sn.yas.itfactory.timesheet.web.ApiException;
import sn.yas.itfactory.timesheet.web.dto.Dtos.*;

import java.time.Instant;
import java.time.LocalDate;
import java.util.*;

@Service
@Transactional
public class TaskService {

    private final TaskRepository tasks;
    private final WorkEntryRepository entries;
    private final ProjectRepository projects;
    private final AppUserRepository users;
    private final CurrentUser current;
    private final MailService mail;
    private final GitCommitRepository commits;
    private final GitMergeRequestRepository mergeRequests;
    private final TaskRefs refs;
    private final TaskActivityRepository activities;
    private final TaskHistory history;

    public TaskService(TaskRepository tasks, WorkEntryRepository entries, ProjectRepository projects,
                       AppUserRepository users, CurrentUser current, MailService mail,
                       GitCommitRepository commits, GitMergeRequestRepository mergeRequests, TaskRefs refs,
                       TaskActivityRepository activities, TaskHistory history) {
        this.tasks = tasks;
        this.entries = entries;
        this.projects = projects;
        this.users = users;
        this.current = current;
        this.mail = mail;
        this.commits = commits;
        this.mergeRequests = mergeRequests;
        this.refs = refs;
        this.activities = activities;
        this.history = history;
    }

    /**
     * scope=mine : mes tâches ; scope=team : celles de mon équipe (PM / Head of Office) ;
     * scope=projects : toutes les tâches des projets dont je suis lead dev ou architecte.
     */
    @Transactional(readOnly = true)
    public List<TaskDto> search(String scope, Long assigneeId, TaskStatus status, Long projectId) {
        Specification<Task> spec;
        if ("projects".equals(scope)) {
            List<Long> led = projects.idsLedBy(current.principal().id());
            spec = (root, q, cb) -> led.isEmpty() ? cb.disjunction() : root.get("project").get("id").in(led);
            if (assigneeId != null) {
                spec = spec.and((root, q, cb) -> cb.equal(root.get("assignee").get("id"), assigneeId));
            }
        } else {
            List<Long> ids = visibleIds(scope, assigneeId);
            spec = (root, q, cb) -> root.get("assignee").get("id").in(ids);
        }
        if (status != null) {
            spec = spec.and((root, q, cb) -> cb.equal(root.get("status"), status));
        }
        if (projectId != null) {
            spec = spec.and((root, q, cb) -> cb.equal(root.get("project").get("id"), projectId));
        }
        return toDtos(tasks.findAll(spec).stream()
                .sorted(Comparator.comparing((Task t) -> t.getStatus() == TaskStatus.TERMINEE)
                        .thenComparing(Task::getPriority, Comparator.reverseOrder())
                        .thenComparing(Task::getDueDate, Comparator.nullsLast(Comparator.naturalOrder()))
                        .thenComparing(Task::getId, Comparator.reverseOrder()))
                .toList());
    }

    /** Consultants dont on lit les tâches : soi, un consultant précis, ou toute l'équipe. */
    List<Long> visibleIds(String scope, Long assigneeId) {
        if (assigneeId != null) {
            return List.of(current.requireVisible(assigneeId).getId());
        }
        if ("team".equals(scope) && current.principal().isManager()) {
            return current.team().stream().map(AppUser::getId).toList();
        }
        return List.of(current.principal().id());
    }

    public TaskDto create(TaskRequest req) {
        AppUser me = current.user();
        Project project = project(req.projectId());
        AppUser assignee = req.assigneeId() == null ? me : assignable(project, req.assigneeId());
        Task t = new Task();
        t.setCreatedBy(me);
        apply(t, req, project, assignee);
        startEstimation(t);
        tasks.save(t);
        history.log(t, me, TaskActivityKind.CREATION, "Tâche créée et assignée à " + assignee.getFullName()
                + (t.isActive() ? "" : " (à estimer)"));
        if (!Objects.equals(assignee.getId(), me.getId())) {
            notifyAssignee(t, me);
        }
        return toDto(t);
    }

    public TaskDto update(Long id, TaskRequest req) {
        Task t = editable(id);
        AppUser me = current.user();
        AppUser before = t.getAssignee();
        Project project = project(req.projectId());
        AppUser assignee = req.assigneeId() == null || Objects.equals(req.assigneeId(), before.getId())
                ? before : assignable(project, req.assigneeId());
        List<String> changes = describeChanges(t, req);
        TaskStatus beforeStatus = t.getStatus();
        apply(t, req, project, assignee);
        if (!changes.isEmpty()) {
            history.log(t, me, TaskActivityKind.MODIFICATION, String.join(" · ", changes));
        }
        List<String> notified = new ArrayList<>(changes);
        if (beforeStatus != t.getStatus()) {
            notified.add("statut " + TaskHistory.STATUS.get(beforeStatus) + " → " + TaskHistory.STATUS.get(t.getStatus()));
        }
        if (!notified.isEmpty()) {
            notifyParticipants(t, me, "Tâche modifiée : " + refs.of(t.getId()) + " · " + t.getTitle(),
                    "a modifié la tâche : " + escape(String.join(" · ", notified)) + ".", null);
        }
        if (!Objects.equals(before.getId(), assignee.getId())) {
            if (!Objects.equals(before.getId(), me.getId())) {
                mail.send(List.of(before.getEmail()), List.of(), "Tâche réassignée : " + t.getTitle(),
                        "<p>Bonjour " + before.getFirstName() + ",</p><p>" + me.getFullName() + " a réassigné la tâche <b>"
                                + refs.of(t.getId()) + " · " + escape(t.getTitle()) + "</b> à " + assignee.getFullName() + ".</p>", List.of());
            }
            history.log(t, me, TaskActivityKind.ASSIGNATION, before.getFullName() + " → " + assignee.getFullName());
            // Nouveau développeur : il estime à son tour.
            startEstimation(t);
            if (!Objects.equals(assignee.getId(), me.getId())) {
                notifyAssignee(t, me);
            }
        }
        return toDto(t);
    }

    public TaskDto changeStatus(Long id, TaskStatus status) {
        Task t = visible(id);
        requireActive(t, status);
        TaskStatus before = t.getStatus();
        AppUser me = current.user();
        history.changeStatus(t, status, me);
        if (before != status) {
            notifyParticipants(t, me, "Statut " + TaskHistory.STATUS.get(status) + " : " + refs.of(t.getId()) + " · " + t.getTitle(),
                    "a passé la tâche de « " + TaskHistory.STATUS.get(before) + " » à <b>« " + TaskHistory.STATUS.get(status) + " »</b>.", null);
        }
        return toDto(t);
    }

    // ---- Estimation : le développeur propose une durée, le lead dev l'accorde et la tâche devient active.

    public TaskDto proposeEstimate(Long id, double days, String comment) {
        Task t = find(id);
        AppUser me = current.user();
        if (!Objects.equals(t.getAssignee().getId(), me.getId())) {
            throw ApiException.forbidden();
        }
        if (t.isActive()) {
            throw ApiException.badRequest("L'estimation de cette tâche est déjà accordée");
        }
        t.setProposedEstimateDays(days);
        t.setEstimateStatus(EstimateStatus.PROPOSEE);
        history.log(t, me, TaskActivityKind.ESTIMATION, "Durée proposée : " + fmt(days) + " j" + note(comment));
        AppUser approver = approver(t);
        if (approver != null && !Objects.equals(approver.getId(), me.getId())) {
            mail.send(List.of(approver.getEmail()), List.of(), "Estimation à valider : " + refs.of(t.getId()) + " · " + t.getTitle(),
                    "<p>" + me.getFullName() + " estime la tâche <b>" + refs.of(t.getId()) + " · " + escape(t.getTitle())
                            + "</b> (" + escape(t.getProject().getName()) + ") à <b>" + fmt(days) + " jour(s)</b>.</p>"
                            + noteHtml(comment) + "<p>Accordez ou refusez l'estimation dans « Tâches ».</p>", List.of());
        }
        return toDto(t);
    }

    /** Accorde la durée proposée (ou une durée ajustée) : la tâche devient active pour le développeur. */
    public TaskDto approveEstimate(Long id, Double days, String comment) {
        Task t = find(id);
        AppUser me = current.user();
        if (!canApprove(t)) {
            throw ApiException.forbidden();
        }
        if (t.getEstimateStatus() != EstimateStatus.PROPOSEE) {
            throw ApiException.badRequest("Aucune estimation en attente sur cette tâche");
        }
        double granted = days != null ? days : t.getProposedEstimateDays();
        t.setEstimateDays(granted);
        t.setEstimateStatus(EstimateStatus.ACCORDEE);
        if (t.getStartDate() == null) {
            t.setStartDate(LocalDate.now());
        }
        boolean adjusted = !Objects.equals(granted, t.getProposedEstimateDays());
        history.log(t, me, TaskActivityKind.ESTIMATION, "Estimation accordée : " + fmt(granted) + " j"
                + (adjusted ? " (proposé : " + fmt(t.getProposedEstimateDays()) + " j)" : "") + " · tâche active" + note(comment));
        notifyEstimate(t, me, "Tâche active : " + t.getTitle(),
                "a accordé votre estimation de <b>" + fmt(granted) + " jour(s)</b>. La tâche est maintenant active.", comment);
        return toDto(t);
    }

    /** Renvoie l'estimation au développeur avec un commentaire. */
    public TaskDto rejectEstimate(Long id, String comment) {
        if (comment == null || comment.isBlank()) {
            throw ApiException.badRequest("Indiquez la raison du refus");
        }
        Task t = find(id);
        AppUser me = current.user();
        if (!canApprove(t)) {
            throw ApiException.forbidden();
        }
        if (t.getEstimateStatus() != EstimateStatus.PROPOSEE) {
            throw ApiException.badRequest("Aucune estimation en attente sur cette tâche");
        }
        Double proposed = t.getProposedEstimateDays();
        t.setEstimateStatus(EstimateStatus.A_ESTIMER);
        t.setProposedEstimateDays(null);
        history.log(t, me, TaskActivityKind.ESTIMATION, "Estimation de " + fmt(proposed) + " j refusée" + note(comment));
        notifyEstimate(t, me, "Estimation à revoir : " + t.getTitle(),
                "a refusé votre estimation de " + fmt(proposed) + " jour(s). Merci d'en proposer une nouvelle.", comment);
        return toDto(t);
    }

    /**
     * Circuit d'estimation : sur un projet qui a un lead dev, une tâche confiée à un développeur
     * (autre que le lead dev) doit être estimée par lui puis accordée avant d'être active.
     */
    private void startEstimation(Task t) {
        Project p = t.getProject();
        boolean needed = p.getLeadDev() != null && !p.isLeadDev(t.getAssignee().getId());
        t.setProposedEstimateDays(null);
        if (!needed) {
            t.setEstimateStatus(null);
            return;
        }
        t.setEstimateStatus(EstimateStatus.A_ESTIMER);
        if (t.getStatus() != TaskStatus.A_FAIRE) {
            history.changeStatus(t, TaskStatus.A_FAIRE, t.getId() == null ? null : current.user());
        }
        t.setProgress(0);
    }

    private void requireActive(Task t, TaskStatus status) {
        if (!t.isActive() && status != null && status != TaskStatus.A_FAIRE) {
            throw ApiException.badRequest("Tâche pas encore active : le développeur l'estime, puis le lead dev accorde l'estimation");
        }
    }

    /** Valide les estimations : le lead dev du projet, sinon un manager qui suit le développeur. */
    private boolean canApprove(Task t) {
        UserPrincipal me = current.principal();
        return t.getProject().isLeadDev(me.id()) || (me.isManager() && current.canSee(t.getAssignee()));
    }

    private AppUser approver(Task t) {
        return t.getProject().getLeadDev() != null ? t.getProject().getLeadDev() : t.getCreatedBy();
    }

    private void notifyEstimate(Task t, AppUser by, String subject, String what, String comment) {
        AppUser dev = t.getAssignee();
        if (Objects.equals(dev.getId(), by.getId())) {
            return;
        }
        mail.send(List.of(dev.getEmail()), List.of(), subject,
                "<p>Bonjour " + dev.getFirstName() + ",</p><p>" + by.getFullName() + " " + what + "</p><p>Tâche <b>"
                        + refs.of(t.getId()) + " · " + escape(t.getTitle()) + "</b> (" + escape(t.getProject().getName())
                        + ").</p>" + noteHtml(comment), List.of());
    }

    /**
     * Prévient les personnes concernées par une tâche (assigné, créateur, lead dev du projet, manager direct
     * de l'assigné), sauf l'auteur de l'action.
     */
    private void notifyParticipants(Task t, AppUser by, String subject, String what, String comment) {
        Set<String> to = new LinkedHashSet<>();
        for (AppUser u : new AppUser[]{t.getAssignee(), t.getCreatedBy(), t.getProject().getLeadDev(), t.getAssignee().getPm()}) {
            if (u != null && u.isActive() && !Objects.equals(u.getId(), by.getId())) {
                to.add(u.getEmail());
            }
        }
        if (to.isEmpty()) {
            return;
        }
        mail.send(List.copyOf(to), List.of(), subject,
                "<p>Bonjour,</p><p>" + by.getFullName() + " " + what + "</p><p>Tâche <b>" + refs.of(t.getId()) + " · "
                        + escape(t.getTitle()) + "</b> (" + escape(t.getProject().getName()) + "), assignée à "
                        + t.getAssignee().getFullName() + ".</p>" + noteHtml(comment), List.of());
    }

    private static String note(String comment) {
        return comment == null || comment.isBlank() ? "" : " — " + comment.trim();
    }

    private static String noteHtml(String comment) {
        return comment == null || comment.isBlank() ? ""
                : "<blockquote>" + escape(comment.trim()).replace("\n", "<br>") + "</blockquote>";
    }

    /**
     * Qui peut recevoir une tâche : le lead dev d'un projet assigne à l'équipe du projet (développeurs, architecte,
     * lui-même) ; un PM / Head of Office à ses consultants.
     */
    private AppUser assignable(Project p, Long assigneeId) {
        Long me = current.principal().id();
        if (p.isLeadDev(me)) {
            AppUser u = users.findById(assigneeId).orElseThrow(() -> ApiException.notFound("Consultant"));
            if (p.isMember(u.getId()) || p.isLeadDev(u.getId()) || p.isArchitect(u.getId())) {
                return u;
            }
            if (!current.canSee(u)) {
                throw ApiException.badRequest(u.getFullName() + " ne fait pas partie de l'équipe du projet " + p.getName());
            }
            return u;
        }
        return current.requireVisible(assigneeId);
    }

    private Project project(Long id) {
        return projects.findById(id).orElseThrow(() -> ApiException.notFound("Projet"));
    }

    public void delete(Long id) {
        Task t = editable(id);
        notifyParticipants(t, current.user(), "Tâche supprimée : " + refs.of(t.getId()) + " · " + t.getTitle(),
                "a supprimé la tâche.", null);
        entries.detachTask(t.getId());
        commits.detachTask(t.getId());
        mergeRequests.detachTask(t.getId());
        activities.deleteByTask(t.getId());
        tasks.delete(t);
    }

    // ---- Fil d'activité

    @Transactional(readOnly = true)
    public List<TaskActivityDto> activity(Long id) {
        visible(id);
        return activities.findByTaskIdOrderByCreatedAtAscIdAsc(id).stream().map(TaskActivityDto::of).toList();
    }

    /** Commentaire ; l'assigné et le créateur sont prévenus par e-mail (sauf l'auteur). */
    public TaskActivityDto comment(Long id, String text) {
        Task t = visible(id);
        AppUser me = current.user();
        TaskActivity a = history.log(t, me, TaskActivityKind.COMMENTAIRE, text.trim());
        Set<String> to = new LinkedHashSet<>();
        for (AppUser u : new AppUser[]{t.getAssignee(), t.getCreatedBy()}) {
            if (u != null && !Objects.equals(u.getId(), me.getId())) {
                to.add(u.getEmail());
            }
        }
        if (!to.isEmpty()) {
            mail.send(List.copyOf(to), List.of(), "Commentaire sur " + refs.of(t.getId()) + " · " + t.getTitle(),
                    "<p>" + me.getFullName() + " a commenté la tâche <b>" + escape(t.getTitle()) + "</b> :</p>"
                            + "<blockquote>" + escape(text.trim()).replace("\n", "<br>") + "</blockquote>", List.of());
        }
        return TaskActivityDto.of(a);
    }

    /** Seul l'auteur supprime son commentaire. */
    public void deleteComment(Long taskId, Long activityId) {
        TaskActivity a = activities.findById(activityId)
                .filter(x -> Objects.equals(x.getTask().getId(), taskId) && x.getKind() == TaskActivityKind.COMMENTAIRE)
                .orElseThrow(() -> ApiException.notFound("Commentaire"));
        if (a.getAuthor() == null || !Objects.equals(a.getAuthor().getId(), current.principal().id())) {
            throw ApiException.forbidden();
        }
        activities.delete(a);
    }

    /** Tâches avec leur référence TS-42, commits GitLab, jours déclarés et nombre de commentaires. */
    @Transactional(readOnly = true)
    public List<TaskDto> toDtos(List<Task> list) {
        if (list.isEmpty()) {
            return List.of();
        }
        List<Long> ids = list.stream().map(Task::getId).toList();
        Map<Long, Object[]> stats = new HashMap<>();
        commits.statsByTask(ids).forEach(row -> stats.put((Long) row[0], row));
        Map<Long, Long> spent = counts(entries.spentDaysByTask(ids));
        Map<Long, Long> comments = counts(activities.commentCounts(ids));
        UserPrincipal me = current.principalOrNull();
        return list.stream().map(t -> {
            Object[] st = stats.get(t.getId());
            return TaskDto.of(t, refs.of(t.getId()), st == null ? 0 : (Long) st[1], st == null ? null : (Instant) st[2],
                    spent.getOrDefault(t.getId(), 0L), comments.getOrDefault(t.getId(), 0L), rights(t, me));
        }).toList();
    }

    private TaskRights rights(Task t, UserPrincipal me) {
        if (me == null) {
            return TaskRights.NONE;
        }
        boolean assignee = Objects.equals(t.getAssignee().getId(), me.id());
        return new TaskRights(canEdit(t, me), assignee && !t.isActive(),
                t.getEstimateStatus() == EstimateStatus.PROPOSEE && canApprove(t));
    }

    private static Map<Long, Long> counts(List<Object[]> rows) {
        Map<Long, Long> m = new HashMap<>();
        rows.forEach(r -> m.put((Long) r[0], ((Number) r[1]).longValue()));
        return m;
    }

    private TaskDto toDto(Task t) {
        return toDtos(List.of(t)).get(0);
    }

    private void apply(Task t, TaskRequest req, Project project, AppUser assignee) {
        if (req.startDate() != null && req.dueDate() != null && req.dueDate().isBefore(req.startDate())) {
            throw ApiException.badRequest("L'échéance doit suivre la date de début");
        }
        if (t.getId() != null) {
            requireActive(t, req.status());
        }
        t.setTitle(req.title().trim());
        t.setDescription(req.description());
        t.setProject(project);
        t.setAssignee(assignee);
        t.setStartDate(req.startDate());
        t.setDueDate(req.dueDate());
        t.setPriority(req.priority() == null ? TaskPriority.NORMALE : req.priority());
        t.setEstimateDays(req.estimateDays());
        t.setProgress(req.progress());
        if (req.status() != null) {
            history.changeStatus(t, req.status(), t.getId() == null ? null : current.user());
        }
    }

    /** Résumé lisible des champs modifiés (le statut et l'assignation sont tracés à part). */
    private List<String> describeChanges(Task t, TaskRequest req) {
        List<String> out = new ArrayList<>();
        if (!t.getTitle().equals(req.title().trim())) {
            out.add("titre modifié");
        }
        if (!Objects.equals(t.getDescription(), req.description())) {
            out.add("description modifiée");
        }
        if (!Objects.equals(t.getProject().getId(), req.projectId())) {
            out.add("projet changé");
        }
        TaskPriority p = req.priority() == null ? TaskPriority.NORMALE : req.priority();
        if (t.getPriority() != p) {
            out.add("priorité " + TaskHistory.PRIORITY.get(t.getPriority()) + " → " + TaskHistory.PRIORITY.get(p));
        }
        if (!Objects.equals(t.getStartDate(), req.startDate())) {
            out.add("début " + day(req.startDate()));
        }
        if (!Objects.equals(t.getDueDate(), req.dueDate())) {
            out.add("échéance " + day(t.getDueDate()) + " → " + day(req.dueDate()));
        }
        if (!Objects.equals(t.getEstimateDays(), req.estimateDays())) {
            out.add("charge estimée " + (req.estimateDays() == null ? "retirée" : fmt(req.estimateDays()) + " j"));
        }
        int progress = req.progress() == null ? 0 : req.progress();
        if (req.status() != TaskStatus.TERMINEE && t.getStatus() != TaskStatus.TERMINEE && t.getProgress() != progress) {
            out.add("avancement " + t.getProgress() + " % → " + progress + " %");
        }
        return out;
    }

    private void notifyAssignee(Task t, AppUser by) {
        AppUser assignee = t.getAssignee();
        mail.send(List.of(assignee.getEmail()), List.of(), "Nouvelle tâche : " + t.getTitle(),
                "<p>Bonjour " + assignee.getFirstName() + ",</p><p>" + by.getFullName()
                        + " vous a assigné la tâche <b>" + refs.of(t.getId()) + " · " + escape(t.getTitle())
                        + "</b> (priorité " + TaskHistory.PRIORITY.get(t.getPriority()).toLowerCase() + ") sur le projet "
                        + t.getProject().getName() + (t.getDueDate() == null ? "" : ", échéance le " + t.getDueDate())
                        + ".</p>", List.of());
    }

    /** Modification complète : le créateur, le lead dev du projet, ou un manager qui voit l'assigné. */
    private Task editable(Long id) {
        Task t = find(id);
        if (!canEdit(t, current.principal())) {
            throw ApiException.forbidden();
        }
        return t;
    }

    private boolean canEdit(Task t, UserPrincipal me) {
        boolean creator = t.getCreatedBy() != null && Objects.equals(t.getCreatedBy().getId(), me.id());
        boolean manager = me.isManager() && current.canSee(t.getAssignee());
        return creator || manager || t.getProject().isLeadDev(me.id());
    }

    private Task visible(Long id) {
        Task t = find(id);
        if (!current.canSeeTask(t)) {
            throw ApiException.forbidden();
        }
        return t;
    }

    private Task find(Long id) {
        return tasks.findById(id).orElseThrow(() -> ApiException.notFound("Tâche"));
    }

    private static String day(LocalDate d) {
        return d == null ? "aucune" : String.format("%02d/%02d/%d", d.getDayOfMonth(), d.getMonthValue(), d.getYear());
    }

    private static String fmt(double v) {
        return v == Math.floor(v) ? String.valueOf((long) v) : String.valueOf(v).replace('.', ',');
    }

    private static String escape(String s) {
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }
}
