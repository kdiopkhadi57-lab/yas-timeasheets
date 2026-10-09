package sn.yas.itfactory.timesheet.service;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import sn.yas.itfactory.timesheet.domain.AppUser;
import sn.yas.itfactory.timesheet.domain.Project;
import sn.yas.itfactory.timesheet.domain.Role;
import sn.yas.itfactory.timesheet.domain.Task;
import sn.yas.itfactory.timesheet.repository.AppUserRepository;
import sn.yas.itfactory.timesheet.security.UserPrincipal;
import sn.yas.itfactory.timesheet.web.ApiException;

import java.util.List;
import java.util.Objects;

/** Utilisateur connecté et règles de visibilité entre consultants, PM et Head of Office. */
@Component
public class CurrentUser {

    private final AppUserRepository users;

    public CurrentUser(AppUserRepository users) {
        this.users = users;
    }

    public UserPrincipal principal() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !(auth.getPrincipal() instanceof UserPrincipal p)) {
            throw new ApiException(org.springframework.http.HttpStatus.UNAUTHORIZED, "Non authentifié");
        }
        return p;
    }

    /** null hors requête authentifiée (tâches planifiées). */
    public UserPrincipal principalOrNull() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return auth != null && auth.getPrincipal() instanceof UserPrincipal p ? p : null;
    }

    public AppUser user() {
        return users.findById(principal().id()).orElseThrow(() -> ApiException.notFound("Utilisateur"));
    }

    /** Peut-on consulter / agir sur les données de ce consultant ? */
    public boolean canSee(AppUser consultant) {
        UserPrincipal me = principal();
        if (Objects.equals(me.id(), consultant.getId()) || me.seesEveryone()) {
            return true;
        }
        // Toute la chaîne hiérarchique : le manager direct et les managers au-dessus de lui.
        return consultant.isManagedBy(me.id());
    }

    /** Tâche visible : celles des consultants qu'on suit, et toutes celles des projets dont on est lead dev ou architecte. */
    public boolean canSeeTask(Task t) {
        Long me = principal().id();
        return canSee(t.getAssignee()) || t.getProject().isLeadDev(me) || t.getProject().isArchitect(me);
    }

    /** Espace projet (documents, équipe) : membres du projet, Head of Office et Admin. */
    public boolean canAccess(Project p) {
        UserPrincipal me = principal();
        return me.seesEveryone() || p.involves(me.id());
    }

    public AppUser requireVisible(Long consultantId) {
        AppUser consultant = consultantId == null ? user()
                : users.findById(consultantId).orElseThrow(() -> ApiException.notFound("Consultant"));
        if (!canSee(consultant)) {
            throw ApiException.forbidden();
        }
        return consultant;
    }

    /**
     * Consultants que l'utilisateur connecté encadre : pour un manager, ses collaborateurs directs et ceux
     * des managers qui lui sont rattachés ; tout le monde pour le Head of Office.
     */
    public List<AppUser> team() {
        UserPrincipal me = principal();
        if (me.seesEveryone()) {
            return users.findByActiveTrueOrderByLastNameAsc().stream()
                    .filter(u -> u.getRole().fillsTimesheet())
                    .toList();
        }
        if (me.role() == Role.PM) {
            return users.findByActiveTrueOrderByLastNameAsc().stream()
                    .filter(u -> u.getRole().fillsTimesheet() && u.isManagedBy(me.id()))
                    .toList();
        }
        return List.of(user());
    }
}
