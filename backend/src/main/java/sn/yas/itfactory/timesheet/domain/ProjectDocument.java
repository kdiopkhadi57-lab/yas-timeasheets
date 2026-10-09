package sn.yas.itfactory.timesheet.domain;

import jakarta.persistence.*;

import java.time.Instant;

/** Document déposé sur un projet (Word, Excel, PDF, code…), stocké sur disque. */
@Entity
@Table(indexes = @Index(columnList = "project_id"))
public class ProjectDocument {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    private Project project;

    @ManyToOne(fetch = FetchType.LAZY)
    private AppUser uploadedBy;

    @Column(nullable = false)
    private String fileName;

    private String contentType;

    private long size;

    /** Chemin relatif au dossier des documents : {projet}/{uuid}. */
    @Column(nullable = false, unique = true)
    private String storageKey;

    @Column(length = 1000)
    private String description;

    /** Nullable : les documents déposés avant l'ajout des catégories sont « Autre ». */
    @Enumerated(EnumType.STRING)
    private DocumentCategory category;

    private Instant uploadedAt = Instant.now();

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Project getProject() { return project; }
    public void setProject(Project project) { this.project = project; }
    public AppUser getUploadedBy() { return uploadedBy; }
    public void setUploadedBy(AppUser uploadedBy) { this.uploadedBy = uploadedBy; }
    public String getFileName() { return fileName; }
    public void setFileName(String fileName) { this.fileName = fileName; }
    public String getContentType() { return contentType; }
    public void setContentType(String contentType) { this.contentType = contentType; }
    public long getSize() { return size; }
    public void setSize(long size) { this.size = size; }
    public String getStorageKey() { return storageKey; }
    public void setStorageKey(String storageKey) { this.storageKey = storageKey; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public DocumentCategory getCategory() { return category == null ? DocumentCategory.AUTRE : category; }
    public void setCategory(DocumentCategory category) { this.category = category; }
    public Instant getUploadedAt() { return uploadedAt; }
    public void setUploadedAt(Instant uploadedAt) { this.uploadedAt = uploadedAt; }
}
