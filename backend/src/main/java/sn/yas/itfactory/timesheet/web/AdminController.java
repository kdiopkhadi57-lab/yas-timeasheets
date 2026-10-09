package sn.yas.itfactory.timesheet.web;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import sn.yas.itfactory.timesheet.service.AdminService;
import sn.yas.itfactory.timesheet.web.dto.Dtos.*;

import java.util.List;

@RestController
@RequestMapping("/api")
public class AdminController {

    private final AdminService admin;

    public AdminController(AdminService admin) {
        this.admin = admin;
    }

    @GetMapping("/users")
    @PreAuthorize("hasAnyRole('ADMIN','HEAD_OF_OFFICE','PM')")
    public List<UserDto> users() {
        return admin.listUsers();
    }

    /** Consultants que je peux suivre / à qui je peux assigner des tâches. */
    @GetMapping("/users/team")
    public List<UserDto> team() {
        return admin.team();
    }

    /** L'admin et le Head of Office créent tout le monde ; un manager crée les développeurs de son équipe. */
    @PostMapping("/users")
    @PreAuthorize("hasAnyRole('ADMIN','HEAD_OF_OFFICE','PM')")
    @ResponseStatus(HttpStatus.CREATED)
    public UserDto createUser(@Valid @RequestBody UserRequest req) {
        return admin.createUser(req);
    }

    @PutMapping("/users/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','HEAD_OF_OFFICE','PM')")
    public UserDto updateUser(@PathVariable Long id, @Valid @RequestBody UserRequest req) {
        return admin.updateUser(id, req);
    }

    @GetMapping("/projects")
    public List<ProjectDto> projects() {
        return admin.listProjects();
    }

    @PostMapping("/projects")
    @PreAuthorize("hasAnyRole('ADMIN','HEAD_OF_OFFICE','PM')")
    @ResponseStatus(HttpStatus.CREATED)
    public ProjectDto createProject(@Valid @RequestBody ProjectRequest req) {
        return admin.createProject(req);
    }

    @PutMapping("/projects/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','HEAD_OF_OFFICE','PM')")
    public ProjectDto updateProject(@PathVariable Long id, @Valid @RequestBody ProjectRequest req) {
        return admin.updateProject(id, req);
    }
}
