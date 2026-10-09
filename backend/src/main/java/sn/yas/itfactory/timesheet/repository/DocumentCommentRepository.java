package sn.yas.itfactory.timesheet.repository;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import sn.yas.itfactory.timesheet.domain.DocumentComment;

import java.util.List;

public interface DocumentCommentRepository extends JpaRepository<DocumentComment, Long> {

    @EntityGraph(attributePaths = {"author"})
    List<DocumentComment> findByDocumentIdOrderByCreatedAtAscIdAsc(Long documentId);

    /** [documentId, nombre de messages] pour une liste de documents. */
    @Query("select c.document.id, count(c) from DocumentComment c where c.document.id in :ids group by c.document.id")
    List<Object[]> countsByDocument(@Param("ids") List<Long> ids);

    @Modifying
    @Query("delete from DocumentComment c where c.document.id = :documentId")
    void deleteByDocument(@Param("documentId") Long documentId);
}
