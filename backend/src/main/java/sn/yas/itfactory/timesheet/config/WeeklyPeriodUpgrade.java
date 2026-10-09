package sn.yas.itfactory.timesheet.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import sn.yas.itfactory.timesheet.domain.Report;
import sn.yas.itfactory.timesheet.domain.ReportType;
import sn.yas.itfactory.timesheet.repository.ReportRepository;

import java.time.DayOfWeek;
import java.util.List;

/**
 * La semaine du rapport hebdo allait du lundi au dimanche ; elle va désormais du samedi au vendredi.
 * Au démarrage, les rapports hebdo enregistrés sur l'ancienne période sont recalés de deux jours
 * (lundi → samedi précédent, dimanche → vendredi) : ils gardent leur numéro de semaine.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 1)
public class WeeklyPeriodUpgrade implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(WeeklyPeriodUpgrade.class);

    private final ReportRepository reports;

    public WeeklyPeriodUpgrade(ReportRepository reports) {
        this.reports = reports;
    }

    @Override
    @Transactional
    public void run(String... args) {
        List<Report> old = reports.findAll().stream()
                .filter(r -> r.getType() == ReportType.HEBDO && r.getPeriodStart().getDayOfWeek() == DayOfWeek.MONDAY)
                .toList();
        for (Report r : old) {
            r.setPeriodStart(r.getPeriodStart().minusDays(2));
            r.setPeriodEnd(r.getPeriodEnd().minusDays(2));
        }
        if (!old.isEmpty()) {
            log.info("{} rapport(s) hebdo recalé(s) sur la semaine du samedi au vendredi", old.size());
        }
    }
}
