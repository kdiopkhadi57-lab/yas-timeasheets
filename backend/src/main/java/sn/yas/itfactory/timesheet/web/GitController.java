package sn.yas.itfactory.timesheet.web;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import sn.yas.itfactory.timesheet.domain.ReportType;
import sn.yas.itfactory.timesheet.service.GitService;
import sn.yas.itfactory.timesheet.service.GitSyncService;
import sn.yas.itfactory.timesheet.web.dto.Dtos.*;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/git")
public class GitController {

    private final GitService git;
    private final GitSyncService sync;

    public GitController(GitService git, GitSyncService sync) {
        this.git = git;
        this.sync = sync;
    }

    @GetMapping("/status")
    public GitStatusDto status() {
        return git.status();
    }

    @PostMapping("/sync")
    @PreAuthorize("hasAnyRole('ADMIN','HEAD_OF_OFFICE','PM')")
    public GitStatusDto syncNow() {
        if (!sync.enabled()) {
            throw ApiException.badRequest("GitLab n'est pas configuré : renseignez GITLAB_TOKEN côté serveur.");
        }
        sync.syncAll();
        return git.status();
    }

    @GetMapping("/repos")
    @PreAuthorize("hasAnyRole('ADMIN','HEAD_OF_OFFICE','PM')")
    public List<GitRepoDto> repos() {
        return git.listRepos();
    }

    @PutMapping("/repos/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','HEAD_OF_OFFICE','PM')")
    public GitRepoDto mapRepo(@PathVariable Long id, @RequestBody GitRepoRequest req) {
        return git.mapRepo(id, req);
    }

    @GetMapping("/suggestions")
    public List<GitSuggestionDto> suggestions(@RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                              @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
                                              @RequestParam(required = false) Long consultantId) {
        return git.suggestions(from, to, consultantId);
    }

    @GetMapping("/activity")
    @PreAuthorize("hasAnyRole('ADMIN','HEAD_OF_OFFICE','PM')")
    public GitActivityDto activity(@RequestParam ReportType type,
                                   @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
                                   @RequestParam(required = false) Long projectId) {
        return git.activity(type, date, projectId);
    }
}
