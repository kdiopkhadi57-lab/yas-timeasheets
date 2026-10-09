package sn.yas.itfactory.timesheet.domain;

import jakarta.persistence.*;

import java.time.Instant;
import java.time.LocalDate;

@Entity
public class Task {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String title;

    @Column(length = 4000)
    private String description;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    private Project project;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    private AppUser assignee;

    @ManyToOne(fetch = FetchType.LAZY)
    private AppUser createdBy;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TaskStatus status = TaskStatus.A_FAIRE;

    /** Nullable : les tâches créées avant l'ajout de la priorité sont « Normale ». */
    @Enumerated(EnumType.STRING)
    private TaskPriority priority = TaskPriority.NORMALE;

    private LocalDate startDate;

    private LocalDate dueDate;

    /** Charge estimée, en jours. */
    private Double estimateDays;

    /** Nullable : les tâches sans circuit d'estimation sont « Accordée ». */
    @Enumerated(EnumType.STRING)
    private EstimateStatus estimateStatus;

    /** Durée proposée par le développeur, en jours, en attente du lead dev. */
    private Double proposedEstimateDays;

    /** Avancement déclaré, de 0 à 100. */
    private Integer progress;

    private Instant createdAt = Instant.now();

    private Instant completedAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public Project getProject() { return project; }
    public void setProject(Project project) { this.project = project; }
    public AppUser getAssignee() { return assignee; }
    public void setAssignee(AppUser assignee) { this.assignee = assignee; }
    public AppUser getCreatedBy() { return createdBy; }
    public void setCreatedBy(AppUser createdBy) { this.createdBy = createdBy; }
    public TaskStatus getStatus() { return status; }
    public void setStatus(TaskStatus status) { this.status = status; }
    public TaskPriority getPriority() { return priority == null ? TaskPriority.NORMALE : priority; }
    public void setPriority(TaskPriority priority) { this.priority = priority; }
    public LocalDate getStartDate() { return startDate; }
    public void setStartDate(LocalDate startDate) { this.startDate = startDate; }
    public Double getEstimateDays() { return estimateDays; }
    public void setEstimateDays(Double estimateDays) { this.estimateDays = estimateDays; }
    public EstimateStatus getEstimateStatus() { return estimateStatus == null ? EstimateStatus.ACCORDEE : estimateStatus; }
    public void setEstimateStatus(EstimateStatus estimateStatus) { this.estimateStatus = estimateStatus; }
    public Double getProposedEstimateDays() { return proposedEstimateDays; }
    public void setProposedEstimateDays(Double proposedEstimateDays) { this.proposedEstimateDays = proposedEstimateDays; }
    /** Active : l'estimation est accordée, le développeur peut la démarrer et y déclarer du temps. */
    public boolean isActive() { return getEstimateStatus() == EstimateStatus.ACCORDEE; }
    public int getProgress() { return status == TaskStatus.TERMINEE ? 100 : progress == null ? 0 : progress; }
    public void setProgress(Integer progress) { this.progress = progress; }
    public LocalDate getDueDate() { return dueDate; }
    public void setDueDate(LocalDate dueDate) { this.dueDate = dueDate; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
    public Instant getCompletedAt() { return completedAt; }
    public void setCompletedAt(Instant completedAt) { this.completedAt = completedAt; }
}
