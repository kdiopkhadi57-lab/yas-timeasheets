package sn.yas.itfactory.timesheet.domain;

import jakarta.persistence.*;

@Entity
@Table(name = "app_user")
public class AppUser {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String firstName;

    @Column(nullable = false)
    private String lastName;

    /** Matricule consultant, ex. C099. */
    @Column(nullable = false, unique = true)
    private String matricule;

    /** Profil affiché sur la fiche, ex. Software Architect. */
    private String profil;

    /** Profil du développeur : backend, frontend ou fullstack. */
    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private DevProfile devProfile;

    /** Technologies (Java, PHP, Talend, Python), séparées par des virgules. */
    @Column(length = 200)
    private String skills;

    @Column(nullable = false, unique = true)
    private String email;

    @Column(nullable = false)
    private String passwordHash;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Role role = Role.CONSULTANT;

    /**
     * Manager direct (PM, ou Head of Office pour les architectes) : reçoit le rapport hebdomadaire.
     * Un manager peut lui-même avoir un manager, qui voit alors aussi les fiches de l'équipe.
     */
    @ManyToOne(fetch = FetchType.EAGER)
    private AppUser pm;

    private boolean active = true;

    /**
     * Autres identités GitLab, séparées par des virgules : e-mails de commit
     * (si différents de l'e-mail ci-dessus) et nom d'utilisateur GitLab.
     */
    @Column(length = 500)
    private String gitIdentities;

    // ---- Coordonnées imprimées sur la facture

    private String phone;

    /** E-mail personnel affiché sur la facture (peut différer de l'e-mail de connexion). */
    private String invoiceEmail;

    @Column(length = 500)
    private String address;

    /** Signature manuscrite (PNG), apposée sur les factures. */
    @Column(length = 1_000_000)
    @Basic(fetch = FetchType.LAZY)
    private byte[] signaturePng;

    /** Chaîne hiérarchique : manager direct, puis son manager, etc. (protégée contre les boucles). */
    public java.util.List<AppUser> managerChain() {
        java.util.List<AppUser> chain = new java.util.ArrayList<>();
        java.util.Set<Long> seen = new java.util.HashSet<>();
        seen.add(id);
        for (AppUser m = pm; m != null && seen.add(m.getId()); m = m.getPm()) {
            chain.add(m);
        }
        return chain;
    }

    /** Ce manager est-il dans la chaîne hiérarchique de l'utilisateur ? */
    public boolean isManagedBy(Long managerId) {
        return managerChain().stream().anyMatch(m -> java.util.Objects.equals(m.getId(), managerId));
    }

    public String getFullName() {
        return firstName + " " + lastName.toUpperCase();
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getFirstName() { return firstName; }
    public void setFirstName(String firstName) { this.firstName = firstName; }
    public String getLastName() { return lastName; }
    public void setLastName(String lastName) { this.lastName = lastName; }
    public String getMatricule() { return matricule; }
    public void setMatricule(String matricule) { this.matricule = matricule; }
    public String getProfil() { return profil; }
    public void setProfil(String profil) { this.profil = profil; }
    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }
    public String getPasswordHash() { return passwordHash; }
    public void setPasswordHash(String passwordHash) { this.passwordHash = passwordHash; }
    public Role getRole() { return role; }
    public void setRole(Role role) { this.role = role; }
    public AppUser getPm() { return pm; }
    public void setPm(AppUser pm) { this.pm = pm; }
    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }
    public String getPhone() { return phone; }
    public void setPhone(String phone) { this.phone = phone; }
    public String getInvoiceEmail() { return invoiceEmail; }
    public void setInvoiceEmail(String invoiceEmail) { this.invoiceEmail = invoiceEmail; }
    public String getAddress() { return address; }
    public void setAddress(String address) { this.address = address; }
    public byte[] getSignaturePng() { return signaturePng; }
    public void setSignaturePng(byte[] signaturePng) { this.signaturePng = signaturePng; }
    public DevProfile getDevProfile() { return devProfile; }
    public void setDevProfile(DevProfile devProfile) { this.devProfile = devProfile; }

    public java.util.List<Skill> getSkills() {
        if (skills == null || skills.isBlank()) {
            return java.util.List.of();
        }
        return java.util.Arrays.stream(skills.split(",")).map(String::trim)
                .filter(s -> java.util.Arrays.stream(Skill.values()).anyMatch(k -> k.name().equals(s)))
                .map(Skill::valueOf).sorted().toList();
    }

    public void setSkills(java.util.Collection<Skill> list) {
        this.skills = list == null || list.isEmpty() ? null
                : list.stream().distinct().sorted().map(Skill::name).collect(java.util.stream.Collectors.joining(","));
    }

    public String getGitIdentities() { return gitIdentities; }
    public void setGitIdentities(String gitIdentities) { this.gitIdentities = gitIdentities; }
}
