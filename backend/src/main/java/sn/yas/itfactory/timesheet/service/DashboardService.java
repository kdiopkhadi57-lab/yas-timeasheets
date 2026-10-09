package sn.yas.itfactory.timesheet.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import sn.yas.itfactory.timesheet.domain.*;
import sn.yas.itfactory.timesheet.repository.ProjectRepository;
import sn.yas.itfactory.timesheet.repository.ReportRepository;
import sn.yas.itfactory.timesheet.repository.WorkEntryRepository;
import sn.yas.itfactory.timesheet.security.UserPrincipal;
import sn.yas.itfactory.timesheet.web.ApiException;
import sn.yas.itfactory.timesheet.web.dto.Dtos.*;

import java.time.*;
import java.time.temporal.IsoFields;
import java.util.*;
import java.util.function.Predicate;
import java.util.stream.Collectors;

/**
 * Tableau de bord des managers et du Head of Office : les personnes qu'ils suivent (développeurs et lead devs pour un
 * manager, architectes pour le Head of Office), le nombre de fiches soumises par chacune, la ponctualité des rapports
 * hebdo et les jours déclarés par projet. Les développeurs, lead devs et architectes n'y ont pas accès.
 */
@Service
@Transactional(readOnly = true)
public class DashboardService {

    static final ZoneId DAKAR = ZoneId.of("Africa/Dakar");
    /** Échéance du rapport hebdo : vendredi 18 h, heure du récapitulatif envoyé au PM. */
    static final LocalTime WEEKLY_DEADLINE = LocalTime.of(18, 0);

    private final WorkEntryRepository entries;
    private final ReportRepository reports;
    private final ProjectRepository projects;
    private final CurrentUser current;
    private final Clock clock;

    public DashboardService(WorkEntryRepository entries, ReportRepository reports, ProjectRepository projects,
                            CurrentUser current) {
        this.entries = entries;
        this.reports = reports;
        this.projects = projects;
        this.current = current;
        this.clock = Clock.system(DAKAR);
    }

    public DashboardDto build(Long projectId, int weeks) {
        UserPrincipal me = current.principal();
        if (!me.isManager()) {
            throw ApiException.forbidden();
        }
        weeks = Math.max(4, Math.min(weeks, 52));
        LocalDate today = LocalDate.now(clock);
        Period week = Period.weekly(today);
        LocalDate from = week.start().minusWeeks(weeks - 1L);
        LocalDate to = week.end();

        Project project = projectId == null ? null
                : projects.findById(projectId).orElseThrow(() -> ApiException.notFound("Projet"));
        List<AppUser> members = current.team().stream()
                .filter(audienceOf(me.role()))
                .filter(u -> project == null || project.involves(u.getId()))
                .toList();
        List<Long> ids = members.stream().map(AppUser::getId).toList();

        List<WorkEntry> work = ids.isEmpty() ? List.of()
                : entries.findByConsultantIdInAndWorkDateBetween(ids, from, to).stream()
                .filter(e -> project == null || Objects.equals(e.getProject().getId(), project.getId())).toList();
        Map<WorkEntry, Double> share = dayShares(work);

        List<Report> weekly = reportsOf(ReportType.HEBDO, ids, from, today);
        List<Report> monthly = reportsOf(ReportType.MENSUEL, ids, from, today);
        List<Report> pending = ids.isEmpty() ? List.of() : reports.findByStatusOrderBySubmittedAtDesc(ReportStatus.SOUMIS)
                .stream().filter(r -> ids.contains(r.getConsultant().getId())).toList();

        List<LocalDate> dueWeeks = new ArrayList<>();
        for (LocalDate w = from; deadlinePassed(w, today); w = w.plusWeeks(1)) {
            dueWeeks.add(w);
        }
        List<Report> dueWeekly = weekly.stream().filter(r -> deadlinePassed(r.getPeriodStart(), today)).toList();
        List<Report> period = new ArrayList<>(weekly);
        period.addAll(monthly);

        List<MemberRowDto> rows = memberRows(members, period, dueWeekly, pending, dueWeeks.size(), share);
        LocalDate lastWeek = dueWeeks.isEmpty() ? null : dueWeeks.get(dueWeeks.size() - 1);
        int submittedLastWeek = lastWeek == null ? 0
                : count(dueWeekly, r -> r.getPeriodStart().equals(lastWeek) && submitted(r));
        long dueSubmitted = count(dueWeekly, DashboardService::submitted);
        long dueOnTime = count(dueWeekly, r -> onTime(r, r.getPeriodStart()));

        DashboardKpis kpis = new DashboardKpis(
                members.size(),
                count(period, DashboardService::submitted),
                pending.size(),
                count(period, r -> r.getStatus() == ReportStatus.VALIDE),
                count(period, r -> r.getStatus() == ReportStatus.REJETE),
                lastWeek == null ? 0 : members.size() - submittedLastWeek,
                dueSubmitted == 0 ? null : (int) Math.round(100.0 * dueOnTime / (members.size() * (double) dueWeeks.size())),
                round(share.values().stream().mapToDouble(Double::doubleValue).sum()));

        return new DashboardDto(from, to, audience(me.role()), kpis, rows, projectRows(share),
                compliance(members.size(), dueWeekly, dueWeeks));
    }

