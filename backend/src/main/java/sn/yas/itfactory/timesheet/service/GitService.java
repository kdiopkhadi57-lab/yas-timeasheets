package sn.yas.itfactory.timesheet.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import sn.yas.itfactory.timesheet.config.AppProperties;
import sn.yas.itfactory.timesheet.domain.*;
import sn.yas.itfactory.timesheet.repository.*;
import sn.yas.itfactory.timesheet.web.ApiException;
import sn.yas.itfactory.timesheet.web.dto.Dtos.*;

import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

/** Lecture de l'activité GitLab : par tâche, suggestions de saisie, tableau de bord d'équipe. */
@Service
@Transactional(readOnly = true)
public class GitService {

    private final GitRepoRepository repos;
    private final GitCommitRepository commits;
    private final GitMergeRequestRepository mergeRequests;
    private final TaskRepository tasks;
    private final ProjectRepository projects;
    private final WorkEntryRepository entries;
    private final TaskService taskService;
    private final GitSyncService sync;
    private final CurrentUser current;
    private final TaskRefs refs;
    private final AppProperties.Gitlab config;

    public GitService(GitRepoRepository repos, GitCommitRepository commits, GitMergeRequestRepository mergeRequests,
                      TaskRepository tasks, ProjectRepository projects, WorkEntryRepository entries,
                      TaskService taskService, GitSyncService sync, CurrentUser current, TaskRefs refs,
                      AppProperties properties) {
        this.repos = repos;
        this.commits = commits;
        this.mergeRequests = mergeRequests;
        this.tasks = tasks;
        this.projects = projects;
        this.entries = entries;
        this.taskService = taskService;
        this.sync = sync;
        this.current = current;
        this.refs = refs;
        this.config = properties.gitlab();
    }

    public GitStatusDto status() {
        GitSyncService.Status s = sync.status();
        return new GitStatusDto(sync.enabled(), config.url(), refs.prefix(), s.lastRunAt(), s.lastError(),
                s.syncedRepos(), s.newCommits());
    }

    public List<GitRepoDto> listRepos() {
        return repos.findAllByOrderByPathWithNamespaceAsc().stream().map(GitRepoDto::of).toList();
    }

    @Transactional
    public GitRepoDto mapRepo(Long id, GitRepoRequest req) {
        GitRepo repo = repos.findById(id).orElseThrow(() -> ApiException.notFound("Dépôt"));
        repo.setProject(req.projectId() == null ? null
                : projects.findById(req.projectId()).orElseThrow(() -> ApiException.notFound("Projet")));
        return GitRepoDto.of(repo);
    }

    public TaskGitDto forTask(Long taskId) {
        Task t = tasks.findById(taskId).orElseThrow(() -> ApiException.notFound("Tâche"));
        if (!current.canSeeTask(t)) {
            throw ApiException.forbidden();
        }
        String ref = refs.of(t.getId());
        return new TaskGitDto(ref, "feature/" + ref + "-" + slug(t.getTitle()),
                commits.findByTaskIdOrderByAuthoredAtDesc(taskId).stream().map(this::dto).toList(),
                mergeRequests.findByTaskIdOrderByUpdatedAtDesc(taskId).stream().map(GitMergeRequestDto::of).toList());
    }

    /**
     * Commits du consultant sur la période qui n'ont pas encore de ligne dans sa fiche :
     * une proposition par jour et par tâche (ou par projet pour le travail non lié à une tâche).
     */
    public List<GitSuggestionDto> suggestions(LocalDate from, LocalDate to, Long consultantId) {
        AppUser consultant = current.requireVisible(consultantId);
        List<GitCommit> list = commits.findByConsultantIdInAndWorkDateBetweenOrderByAuthoredAtDesc(
                List.of(consultant.getId()), from, to);
        if (list.isEmpty()) {
            return List.of();
        }
        List<WorkEntry> declared = entries.findByConsultantIdAndWorkDateBetweenOrderByWorkDateAscIdAsc(consultant.getId(), from, to);
        Map<String, List<GitCommit>> groups = new LinkedHashMap<>();
        for (GitCommit c : list.reversed()) {
            Project p = c.getTask() != null ? c.getTask().getProject() : c.getRepo().getProject();
            if (p == null) {
                continue;
            }
            boolean covered = declared.stream().anyMatch(e -> e.getWorkDate().equals(c.getWorkDate())
                    && (c.getTask() != null
                    ? e.getTask() != null && e.getTask().getId().equals(c.getTask().getId())
                    : e.getProject().getId().equals(p.getId())));
            if (!covered) {
                String key = c.getWorkDate() + "|" + (c.getTask() != null ? "t" + c.getTask().getId() : "p" + p.getId());
                groups.computeIfAbsent(key, k -> new ArrayList<>()).add(c);
            }
        }
        return groups.values().stream().map(group -> {
            GitCommit first = group.get(0);
            Task t = first.getTask();
            Project p = t != null ? t.getProject() : first.getRepo().getProject();
            String description = group.stream().map(c -> refs.strip(c.getTitle())).filter(s -> !s.isBlank())
                    .distinct().collect(Collectors.joining("\n"));
            return new GitSuggestionDto(first.getWorkDate(), p.getId(), p.getName(), t == null ? null : t.getId(),
                    t == null ? null : refs.of(t.getId()), t == null ? null : t.getTitle(),
                    truncate(description, 2000), group.stream().map(this::dto).toList());
        }).sorted(Comparator.comparing(GitSuggestionDto::date)).toList();
    }

