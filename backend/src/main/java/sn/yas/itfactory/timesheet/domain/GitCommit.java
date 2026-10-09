package sn.yas.itfactory.timesheet.domain;

import jakarta.persistence.*;

import java.time.Instant;
import java.time.LocalDate;

/** Commit GitLab, rattaché si possible à un consultant (par e-mail) et à une tâche (par sa référence TS-42). */
@Entity
@Table(uniqueConstraints = @UniqueConstraint(columnNames = {"repo_id", "sha"}),
        indexes = {@Index(columnList = "consultant_id, workDate"), @Index(columnList = "task_id")})
public class GitCommit {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    private GitRepo repo;

    @Column(nullable = false, length = 40)
    private String sha;

    @Column(nullable = false, length = 500)
    private String title;

    @Column(length = 4000)
    private String message;

    private String authorName;

    private String authorEmail;

    @Column(nullable = false)
    private Instant authoredAt;

    /** Jour de travail (heure de Dakar), pour le rapprochement avec les saisies. */
    @Column(nullable = false)
    private LocalDate workDate;

    private String webUrl;

    @ManyToOne(fetch = FetchType.LAZY)
    private AppUser consultant;

    @ManyToOne(fetch = FetchType.LAZY)
    private Task task;

    @Enumerated(EnumType.STRING)
    private GitLinkSource linkSource;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public GitRepo getRepo() { return repo; }
    public void setRepo(GitRepo repo) { this.repo = repo; }
    public String getSha() { return sha; }
    public void setSha(String sha) { this.sha = sha; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }
    public String getAuthorName() { return authorName; }
    public void setAuthorName(String authorName) { this.authorName = authorName; }
    public String getAuthorEmail() { return authorEmail; }
    public void setAuthorEmail(String authorEmail) { this.authorEmail = authorEmail; }
    public Instant getAuthoredAt() { return authoredAt; }
    public void setAuthoredAt(Instant authoredAt) { this.authoredAt = authoredAt; }
    public LocalDate getWorkDate() { return workDate; }
    public void setWorkDate(LocalDate workDate) { this.workDate = workDate; }
    public String getWebUrl() { return webUrl; }
    public void setWebUrl(String webUrl) { this.webUrl = webUrl; }
    public AppUser getConsultant() { return consultant; }
    public void setConsultant(AppUser consultant) { this.consultant = consultant; }
    public Task getTask() { return task; }
    public void setTask(Task task) { this.task = task; }
    public GitLinkSource getLinkSource() { return linkSource; }
    public void setLinkSource(GitLinkSource linkSource) { this.linkSource = linkSource; }
}
