package sn.yas.itfactory.timesheet.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import sn.yas.itfactory.timesheet.config.AppProperties;
import sn.yas.itfactory.timesheet.domain.*;
import sn.yas.itfactory.timesheet.gitlab.GitLabApi;
import sn.yas.itfactory.timesheet.repository.*;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.regex.Pattern;

/**
 * Synchronisation GitLab, toutes les 15 minutes :
 * 1. découverte des dépôts accessibles au jeton (rattachement automatique au projet de même nom) ;
 * 2. pour chaque dépôt rattaché à un projet : commits, merge requests et branches depuis la dernière synchro ;
 * 3. liaison commit → tâche par la référence TS-42 (message, puis MR, puis branche) et commit → consultant par e-mail.
 */
@Service
public class GitSyncService {

    private static final Logger log = LoggerFactory.getLogger(GitSyncService.class);
    private static final ZoneId DAKAR = ZoneId.of("Africa/Dakar");
    private static final Pattern MERGE_COMMIT = Pattern.compile("^Merge (branch|remote-tracking branch|.+ into ).*", Pattern.DOTALL);

    private final GitLabApi api;
    private final AppProperties.Gitlab config;
    private final GitRepoRepository repos;
    private final GitCommitRepository commits;
    private final GitMergeRequestRepository mergeRequests;
    private final TaskRepository tasks;
    private final ProjectRepository projects;
    private final AppUserRepository users;
    private final TaskRefs refs;
    private final TransactionTemplate tx;

    private volatile Status status = new Status(null, null, 0, 0);

    public GitSyncService(GitLabApi api, AppProperties properties, GitRepoRepository repos, GitCommitRepository commits,
                          GitMergeRequestRepository mergeRequests, TaskRepository tasks, ProjectRepository projects,
                          AppUserRepository users, TaskRefs refs, TransactionTemplate tx) {
        this.api = api;
        this.config = properties.gitlab();
        this.repos = repos;
        this.commits = commits;
        this.mergeRequests = mergeRequests;
        this.tasks = tasks;
        this.projects = projects;
        this.users = users;
        this.refs = refs;
        this.tx = tx;
    }

    public record Status(Instant lastRunAt, String lastError, int syncedRepos, int newCommits) {
    }

    public boolean enabled() {
        return config != null && config.enabled();
    }

    public Status status() {
        return status;
    }

    @Scheduled(cron = "${app.schedule.gitlab-sync}", zone = "Africa/Dakar")
    public void scheduledSync() {
        if (enabled()) {
            syncAll();
        }
    }

    public synchronized Status syncAll() {
        List<String> errors = new ArrayList<>();
        int synced = 0;
        int created = 0;
        try {
            discover();
        } catch (RuntimeException e) {
            log.warn("GitLab : découverte des dépôts impossible", e);
            errors.add("Découverte des dépôts : " + e.getMessage());
        }
        for (GitRepo repo : repos.findByProjectIsNotNull()) {
            if (repo.getGitlabId() < 0) {
                continue; // dépôt de démonstration
            }
            try {
                created += syncRepo(repo.getId());
                synced++;
            } catch (RuntimeException e) {
                log.warn("GitLab : synchronisation de {} impossible", repo.getPathWithNamespace(), e);
                errors.add(repo.getPathWithNamespace() + " : " + e.getMessage());
            }
        }
        status = new Status(Instant.now(), errors.isEmpty() ? null : String.join(" ; ", errors), synced, created);
        log.info("GitLab : {} dépôt(s) synchronisé(s), {} nouveau(x) commit(s)", synced, created);
        return status;
    }

