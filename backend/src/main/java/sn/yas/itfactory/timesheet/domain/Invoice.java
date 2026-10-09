package sn.yas.itfactory.timesheet.domain;

import jakarta.persistence.*;

import java.time.Instant;
import java.time.LocalDate;

/**
 * Facture mensuelle d'un consultant (modèle CONST-IT-C060-09-2026) : une par consultant et par mois.
 * Le consultant saisit ses honoraires nets ; brut et retenue sont calculés.
 */
@Entity
@Table(uniqueConstraints = @UniqueConstraint(columnNames = {"consultant_id", "period_start"}))
public class Invoice {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    private AppUser consultant;

    /** CONST-IT-{matricule}-{MM}-{AAAA}. */
    @Column(nullable = false, unique = true)
    private String number;

    /** Premier et dernier jour du mois facturé. */
    @Column(nullable = false)
    private LocalDate periodStart;

    @Column(nullable = false)
    private LocalDate periodEnd;

    @Column(nullable = false)
    private LocalDate invoiceDate;

    @Column(nullable = false, length = 500)
    private String object;

    /** Honoraires nets saisis par le consultant (FCFA). */
    private long netAmount;

    /** Retenue ordinateur à reverser à STAM (FCFA). */
    private long computerDeduction;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private InvoiceStatus status = InvoiceStatus.BROUILLON;

    @Column(length = 2000)
    private String reviewComment;

    @ManyToOne(fetch = FetchType.LAZY)
    private AppUser reviewer;

    private Instant submittedAt;

    private Instant reviewedAt;

    /** PDF figé à l'envoi : c'est ce document que reçoit le Head of Office. */
    @Column(length = 5_000_000)
    @Basic(fetch = FetchType.LAZY)
    private byte[] pdf;

    private Instant createdAt = Instant.now();

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public AppUser getConsultant() { return consultant; }
    public void setConsultant(AppUser consultant) { this.consultant = consultant; }
    public String getNumber() { return number; }
    public void setNumber(String number) { this.number = number; }
    public LocalDate getPeriodStart() { return periodStart; }
    public void setPeriodStart(LocalDate periodStart) { this.periodStart = periodStart; }
    public LocalDate getPeriodEnd() { return periodEnd; }
    public void setPeriodEnd(LocalDate periodEnd) { this.periodEnd = periodEnd; }
    public LocalDate getInvoiceDate() { return invoiceDate; }
    public void setInvoiceDate(LocalDate invoiceDate) { this.invoiceDate = invoiceDate; }
    public String getObject() { return object; }
    public void setObject(String object) { this.object = object; }
    public long getNetAmount() { return netAmount; }
    public void setNetAmount(long netAmount) { this.netAmount = netAmount; }
    public long getComputerDeduction() { return computerDeduction; }
    public void setComputerDeduction(long computerDeduction) { this.computerDeduction = computerDeduction; }
    public InvoiceStatus getStatus() { return status; }
    public void setStatus(InvoiceStatus status) { this.status = status; }
    public String getReviewComment() { return reviewComment; }
    public void setReviewComment(String reviewComment) { this.reviewComment = reviewComment; }
    public AppUser getReviewer() { return reviewer; }
    public void setReviewer(AppUser reviewer) { this.reviewer = reviewer; }
    public Instant getSubmittedAt() { return submittedAt; }
    public void setSubmittedAt(Instant submittedAt) { this.submittedAt = submittedAt; }
    public Instant getReviewedAt() { return reviewedAt; }
    public void setReviewedAt(Instant reviewedAt) { this.reviewedAt = reviewedAt; }
    public byte[] getPdf() { return pdf; }
    public void setPdf(byte[] pdf) { this.pdf = pdf; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
}
