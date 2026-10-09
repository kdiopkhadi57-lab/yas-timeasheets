package sn.yas.itfactory.timesheet.domain;

import jakarta.persistence.*;

import java.time.Instant;

/** Question d'un développeur ou explication du lead dev / de l'architecte sur un document de projet. */
@Entity
@Table(indexes = @Index(columnList = "document_id"))
public class DocumentComment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    private ProjectDocument document;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    private AppUser author;

    @Column(nullable = false, length = 4000)
    private String text;

    private Instant createdAt = Instant.now();

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public ProjectDocument getDocument() { return document; }
    public void setDocument(ProjectDocument document) { this.document = document; }
    public AppUser getAuthor() { return author; }
    public void setAuthor(AppUser author) { this.author = author; }
    public String getText() { return text; }
    public void setText(String text) { this.text = text; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
}
