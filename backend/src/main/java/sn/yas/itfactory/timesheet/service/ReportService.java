package sn.yas.itfactory.timesheet.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import sn.yas.itfactory.timesheet.domain.*;
import sn.yas.itfactory.timesheet.excel.SummaryExcelWriter;
import sn.yas.itfactory.timesheet.excel.TimesheetExcelWriter;
import sn.yas.itfactory.timesheet.repository.AppUserRepository;
import sn.yas.itfactory.timesheet.repository.ReportRepository;
import sn.yas.itfactory.timesheet.repository.TaskRepository;
import sn.yas.itfactory.timesheet.security.UserPrincipal;
import sn.yas.itfactory.timesheet.web.ApiException;
import sn.yas.itfactory.timesheet.web.dto.Dtos.*;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.*;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * Cycle de vie des rapports : brouillon → soumis (envoi automatique de l'Excel) → validé ou à corriger.
 * HEBDO : envoyé au manager direct du consultant, les managers au-dessus de lui en copie.
 * MENSUEL : envoyé au Head of Office, la chaîne hiérarchique en copie.
 * Chaque étape (soumission, retrait, validation, renvoi) est notifiée par e-mail.
 */
@Service
@Transactional
public class ReportService {

    public record ExcelFile(String fileName, byte[] content) {
    }

    private final ReportRepository reports;
    private final AppUserRepository users;
    private final TaskRepository tasks;
    private final TimesheetService timesheets;
    private final TimesheetExcelWriter excel;
    private final SummaryExcelWriter summaryExcel;
    private final MailService mail;
    private final CurrentUser current;
    private final TaskService taskService;

    public ReportService(ReportRepository reports, AppUserRepository users, TaskRepository tasks,
                         TimesheetService timesheets, TimesheetExcelWriter excel, SummaryExcelWriter summaryExcel,
                         MailService mail, CurrentUser current, TaskService taskService) {
        this.reports = reports;
        this.users = users;
        this.tasks = tasks;
        this.timesheets = timesheets;
        this.excel = excel;
        this.summaryExcel = summaryExcel;
        this.mail = mail;
        this.current = current;
        this.taskService = taskService;
    }

    @Transactional(readOnly = true)
    public TimesheetDto timesheet(ReportType type, LocalDate date, Long consultantId) {
        AppUser consultant = current.requireVisible(consultantId);
        return timesheets.build(consultant, Period.of(type, date));
    }

    public TimesheetDto submit(ReportType type, LocalDate date, String comment) {
        AppUser me = current.user();
        Period period = Period.of(type, date);
        TimesheetDto ts = timesheets.build(me, period);
        if (ts.entryCount() == 0) {
            throw ApiException.badRequest("Aucune tâche saisie sur la période " + period.label());
        }
        Report report = reports.findByConsultantIdAndTypeAndPeriodStart(me.getId(), type, period.start())
                .orElseGet(() -> newReport(me, period));
        if (report.getStatus().locksEntries()) {
            throw ApiException.conflict("Ce rapport est déjà " + (report.getStatus() == ReportStatus.VALIDE ? "validé" : "soumis"));
        }
        report.setStatus(ReportStatus.SOUMIS);
        report.setConsultantComment(comment);
        report.setSubmittedAt(Instant.now());
        report.setReviewComment(null);
        report.setReviewedAt(null);
        report.setReviewer(null);
        reports.save(report);

        ExcelFile file = excel(me, period);
        boolean sent = sendSubmission(me, report, period, file);
        if (sent) {
            report.setSentAt(Instant.now());
        }
        return timesheets.build(me, period);
    }

    /** Le consultant peut retirer un rapport soumis tant qu'il n'est pas validé. */
    public TimesheetDto withdraw(ReportType type, LocalDate date) {
        AppUser me = current.user();
        Period period = Period.of(type, date);
        Report report = reports.findByConsultantIdAndTypeAndPeriodStart(me.getId(), type, period.start())
                .orElseThrow(() -> ApiException.notFound("Rapport"));
        if (report.getStatus() != ReportStatus.SOUMIS) {
            throw ApiException.conflict("Seul un rapport soumis peut être retiré");
        }
        report.setStatus(ReportStatus.BROUILLON);
        Recipients r = recipients(me, report.getType());
        mail.send(r.to(), withConsultant(r.cc(), me), "Retrait : " + kind(report) + " " + period.label() + " - " + me.getFullName(),
                "<p>Bonjour,</p><p><b>" + me.getFullName() + "</b> a retiré son " + kind(report) + " <b>" + period.label()
                        + "</b> pour le corriger ; il sera soumis à nouveau.</p>", List.of());
        return timesheets.build(me, period);
    }

    public ReportDto approve(Long reportId, String comment) {
        Report report = reviewable(reportId);
        report.setStatus(ReportStatus.VALIDE);
        report.setReviewComment(comment);
        report.setReviewer(current.user());
        report.setReviewedAt(Instant.now());
        Period period = periodOf(report);
        mail.send(List.of(report.getConsultant().getEmail()), reviewCopies(report),
                "Votre " + kind(report) + " " + period.label() + " a été validé",
                "<p>Bonjour " + report.getConsultant().getFirstName() + ",</p><p>Votre " + kind(report) + " <b>"
                        + period.label() + "</b> a été validé par " + report.getReviewer().getFullName() + ".</p>"
                        + quote(comment), List.of());
        return TimesheetService.toReportDto(report);
    }

    public ReportDto reject(Long reportId, String comment) {
        if (comment == null || comment.isBlank()) {
            throw ApiException.badRequest("Indiquez ce qu'il faut corriger");
        }
        Report report = reviewable(reportId);
        report.setStatus(ReportStatus.REJETE);
        report.setReviewComment(comment);
        report.setReviewer(current.user());
        report.setReviewedAt(Instant.now());
        Period period = periodOf(report);
        mail.send(List.of(report.getConsultant().getEmail()), reviewCopies(report),
                "À corriger : " + kind(report) + " " + period.label(),
                "<p>Bonjour " + report.getConsultant().getFirstName() + ",</p><p>"
                        + report.getReviewer().getFullName() + " vous demande de corriger votre " + kind(report)
                        + " <b>" + period.label() + "</b> :</p>" + quote(comment)
                        + "<p>Corrigez vos saisies puis soumettez à nouveau.</p>", List.of());
        return TimesheetService.toReportDto(report);
    }

    @Transactional(readOnly = true)
    public List<TeamStatusDto> teamStatus(ReportType type, LocalDate date) {
        requireManager();
        Period period = Period.of(type, date);
        return teamStatus(current.team(), period);
    }

    @Transactional(readOnly = true)
    public List<TeamStatusDto> teamStatus(List<AppUser> team, Period period) {
        Map<Long, Report> byConsultant = new HashMap<>();
        if (!team.isEmpty()) {
            reports.findByTypeAndPeriodStartAndConsultantIdIn(period.type(), period.start(),
                            team.stream().map(AppUser::getId).toList())
                    .forEach(r -> byConsultant.put(r.getConsultant().getId(), r));
        }
        return team.stream().map(u -> {
            TimesheetDto ts = timesheets.build(u, period);
            Report r = byConsultant.get(u.getId());
            return new TeamStatusDto(UserDto.of(u), r == null ? null : TimesheetService.toReportDto(r),
                    ts.workedDays(), ts.entryCount());
        }).toList();
    }

    @Transactional(readOnly = true)
    public ExcelFile excelFor(ReportType type, LocalDate date, Long consultantId) {
        AppUser consultant = current.requireVisible(consultantId);
        return excel(consultant, Period.of(type, date));
    }

    @Transactional(readOnly = true)
    public ExcelFile excel(AppUser consultant, Period period) {
        TimesheetDto ts = timesheets.build(consultant, period);
        List<TaskDto> taskList = consultant.getRole().usesTasks() ? tasksOfPeriod(consultant, period) : List.of();
        return new ExcelFile(excel.fileName(ts), excel.write(ts, taskList));
    }

    /** ZIP de l'équipe : une fiche par consultant au format YAS + un fichier de synthèse. */
    @Transactional(readOnly = true)
    public ExcelFile teamZip(ReportType type, LocalDate date) {
        requireManager();
        return teamZip(current.team(), Period.of(type, date));
    }

    @Transactional(readOnly = true)
    public ExcelFile teamZip(List<AppUser> team, Period period) {
        try (ByteArrayOutputStream bytes = new ByteArrayOutputStream(); ZipOutputStream zip = new ZipOutputStream(bytes)) {
            String kind = period.type() == ReportType.MENSUEL ? "Fiches de temps" : "Rapports hebdo";
            zip.putNextEntry(new ZipEntry("Synthese - " + kind + " " + period.label() + ".xlsx"));
            zip.write(summaryExcel.write(kind + " " + period.label(), teamStatus(team, period)));
            zip.closeEntry();
            for (AppUser u : team) {
                ExcelFile f = excel(u, period);
                zip.putNextEntry(new ZipEntry(f.fileName()));
                zip.write(f.content());
                zip.closeEntry();
            }
            zip.finish();
            return new ExcelFile(kind + " " + period.label() + ".zip", bytes.toByteArray());
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @Transactional(readOnly = true)
    public List<ReportDto> history(Long consultantId) {
        AppUser consultant = current.requireVisible(consultantId);
        return reports.findByConsultantIdOrderByPeriodStartDesc(consultant.getId()).stream()
                .map(TimesheetService::toReportDto).toList();
    }

    /**
     * Fiches soumises en attente de validation adressées à l'utilisateur connecté :
     * rapports hebdo de ses collaborateurs (manager direct ou au-dessus ; Head of Office pour ceux sans manager),
     * fiches mensuelles pour le Head of Office. L'Admin voit toutes les fiches en attente.
     */
    @Transactional(readOnly = true)
    public List<ReportDto> inbox() {
        UserPrincipal me = current.principal();
        if (!me.isManager()) {
            return List.of();
        }
        return reports.findByStatusOrderBySubmittedAtDesc(ReportStatus.SOUMIS).stream()
                .filter(r -> !Objects.equals(r.getConsultant().getId(), me.id()))
                .filter(r -> me.role() == Role.ADMIN || receives(me, r))
                .map(TimesheetService::toReportDto).toList();
    }

    private static boolean receives(UserPrincipal me, Report r) {
        boolean head = me.role() == Role.HEAD_OF_OFFICE;
        if (r.getType() == ReportType.MENSUEL) {
            return head;
        }
        List<AppUser> chain = r.getConsultant().managerChain();
        return chain.isEmpty() ? head : r.getConsultant().isManagedBy(me.id());
    }

    // ------------------------------------------------------------------

    /** Tâches à joindre au rapport : celles encore ouvertes et celles terminées pendant la période. */
    private List<TaskDto> tasksOfPeriod(AppUser consultant, Period period) {
        Instant from = period.start().atStartOfDay(ZoneId.of("Africa/Dakar")).toInstant();
        Instant to = period.end().plusDays(1).atStartOfDay(ZoneId.of("Africa/Dakar")).toInstant();
        return taskService.toDtos(tasks.findAll((root, q, cb) -> cb.equal(root.get("assignee").get("id"), consultant.getId())).stream()
                .filter(t -> t.getStatus() != TaskStatus.TERMINEE
                        || (t.getCompletedAt() != null && !t.getCompletedAt().isBefore(from) && t.getCompletedAt().isBefore(to)))
                .filter(t -> t.getCreatedAt() == null || t.getCreatedAt().isBefore(to))
                .sorted(Comparator.comparing(Task::getStatus).thenComparing(Task::getId))
                .toList());
    }

    /** Destinataires d'un rapport : à qui il est adressé, et qui est en copie. */
    private record Recipients(List<String> to, List<String> cc) {
    }

    /**
     * HEBDO : au manager direct (Head of Office s'il n'en a pas), les managers au-dessus en copie.
     * MENSUEL : au Head of Office, toute la chaîne hiérarchique en copie.
     */
    private Recipients recipients(AppUser consultant, ReportType type) {
        List<String> heads = users.findByRoleAndActiveTrue(Role.HEAD_OF_OFFICE).stream().map(AppUser::getEmail).toList();
        List<String> chain = consultant.managerChain().stream().filter(AppUser::isActive).map(AppUser::getEmail).toList();
        List<String> to = new ArrayList<>();
        List<String> cc = new ArrayList<>();
        if (type == ReportType.HEBDO && !chain.isEmpty()) {
            to.add(chain.get(0));
            cc.addAll(chain.subList(1, chain.size()));
        } else {
            to.addAll(heads);
            cc.addAll(chain);
        }
        cc.removeAll(to);
        return new Recipients(to, cc);
    }

    /** Validation / renvoi : la chaîne hiérarchique est tenue informée (sauf le validateur lui-même). */
    private List<String> reviewCopies(Report report) {
        Long reviewer = report.getReviewer() == null ? null : report.getReviewer().getId();
        return report.getConsultant().managerChain().stream()
                .filter(m -> m.isActive() && !Objects.equals(m.getId(), reviewer))
                .map(AppUser::getEmail).toList();
    }

    private static List<String> withConsultant(List<String> cc, AppUser consultant) {
        List<String> all = new ArrayList<>(cc);
        all.add(consultant.getEmail());
        return all;
    }

    private boolean sendSubmission(AppUser me, Report report, Period period, ExcelFile file) {
        Recipients r = recipients(me, report.getType());
        List<String> to = r.to();
        List<String> cc = withConsultant(r.cc(), me);
        String subject = (report.getType() == ReportType.HEBDO ? "Rapport hebdo" : "Fiche de temps") + " "
                + period.label() + " - " + me.getFullName() + " (" + me.getMatricule() + ")";
        String html = "<p>Bonjour,</p><p>Veuillez trouver ci-joint " + (report.getType() == ReportType.HEBDO
                ? "le rapport hebdomadaire" : "la fiche de temps") + " de <b>" + me.getFullName() + "</b> pour la période <b>"
                + period.label() + "</b>.</p>" + quote(report.getConsultantComment())
                + "<p>Vous pouvez le valider ou demander une correction depuis l'application.</p>";
        return mail.send(to, cc, subject, html, List.of(new MailService.Attachment(file.fileName(), file.content())));
    }

    private Report reviewable(Long reportId) {
        Report report = reports.findById(reportId).orElseThrow(() -> ApiException.notFound("Rapport"));
        UserPrincipal me = current.principal();
        if (Objects.equals(report.getConsultant().getId(), me.id())) {
            throw ApiException.forbidden();
        }
        boolean allowed = report.getType() == ReportType.MENSUEL
                ? me.seesEveryone()
                : current.canSee(report.getConsultant());
        if (!allowed) {
            throw ApiException.forbidden();
        }
        if (report.getStatus() != ReportStatus.SOUMIS) {
            throw ApiException.conflict("Seul un rapport soumis peut être validé ou renvoyé");
        }
        return report;
    }

    private void requireManager() {
        if (!current.principal().isManager()) {
            throw ApiException.forbidden();
        }
    }

    private static Report newReport(AppUser consultant, Period period) {
        Report r = new Report();
        r.setConsultant(consultant);
        r.setType(period.type());
        r.setPeriodStart(period.start());
        r.setPeriodEnd(period.end());
        return r;
    }

    private static Period periodOf(Report r) {
        return new Period(r.getType(), r.getPeriodStart(), r.getPeriodEnd());
    }

    private static String kind(Report r) {
        return r.getType() == ReportType.HEBDO ? "rapport hebdo" : "fiche de temps";
    }

    private static String quote(String text) {
        if (text == null || text.isBlank()) {
            return "";
        }
        String safe = text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\n", "<br>");
        return "<blockquote style='border-left:3px solid #4BACC6;margin:8px 0;padding:4px 12px;color:#333'>" + safe + "</blockquote>";
    }
}
