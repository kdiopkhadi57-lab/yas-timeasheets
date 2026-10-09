package sn.yas.itfactory.timesheet.web;

import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import jakarta.validation.Valid;
import sn.yas.itfactory.timesheet.domain.DocumentCategory;
import sn.yas.itfactory.timesheet.service.ProjectSpaceService;
import sn.yas.itfactory.timesheet.web.dto.Dtos.*;

import java.nio.charset.StandardCharsets;
import java.util.List;

/** Mes projets : équipe et documents partagés (Word, Excel, PDF, code…). */
@RestController
@RequestMapping("/api/projects")
public class ProjectSpaceController {

    private final ProjectSpaceService spaces;

    public ProjectSpaceController(ProjectSpaceService spaces) {
        this.spaces = spaces;
    }

    @GetMapping("/spaces")
    public List<ProjectSpaceDto> spaces() {
        return spaces.spaces();
    }

    @GetMapping("/{id}/documents")
    public List<ProjectDocumentDto> documents(@PathVariable Long id) {
        return spaces.list(id);
    }

    @PostMapping(value = "/{id}/documents", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    public List<ProjectDocumentDto> upload(@PathVariable Long id, @RequestParam("files") List<MultipartFile> files,
                                           @RequestParam(required = false) String description,
                                           @RequestParam(required = false) DocumentCategory category) {
        return spaces.upload(id, files, description, category);
    }

    /** Questions des développeurs et explications du lead dev / de l'architecte. */
    @GetMapping("/{id}/documents/{documentId}/comments")
    public List<DocumentCommentDto> comments(@PathVariable Long id, @PathVariable Long documentId) {
        return spaces.comments(id, documentId);
    }

    @PostMapping("/{id}/documents/{documentId}/comments")
    @ResponseStatus(HttpStatus.CREATED)
    public DocumentCommentDto comment(@PathVariable Long id, @PathVariable Long documentId,
                                      @Valid @RequestBody CommentRequest req) {
        return spaces.comment(id, documentId, req.text());
    }

    @DeleteMapping("/{id}/documents/{documentId}/comments/{commentId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteComment(@PathVariable Long id, @PathVariable Long documentId, @PathVariable Long commentId) {
        spaces.deleteComment(id, documentId, commentId);
    }

    /** Toujours en pièce jointe : un fichier HTML ou SVG déposé n'est jamais affiché par le navigateur. */
    @GetMapping("/{id}/documents/{documentId}")
    public ResponseEntity<Resource> download(@PathVariable Long id, @PathVariable Long documentId) {
        ProjectSpaceService.StoredFile f = spaces.download(id, documentId);
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .contentLength(f.size())
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                        .filename(f.fileName(), StandardCharsets.UTF_8).build().toString())
                .body(new FileSystemResource(f.path()));
    }

    @DeleteMapping("/{id}/documents/{documentId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id, @PathVariable Long documentId) {
        spaces.delete(id, documentId);
    }
}
