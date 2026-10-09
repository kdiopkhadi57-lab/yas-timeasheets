package sn.yas.itfactory.timesheet.repository;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import sn.yas.itfactory.timesheet.domain.WorkEntry;

import java.time.LocalDate;
import java.util.List;

public interface WorkEntryRepository extends JpaRepository<WorkEntry, Long> {

    @EntityGraph(attributePaths = {"project", "task"})
    List<WorkEntry> findByConsultantIdAndWorkDateBetweenOrderByWorkDateAscIdAsc(Long consultantId, LocalDate from, LocalDate to);

    long countByConsultantIdAndWorkDateBetween(Long consultantId, LocalDate from, LocalDate to);

    /** Jours déclarés sur chaque tâche (un jour compte une fois, quel que soit le nombre de lignes). */
    @Query("select e.task.id, count(distinct e.workDate) from WorkEntry e where e.task.id in :taskIds group by e.task.id")
    List<Object[]> spentDaysByTask(java.util.Collection<Long> taskIds);

    @EntityGraph(attributePaths = {"project", "consultant"})
    List<WorkEntry> findByConsultantIdInAndWorkDateBetween(java.util.Collection<Long> consultantIds, LocalDate from, LocalDate to);

    /** Les saisies restent sur la fiche de temps même si la tâche est supprimée. */
    @Modifying
    @Query("update WorkEntry e set e.task = null where e.task.id = :taskId")
    void detachTask(Long taskId);
}
