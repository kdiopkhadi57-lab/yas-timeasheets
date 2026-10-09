package sn.yas.itfactory.timesheet.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import sn.yas.itfactory.timesheet.domain.*;
import sn.yas.itfactory.timesheet.repository.AppUserRepository;
import sn.yas.itfactory.timesheet.repository.ReportRepository;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.*;

/**
 * Automatisations qui remplacent l'envoi manuel des fichiers Excel :
 * - relances des consultants avant l'échéance (hebdo et mensuel) ;
 * - chaque vendredi soir, chaque manager reçoit le ZIP des rapports de son équipe (y compris les équipes
 *   des managers qui lui sont rattachés) ;
 * - le 20 du mois, le Head of Office reçoit le ZIP des fiches de temps de la période écoulée.
 */
@Component
public class ReportScheduler {

    private static final Logger log = LoggerFactory.getLogger(ReportScheduler.class);
    private static final ZoneId DAKAR = ZoneId.of("Africa/Dakar");

    private final AppUserRepository users;
    private final ReportRepository reports;
    private final ReportService reportService;
    private final MailService mail;

    public ReportScheduler(AppUserRepository users, ReportRepository reports, ReportService reportService, MailService mail) {
        this.users = users;
        this.reports = reports;
        this.reportService = reportService;
        this.mail = mail;
    }

    /** Jeudi 16h et vendredi 10h : relance de ceux qui n'ont pas soumis leur rapport hebdo. */
    @Scheduled(cron = "${app.schedule.weekly-reminder}", zone = "Africa/Dakar")
    @Scheduled(cron = "${app.schedule.weekly-reminder-last-call}", zone = "Africa/Dakar")
    @Transactional(readOnly = true)
    public void weeklyReminder() {
        remind(Period.weekly(LocalDate.now(DAKAR)));
    }

    /** Le 18 et le 19 : relance pour la fiche de temps mensuelle. */
    @Scheduled(cron = "${app.schedule.monthly-reminder}", zone = "Africa/Dakar")
    @Transactional(readOnly = true)
    public void monthlyReminder() {
        remind(Period.monthly(LocalDate.now(DAKAR)));
    }

    /** Vendredi 18h : chaque manager reçoit les rapports hebdo de toute son équipe, sous-équipes comprises (soumis ou non). */
    @Scheduled(cron = "${app.schedule.weekly-digest}", zone = "Africa/Dakar")
    @Transactional(readOnly = true)
    public void weeklyDigest() {
        Period period = Period.weekly(LocalDate.now(DAKAR));
        Map<AppUser, List<AppUser>> byPm = new LinkedHashMap<>();
        for (AppUser c : consultants()) {
            c.managerChain().stream().filter(AppUser::isActive)
                    .forEach(m -> byPm.computeIfAbsent(m, k -> new ArrayList<>()).add(c));
        }
        byPm.forEach((pm, team) -> sendDigest(List.of(pm.getEmail()), List.of(), team, period,
                "Rapports hebdo de votre équipe - " + period.label(), pm.getFirstName()));
    }

    /** Le 20 à 8h : le Head of Office reçoit toutes les fiches de temps de la période qui vient de se terminer. */
    @Scheduled(cron = "${app.schedule.monthly-digest}", zone = "Africa/Dakar")
    @Transactional(readOnly = true)
    public void monthlyDigest() {
        Period period = Period.monthly(LocalDate.now(DAKAR)).previous();
        List<String> heads = users.findByRoleAndActiveTrue(Role.HEAD_OF_OFFICE).stream().map(AppUser::getEmail).toList();
        sendDigest(heads, List.of(), consultants(), period, "Fiches de temps " + period.label() + " - tous les consultants", null);
    }

    private void sendDigest(List<String> to, List<String> cc, List<AppUser> team, Period period, String subject, String firstName) {
        if (team.isEmpty()) {
            return;
        }
        List<String> missing = missing(team, period).stream().map(AppUser::getFullName).toList();
        ReportService.ExcelFile zip = reportService.teamZip(team, period);
        String html = "<p>Bonjour" + (firstName == null ? "" : " " + firstName) + ",</p>"
                + "<p>Vous trouverez ci-joint les fiches de " + team.size() + " consultant(s) pour la période <b>"
                + period.label() + "</b>, ainsi qu'un fichier de synthèse.</p>"
                + (missing.isEmpty() ? "<p>Tous les rapports ont été soumis.</p>"
                : "<p><b>Non soumis à ce jour :</b> " + String.join(", ", missing) + "</p>");
        mail.send(to, cc, subject, html, List.of(new MailService.Attachment(zip.fileName(), zip.content())));
        log.info("Synthèse {} envoyée à {} ({} consultants, {} manquants)", period.label(), to, team.size(), missing.size());
    }

    private void remind(Period period) {
        String what = period.type() == ReportType.HEBDO ? "rapport hebdo" : "fiche de temps";
        for (AppUser c : missing(consultants(), period)) {
            mail.send(List.of(c.getEmail()), List.of(), "Rappel : " + what + " " + period.label() + " à soumettre",
                    "<p>Bonjour " + c.getFirstName() + ",</p><p>Votre " + what + " <b>" + period.label()
                            + "</b> n'est pas encore soumis. Vérifiez vos tâches du " + period.start() + " au " + period.end()
                            + " puis cliquez sur « Soumettre ».</p>", List.of());
        }
    }

    private List<AppUser> missing(List<AppUser> team, Period period) {
        if (team.isEmpty()) {
            return List.of();
        }
        Set<Long> submitted = new HashSet<>();
        reports.findByTypeAndPeriodStartAndConsultantIdIn(period.type(), period.start(), team.stream().map(AppUser::getId).toList())
                .stream().filter(r -> r.getStatus().locksEntries())
                .forEach(r -> submitted.add(r.getConsultant().getId()));
        return team.stream().filter(u -> !submitted.contains(u.getId())).toList();
    }

    private List<AppUser> consultants() {
        return users.findByActiveTrueOrderByLastNameAsc().stream()
                .filter(u -> u.getRole().fillsTimesheet())
                .toList();
    }
}
