package sn.yas.itfactory.timesheet.repository;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import sn.yas.itfactory.timesheet.domain.Report;
import sn.yas.itfactory.timesheet.domain.ReportStatus;
import sn.yas.itfactory.timesheet.domain.ReportType;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface ReportRepository extends JpaRepository<Report, Long> {

    @EntityGraph(attributePaths = {"consultant", "reviewer"})
    Optional<Report> findByConsultantIdAndTypeAndPeriodStart(Long consultantId, ReportType type, LocalDate periodStart);

    @EntityGraph(attributePaths = {"consultant", "reviewer"})
    List<Report> findByTypeAndPeriodStartAndConsultantIdIn(ReportType type, LocalDate periodStart, Collection<Long> consultantIds);

    /** Rapports qui couvrent une date donnée, pour savoir si une saisie est verrouillée. */
    @Query("select r from Report r where r.consultant.id = :consultantId and :date between r.periodStart and r.periodEnd")
    List<Report> findCovering(Long consultantId, LocalDate date);

    @EntityGraph(attributePaths = {"consultant"})
    List<Report> findByTypeAndPeriodStartBetweenAndConsultantIdIn(ReportType type, LocalDate from, LocalDate to,
                                                                   Collection<Long> consultantIds);

    @EntityGraph(attributePaths = {"consultant"})
    List<Report> findByConsultantIdOrderByPeriodStartDesc(Long consultantId);

    @EntityGraph(attributePaths = {"consultant"})
    List<Report> findByStatusOrderBySubmittedAtDesc(ReportStatus status);
}
