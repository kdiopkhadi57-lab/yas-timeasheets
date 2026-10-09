package sn.yas.itfactory.timesheet.repository;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import sn.yas.itfactory.timesheet.domain.AppUser;
import sn.yas.itfactory.timesheet.domain.GitCommit;
import sn.yas.itfactory.timesheet.domain.GitRepo;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface GitCommitRepository extends JpaRepository<GitCommit, Long> {

    Optional<GitCommit> findByRepoAndSha(GitRepo repo, String sha);

    @EntityGraph(attributePaths = {"repo", "consultant"})
    List<GitCommit> findByTaskIdOrderByAuthoredAtDesc(Long taskId);

    @EntityGraph(attributePaths = {"repo", "repo.project", "consultant", "task", "task.project"})
    List<GitCommit> findByConsultantIdInAndWorkDateBetweenOrderByAuthoredAtDesc(Collection<Long> consultantIds, LocalDate from, LocalDate to);

    /** Commits de la période dont l'auteur n'est relié à aucun utilisateur. */
    @EntityGraph(attributePaths = {"repo"})
    List<GitCommit> findByConsultantIsNullAndWorkDateBetween(LocalDate from, LocalDate to);

    @Query("select c.task.id, count(c), max(c.authoredAt) from GitCommit c where c.task.id in :taskIds group by c.task.id")
    List<Object[]> statsByTask(Collection<Long> taskIds);

    /** Rattache après coup les commits d'un auteur dont l'identité vient d'être renseignée. */
    @Modifying
    @Query("update GitCommit c set c.consultant = :user where c.consultant is null and lower(c.authorEmail) in :emails")
    int assignAuthor(AppUser user, Collection<String> emails);

    @Modifying
    @Query("update GitCommit c set c.task = null, c.linkSource = null where c.task.id = :taskId")
    void detachTask(Long taskId);
}
