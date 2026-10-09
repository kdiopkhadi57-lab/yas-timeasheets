package sn.yas.itfactory.timesheet.security;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import sn.yas.itfactory.timesheet.domain.AppUser;
import sn.yas.itfactory.timesheet.domain.Role;

import java.util.Collection;
import java.util.List;

public record UserPrincipal(Long id, String email, String passwordHash, Role role, boolean active) implements UserDetails {

    public static UserPrincipal of(AppUser user) {
        return new UserPrincipal(user.getId(), user.getEmail(), user.getPasswordHash(), user.getRole(), user.isActive());
    }

    public boolean isManager() {
        return role == Role.PM || role == Role.HEAD_OF_OFFICE || role == Role.ADMIN;
    }

    /** Head of Office et Admin voient tous les consultants. */
    public boolean seesEveryone() {
        return role == Role.HEAD_OF_OFFICE || role == Role.ADMIN;
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return List.of(new SimpleGrantedAuthority("ROLE_" + role.name()));
    }

    @Override
    public String getPassword() {
        return passwordHash;
    }

    @Override
    public String getUsername() {
        return email;
    }

    @Override
    public boolean isEnabled() {
        return active;
    }
}
