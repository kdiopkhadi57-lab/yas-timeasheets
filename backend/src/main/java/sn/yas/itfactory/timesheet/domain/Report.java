package sn.yas.itfactory.timesheet.domain;

import jakarta.persistence.*;

import java.time.Instant;
import java.time.LocalDate;

@Entity
@Table(uniqueConstraints = @UniqueConstraint(columnNames = {"consultant_id", "type", "periodStart"}))
public class Report {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ReportType type;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    private AppUser consultant;

    @Column(nullable = false)
    private LocalDate periodStart;

    @Column(nullable = false)
    private LocalDate periodEnd;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ReportStatus status = ReportStatus.BROUILLON;

    @Column(length = 2000)
    private String consultantComment;

    @Column(length = 2000)
    private String reviewComment;

    @ManyToOne(fetch = FetchType.LAZY)
    private AppUser reviewer;

    private Instant submittedAt;
    private Instant reviewedAt;
    private Instant sentAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public ReportType getType() { return type; }
    public void setType(ReportType type) { this.type = type; }
    public AppUser getConsultant() { return consultant; }
    public void setConsultant(AppUser consultant) { this.consultant = consultant; }
    public LocalDate getPeriodStart() { return periodStart; }
    public void setPeriodStart(LocalDate periodStart) { this.periodStart = periodStart; }
    public LocalDate getPeriodEnd() { return periodEnd; }
    public void setPeriodEnd(LocalDate periodEnd) { this.periodEnd = periodEnd; }
    public ReportStatus getStatus() { return status; }
    public void setStatus(ReportStatus status) { this.status = status; }
    public String getConsultantComment() { return consultantComment; }
    public void setConsultantComment(String consultantComment) { this.consultantComment = consultantComment; }
    public String getReviewComment() { return reviewComment; }
    public void setReviewComment(String reviewComment) { this.reviewComment = reviewComment; }
    public AppUser getReviewer() { return reviewer; }
    public void setReviewer(AppUser reviewer) { this.reviewer = reviewer; }
    public Instant getSubmittedAt() { return submittedAt; }
    public void setSubmittedAt(Instant submittedAt) { this.submittedAt = submittedAt; }
    public Instant getReviewedAt() { return reviewedAt; }
    public void setReviewedAt(Instant reviewedAt) { this.reviewedAt = reviewedAt; }
    public Instant getSentAt() { return sentAt; }
    public void setSentAt(Instant sentAt) { this.sentAt = sentAt; }
}