    /** Enregistre les dépôts visibles et rattache ceux dont le groupe porte le nom d'un projet (numflex → NumFlex). */
    void discover() {
        List<String> groups = config.groups() == null ? List.of()
                : config.groups().stream().map(String::trim).filter(g -> !g.isEmpty()).toList();
        List<GitLabApi.Project> found = api.projects(groups);
        tx.executeWithoutResult(s -> {
            Map<String, Project> byName = new HashMap<>();
            projects.findAll().forEach(p -> byName.put(normalize(p.getName()), p));
            for (GitLabApi.Project gp : found) {
                GitRepo repo = repos.findByGitlabId(gp.id()).orElseGet(GitRepo::new);
                boolean isNew = repo.getId() == null;
                repo.setGitlabId(gp.id());
                repo.setPathWithNamespace(gp.pathWithNamespace());
                repo.setWebUrl(gp.webUrl());
                repo.setDefaultBranch(gp.defaultBranch());
                if (isNew && gp.namespace() != null) {
                    String top = gp.namespace().fullPath() == null ? gp.namespace().name() : gp.namespace().fullPath().split("/")[0];
                    repo.setProject(byName.get(normalize(top)));
                }
                repos.save(repo);
            }
        });
    }

    /** @return le nombre de commits nouvellement enregistrés. */
    int syncRepo(Long repoId) {
        GitRepo repo = repos.findById(repoId).orElseThrow();
        Instant startedAt = Instant.now();
        OffsetDateTime since = (repo.getLastSyncedAt() == null
                ? startedAt.minus(config.initialDays(), ChronoUnit.DAYS)
                : repo.getLastSyncedAt().minus(1, ChronoUnit.DAYS)).atOffset(ZoneOffset.UTC);
        long gid = repo.getGitlabId();

        // Appels GitLab hors transaction.
        List<GitLabApi.Commit> recent = api.commits(gid, since);
        Map<GitLabApi.MergeRequest, List<GitLabApi.Commit>> linkedMrs = new LinkedHashMap<>();
        for (GitLabApi.MergeRequest mr : api.mergeRequests(gid, since)) {
            if (refs.find(mr.title(), mr.sourceBranch()).isPresent()) {
                linkedMrs.put(mr, api.mergeRequestCommits(gid, mr.iid()));
            }
        }
        Set<String> mrBranches = new HashSet<>();
        linkedMrs.keySet().forEach(mr -> mrBranches.add(mr.sourceBranch()));
        Map<String, List<GitLabApi.Commit>> linkedBranches = new LinkedHashMap<>();
        if (repo.getDefaultBranch() != null) {
            for (GitLabApi.Branch b : api.branches(gid)) {
                if (refs.find(b.name()).isPresent() && !mrBranches.contains(b.name())
                        && !mergeRequests.existsByRepoAndSourceBranch(repo, b.name())) {
                    linkedBranches.put(b.name(), api.compare(gid, repo.getDefaultBranch(), b.name()));
                }
            }
        }

        Integer created = tx.execute(s -> {
            GitRepo r = repos.findById(repoId).orElseThrow();
            Linker linker = new Linker(r);
            recent.forEach(linker::upsert);
            linkedMrs.forEach((mr, mrCommits) -> {
                Task task = linker.task(refs.find(mr.title(), mr.sourceBranch()).orElseThrow());
                GitMergeRequest m = mergeRequests.findByRepoAndIid(r, mr.iid()).orElseGet(GitMergeRequest::new);
                m.setRepo(r);
                m.setIid(mr.iid());
                m.setTitle(truncate(mr.title(), 500));
                m.setSourceBranch(mr.sourceBranch());
                m.setState(mr.state());
                m.setWebUrl(mr.webUrl());
                m.setUpdatedAt(mr.updatedAt() == null ? null : mr.updatedAt().toInstant());
                m.setMergedAt(mr.mergedAt() == null ? null : mr.mergedAt().toInstant());
                if (mr.author() != null) {
                    m.setAuthorUsername(mr.author().username());
                    m.setConsultant(linker.user(mr.author().username()));
                }
                m.setTask(task);
                mergeRequests.save(m);
                mrCommits.forEach(c -> linker.link(linker.upsert(c), task, GitLinkSource.MERGE_REQUEST));
            });
            linkedBranches.forEach((branch, branchCommits) -> {
                Task task = linker.task(refs.find(branch).orElseThrow());
                branchCommits.forEach(c -> linker.link(linker.upsert(c), task, GitLinkSource.BRANCHE));
            });
            r.setLastSyncedAt(startedAt);
            return linker.created;
        });
        return created == null ? 0 : created;
    }

