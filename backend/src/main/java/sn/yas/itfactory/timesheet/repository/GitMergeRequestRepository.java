package sn.yas.itfactory.timesheet.repository;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import sn.yas.itfactory.timesheet.domain.GitMergeRequest;
import sn.yas.itfactory.timesheet.domain.GitRepo;

import java.util.List;
import java.util.Optional;

public interface GitMergeRequestRepository extends JpaRepository<GitMergeRequest, Long> {

    Optional<GitMergeRequest> findByRepoAndIid(GitRepo repo, Long iid);

    boolean existsByRepoAndSourceBranch(GitRepo repo, String sourceBranch);

    @EntityGraph(attributePaths = {"repo", "consultant"})
    List<GitMergeRequest> findByTaskIdOrderByUpdatedAtDesc(Long taskId);

    @Modifying
    @Query("update GitMergeRequest m set m.task = null where m.task.id = :taskId")
    void detachTask(Long taskId);
}
