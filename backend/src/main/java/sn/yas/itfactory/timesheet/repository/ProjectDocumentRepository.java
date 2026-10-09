package sn.yas.itfactory.timesheet.repository;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import sn.yas.itfactory.timesheet.domain.ProjectDocument;

import java.util.List;

public interface ProjectDocumentRepository extends JpaRepository<ProjectDocument, Long> {

    @EntityGraph(attributePaths = {"uploadedBy"})
    List<ProjectDocument> findByProjectIdOrderByUploadedAtDescIdDesc(Long projectId);

    long countByProjectId(Long projectId);
}
