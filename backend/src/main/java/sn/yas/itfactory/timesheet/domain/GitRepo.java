package sn.yas.itfactory.timesheet.domain;

import jakarta.persistence.*;

import java.time.Instant;

/** Dépôt GitLab découvert grâce au jeton ; seuls ceux rattachés à un projet sont synchronisés. */
@Entity
public class GitRepo {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Identifiant du projet côté GitLab. */
    @Column(nullable = false, unique = true)
    private Long gitlabId;

    /** Ex. numflex/numflex-backend. */
    @Column(nullable = false)
    private String pathWithNamespace;

    private String webUrl;

    private String defaultBranch;

    @ManyToOne(fetch = FetchType.EAGER)
    private Project project;

    private Instant lastSyncedAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getGitlabId() { return gitlabId; }
    public void setGitlabId(Long gitlabId) { this.gitlabId = gitlabId; }
    public String getPathWithNamespace() { return pathWithNamespace; }
    public void setPathWithNamespace(String pathWithNamespace) { this.pathWithNamespace = pathWithNamespace; }
    public String getWebUrl() { return webUrl; }
    public void setWebUrl(String webUrl) { this.webUrl = webUrl; }
    public String getDefaultBranch() { return defaultBranch; }
    public void setDefaultBranch(String defaultBranch) { this.defaultBranch = defaultBranch; }
    public Project getProject() { return project; }
    public void setProject(Project project) { this.project = project; }
    public Instant getLastSyncedAt() { return lastSyncedAt; }
    public void setLastSyncedAt(Instant lastSyncedAt) { this.lastSyncedAt = lastSyncedAt; }
}
