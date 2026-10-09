package sn.yas.itfactory.timesheet.domain;

import jakarta.persistence.*;

import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;

@Entity
public class Project {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String name;

    /** Colonne « Business Unit » de la fiche de temps. */
    private String businessUnit;

    @ManyToOne(fetch = FetchType.EAGER)
    private AppUser pm;

    private boolean active = true;

    /** Architecte : dépose les documents du projet (specs, Word, Excel, PDF, code…). */
    @ManyToOne(fetch = FetchType.EAGER)
    private AppUser architect;

    /** Lead dev : assigne les tâches aux développeurs et valide leurs estimations. */
    @ManyToOne(fetch = FetchType.EAGER)
    private AppUser leadDev;

    /** Développeurs du projet : accès aux documents, tâches assignées par le lead dev. */
    @ManyToMany(fetch = FetchType.EAGER)
    @JoinTable(name = "project_member")
    private Set<AppUser> members = new LinkedHashSet<>();

    public boolean isArchitect(Long userId) {
        return architect != null && Objects.equals(architect.getId(), userId);
    }

    public boolean isLeadDev(Long userId) {
        return leadDev != null && Objects.equals(leadDev.getId(), userId);
    }

    public boolean isPm(Long userId) {
        return pm != null && Objects.equals(pm.getId(), userId);
    }

    public boolean isMember(Long userId) {
        return members.stream().anyMatch(u -> Objects.equals(u.getId(), userId));
    }

    /** Fait partie de l'équipe projet (PM, architecte, lead dev ou développeur). */
    public boolean involves(Long userId) {
        return isPm(userId) || isArchitect(userId) || isLeadDev(userId) || isMember(userId);
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getBusinessUnit() { return businessUnit; }
    public void setBusinessUnit(String businessUnit) { this.businessUnit = businessUnit; }
    public AppUser getPm() { return pm; }
    public void setPm(AppUser pm) { this.pm = pm; }
    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }
    public AppUser getArchitect() { return architect; }
    public void setArchitect(AppUser architect) { this.architect = architect; }
    public AppUser getLeadDev() { return leadDev; }
    public void setLeadDev(AppUser leadDev) { this.leadDev = leadDev; }
    public Set<AppUser> getMembers() { return members; }
    public void setMembers(Set<AppUser> members) { this.members = members; }
}
