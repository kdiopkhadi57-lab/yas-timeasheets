package sn.yas.itfactory.timesheet.domain;

import jakarta.persistence.*;

import java.time.Instant;

/** Merge request GitLab qui référence une tâche (TS-42 dans le titre ou la branche source). */
@Entity
@Table(uniqueConstraints = @UniqueConstraint(columnNames = {"repo_id", "iid"}))
public class GitMergeRequest {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    private GitRepo repo;

    /** Numéro de la MR dans le dépôt (!12). */
    @Column(nullable = false)
    private Long iid;

    @Column(nullable = false, length = 500)
    private String title;

    private String sourceBranch;

    /** opened, merged, closed. */
    private String state;

    private String authorUsername;

    private String webUrl;

    private Instant updatedAt;

    private Instant mergedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    private AppUser consultant;

    @ManyToOne(fetch = FetchType.LAZY)
    private Task task;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public GitRepo getRepo() { return repo; }
    public void setRepo(GitRepo repo) { this.repo = repo; }
    public Long getIid() { return iid; }
    public void setIid(Long iid) { this.iid = iid; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getSourceBranch() { return sourceBranch; }
    public void setSourceBranch(String sourceBranch) { this.sourceBranch = sourceBranch; }
    public String getState() { return state; }
    public void setState(String state) { this.state = state; }
    public String getAuthorUsername() { return authorUsername; }
    public void setAuthorUsername(String authorUsername) { this.authorUsername = authorUsername; }
    public String getWebUrl() { return webUrl; }
    public void setWebUrl(String webUrl) { this.webUrl = webUrl; }
    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
    public Instant getMergedAt() { return mergedAt; }
    public void setMergedAt(Instant mergedAt) { this.mergedAt = mergedAt; }
    public AppUser getConsultant() { return consultant; }
    public void setConsultant(AppUser consultant) { this.consultant = consultant; }
    public Task getTask() { return task; }
    public void setTask(Task task) { this.task = task; }
}