    /** Rattache les commits déjà enregistrés aux identités Git d'un utilisateur (après modification de son compte). */
    public void assignAuthor(AppUser user) {
        Set<String> emails = new HashSet<>();
        identities(user).stream().filter(i -> i.contains("@")).forEach(emails::add);
        if (!emails.isEmpty()) {
            commits.assignAuthor(user, emails);
        }
    }

    /** E-mail du compte + identités Git déclarées, en minuscules. */
    public static Set<String> identities(AppUser u) {
        Set<String> ids = new LinkedHashSet<>();
        ids.add(u.getEmail().toLowerCase());
        if (u.getGitIdentities() != null) {
            Arrays.stream(u.getGitIdentities().split("[,;\\s]+"))
                    .map(String::trim).filter(s -> !s.isEmpty()).map(String::toLowerCase).forEach(ids::add);
        }
        return ids;
    }

    static boolean isMergeCommit(String title) {
        return title != null && MERGE_COMMIT.matcher(title).matches();
    }

    private static String normalize(String s) {
        return s == null ? "" : s.toLowerCase().replaceAll("[^a-z0-9]", "");
    }

    private static String truncate(String s, int max) {
        return s == null || s.length() <= max ? s : s.substring(0, max);
    }

    /** État d'une passe de synchronisation sur un dépôt (dans la transaction). */
    private final class Linker {
        private final GitRepo repo;
        private final Map<String, AppUser> byIdentity = new HashMap<>();
        private final Map<Long, Optional<Task>> taskCache = new HashMap<>();
        private int created;

        Linker(GitRepo repo) {
            this.repo = repo;
            users.findAll().forEach(u -> identities(u).forEach(id -> byIdentity.putIfAbsent(id, u)));
        }

        AppUser user(String identity) {
            return identity == null ? null : byIdentity.get(identity.toLowerCase());
        }

        Task task(Long id) {
            return taskCache.computeIfAbsent(id, tasks::findById).orElse(null);
        }

        /** Crée ou met à jour le commit ; la référence dans le message est prioritaire sur toute autre liaison. */
        GitCommit upsert(GitLabApi.Commit c) {
            if (isMergeCommit(c.title())) {
                return null;
            }
            GitCommit gc = commits.findByRepoAndSha(repo, c.id()).orElseGet(GitCommit::new);
            if (gc.getId() == null) {
                created++;
                gc.setRepo(repo);
                gc.setSha(c.id());
                gc.setTitle(truncate(c.title() == null ? c.id() : c.title(), 500));
                gc.setMessage(truncate(c.message(), 4000));
                gc.setAuthorName(c.authorName());
                gc.setAuthorEmail(c.authorEmail() == null ? null : c.authorEmail().toLowerCase());
                OffsetDateTime at = c.authoredDate() == null ? OffsetDateTime.now() : c.authoredDate();
                gc.setAuthoredAt(at.toInstant());
                gc.setWorkDate(at.atZoneSameInstant(DAKAR).toLocalDate());
                gc.setWebUrl(c.webUrl());
            }
            if (gc.getConsultant() == null) {
                gc.setConsultant(user(gc.getAuthorEmail()));
            }
            refs.find(gc.getMessage(), gc.getTitle()).map(this::task)
                    .ifPresent(t -> { gc.setTask(t); gc.setLinkSource(GitLinkSource.MESSAGE); });
            return commits.save(gc);
        }

        /** Liaison par MR ou branche, sans écraser une liaison plus explicite. */
        void link(GitCommit gc, Task task, GitLinkSource source) {
            if (gc == null || task == null) {
                return;
            }
            GitLinkSource current = gc.getLinkSource();
            if (current == null || current.ordinal() >= source.ordinal()) {
                gc.setTask(task);
                gc.setLinkSource(source);
            }
        }
    }
}
