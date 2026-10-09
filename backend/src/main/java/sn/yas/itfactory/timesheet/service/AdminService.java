package sn.yas.itfactory.timesheet.service;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import sn.yas.itfactory.timesheet.domain.AppUser;
import sn.yas.itfactory.timesheet.domain.Project;
import sn.yas.itfactory.timesheet.domain.Role;
import sn.yas.itfactory.timesheet.repository.AppUserRepository;
import sn.yas.itfactory.timesheet.repository.ProjectRepository;
import sn.yas.itfactory.timesheet.security.UserPrincipal;
import sn.yas.itfactory.timesheet.web.ApiException;
import sn.yas.itfactory.timesheet.web.dto.Dtos.*;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/** Référentiel : utilisateurs et projets. Les créations et changements sont notifiés par e-mail aux intéressés. */
@Service
@Transactional
public class AdminService {

    private final AppUserRepository users;
    private final ProjectRepository projects;
    private final PasswordEncoder encoder;
    private final CurrentUser current;
    private final GitSyncService gitSync;
    private final MailService mail;

    public AdminService(AppUserRepository users, ProjectRepository projects, PasswordEncoder encoder, CurrentUser current,
                        GitSyncService gitSync, MailService mail) {
        this.mail = mail;
        this.users = users;
        this.projects = projects;
        this.encoder = encoder;
        this.current = current;
        this.gitSync = gitSync;
    }

    @Transactional(readOnly = true)
    public List<UserDto> listUsers() {
        Map<Long, List<ProjectRefDto>> byUser = projectsByUser();
        return users.findAll().stream()
                .sorted((a, b) -> a.getLastName().compareToIgnoreCase(b.getLastName()))
                .map(u -> UserDto.of(u, byUser.getOrDefault(u.getId(), List.of()))).toList();
    }

    @Transactional(readOnly = true)
    public List<UserDto> team() {
        Map<Long, List<ProjectRefDto>> byUser = projectsByUser();
        return current.team().stream().map(u -> UserDto.of(u, byUser.getOrDefault(u.getId(), List.of()))).toList();
    }

    /** Projets de chaque utilisateur : développeur, lead dev ou architecte. */
    private Map<Long, List<ProjectRefDto>> projectsByUser() {
        Map<Long, List<ProjectRefDto>> out = new HashMap<>();
        for (Project p : projects.findAllByOrderByNameAsc()) {
            ProjectRefDto ref = new ProjectRefDto(p.getId(), p.getName());
            Set<Long> ids = new LinkedHashSet<>();
            p.getMembers().forEach(m -> ids.add(m.getId()));
            if (p.getLeadDev() != null) {
                ids.add(p.getLeadDev().getId());
            }
            if (p.getArchitect() != null) {
                ids.add(p.getArchitect().getId());
            }
            ids.forEach(id -> out.computeIfAbsent(id, k -> new ArrayList<>()).add(ref));
        }
        return out;
    }

    private UserDto dto(AppUser u) {
        return UserDto.of(u, projectsByUser().getOrDefault(u.getId(), List.of()));
    }

    /**
     * Admin : tous les comptes. Head of Office : tous sauf les administrateurs.
     * Manager : les développeurs (consultant, lead dev) de son équipe, rattachés à lui ou à un manager sous lui.
     */
    private void requireCanManage(AppUser target, UserRequest req) {
        UserPrincipal me = current.principal();
        if (me.role() == Role.ADMIN) {
            return;
        }
        if (me.role() == Role.HEAD_OF_OFFICE) {
            if (req.role() == Role.ADMIN || (target != null && target.getRole() == Role.ADMIN)) {
                throw ApiException.forbidden();
            }
            return;
        }
        if (me.role() != Role.PM) {
            throw ApiException.forbidden();
        }
        if (target != null && (!target.isManagedBy(me.id()) || !isDeveloper(target.getRole()))) {
            throw ApiException.badRequest("Vous ne pouvez modifier que les développeurs de votre équipe");
        }
        if (!isDeveloper(req.role())) {
            throw ApiException.badRequest("Un manager crée des développeurs (rôle Consultant ou Lead dev)");
        }
        if (req.pmId() == null) {
            throw ApiException.badRequest("Choisissez le manager du développeur");
        }
        AppUser manager = users.findById(req.pmId()).orElseThrow(() -> ApiException.notFound("Manager"));
        if (!Objects.equals(manager.getId(), me.id()) && !manager.isManagedBy(me.id())) {
            throw ApiException.badRequest("Le développeur doit être rattaché à vous ou à un manager de votre équipe");
        }
    }

    private static boolean isDeveloper(Role r) {
        return r == Role.CONSULTANT || r == Role.LEAD_DEV;
    }

