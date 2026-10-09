package sn.yas.itfactory.timesheet.service;

import org.springframework.stereotype.Component;
import sn.yas.itfactory.timesheet.domain.*;
import sn.yas.itfactory.timesheet.repository.TaskActivityRepository;

import java.time.Instant;
import java.util.Map;

/** Trace les changements d'une tâche dans son fil d'activité. */
@Component
public class TaskHistory {

    static final Map<TaskStatus, String> STATUS = Map.of(
            TaskStatus.A_FAIRE, "À faire", TaskStatus.EN_COURS, "En cours",
            TaskStatus.BLOQUEE, "Bloquée", TaskStatus.TERMINEE, "Terminée");

    static final Map<TaskPriority, String> PRIORITY = Map.of(
            TaskPriority.BASSE, "Basse", TaskPriority.NORMALE, "Normale",
            TaskPriority.HAUTE, "Haute", TaskPriority.CRITIQUE, "Critique");

    private final TaskActivityRepository activities;

    public TaskHistory(TaskActivityRepository activities) {
        this.activities = activities;
    }

    public TaskActivity log(Task task, AppUser author, TaskActivityKind kind, String text) {
        TaskActivity a = new TaskActivity();
        a.setTask(task);
        a.setAuthor(author);
        a.setKind(kind);
        a.setText(text);
        return activities.save(a);
    }

    /** Change le statut, tient à jour la date de fin et trace le changement. */
    public void changeStatus(Task t, TaskStatus status, AppUser author) {
        if (status == null || status == t.getStatus()) {
            return;
        }
        TaskStatus before = t.getStatus();
        if (status == TaskStatus.TERMINEE) {
            t.setCompletedAt(Instant.now());
        } else {
            t.setCompletedAt(null);
        }
        t.setStatus(status);
        if (t.getId() != null) {
            log(t, author, TaskActivityKind.STATUT, STATUS.get(before) + " → " + STATUS.get(status));
        }
    }
}
