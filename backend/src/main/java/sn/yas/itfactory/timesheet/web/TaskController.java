package sn.yas.itfactory.timesheet.web;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import sn.yas.itfactory.timesheet.domain.TaskStatus;
import sn.yas.itfactory.timesheet.service.GitService;
import sn.yas.itfactory.timesheet.service.TaskService;
import sn.yas.itfactory.timesheet.web.dto.Dtos.*;

import java.util.List;

@RestController
@RequestMapping("/api/tasks")
public class TaskController {

    private final TaskService tasks;
    private final GitService git;

    public TaskController(TaskService tasks, GitService git) {
        this.tasks = tasks;
        this.git = git;
    }

    /** Commits et merge requests GitLab liés à la tâche. */
    @GetMapping("/{id}/git")
    public TaskGitDto git(@PathVariable Long id) {
        return git.forTask(id);
    }

    @GetMapping
    public List<TaskDto> search(@RequestParam(defaultValue = "mine") String scope,
                                @RequestParam(required = false) Long assigneeId,
                                @RequestParam(required = false) TaskStatus status,
                                @RequestParam(required = false) Long projectId) {
        return tasks.search(scope, assigneeId, status, projectId);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public TaskDto create(@Valid @RequestBody TaskRequest req) {
        return tasks.create(req);
    }

    @PutMapping("/{id}")
    public TaskDto update(@PathVariable Long id, @Valid @RequestBody TaskRequest req) {
        return tasks.update(id, req);
    }

    @PatchMapping("/{id}/status")
    public TaskDto status(@PathVariable Long id, @Valid @RequestBody TaskStatusRequest req) {
        return tasks.changeStatus(id, req.status());
    }

    /** Le développeur assigné propose une durée (jours). */
    @PostMapping("/{id}/estimate")
    public TaskDto proposeEstimate(@PathVariable Long id, @Valid @RequestBody EstimateRequest req) {
        return tasks.proposeEstimate(id, req.days(), req.comment());
    }

    /** Le lead dev accorde l'estimation (éventuellement ajustée) : la tâche devient active. */
    @PostMapping("/{id}/estimate/approve")
    public TaskDto approveEstimate(@PathVariable Long id, @Valid @RequestBody EstimateDecisionRequest req) {
        return tasks.approveEstimate(id, req.days(), req.comment());
    }

    @PostMapping("/{id}/estimate/reject")
    public TaskDto rejectEstimate(@PathVariable Long id, @Valid @RequestBody EstimateDecisionRequest req) {
        return tasks.rejectEstimate(id, req.comment());
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id) {
        tasks.delete(id);
    }

    /** Historique des changements et commentaires, du plus ancien au plus récent. */
    @GetMapping("/{id}/activity")
    public List<TaskActivityDto> activity(@PathVariable Long id) {
        return tasks.activity(id);
    }

    @PostMapping("/{id}/comments")
    @ResponseStatus(HttpStatus.CREATED)
    public TaskActivityDto comment(@PathVariable Long id, @Valid @RequestBody CommentRequest req) {
        return tasks.comment(id, req.text());
    }

    @DeleteMapping("/{id}/comments/{commentId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteComment(@PathVariable Long id, @PathVariable Long commentId) {
        tasks.deleteComment(id, commentId);
    }
}
