package sn.yas.itfactory.timesheet.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import sn.yas.itfactory.timesheet.config.AppProperties;
import sn.yas.itfactory.timesheet.domain.AppUser;
import sn.yas.itfactory.timesheet.domain.Invoice;
import sn.yas.itfactory.timesheet.domain.InvoiceStatus;
import sn.yas.itfactory.timesheet.domain.Role;
import sn.yas.itfactory.timesheet.pdf.InvoicePdfWriter;
import sn.yas.itfactory.timesheet.repository.AppUserRepository;
import sn.yas.itfactory.timesheet.repository.InvoiceRepository;
import sn.yas.itfactory.timesheet.security.UserPrincipal;
import sn.yas.itfactory.timesheet.web.ApiException;
import sn.yas.itfactory.timesheet.web.dto.Dtos.*;

import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeParseException;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Factures mensuelles des consultants : le consultant saisit ses honoraires nets du mois (dates calculées),
 * signe et envoie le PDF au Head of Office, qui valide ou renvoie la facture.
 */
@Service
@Transactional
public class InvoiceService {

    /** Fichier PDF : nom et contenu. */
    public record PdfFile(String fileName, byte[] content) {
    }

    private static final int MAX_SIGNATURE_BYTES = 500_000;

    private final InvoiceRepository invoices;
    private final AppUserRepository users;
    private final CurrentUser current;
    private final MailService mail;
    private final String client;
    private final String prefix;
    private final double rate;

    public InvoiceService(InvoiceRepository invoices, AppUserRepository users, CurrentUser current, MailService mail,
                          AppProperties properties) {
        this.invoices = invoices;
        this.users = users;
        this.current = current;
        this.mail = mail;
        AppProperties.Invoice cfg = properties.invoice();
        this.client = cfg == null || cfg.client() == null ? "SAGA AFRICA HOLDINGS LIMITED SA" : cfg.client();
        this.prefix = cfg == null || cfg.prefix() == null ? "CONST-IT" : cfg.prefix();
        this.rate = cfg == null || cfg.withholdingRate() == null ? 0.05 : cfg.withholdingRate();
    }

    // ---- Consultant

    @Transactional(readOnly = true)
    public List<InvoiceDto> mine() {
        return invoices.findByConsultantIdOrderByPeriodStartDesc(current.principal().id()).stream().map(this::dto).toList();
    }

    /** Facture du mois, ou proposition pré-remplie (dates du mois, intitulé et montant de la dernière facture). */
    @Transactional(readOnly = true)
    public InvoiceDto forMonth(String month) {
        AppUser me = current.user();
        YearMonth ym = parse(month);
        return invoices.findByConsultantIdAndPeriodStart(me.getId(), ym.atDay(1)).map(this::dto)
                .orElseGet(() -> dto(proposal(me, ym)));
    }

    public InvoiceDto save(InvoiceRequest req) {
        AppUser me = current.user();
        YearMonth ym = parse(req.month());
        Invoice inv = invoices.findByConsultantIdAndPeriodStart(me.getId(), ym.atDay(1)).orElseGet(() -> proposal(me, ym));
        if (!inv.getStatus().editable()) {
            throw ApiException.conflict("Facture déjà envoyée : elle n'est plus modifiable");
        }
        long deduction = req.computerDeduction() == null ? 0 : req.computerDeduction();
        if (deduction > req.netAmount()) {
            throw ApiException.badRequest("La retenue ordinateur dépasse les honoraires nets");
        }
        inv.setInvoiceDate(req.invoiceDate());
        inv.setObject(req.title().trim());
        inv.setNetAmount(req.netAmount());
        inv.setComputerDeduction(deduction);
        return dto(invoices.save(inv));
    }

