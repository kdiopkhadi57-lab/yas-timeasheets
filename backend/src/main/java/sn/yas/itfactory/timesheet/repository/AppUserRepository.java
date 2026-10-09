package sn.yas.itfactory.timesheet.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import sn.yas.itfactory.timesheet.domain.AppUser;
import sn.yas.itfactory.timesheet.domain.Role;

import java.util.List;
import java.util.Optional;

public interface AppUserRepository extends JpaRepository<AppUser, Long> {

    Optional<AppUser> findByEmailIgnoreCase(String email);

    boolean existsByEmailIgnoreCase(String email);

    boolean existsByMatriculeIgnoreCase(String matricule);

    List<AppUser> findByActiveTrueOrderByLastNameAsc();

    List<AppUser> findByRoleAndActiveTrue(Role role);
}
