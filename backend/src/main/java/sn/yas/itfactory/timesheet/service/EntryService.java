package sn.yas.itfactory.timesheet.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import sn.yas.itfactory.timesheet.domain.*;
import sn.yas.itfactory.timesheet.repository.ProjectRepository;
import sn.yas.itfactory.timesheet.repository.ReportRepository;
import sn.yas.itfactory.timesheet.repository.TaskRepository;
import sn.yas.itfactory.timesheet.repository.WorkEntryRepository;
import sn.yas.itfactory.timesheet.web.ApiException;
import sn.yas.itfactory.timesheet.web.dto.Dtos.*;

import java.time.LocalDate;
import java.util.List;
import java.util.Objects;

/** Saisie au fil de l'eau de ce qui a été fait chaque jour. */
@Service
@Transactional
public class EntryService {

    private final WorkEntryRepository entries;
    private final ProjectRepository projects;
    private final TaskRepository tasks;
    private final ReportRepository reports;
    private final CurrentUser current;
    private final TaskHistory history;

    public EntryService(WorkEntryRepository entries, ProjectRepository projects, TaskRepository tasks,
                        ReportRepository reports, CurrentUser current, TaskHistory history) {
        this.entries = entries;
        this.projects = projects;
        this.tasks = tasks;
        this.reports = reports;
        this.current = current;
        this.history = history;
    }

    @Transactional(readOnly = true)
    public List<EntryDto> list(LocalDate from, LocalDate to, Long consultantId) {
        AppUser consultant = current.requireVisible(consultantId);
        return entries.findByConsultantIdAndWorkDateBetweenOrderByWorkDateAscIdAsc(consultant.getId(), from, to)
                .stream().map(EntryDto::of).toList();
    }

    public EntryDto create(EntryRequest req) {
        AppUser me = current.user();
        WorkEntry e = new WorkEntry();
        e.setConsultant(me);
        apply(e, req, me);
        return EntryDto.of(entries.save(e));
    }

    public EntryDto update(Long id, EntryRequest req) {
        WorkEntry e = own(id);
        ensureUnlocked(e.getConsultant(), e.getWorkDate());
        apply(e, req, e.getConsultant());
        return EntryDto.of(e);
    }

    public void delete(Long id) {
        WorkEntry e = own(id);
        ensureUnlocked(e.getConsultant(), e.getWorkDate());
        entries.delete(e);
    }

    private void apply(WorkEntry e, EntryRequest req, AppUser consultant) {
        if (req.workDate().isAfter(LocalDate.now().plusDays(7))) {
            throw ApiException.badRequest("Impossible de saisir plus d'une semaine à l'avance");
        }
        ensureUnlocked(consultant, req.workDate());
        Task task = null;
        if (req.taskId() != null) {
            task = tasks.findById(req.taskId()).orElseThrow(() -> ApiException.notFound("Tâche"));
            if (!Objects.equals(task.getAssignee().getId(), consultant.getId())) {
                throw ApiException.badRequest("Cette tâche ne vous est pas assignée");
            }
            if (!task.isActive()) {
                throw ApiException.badRequest("Cette tâche n'est pas encore active : son estimation doit être accordée par le lead dev");
            }
            if (req.taskStatus() != null) {
                history.changeStatus(task, req.taskStatus(), consultant);
            }
        }
        Project project = req.projectId() != null
                ? projects.findById(req.projectId()).orElseThrow(() -> ApiException.notFound("Projet"))
                : task != null ? task.getProject() : null;
        if (project == null) {
            throw ApiException.badRequest("Choisissez un projet");
        }
        e.setWorkDate(req.workDate());
        e.setProject(project);
        e.setTask(task);
        e.setDescription(req.description().trim());
        e.setComment(req.comment() == null || req.comment().isBlank() ? null : req.comment().trim());
    }

    /** Une journée couverte par un rapport soumis ou validé n'est plus modifiable. */
    private void ensureUnlocked(AppUser consultant, LocalDate date) {
        reports.findCovering(consultant.getId(), date).stream()
                .filter(r -> r.getStatus().locksEntries())
                .findFirst()
                .ifPresent(r -> {
                    throw ApiException.conflict("Le " + date + " est couvert par un rapport "
                            + (r.getStatus() == ReportStatus.VALIDE ? "validé" : "soumis")
                            + ". Retirez la soumission ou demandez une correction.");
                });
    }

    private WorkEntry own(Long id) {
        WorkEntry e = entries.findById(id).orElseThrow(() -> ApiException.notFound("Saisie"));
        if (!Objects.equals(e.getConsultant().getId(), current.principal().id())) {
            throw ApiException.forbidden();
        }
        return e;
    }
}