    /** Manager : ses développeurs et lead devs. Head of Office : les architectes. Admin : tout le monde. */
    private static Predicate<AppUser> audienceOf(Role role) {
        return switch (role) {
            case PM -> u -> u.getRole() == Role.CONSULTANT || u.getRole() == Role.LEAD_DEV;
            case HEAD_OF_OFFICE -> u -> u.getRole() == Role.ARCHITECTE;
            default -> u -> true;
        };
    }

    private static String audience(Role role) {
        return switch (role) {
            case PM -> "DEV";
            case HEAD_OF_OFFICE -> "ARCHITECTE";
            default -> "ALL";
        };
    }

    // ---- Personnes suivies

    private List<MemberRowDto> memberRows(List<AppUser> members, List<Report> period, List<Report> dueWeekly,
                                          List<Report> pending, int expected, Map<WorkEntry, Double> share) {
        Map<Long, List<Report>> byMember = period.stream().collect(Collectors.groupingBy(r -> r.getConsultant().getId()));
        Map<Long, List<Report>> dueByMember = dueWeekly.stream().collect(Collectors.groupingBy(r -> r.getConsultant().getId()));
        Map<Long, Long> pendingByMember = pending.stream()
                .collect(Collectors.groupingBy(r -> r.getConsultant().getId(), Collectors.counting()));
        Map<Long, Double> declared = new HashMap<>();
        share.forEach((e, v) -> declared.merge(e.getConsultant().getId(), v, Double::sum));
        return members.stream().map(u -> {
            List<Report> rs = byMember.getOrDefault(u.getId(), List.of());
            List<Report> due = dueByMember.getOrDefault(u.getId(), List.of());
            int dueSubmitted = count(due, DashboardService::submitted);
            Instant last = rs.stream().map(Report::getSubmittedAt).filter(Objects::nonNull)
                    .max(Comparator.naturalOrder()).orElse(null);
            return new MemberRowDto(u.getId(), u.getFullName(), u.getMatricule(), u.getRole(), u.getProfil(),
                    count(rs, DashboardService::submitted),
                    count(rs, r -> r.getType() == ReportType.HEBDO && submitted(r)),
                    count(rs, r -> r.getType() == ReportType.MENSUEL && submitted(r)),
                    pendingByMember.getOrDefault(u.getId(), 0L).intValue(),
                    count(rs, r -> r.getStatus() == ReportStatus.VALIDE),
                    count(rs, r -> r.getStatus() == ReportStatus.REJETE),
                    dueSubmitted - count(due, r -> onTime(r, r.getPeriodStart())),
                    Math.max(0, expected - dueSubmitted),
                    round(declared.getOrDefault(u.getId(), 0.0)),
                    last);
        }).sorted(Comparator.comparingInt(MemberRowDto::pending).reversed()
                .thenComparing(MemberRowDto::missing, Comparator.reverseOrder())
                .thenComparing(MemberRowDto::fullName)).toList();
    }

