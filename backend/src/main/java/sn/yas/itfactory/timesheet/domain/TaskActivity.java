package sn.yas.itfactory.timesheet.domain;

import jakarta.persistence.*;

import java.time.Instant;

/** Historique et commentaires d'une tâche. */
@Entity
@Table(indexes = @Index(columnList = "task_id"))
public class TaskActivity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    private Task task;

    @ManyToOne(fetch = FetchType.LAZY)
    private AppUser author;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TaskActivityKind kind;

    @Column(nullable = false, length = 4000)
    private String text;

    private Instant createdAt = Instant.now();

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Task getTask() { return task; }
    public void setTask(Task task) { this.task = task; }
    public AppUser getAuthor() { return author; }
    public void setAuthor(AppUser author) { this.author = author; }
    public TaskActivityKind getKind() { return kind; }
    public void setKind(TaskActivityKind kind) { this.kind = kind; }
    public String getText() { return text; }
    public void setText(String text) { this.text = text; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
}
