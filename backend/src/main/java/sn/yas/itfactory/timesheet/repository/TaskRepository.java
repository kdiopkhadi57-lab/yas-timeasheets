package sn.yas.itfactory.timesheet.repository;

import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import sn.yas.itfactory.timesheet.domain.EstimateStatus;
import sn.yas.itfactory.timesheet.domain.Task;

import java.util.List;

public interface TaskRepository extends JpaRepository<Task, Long>, JpaSpecificationExecutor<Task> {

    @Override
    @EntityGraph(attributePaths = {"project", "assignee", "createdBy"})
    List<Task> findAll(Specification<Task> spec);

    long countByProjectIdAndEstimateStatus(Long projectId, EstimateStatus estimateStatus);
}
