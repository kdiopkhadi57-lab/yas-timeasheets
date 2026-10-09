package sn.yas.itfactory.timesheet.web;

import jakarta.validation.Valid;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.web.bind.annotation.*;
import sn.yas.itfactory.timesheet.security.JwtService;
import sn.yas.itfactory.timesheet.security.UserPrincipal;
import sn.yas.itfactory.timesheet.service.AdminService;
import sn.yas.itfactory.timesheet.service.CurrentUser;
import sn.yas.itfactory.timesheet.web.dto.Dtos.*;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthenticationManager authManager;
    private final JwtService jwt;
    private final CurrentUser current;
    private final AdminService admin;

    public AuthController(AuthenticationManager authManager, JwtService jwt, CurrentUser current, AdminService admin) {
        this.authManager = authManager;
        this.jwt = jwt;
        this.current = current;
        this.admin = admin;
    }

    @PostMapping("/login")
    public LoginResponse login(@Valid @RequestBody LoginRequest req) {
        var auth = authManager.authenticate(
                new UsernamePasswordAuthenticationToken(req.email().trim().toLowerCase(), req.password()));
        UserPrincipal principal = (UserPrincipal) auth.getPrincipal();
        org.springframework.security.core.context.SecurityContextHolder.getContext().setAuthentication(auth);
        return new LoginResponse(jwt.generate(principal), UserDto.of(current.user()));
    }

    @GetMapping("/me")
    public UserDto me() {
        return UserDto.of(current.user());
    }

    @PostMapping("/password")
    public void changePassword(@Valid @RequestBody ChangePasswordRequest req) {
        admin.changePassword(req);
    }
}