    /** Envoie la facture signée en PDF au Head of Office (consultant en copie). */
    public InvoiceDto submit(Long id) {
        Invoice inv = own(id);
        AppUser me = inv.getConsultant();
        if (!inv.getStatus().editable()) {
            throw ApiException.conflict("Facture déjà envoyée");
        }
        if (me.getSignaturePng() == null) {
            throw ApiException.badRequest("Ajoutez votre signature avant d'envoyer la facture");
        }
        if (blank(me.getPhone()) || blank(me.getAddress())) {
            throw ApiException.badRequest("Renseignez votre téléphone et votre adresse (ils figurent sur la facture)");
        }
        byte[] pdf = render(inv);
        inv.setPdf(pdf);
        inv.setStatus(InvoiceStatus.SOUMISE);
        inv.setSubmittedAt(Instant.now());
        inv.setReviewComment(null);
        inv.setReviewer(null);
        inv.setReviewedAt(null);
        List<String> heads = users.findByRoleAndActiveTrue(Role.HEAD_OF_OFFICE).stream().map(AppUser::getEmail).toList();
        if (heads.isEmpty()) {
            throw ApiException.badRequest("Aucun Head of Office actif pour recevoir la facture");
        }
        mail.send(heads, List.of(me.getEmail()), "Facture " + inv.getNumber() + " · " + me.getFullName(),
                "<p>Bonjour,</p><p>Veuillez trouver ci-joint la facture <b>" + inv.getNumber() + "</b> de "
                        + me.getFullName() + " (" + me.getMatricule() + ") : "
                        + InvoicePdfWriter.periodLabel(inv.getPeriodStart(), inv.getPeriodEnd()).toLowerCase()
                        + ", net à payer <b>" + InvoicePdfWriter.amount(netToPay(inv)) + " FCFA</b>.</p>"
                        + "<p>Vous pouvez la valider ou la renvoyer dans « Factures ».</p>",
                List.of(new MailService.Attachment(fileName(inv), pdf)));
        return dto(inv);
    }

    // ---- Head of Office

    /** Tous les consultants et leur facture du mois. */
    @Transactional(readOnly = true)
    public List<InvoiceTeamRowDto> team(String month) {
        requireHead();
        YearMonth ym = parse(month);
        Map<Long, Invoice> byConsultant = invoices.findByPeriodStartOrderByIdAsc(ym.atDay(1)).stream()
                .collect(Collectors.toMap(i -> i.getConsultant().getId(), Function.identity()));
        List<InvoiceTeamRowDto> rows = new ArrayList<>();
        for (AppUser u : users.findByActiveTrueOrderByLastNameAsc()) {
            Invoice inv = byConsultant.remove(u.getId());
            if (u.getRole().fillsTimesheet() || inv != null) {
                rows.add(new InvoiceTeamRowDto(UserDto.of(u), inv == null ? null : dto(inv)));
            }
        }
        // Factures de comptes désactivés depuis.
        byConsultant.values().forEach(inv -> rows.add(new InvoiceTeamRowDto(UserDto.of(inv.getConsultant()), dto(inv))));
        return rows;
    }

    public InvoiceDto approve(Long id, String comment) {
        Invoice inv = reviewable(id);
        inv.setStatus(InvoiceStatus.VALIDEE);
        review(inv, comment);
        notifyConsultant(inv, "Facture " + inv.getNumber() + " validée", "a validé votre facture.");
        return dto(inv);
    }

    public InvoiceDto reject(Long id, String comment) {
        if (blank(comment)) {
            throw ApiException.badRequest("Indiquez ce qu'il faut corriger");
        }
        Invoice inv = reviewable(id);
        inv.setStatus(InvoiceStatus.REJETEE);
        review(inv, comment);
        notifyConsultant(inv, "Facture " + inv.getNumber() + " à corriger", "vous renvoie votre facture pour correction.");
        return dto(inv);
    }

    // ---- PDF

    /** PDF envoyé (figé) ou aperçu du brouillon. Consultant propriétaire, Head of Office ou Admin. */
    @Transactional(readOnly = true)
    public PdfFile pdf(Long id) {
        Invoice inv = invoices.findById(id).orElseThrow(() -> ApiException.notFound("Facture"));
        UserPrincipal me = current.principal();
        if (!Objects.equals(inv.getConsultant().getId(), me.id()) && !me.seesEveryone()) {
            throw ApiException.forbidden();
        }
        byte[] content = inv.getPdf() != null && !inv.getStatus().editable() ? inv.getPdf() : render(inv);
        return new PdfFile(fileName(inv), content);
    }