    /**
     * Projets du développeur : il est ajouté aux projets cochés et retiré des autres (en tant que développeur ;
     * les rôles de lead dev et d'architecte se gèrent sur le projet). Chaque nouveau projet lui est notifié.
     */
    private void applyProjects(AppUser u, List<Long> projectIds) {
        if (projectIds == null) {
            return;
        }
        Set<Long> wanted = new LinkedHashSet<>(projectIds);
        if (!wanted.isEmpty() && !isDeveloper(u.getRole())) {
            throw ApiException.badRequest("Seul un développeur (Consultant ou Lead dev) est ajouté aux projets ici");
        }
        for (Project p : projects.findAllByOrderByNameAsc()) {
            boolean member = p.isMember(u.getId());
            if (wanted.contains(p.getId()) && !member && !p.isLeadDev(u.getId())) {
                if (!p.isActive()) {
                    throw ApiException.badRequest("Le projet " + p.getName() + " est archivé");
                }
                Set<Long> before = teamIds(p);
                p.getMembers().add(u);
                notifyTeam(p, before, false);
            } else if (!wanted.contains(p.getId()) && member) {
                p.getMembers().removeIf(m -> Objects.equals(m.getId(), u.getId()));
            }
            wanted.remove(p.getId());
        }
        if (!wanted.isEmpty()) {
            throw ApiException.notFound("Projet");
        }
    }

    public UserDto createUser(UserRequest req) {
        requireCanManage(null, req);
        if (users.existsByEmailIgnoreCase(req.email())) {
            throw ApiException.conflict("Cet e-mail est déjà utilisé");
        }
        if (users.existsByMatriculeIgnoreCase(req.matricule())) {
            throw ApiException.conflict("Ce matricule est déjà utilisé");
        }
        if (req.password() == null || req.password().length() < 6) {
            throw ApiException.badRequest("Mot de passe de 6 caractères minimum");
        }
        AppUser u = new AppUser();
        applyUser(u, req);
        users.save(u);
        gitSync.assignAuthor(u);
        String profile = profileText(u);
        mail.send(List.of(u.getEmail()), u.getPm() == null ? List.of() : List.of(u.getPm().getEmail()),
                "Votre compte Fiches de temps YAS IT-FACTORY",
                "<p>Bonjour " + escape(u.getFirstName()) + ",</p><p>Votre compte a été créé par " + current.user().getFullName()
                        + " : identifiant <b>" + u.getEmail() + "</b>, rôle <b>" + roleLabel(u.getRole()) + "</b>"
                        + (u.getPm() == null ? "" : ", manager <b>" + u.getPm().getFullName() + "</b>")
                        + (profile.isEmpty() ? "" : ", profil <b>" + profile + "</b>")
                        + ".</p><p>Votre mot de passe vous est communiqué par l'administrateur ; changez-le à la première connexion.</p>",
                List.of());
        applyProjects(u, req.projectIds());
        return dto(u);
    }

    public UserDto updateUser(Long id, UserRequest req) {
        AppUser u = users.findById(id).orElseThrow(() -> ApiException.notFound("Utilisateur"));
        requireCanManage(u, req);
        if (!u.getEmail().equalsIgnoreCase(req.email()) && users.existsByEmailIgnoreCase(req.email())) {
            throw ApiException.conflict("Cet e-mail est déjà utilisé");
        }
        if (!u.getMatricule().equalsIgnoreCase(req.matricule()) && users.existsByMatriculeIgnoreCase(req.matricule())) {
            throw ApiException.conflict("Ce matricule est déjà utilisé");
        }
        AppUser oldManager = u.getPm();
        Role oldRole = u.getRole();
        boolean wasActive = u.isActive();
        applyUser(u, req);
        gitSync.assignAuthor(u);
        notifyUserChanges(u, oldManager, oldRole, wasActive, req.password() != null && !req.password().isBlank());
        applyProjects(u, req.projectIds());
        return dto(u);
    }

