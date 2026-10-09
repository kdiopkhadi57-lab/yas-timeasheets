package sn.yas.itfactory.timesheet.repository;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import sn.yas.itfactory.timesheet.domain.TaskActivity;

import java.util.Collection;
import java.util.List;

public interface TaskActivityRepository extends JpaRepository<TaskActivity, Long> {

    @EntityGraph(attributePaths = {"author"})
    List<TaskActivity> findByTaskIdOrderByCreatedAtAscIdAsc(Long taskId);

    @Query("select a.task.id, count(a) from TaskActivity a where a.task.id in :taskIds"
            + " and a.kind = sn.yas.itfactory.timesheet.domain.TaskActivityKind.COMMENTAIRE group by a.task.id")
    List<Object[]> commentCounts(Collection<Long> taskIds);

    @Modifying
    @Query("delete from TaskActivity a where a.task.id = :taskId")
    void deleteByTask(Long taskId);
}