    // ---- Coordonnées et signature

    @Transactional(readOnly = true)
    public InvoiceProfileDto profile() {
        return profileDto(current.user());
    }

    public InvoiceProfileDto updateProfile(InvoiceProfileRequest req) {
        AppUser me = current.user();
        me.setPhone(trim(req.phone()));
        me.setInvoiceEmail(trim(req.invoiceEmail()));
        me.setAddress(req.address() == null ? null : trim(req.address().replaceAll("\\s*\\R\\s*", " ")));
        return profileDto(me);
    }

    public InvoiceProfileDto saveSignature(String dataUrl) {
        String b64 = dataUrl.startsWith("data:") ? dataUrl.substring(dataUrl.indexOf(',') + 1) : dataUrl;
        byte[] png;
        try {
            png = Base64.getDecoder().decode(b64.trim());
        } catch (IllegalArgumentException e) {
            throw ApiException.badRequest("Signature illisible");
        }
        boolean isPng = png.length > 8 && (png[0] & 0xFF) == 0x89 && png[1] == 'P' && png[2] == 'N' && png[3] == 'G';
        if (!isPng) {
            throw ApiException.badRequest("La signature doit être une image PNG");
        }
        if (png.length > MAX_SIGNATURE_BYTES) {
            throw ApiException.badRequest("Image de signature trop lourde (500 Ko maximum)");
        }
        AppUser me = current.user();
        me.setSignaturePng(png);
        return profileDto(me);
    }

    public InvoiceProfileDto deleteSignature() {
        AppUser me = current.user();
        me.setSignaturePng(null);
        return profileDto(me);
    }

    @Transactional(readOnly = true)
    public byte[] signature() {
        byte[] png = current.user().getSignaturePng();
        if (png == null) {
            throw ApiException.notFound("Signature");
        }
        return png;
    }

    // ---- Calculs

    /** Honoraires bruts tels que brut − retenue = net (800 000 net → 842 105 brut à 5 %). */
    long gross(long net) {
        return Math.round(net / (1 - rate));
    }

    private long netToPay(Invoice inv) {
        return inv.getNetAmount() - inv.getComputerDeduction();
    }

    private Invoice proposal(AppUser me, YearMonth ym) {
        Invoice inv = new Invoice();
        inv.setConsultant(me);
        inv.setPeriodStart(ym.atDay(1));
        inv.setPeriodEnd(ym.atEndOfMonth());
        inv.setNumber(prefix + "-" + me.getMatricule() + "-" + String.format("%02d", ym.getMonthValue()) + "-" + ym.getYear());
        LocalDate today = LocalDate.now();
        inv.setInvoiceDate(YearMonth.from(today).equals(ym) || today.isAfter(ym.atEndOfMonth()) ? today : ym.atEndOfMonth());
        Optional<Invoice> last = invoices.findFirstByConsultantIdOrderByPeriodStartDesc(me.getId());
        inv.setObject(last.map(Invoice::getObject).orElse("Consultant " + (blank(me.getProfil()) ? "" : me.getProfil()).trim()));
        inv.setNetAmount(last.map(Invoice::getNetAmount).orElse(0L));
        inv.setComputerDeduction(last.map(Invoice::getComputerDeduction).orElse(0L));
        return inv;
    }

    private byte[] render(Invoice inv) {
        AppUser c = inv.getConsultant();
        long gross = gross(inv.getNetAmount());
        return InvoicePdfWriter.write(new InvoicePdfWriter.Data(c.getFullName(), c.getPhone(),
                blank(c.getInvoiceEmail()) ? c.getEmail() : c.getInvoiceEmail(), c.getAddress(), inv.getNumber(),
                inv.getInvoiceDate(), client, objectLine(inv), inv.getNetAmount(), gross, gross - inv.getNetAmount(),
                (int) Math.round(rate * 100), inv.getNetAmount(), inv.getComputerDeduction(), netToPay(inv),
                c.getSignaturePng()));
    }

