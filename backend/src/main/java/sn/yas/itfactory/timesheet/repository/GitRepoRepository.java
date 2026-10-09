package sn.yas.itfactory.timesheet.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import sn.yas.itfactory.timesheet.domain.GitRepo;

import java.util.List;
import java.util.Optional;

public interface GitRepoRepository extends JpaRepository<GitRepo, Long> {

    Optional<GitRepo> findByGitlabId(Long gitlabId);

    List<GitRepo> findAllByOrderByPathWithNamespaceAsc();

    List<GitRepo> findByProjectIsNotNull();
}
