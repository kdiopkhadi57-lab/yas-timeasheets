package sn.yas.itfactory.timesheet.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import sn.yas.itfactory.timesheet.domain.Project;

import java.util.List;

public interface ProjectRepository extends JpaRepository<Project, Long> {

    List<Project> findAllByOrderByNameAsc();

    boolean existsByNameIgnoreCase(String name);

    java.util.Optional<Project> findByNameIgnoreCase(String name);

    /** Projets dont l'utilisateur est lead dev ou architecte : il en voit toutes les tâches. */
    @Query("select p.id from Project p where p.leadDev.id = :userId or p.architect.id = :userId")
    List<Long> idsLedBy(@Param("userId") Long userId);
}
