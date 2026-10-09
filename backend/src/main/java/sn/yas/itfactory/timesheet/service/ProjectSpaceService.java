package sn.yas.itfactory.timesheet.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import sn.yas.itfactory.timesheet.config.AppProperties;
import sn.yas.itfactory.timesheet.domain.*;
import sn.yas.itfactory.timesheet.repository.DocumentCommentRepository;
import sn.yas.itfactory.timesheet.repository.ProjectDocumentRepository;
import sn.yas.itfactory.timesheet.repository.ProjectRepository;
import sn.yas.itfactory.timesheet.repository.TaskRepository;
import sn.yas.itfactory.timesheet.security.UserPrincipal;
import sn.yas.itfactory.timesheet.web.ApiException;
import sn.yas.itfactory.timesheet.web.dto.Dtos.*;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.*;

/**
 * Espace projet : équipe (PM, architecte, lead dev, développeurs) et documents partagés.
 * L'architecte, le lead dev et le PM déposent les documents ; toute l'équipe du projet les consulte.
 */
@Service
@Transactional
public class ProjectSpaceService {

    private static final Map<DocumentCategory, String> CATEGORY = Map.of(
            DocumentCategory.TDR, "TDR", DocumentCategory.REUNION, "Réunion",
            DocumentCategory.SPECIFICATION, "Spécification", DocumentCategory.ARCHITECTURE, "Architecture",
            DocumentCategory.CODE, "Code", DocumentCategory.AUTRE, "Autre");

    /** Fichier à télécharger : nom d'origine, type et chemin sur disque. */
    public record StoredFile(String fileName, String contentType, long size, Path path) {
    }

    private final ProjectRepository projects;
    private final ProjectDocumentRepository documents;
    private final TaskRepository tasks;
    private final CurrentUser current;
    private final MailService mail;
    private final DocumentCommentRepository comments;
    private final Path root;

    public ProjectSpaceService(ProjectRepository projects, ProjectDocumentRepository documents, TaskRepository tasks,
                               CurrentUser current, MailService mail, AppProperties properties,
                               DocumentCommentRepository comments) {
        this.projects = projects;
        this.documents = documents;
        this.comments = comments;
        this.tasks = tasks;
        this.current = current;
        this.mail = mail;
        String dir = properties.documents() == null ? null : properties.documents().dir();
        this.root = Path.of(dir == null || dir.isBlank() ? "data/documents" : dir).toAbsolutePath().normalize();
    }

