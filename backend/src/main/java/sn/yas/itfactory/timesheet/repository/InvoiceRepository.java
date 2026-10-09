package sn.yas.itfactory.timesheet.repository;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import sn.yas.itfactory.timesheet.domain.Invoice;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface InvoiceRepository extends JpaRepository<Invoice, Long> {

    Optional<Invoice> findByConsultantIdAndPeriodStart(Long consultantId, LocalDate periodStart);

    @EntityGraph(attributePaths = {"consultant", "reviewer"})
    List<Invoice> findByConsultantIdOrderByPeriodStartDesc(Long consultantId);

    @EntityGraph(attributePaths = {"consultant", "reviewer"})
    List<Invoice> findByPeriodStartOrderByIdAsc(LocalDate periodStart);

    Optional<Invoice> findFirstByConsultantIdAndIdNotOrderByPeriodStartDesc(Long consultantId, Long excludedId);

    Optional<Invoice> findFirstByConsultantIdOrderByPeriodStartDesc(Long consultantId);
}