    private String objectLine(Invoice inv) {
        return inv.getObject() + " - " + InvoicePdfWriter.periodLabel(inv.getPeriodStart(), inv.getPeriodEnd());
    }

    private InvoiceDto dto(Invoice inv) {
        AppUser c = inv.getConsultant();
        long gross = gross(inv.getNetAmount());
        UserPrincipal me = current.principalOrNull();
        boolean editable = inv.getStatus().editable() && me != null && Objects.equals(me.id(), c.getId());
        return new InvoiceDto(inv.getId(), c.getId(), c.getFullName(), c.getMatricule(), inv.getNumber(),
                YearMonth.from(inv.getPeriodStart()).toString(), inv.getPeriodStart(), inv.getPeriodEnd(),
                inv.getInvoiceDate(), inv.getObject(), objectLine(inv), client, inv.getNetAmount(), gross,
                gross - inv.getNetAmount(), (int) Math.round(rate * 100), inv.getComputerDeduction(), netToPay(inv),
                inv.getStatus(), inv.getReviewComment(), inv.getReviewer() == null ? null : inv.getReviewer().getFullName(),
                inv.getSubmittedAt(), inv.getReviewedAt(), editable);
    }

    private InvoiceProfileDto profileDto(AppUser u) {
        return new InvoiceProfileDto(u.getFullName(), u.getPhone(), blank(u.getInvoiceEmail()) ? u.getEmail() : u.getInvoiceEmail(),
                u.getAddress(), u.getSignaturePng() != null);
    }

    private Invoice own(Long id) {
        Invoice inv = invoices.findById(id).orElseThrow(() -> ApiException.notFound("Facture"));
        if (!Objects.equals(inv.getConsultant().getId(), current.principal().id())) {
            throw ApiException.forbidden();
        }
        return inv;
    }

    private Invoice reviewable(Long id) {
        requireHead();
        Invoice inv = invoices.findById(id).orElseThrow(() -> ApiException.notFound("Facture"));
        if (inv.getStatus() != InvoiceStatus.SOUMISE) {
            throw ApiException.conflict("Seule une facture envoyée peut être validée ou renvoyée");
        }
        return inv;
    }

    private void review(Invoice inv, String comment) {
        inv.setReviewer(current.user());
        inv.setReviewedAt(Instant.now());
        inv.setReviewComment(blank(comment) ? null : comment.trim());
    }

    private void notifyConsultant(Invoice inv, String subject, String what) {
        AppUser c = inv.getConsultant();
        mail.send(List.of(c.getEmail()), List.of(), subject,
                "<p>Bonjour " + c.getFirstName() + ",</p><p>" + current.user().getFullName() + " " + what + "</p>"
                        + (inv.getReviewComment() == null ? "" : "<blockquote>" + escape(inv.getReviewComment()) + "</blockquote>")
                        + "<p>Facture <b>" + inv.getNumber() + "</b>.</p>", List.of());
    }

    private void requireHead() {
        if (!current.principal().seesEveryone()) {
            throw ApiException.forbidden();
        }
    }

    private static YearMonth parse(String month) {
        try {
            return YearMonth.parse(month);
        } catch (DateTimeParseException | NullPointerException e) {
            throw ApiException.badRequest("Mois invalide (attendu AAAA-MM)");
        }
    }

    /** « Facture_CONST-IT-C060-09-2026.pdf ». */
    private static String fileName(Invoice inv) {
        return "Facture_" + inv.getNumber() + ".pdf";
    }

    private static boolean blank(String s) {
        return s == null || s.isBlank();
    }

    private static String trim(String s) {
        return blank(s) ? null : s.trim();
    }

    private static String escape(String s) {
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }
}