    private static List<ProjectRowDto> projectRows(Map<WorkEntry, Double> share) {
        Map<Long, Project> byId = new LinkedHashMap<>();
        Map<Long, Double> declared = new HashMap<>();
        Map<Long, Set<Long>> people = new HashMap<>();
        share.forEach((e, v) -> {
            Long id = e.getProject().getId();
            byId.putIfAbsent(id, e.getProject());
            declared.merge(id, v, Double::sum);
            people.computeIfAbsent(id, k -> new HashSet<>()).add(e.getConsultant().getId());
        });
        return byId.values().stream()
                .map(p -> new ProjectRowDto(p.getId(), p.getName(), p.getBusinessUnit(),
                        people.get(p.getId()).size(), round(declared.get(p.getId()))))
                .sorted(Comparator.comparingDouble(ProjectRowDto::declaredDays).reversed()).toList();
    }

    /** Une journée déclarée vaut 1 ; partagée entre plusieurs lignes, elle est répartie à parts égales. */
    static Map<WorkEntry, Double> dayShares(List<WorkEntry> work) {
        Map<String, Long> perDay = work.stream().collect(Collectors.groupingBy(
                e -> e.getConsultant().getId() + "|" + e.getWorkDate(), Collectors.counting()));
        Map<WorkEntry, Double> share = new IdentityHashMap<>();
        work.forEach(e -> share.put(e, 1.0 / perDay.get(e.getConsultant().getId() + "|" + e.getWorkDate())));
        return share;
    }

    // ---- Rapports hebdo

    private List<ComplianceWeekDto> compliance(int expected, List<Report> dueWeekly, List<LocalDate> dueWeeks) {
        Map<LocalDate, List<Report>> byWeek = dueWeekly.stream().collect(Collectors.groupingBy(Report::getPeriodStart));
        return dueWeeks.stream().map(w -> {
            List<Report> rs = byWeek.getOrDefault(w, List.of());
            return new ComplianceWeekDto(w, weekLabel(w), expected, count(rs, DashboardService::submitted),
                    count(rs, r -> onTime(r, w)));
        }).toList();
    }

    private List<Report> reportsOf(ReportType type, List<Long> ids, LocalDate from, LocalDate today) {
        return ids.isEmpty() ? List.of() : reports.findByTypeAndPeriodStartBetweenAndConsultantIdIn(type, from, today, ids);
    }

    private boolean deadlinePassed(LocalDate weekStart, LocalDate today) {
        LocalDateTime deadline = Period.friday(weekStart).atTime(WEEKLY_DEADLINE);
        return !weekStart.isAfter(today) && LocalDateTime.now(clock).isAfter(deadline);
    }

    private static boolean submitted(Report r) {
        return r.getStatus() != ReportStatus.BROUILLON && r.getSubmittedAt() != null;
    }

    private static boolean onTime(Report r, LocalDate weekStart) {
        return submitted(r) && !r.getSubmittedAt().atZone(DAKAR).toLocalDateTime()
                .isAfter(Period.friday(weekStart).atTime(WEEKLY_DEADLINE));
    }

    // ---- Calculs

    private static int count(Collection<Report> list, Predicate<Report> p) {
        return (int) list.stream().filter(p).count();
    }

    private static String weekLabel(LocalDate weekStart) {
        return "S" + Period.monday(weekStart).get(IsoFields.WEEK_OF_WEEK_BASED_YEAR);
    }

    private static double round(double v) {
        return Math.round(v * 10) / 10.0;
    }
}