    /** Changement de manager, de rôle, d'état du compte ou de mot de passe : l'utilisateur et ses managers sont prévenus. */
    private void notifyUserChanges(AppUser u, AppUser oldManager, Role oldRole, boolean wasActive, boolean passwordReset) {
        List<String> changes = new ArrayList<>();
        Set<String> cc = new LinkedHashSet<>();
        Long oldId = oldManager == null ? null : oldManager.getId();
        Long newId = u.getPm() == null ? null : u.getPm().getId();
        if (!Objects.equals(oldId, newId)) {
            changes.add("Manager : " + (oldManager == null ? "aucun" : oldManager.getFullName()) + " → "
                    + (u.getPm() == null ? "aucun" : "<b>" + u.getPm().getFullName() + "</b>"
                    + " (vos rapports hebdo lui sont désormais adressés)"));
            if (oldManager != null) {
                cc.add(oldManager.getEmail());
            }
            if (u.getPm() != null) {
                cc.add(u.getPm().getEmail());
            }
        }
        if (oldRole != u.getRole()) {
            changes.add("Rôle : " + roleLabel(oldRole) + " → <b>" + roleLabel(u.getRole()) + "</b>");
        }
        if (wasActive != u.isActive()) {
            changes.add(u.isActive() ? "Compte <b>réactivé</b>" : "Compte <b>désactivé</b>");
        }
        if (passwordReset) {
            changes.add("Mot de passe <b>réinitialisé</b> par l'administrateur");
        }
        if (changes.isEmpty()) {
            return;
        }
        if (u.getPm() != null) {
            cc.add(u.getPm().getEmail());
        }
        cc.remove(u.getEmail());
        mail.send(List.of(u.getEmail()), List.copyOf(cc), "Mise à jour de votre compte Fiches de temps",
                "<p>Bonjour " + escape(u.getFirstName()) + ",</p><p>" + current.user().getFullName()
                        + " a modifié votre compte :</p><ul><li>" + String.join("</li><li>", changes) + "</li></ul>", List.of());
    }

    public void changePassword(ChangePasswordRequest req) {
        AppUser me = current.user();
        if (!encoder.matches(req.currentPassword(), me.getPasswordHash())) {
            throw ApiException.badRequest("Mot de passe actuel incorrect");
        }
        if (req.newPassword().length() < 6) {
            throw ApiException.badRequest("Mot de passe de 6 caractères minimum");
        }
        me.setPasswordHash(encoder.encode(req.newPassword()));
        mail.send(List.of(me.getEmail()), List.of(), "Votre mot de passe a été modifié",
                "<p>Bonjour " + escape(me.getFirstName()) + ",</p><p>Le mot de passe de votre compte Fiches de temps vient d'être modifié."
                        + " Si vous n'êtes pas à l'origine de ce changement, prévenez immédiatement l'administrateur.</p>", List.of());
    }

    private void applyUser(AppUser u, UserRequest req) {
        u.setFirstName(req.firstName().trim());
        u.setLastName(req.lastName().trim());
        u.setMatricule(req.matricule().trim().toUpperCase());
        u.setProfil(req.profil());
        u.setEmail(req.email().trim().toLowerCase());
        u.setRole(req.role());
        u.setActive(req.active() == null || req.active());
        u.setGitIdentities(req.gitIdentities() == null || req.gitIdentities().isBlank() ? null : req.gitIdentities().trim());
        if (req.password() != null && !req.password().isBlank()) {
            u.setPasswordHash(encoder.encode(req.password()));
        }
        u.setPm(req.pmId() == null ? null : manager(u, req.pmId()));
        u.setDevProfile(req.devProfile());
        u.setSkills(req.skills());
    }

    /** « Backend · Java, Python ». */
    private static String profileText(AppUser u) {
        List<String> parts = new ArrayList<>();
        if (u.getDevProfile() != null) {
            parts.add(switch (u.getDevProfile()) {
                case BACKEND -> "Backend";
                case FRONTEND -> "Frontend";
                case FULLSTACK -> "Fullstack";
            });
        }
        if (!u.getSkills().isEmpty()) {
            parts.add(String.join(", ", u.getSkills().stream().map(s -> switch (s) {
                case JAVA -> "Java";
                case PHP -> "PHP";
                case TALEND -> "Talend";
                case PYTHON -> "Python";
            }).toList()));
        }
        return String.join(" · ", parts);
    }

    /**
     * Manager d'un utilisateur : un PM (qui peut lui-même avoir un manager) ou le Head of Office.
     * Les architectes sont rattachés au Head of Office. Pas de boucle dans la hiérarchie.
     */
    private AppUser manager(AppUser u, Long managerId) {
        if (managerId.equals(u.getId())) {
            throw ApiException.badRequest("Un utilisateur ne peut pas être son propre manager");
        }
        AppUser m = users.findById(managerId).orElseThrow(() -> ApiException.notFound("Manager"));
        if (u.getRole() == Role.ARCHITECTE && m.getRole() != Role.HEAD_OF_OFFICE) {
            throw ApiException.badRequest("Un architecte est rattaché au Head of Office");
        }
        if (m.getRole() != Role.PM && m.getRole() != Role.HEAD_OF_OFFICE) {
            throw ApiException.badRequest(m.getFullName() + " n'est pas manager (rôle Manager ou Head of Office)");
        }
        if (u.getId() != null && m.isManagedBy(u.getId())) {
            throw ApiException.badRequest(m.getFullName() + " est déjà sous la responsabilité de " + u.getFullName());
        }
        return m;
    }

