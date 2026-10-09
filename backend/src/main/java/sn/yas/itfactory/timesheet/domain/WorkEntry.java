package sn.yas.itfactory.timesheet.domain;

import jakarta.persistence.*;

import java.time.Instant;
import java.time.LocalDate;

/**
 * Une ligne de travail d'une journée : c'est ce qui alimente la colonne
 * « Liste des tâches » de la fiche de temps (une entrée = un point numéroté).
 */
@Entity
@Table(indexes = @Index(columnList = "consultant_id, workDate"))
public class WorkEntry {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    private AppUser consultant;

    @Column(nullable = false)
    private LocalDate workDate;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    private Project project;

    /** Tâche assignée liée, facultative (travail non planifié possible). */
    @ManyToOne(fetch = FetchType.LAZY)
    private Task task;

    @Column(nullable = false, length = 2000)
    private String description;

    @Column(length = 1000)
    private String comment;

    private Instant createdAt = Instant.now();

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public AppUser getConsultant() { return consultant; }
    public void setConsultant(AppUser consultant) { this.consultant = consultant; }
    public LocalDate getWorkDate() { return workDate; }
    public void setWorkDate(LocalDate workDate) { this.workDate = workDate; }
    public Project getProject() { return project; }
    public void setProject(Project project) { this.project = project; }
    public Task getTask() { return task; }
    public void setTask(Task task) { this.task = task; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public String getComment() { return comment; }
    public void setComment(String comment) { this.comment = comment; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
}