    /** Projets actifs dont je fais partie (tous pour le Head of Office et l'Admin), avec mes rôles. */
    @Transactional(readOnly = true)
    public List<ProjectSpaceDto> spaces() {
        UserPrincipal me = current.principal();
        return projects.findAllByOrderByNameAsc().stream()
                .filter(Project::isActive)
                .filter(p -> me.seesEveryone() || p.involves(me.id()))
                .map(p -> space(p, me))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<ProjectDocumentDto> list(Long projectId) {
        Project p = accessible(projectId);
        UserPrincipal me = current.principal();
        List<ProjectDocument> list = documents.findByProjectIdOrderByUploadedAtDescIdDesc(p.getId());
        Map<Long, Long> counts = new HashMap<>();
        if (!list.isEmpty()) {
            comments.countsByDocument(list.stream().map(ProjectDocument::getId).toList())
                    .forEach(r -> counts.put((Long) r[0], ((Number) r[1]).longValue()));
        }
        return list.stream()
                .map(d -> ProjectDocumentDto.of(d, counts.getOrDefault(d.getId(), 0L), canDelete(d, me)))
                .toList();
    }

    // ---- Discussion d'un document : les développeurs posent leurs questions, le lead dev et l'architecte expliquent.

    @Transactional(readOnly = true)
    public List<DocumentCommentDto> comments(Long projectId, Long documentId) {
        ProjectDocument d = document(projectId, documentId);
        UserPrincipal me = current.principal();
        return comments.findByDocumentIdOrderByCreatedAtAscIdAsc(d.getId()).stream()
                .map(c -> commentDto(c, d.getProject(), me)).toList();
    }

    /**
     * Question ou explication. Sont prévenus par e-mail : l'architecte, le lead dev et tous ceux qui ont déjà
     * participé à la discussion (sauf l'auteur).
     */
    public DocumentCommentDto comment(Long projectId, Long documentId, String text) {
        ProjectDocument d = document(projectId, documentId);
        Project p = d.getProject();
        AppUser me = current.user();
        DocumentComment c = new DocumentComment();
        c.setDocument(d);
        c.setAuthor(me);
        c.setText(text.trim());
        comments.save(c);

        Set<String> to = new LinkedHashSet<>();
        for (AppUser u : new AppUser[]{p.getArchitect(), p.getLeadDev(), d.getUploadedBy()}) {
            if (u != null) {
                to.add(u.getEmail());
            }
        }
        comments.findByDocumentIdOrderByCreatedAtAscIdAsc(d.getId()).forEach(x -> to.add(x.getAuthor().getEmail()));
        to.remove(me.getEmail());
        if (!to.isEmpty()) {
            String role = roleOn(p, me.getId());
            boolean explains = !"DEV".equals(role);
            mail.send(List.copyOf(to), List.of(),
                    (explains ? "Explication sur " : "Question sur ") + d.getFileName() + " · " + p.getName(),
                    "<p>" + me.getFullName() + (explains ? " a répondu" : " pose une question")
                            + " sur le document <b>" + escape(d.getFileName()) + "</b> du projet <b>" + escape(p.getName())
                            + "</b> :</p><blockquote>" + escape(c.getText()).replace("\n", "<br>")
                            + "</blockquote><p>Répondez dans « Mes projets ».</p>", List.of());
        }
        return commentDto(c, p, current.principal());
    }

    /** Seul l'auteur supprime son message. */
    public void deleteComment(Long projectId, Long documentId, Long commentId) {
        ProjectDocument d = document(projectId, documentId);
        DocumentComment c = comments.findById(commentId)
                .filter(x -> Objects.equals(x.getDocument().getId(), d.getId()))
                .orElseThrow(() -> ApiException.notFound("Message"));
        if (!Objects.equals(c.getAuthor().getId(), current.principal().id())) {
            throw ApiException.forbidden();
        }
        comments.delete(c);
    }

    private DocumentCommentDto commentDto(DocumentComment c, Project p, UserPrincipal me) {
        AppUser a = c.getAuthor();
        return new DocumentCommentDto(c.getId(), a.getId(), a.getFullName(), roleOn(p, a.getId()), c.getText(),
                c.getCreatedAt(), Objects.equals(a.getId(), me.id()));
    }

    /** Rôle d'un utilisateur sur le projet, le plus élevé s'il en a plusieurs. */
    private static String roleOn(Project p, Long userId) {
        if (p.isArchitect(userId)) {
            return "ARCHITECTE";
        }
        if (p.isLeadDev(userId)) {
            return "LEAD_DEV";
        }
        if (p.isPm(userId)) {
            return "PM";
        }
        return p.isMember(userId) ? "DEV" : null;
    }

    /** Dépose un ou plusieurs fichiers ; l'équipe du projet est prévenue par e-mail. */
    public List<ProjectDocumentDto> upload(Long projectId, List<MultipartFile> files, String description,
                                           DocumentCategory category) {
        Project p = accessible(projectId);
        UserPrincipal me = current.principal();
        if (!canUpload(p, me)) {
            throw ApiException.forbidden();
        }
        List<MultipartFile> real = files == null ? List.of() : files.stream().filter(f -> !f.isEmpty()).toList();
        if (real.isEmpty()) {
            throw ApiException.badRequest("Choisissez au moins un fichier");
        }
        AppUser uploader = current.user();
        String desc = description == null || description.isBlank() ? null : description.trim();
        if (desc != null && desc.length() > 1000) {
            throw ApiException.badRequest("Description de 1000 caractères maximum");
        }
        List<ProjectDocument> saved = new ArrayList<>();
        List<Path> written = new ArrayList<>();
        try {
            for (MultipartFile f : real) {
                String key = p.getId() + "/" + UUID.randomUUID();
                Path target = resolve(key);
                Files.createDirectories(target.getParent());
                try (InputStream in = f.getInputStream()) {
                    Files.copy(in, target, StandardCopyOption.REPLACE_EXISTING);
                }
                written.add(target);
                ProjectDocument d = new ProjectDocument();
                d.setProject(p);
                d.setUploadedBy(uploader);
                d.setFileName(cleanName(f.getOriginalFilename()));
                d.setContentType(f.getContentType());
                d.setSize(f.getSize());
                d.setStorageKey(key);
                d.setDescription(desc);
                d.setCategory(category == null ? DocumentCategory.AUTRE : category);
                saved.add(documents.save(d));
            }
        } catch (IOException | RuntimeException e) {
            written.forEach(ProjectSpaceService::deleteQuietly);
            if (e instanceof ApiException api) {
                throw api;
            }
            throw new ApiException(org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR, "Enregistrement du fichier impossible");
        }
        notifyTeam(p, uploader, saved, desc);
        return saved.stream().map(d -> ProjectDocumentDto.of(d, 0, true)).toList();
    }

    @Transactional(readOnly = true)
    public StoredFile download(Long projectId, Long documentId) {
        ProjectDocument d = document(projectId, documentId);
        Path path = resolve(d.getStorageKey());
        if (!Files.isRegularFile(path)) {
            throw ApiException.notFound("Fichier");
        }
        return new StoredFile(d.getFileName(), d.getContentType(), d.getSize(), path);
    }

    /** Supprimé par son auteur, l'architecte, le PM du projet, le Head of Office ou l'Admin. */
    public void delete(Long projectId, Long documentId) {
        ProjectDocument d = document(projectId, documentId);
        if (!canDelete(d, current.principal())) {
            throw ApiException.forbidden();
        }
        comments.deleteByDocument(d.getId());
        documents.delete(d);
        deleteQuietly(resolve(d.getStorageKey()));
        AppUser author = d.getUploadedBy();
        if (author != null && author.isActive() && !Objects.equals(author.getId(), current.principal().id())) {
            mail.send(List.of(author.getEmail()), List.of(), "Document supprimé sur " + d.getProject().getName(),
                    "<p>Bonjour " + author.getFirstName() + ",</p><p>" + current.user().getFullName() + " a supprimé le document <b>"
                            + d.getFileName().replace("<", "&lt;") + "</b> que vous aviez déposé sur le projet "
                            + d.getProject().getName() + ".</p>", List.of());
        }
    }

    private ProjectSpaceDto space(Project p, UserPrincipal me) {
        List<String> roles = new ArrayList<>();
        if (p.isPm(me.id())) {
            roles.add("PM");
        }
        if (p.isArchitect(me.id())) {
            roles.add("ARCHITECTE");
        }
        if (p.isLeadDev(me.id())) {
            roles.add("LEAD_DEV");
        }
        if (p.isMember(me.id())) {
            roles.add("DEV");
        }
        boolean approves = p.isLeadDev(me.id()) || p.isPm(me.id()) || me.seesEveryone();
        return new ProjectSpaceDto(ProjectDto.of(p), roles, canUpload(p, me), p.isLeadDev(me.id()) || me.isManager(),
                documents.countByProjectId(p.getId()),
                approves ? tasks.countByProjectIdAndEstimateStatus(p.getId(), EstimateStatus.PROPOSEE) : 0);
    }

    private boolean canUpload(Project p, UserPrincipal me) {
        return me.seesEveryone() || p.isArchitect(me.id()) || p.isLeadDev(me.id()) || p.isPm(me.id());
    }

    private boolean canDelete(ProjectDocument d, UserPrincipal me) {
        Project p = d.getProject();
        boolean author = d.getUploadedBy() != null && Objects.equals(d.getUploadedBy().getId(), me.id());
        return author || me.seesEveryone() || p.isArchitect(me.id()) || p.isPm(me.id());
    }

    private Project accessible(Long projectId) {
        Project p = projects.findById(projectId).orElseThrow(() -> ApiException.notFound("Projet"));
        if (!current.canAccess(p)) {
            throw ApiException.forbidden();
        }
        return p;
    }

    private ProjectDocument document(Long projectId, Long documentId) {
        accessible(projectId);
        return documents.findById(documentId)
                .filter(d -> Objects.equals(d.getProject().getId(), projectId))
                .orElseThrow(() -> ApiException.notFound("Document"));
    }

    private Path resolve(String key) {
        Path path = root.resolve(key).normalize();
        if (!path.startsWith(root)) {
            throw ApiException.badRequest("Chemin invalide");
        }
        return path;
    }

    private void notifyTeam(Project p, AppUser by, List<ProjectDocument> docs, String desc) {
        Set<String> to = new LinkedHashSet<>();
        p.getMembers().forEach(u -> to.add(u.getEmail()));
        for (AppUser u : new AppUser[]{p.getLeadDev(), p.getArchitect()}) {
            if (u != null) {
                to.add(u.getEmail());
            }
        }
        to.remove(by.getEmail());
        if (to.isEmpty()) {
            return;
        }
        StringBuilder list = new StringBuilder("<ul>");
        docs.forEach(d -> list.append("<li>").append(escape(d.getFileName())).append(" (")
                .append(CATEGORY.get(d.getCategory())).append(")</li>"));
        list.append("</ul>");
        mail.send(List.copyOf(to), List.of(), "Nouveau(x) document(s) sur " + p.getName(),
                "<p>" + by.getFullName() + " a déposé " + (docs.size() > 1 ? docs.size() + " documents" : "un document")
                        + " sur le projet <b>" + escape(p.getName()) + "</b> :</p>" + list
                        + (desc == null ? "" : "<blockquote>" + escape(desc) + "</blockquote>")
                        + "<p>Retrouvez-les dans votre tableau de bord ou « Mes projets ». Une question ? "
                        + "Posez-la sous le document : le lead dev et l'architecte vous répondent.</p>", List.of());
    }

    /** Garde seulement le nom du fichier (pas de chemin), sans caractères de contrôle. */
    static String cleanName(String original) {
        String name = original == null ? "" : original.replace('\\', '/');
        name = name.substring(name.lastIndexOf('/') + 1).replaceAll("[\\p{Cntrl}\"]", "").trim();
        if (name.isEmpty()) {
            name = "document";
        }
        return name.length() > 200 ? name.substring(name.length() - 200) : name;
    }

    private static void deleteQuietly(Path path) {
        try {
            Files.deleteIfExists(path);
        } catch (IOException ignored) {
            // Fichier orphelin : sans conséquence pour l'application.
        }
    }

    private static String escape(String s) {
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }
}
