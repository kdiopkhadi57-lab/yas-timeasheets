package sn.yas.itfactory.timesheet.gitlab;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;

import java.time.OffsetDateTime;
import java.util.List;

/** Les appels de l'API GitLab v4 dont la synchronisation a besoin. */
public interface GitLabApi {

    /** groups vide : tous les dépôts dont le propriétaire du jeton est membre. */
    List<Project> projects(List<String> groups);

    /** Commits de toutes les branches depuis la date donnée. */
    List<Commit> commits(long projectId, OffsetDateTime since);

    List<MergeRequest> mergeRequests(long projectId, OffsetDateTime updatedAfter);

    List<Commit> mergeRequestCommits(long projectId, long iid);

    List<Branch> branches(long projectId);

    /** Commits présents sur « to » mais pas sur « from ». */
    List<Commit> compare(long projectId, String from, String to);

    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    record Project(long id, String pathWithNamespace, String webUrl, String defaultBranch, Namespace namespace) {
    }

    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    record Namespace(String name, String fullPath) {
    }

    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    record Commit(String id, String title, String message, String authorName, String authorEmail,
                  OffsetDateTime authoredDate, String webUrl) {
    }

    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    record MergeRequest(long iid, String title, String sourceBranch, String state, Author author, String webUrl,
                        OffsetDateTime updatedAt, OffsetDateTime mergedAt) {
    }

    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    record Author(String username, String name) {
    }

    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    record Branch(String name) {
    }

    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    record Comparison(List<Commit> commits) {
    }
}