    @Transactional(readOnly = true)
    public List<ProjectDto> listProjects() {
        return projects.findAllByOrderByNameAsc().stream().map(ProjectDto::of).toList();
    }

    public ProjectDto createProject(ProjectRequest req) {
        if (projects.existsByNameIgnoreCase(req.name().trim())) {
            throw ApiException.conflict("Ce projet existe déjà");
        }
        Project p = new Project();
        applyProject(p, req);
        projects.save(p);
        notifyTeam(p, Set.of(), true);
        return ProjectDto.of(p);
    }

    public ProjectDto updateProject(Long id, ProjectRequest req) {
        Project p = projects.findById(id).orElseThrow(() -> ApiException.notFound("Projet"));
        Set<Long> before = teamIds(p);
        applyProject(p, req);
        notifyTeam(p, before, false);
        return ProjectDto.of(p);
    }

    private static Set<Long> teamIds(Project p) {
        Set<Long> ids = new LinkedHashSet<>();
        for (AppUser u : team(p)) {
            ids.add(u.getId());
        }
        return ids;
    }

    private static List<AppUser> team(Project p) {
        List<AppUser> all = new ArrayList<>();
        for (AppUser u : new AppUser[]{p.getPm(), p.getArchitect(), p.getLeadDev()}) {
            if (u != null) {
                all.add(u);
            }
        }
        all.addAll(p.getMembers());
        return all;
    }

    /** Chaque nouveau membre de l'équipe projet (PM, architecte, lead dev, développeur) est prévenu de son rôle. */
    private void notifyTeam(Project p, Set<Long> before, boolean created) {
        Long me = current.principal().id();
        String by = current.user().getFullName();
        Set<Long> done = new LinkedHashSet<>(before);
        for (AppUser u : team(p)) {
            if (!done.add(u.getId()) || Objects.equals(u.getId(), me)) {
                continue;
            }
            String role = p.isPm(u.getId()) ? "manager" : p.isArchitect(u.getId()) ? "architecte"
                    : p.isLeadDev(u.getId()) ? "lead dev" : "développeur";
            mail.send(List.of(u.getEmail()), List.of(), "Projet " + p.getName() + " : vous êtes " + role,
                    "<p>Bonjour " + escape(u.getFirstName()) + ",</p><p>" + by + (created ? " a créé le projet <b>" : " vous a ajouté au projet <b>")
                            + escape(p.getName()) + "</b>" + (created ? " et vous y a ajouté" : "") + " en tant que <b>" + role
                            + "</b>.</p><p>Retrouvez ses documents et ses tâches dans « Mes projets ».</p>", List.of());
        }
    }

    private void applyProject(Project p, ProjectRequest req) {
        p.setName(req.name().trim());
        p.setBusinessUnit(req.businessUnit());
        p.setActive(req.active() == null || req.active());
        p.setPm(optionalUser(req.pmId(), "Manager"));
        AppUser architect = optionalUser(req.architectId(), "Architecte");
        if (architect != null && architect.getRole() != Role.ARCHITECTE) {
            throw ApiException.badRequest(architect.getFullName() + " n'a pas le rôle Architecte");
        }
        AppUser lead = optionalUser(req.leadDevId(), "Lead dev");
        if (lead != null && lead.getRole() != Role.LEAD_DEV) {
            throw ApiException.badRequest(lead.getFullName() + " n'a pas le rôle Lead dev");
        }
        p.setArchitect(architect);
        p.setLeadDev(lead);
        Set<AppUser> members = new LinkedHashSet<>();
        if (req.memberIds() != null) {
            for (Long id : new LinkedHashSet<>(req.memberIds())) {
                AppUser dev = users.findById(id).orElseThrow(() -> ApiException.notFound("Développeur"));
                if (dev.getRole() != Role.CONSULTANT && dev.getRole() != Role.LEAD_DEV) {
                    throw ApiException.badRequest(dev.getFullName() + " n'est pas développeur (rôle Consultant ou Lead dev)");
                }
                if (lead == null || !lead.getId().equals(dev.getId())) {
                    members.add(dev);
                }
            }
        }
        p.getMembers().clear();
        p.getMembers().addAll(members);
    }

    private AppUser optionalUser(Long id, String what) {
        return id == null ? null : users.findById(id).orElseThrow(() -> ApiException.notFound(what));
    }

    private static String roleLabel(Role r) {
        return switch (r) {
            case CONSULTANT -> "Consultant (développeur)";
            case ARCHITECTE -> "Architecte";
            case LEAD_DEV -> "Lead dev";
            case PM -> "Manager";
            case HEAD_OF_OFFICE -> "Head of Office";
            case ADMIN -> "Administrateur";
        };
    }

    private static String escape(String s) {
        return s == null ? "" : s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }
}
