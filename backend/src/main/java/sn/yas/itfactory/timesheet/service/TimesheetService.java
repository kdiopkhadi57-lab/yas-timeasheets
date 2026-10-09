package sn.yas.itfactory.timesheet.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import sn.yas.itfactory.timesheet.domain.*;
import sn.yas.itfactory.timesheet.repository.ReportRepository;
import sn.yas.itfactory.timesheet.repository.WorkEntryRepository;
import sn.yas.itfactory.timesheet.web.dto.Dtos.*;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.*;
import java.util.function.Function;

/** Construit la fiche de temps (une ligne par jour) à partir des saisies du consultant. */
@Service
@Transactional(readOnly = true)
public class TimesheetService {

    private final WorkEntryRepository entries;
    private final ReportRepository reports;

    public TimesheetService(WorkEntryRepository entries, ReportRepository reports) {
        this.entries = entries;
        this.reports = reports;
    }

    public TimesheetDto build(AppUser consultant, Period period) {
        List<WorkEntry> list = entries.findByConsultantIdAndWorkDateBetweenOrderByWorkDateAscIdAsc(
                consultant.getId(), period.start(), period.end());
        Map<LocalDate, List<WorkEntry>> byDay = new HashMap<>();
        list.forEach(e -> byDay.computeIfAbsent(e.getWorkDate(), d -> new ArrayList<>()).add(e));

        List<DayDto> days = new ArrayList<>();
        for (LocalDate d = period.start(); !d.isAfter(period.end()); d = d.plusDays(1)) {
            days.add(toDay(d, byDay.getOrDefault(d, List.of())));
        }

        Report report = reports.findByConsultantIdAndTypeAndPeriodStart(consultant.getId(), period.type(), period.start())
                .orElse(null);
        boolean editable = report == null || !report.getStatus().locksEntries();
        return new TimesheetDto(UserDto.of(consultant), period.type(), period.start(), period.end(), period.label(),
                report == null ? null : toReportDto(report), days, byDay.size(), list.size(), editable);
    }

    private DayDto toDay(LocalDate date, List<WorkEntry> dayEntries) {
        boolean weekend = date.getDayOfWeek() == DayOfWeek.SATURDAY || date.getDayOfWeek() == DayOfWeek.SUNDAY;
        List<String> projects = distinct(dayEntries, e -> e.getProject().getName());
        List<String> units = distinct(dayEntries, e -> e.getProject().getBusinessUnit());
        List<String> tasks = dayEntries.stream().map(WorkEntry::getDescription).toList();
        List<String> comments = distinct(dayEntries, WorkEntry::getComment);
        return new DayDto(date, weekend, projects, units, tasks, comments,
                dayEntries.stream().map(EntryDto::of).toList());
    }

    private static List<String> distinct(List<WorkEntry> list, Function<WorkEntry, String> f) {
        return list.stream().map(f).filter(s -> s != null && !s.isBlank()).distinct().toList();
    }

    public static ReportDto toReportDto(Report r) {
        Period p = new Period(r.getType(), r.getPeriodStart(), r.getPeriodEnd());
        return new ReportDto(r.getId(), r.getType(), r.getConsultant().getId(), r.getConsultant().getFullName(),
                r.getConsultant().getMatricule(), r.getPeriodStart(), r.getPeriodEnd(), p.label(), r.getStatus(),
                r.getConsultantComment(), r.getReviewComment(),
                r.getReviewer() == null ? null : r.getReviewer().getFullName(),
                r.getSubmittedAt(), r.getReviewedAt(), r.getSentAt());
    }

    /** Texte de la colonne « Liste des tâches » : numéroté dès qu'il y a plusieurs points, comme dans la fiche. */
    public static String formatTasks(List<String> tasks) {
        if (tasks.size() == 1) {
            return tasks.getFirst();
        }
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < tasks.size(); i++) {
            if (i > 0) {
                sb.append('\n');
            }
            sb.append(i + 1).append(". ").append(tasks.get(i));
        }
        return sb.toString();
    }
}