    /** Vue globale PM / Head of Office : activité par consultant et projet, et les écarts à traiter. */
    public GitActivityDto activity(ReportType type, LocalDate date, Long projectId) {
        Period period = Period.of(type, date);
        List<AppUser> team = current.team();
        Map<Long, AppUser> byId = team.stream().collect(Collectors.toMap(AppUser::getId, u -> u));
        List<GitCommit> list = commits.findByConsultantIdInAndWorkDateBetweenOrderByAuthoredAtDesc(
                byId.keySet(), period.start(), period.end()).stream()
                .filter(c -> projectId == null || projectId.equals(projectOf(c) == null ? null : projectOf(c).getId()))
                .toList();

        // Jours déclarés dans la fiche, par consultant et projet.
        Map<Long, Map<Long, Set<LocalDate>>> declared = new HashMap<>();
        Map<Long, Set<LocalDate>> declaredAny = new HashMap<>();
        for (AppUser u : team) {
            for (WorkEntry e : entries.findByConsultantIdAndWorkDateBetweenOrderByWorkDateAscIdAsc(u.getId(), period.start(), period.end())) {
                declared.computeIfAbsent(u.getId(), k -> new HashMap<>())
                        .computeIfAbsent(e.getProject().getId(), k -> new HashSet<>()).add(e.getWorkDate());
                declaredAny.computeIfAbsent(u.getId(), k -> new HashSet<>()).add(e.getWorkDate());
            }
        }

        Map<String, List<GitCommit>> byRow = new LinkedHashMap<>();
        for (GitCommit c : list) {
            Project p = projectOf(c);
            byRow.computeIfAbsent(c.getConsultant().getId() + "|" + (p == null ? "" : p.getId()), k -> new ArrayList<>()).add(c);
        }
        List<GitActivityRowDto> rows = byRow.values().stream().map(group -> {
            GitCommit first = group.get(0);
            AppUser u = first.getConsultant();
            Project p = projectOf(first);
            Set<LocalDate> days = group.stream().map(GitCommit::getWorkDate).collect(Collectors.toSet());
            Set<LocalDate> onProject = p == null ? Set.of()
                    : declared.getOrDefault(u.getId(), Map.of()).getOrDefault(p.getId(), Set.of());
            Set<LocalDate> any = declaredAny.getOrDefault(u.getId(), Set.of());
            return new GitActivityRowDto(u.getId(), u.getFullName(), p == null ? null : p.getId(),
                    p == null ? "—" : p.getName(), group.size(),
                    (int) group.stream().filter(c -> c.getTask() != null).count(),
                    (int) group.stream().filter(c -> c.getTask() != null).map(c -> c.getTask().getId()).distinct().count(),
                    days.size(), onProject.size(), (int) days.stream().filter(d -> !any.contains(d)).count(),
                    first.getAuthoredAt());
        }).sorted(Comparator.comparing(GitActivityRowDto::consultantName).thenComparing(GitActivityRowDto::projectName)).toList();

        // Tâches « En cours » sur un projet suivi dans GitLab, sans aucun commit sur la période.
        Set<Long> trackedProjects = repos.findByProjectIsNotNull().stream().map(r -> r.getProject().getId()).collect(Collectors.toSet());
        Set<Long> activeTasks = list.stream().filter(c -> c.getTask() != null).map(c -> c.getTask().getId()).collect(Collectors.toSet());
        List<Task> idle = byId.isEmpty() ? List.of() : tasks.findAll((root, q, cb) -> cb.and(
                root.get("assignee").get("id").in(byId.keySet()),
                cb.equal(root.get("status"), TaskStatus.EN_COURS))).stream()
                .filter(t -> trackedProjects.contains(t.getProject().getId()) && !activeTasks.contains(t.getId()))
                .filter(t -> projectId == null || projectId.equals(t.getProject().getId()))
                .sorted(Comparator.comparing((Task t) -> t.getAssignee().getLastName()).thenComparing(Task::getId))
                .toList();

        List<GitCommitDto> unlinked = list.stream().filter(c -> c.getTask() == null).limit(100).map(this::dto).toList();

        List<GitAuthorDto> unknown = List.of();
        if (current.principal().seesEveryone()) {
            Map<String, List<GitCommit>> byEmail = commits.findByConsultantIsNullAndWorkDateBetween(period.start(), period.end())
                    .stream().collect(Collectors.groupingBy(c -> Objects.toString(c.getAuthorEmail(), "?")));
            unknown = byEmail.entrySet().stream()
                    .map(e -> new GitAuthorDto(e.getKey(), e.getValue().get(0).getAuthorName(), e.getValue().size()))
                    .sorted(Comparator.comparing(GitAuthorDto::commits).reversed()).toList();
        }
        return new GitActivityDto(period.start(), period.end(), period.label(), rows, taskService.toDtos(idle), unlinked, unknown);
    }

    private GitCommitDto dto(GitCommit c) {
        return GitCommitDto.of(c, c.getTask() == null ? null : refs.of(c.getTask().getId()));
    }

    private static Project projectOf(GitCommit c) {
        return c.getTask() != null ? c.getTask().getProject() : c.getRepo().getProject();
    }

    static String slug(String title) {
        String s = java.text.Normalizer.normalize(title, java.text.Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "").toLowerCase().replaceAll("[^a-z0-9]+", "-").replaceAll("(^-|-$)", "");
        return s.length() > 40 ? s.substring(0, 40).replaceAll("-$", "") : s;
    }

    private static String truncate(String s, int max) {
        return s.length() <= max ? s : s.substring(0, max);
    }
}
