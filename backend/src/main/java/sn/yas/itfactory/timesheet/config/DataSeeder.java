package sn.yas.itfactory.timesheet.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import sn.yas.itfactory.timesheet.domain.*;
import sn.yas.itfactory.timesheet.repository.*;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;

/** Données de démonstration, créées seulement si la base est vide (app.seed=true). */
@Component
@ConditionalOnProperty(name = "app.seed", havingValue = "true")
public class DataSeeder implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(DataSeeder.class);
    private static final ZoneId DAKAR = ZoneId.of("Africa/Dakar");
    public static final String DEMO_PASSWORD = "Yas@2026";

    private final AppUserRepository users;
    private final ProjectRepository projects;
    private final TaskRepository tasks;
    private final WorkEntryRepository entries;
    private final GitRepoRepository gitRepos;
    private final GitCommitRepository gitCommits;
    private final ReportRepository reports;
    private final TaskActivityRepository activities;
    private final PasswordEncoder encoder;

    public DataSeeder(AppUserRepository users, ProjectRepository projects, TaskRepository tasks,
                      WorkEntryRepository entries, GitRepoRepository gitRepos, GitCommitRepository gitCommits,
                      ReportRepository reports, TaskActivityRepository activities, PasswordEncoder encoder) {
        this.users = users;
        this.projects = projects;
        this.tasks = tasks;
        this.entries = entries;
        this.gitRepos = gitRepos;
        this.gitCommits = gitCommits;
        this.reports = reports;
        this.activities = activities;
        this.encoder = encoder;
    }

    @Override
    @Transactional
    public void run(String... args) {
        if (users.count() > 0) {
            upgradeDemo();
            upgradeManagers();
            demoSkills();
            reportsDemo();
            return;
        }
        AppUser admin = user("Admin", "YAS", "A001", "Administrateur", "admin@yas-itfactory.sn", Role.ADMIN, null);
        AppUser head = user("Toussaint", "Bassene", "H001", "Head of Office", "head@yas-itfactory.sn", Role.HEAD_OF_OFFICE, null);
        // Hiérarchie : les développeurs → Demba FALL → Mouhamadou Moustapha BITEYE ; les architectes → Head of Office.
        AppUser biteye = user("Mouhamadou Moustapha", "Biteye", "P002", "Manager", BITEYE_EMAIL, Role.PM, null);
        AppUser pm = user("Demba", "Fall", "P001", "Manager", "pm@yas-itfactory.sn", Role.PM, biteye);
        AppUser moussa = user("Moussa", "Thioune", "C060", "Lead dev", "moussa@yas-itfactory.sn", Role.LEAD_DEV, pm);
        AppUser fatou = user("Fatou", "Sow", "C072", "Développeuse PHP", "fatou@yas-itfactory.sn", Role.CONSULTANT, pm);
        AppUser cheikh = user("Cheikh", "Fall", "C099", "Software Architect", "cheikh@yas-itfactory.sn", Role.CONSULTANT, pm);
        AppUser papa = papa(head);
        demoSkills();

        Project numflex = project("NumFlex", "Portabilité", pm);
        // Équipe NumFlex : Pape Samba NDOUR architecte (documents), Moussa lead dev (assigne et valide les estimations).
        numflex.setArchitect(papa);
        numflex.setLeadDev(moussa);
        numflex.getMembers().addAll(java.util.List.of(fatou, cheikh));
        Project crm = project("CRM Entreprise", "Digital", pm);
        project("Interne YAS IT-FACTORY", "IT-Factory", head);

        Task t1 = task("Intégration des API de désactivation PORT OUT", numflex, moussa, pm, TaskStatus.EN_COURS, 5);
        Task t2 = task("Tests End-to-end du workflow", numflex, moussa, pm, TaskStatus.A_FAIRE, 8);
        task("Review des merge requests", numflex, moussa, pm, TaskStatus.EN_COURS, null);
        Task t4 = task("Écrans de gestion des contacts", crm, fatou, pm, TaskStatus.EN_COURS, 6);
        task("Export CSV des opportunités", crm, fatou, pm, TaskStatus.A_FAIRE, 12);
        Task t6 = task("Architecture cible NumFlex / ARTP", numflex, cheikh, pm, TaskStatus.EN_COURS, 10);
        plan(t1, TaskPriority.HAUTE, 6, 8.0, 60);
        plan(t2, TaskPriority.NORMALE, 2, 5.0, 0);
        plan(t4, TaskPriority.HAUTE, 9, 10.0, 45);
        plan(t6, TaskPriority.CRITIQUE, 12, 15.0, 35);
        Task blocked = task("Accès VPN à la recette ARTP", numflex, moussa, pm, TaskStatus.BLOQUEE, 1);
        plan(blocked, TaskPriority.CRITIQUE, 4, 1.0, 0);
        Task late = task("Migration des contacts de l'ancien CRM", crm, fatou, pm, TaskStatus.EN_COURS, -2);
        plan(late, TaskPriority.HAUTE, 15, 6.0, 70);

        comment(t6, pm, "Prévoir une revue du dossier avec l'ARTP avant la fin du mois.", 3);
        comment(t6, cheikh, "Diagrammes de séquence PORT IN prêts, PORT OUT en cours.", 1);
        comment(blocked, moussa, "Demande d'accès envoyée à l'équipe réseau, toujours sans réponse.", 2);
        comment(blocked, pm, "Je relance le responsable réseau aujourd'hui.", 1);

        history(numflex, crm, moussa, fatou, cheikh, pm);

        // Quelques jours déjà saisis dans la période en cours.
        LocalDate today = LocalDate.now();
        for (int back = 6; back >= 1; back--) {
            LocalDate d = today.minusDays(back);
            if (d.getDayOfWeek() == DayOfWeek.SATURDAY || d.getDayOfWeek() == DayOfWeek.SUNDAY) {
                continue;
            }
            entry(moussa, d, t1, "Intégration des API pour la désactivation d'une demande PORT OUT chez YAS et NUMFLEX/ARTP", null);
            entry(moussa, d, null, "Assignation des MR et review code", numflex, null);
            entry(fatou, d, t4, "Développement du formulaire contact et validations", "RAS");
            entry(cheikh, d, t6, "Rédaction du dossier d'architecture et diagrammes de séquence", null);
        }
        LocalDate lastWorkday = today.minusDays(1);
        while (lastWorkday.getDayOfWeek() == DayOfWeek.SATURDAY || lastWorkday.getDayOfWeek() == DayOfWeek.SUNDAY) {
            lastWorkday = lastWorkday.minusDays(1);
        }
        entry(moussa, lastWorkday, t2, "Préparation du jeu de données pour les tests E2E", "En attente des accès recette");

        // Activité GitLab fictive (dépôt d'identifiant négatif : jamais synchronisé avec le vrai GitLab).
        GitRepo backend = new GitRepo();
        backend.setGitlabId(-1L);
        backend.setPathWithNamespace("numflex/numflex-backend (démo)");
        backend.setWebUrl("https://it-gitlab.yas.sn/numflex/numflex-backend");
        backend.setDefaultBranch("develop");
        backend.setProject(numflex);
        gitRepos.save(backend);
        commit(backend, moussa, t1, GitLinkSource.MESSAGE, today.minusDays(1), "TS-" + t1.getId() + " Appel API ARTP pour la désactivation PORT OUT");
        commit(backend, moussa, t1, GitLinkSource.MESSAGE, today.minusDays(1), "TS-" + t1.getId() + " Gestion des erreurs de l'API de désactivation");
        commit(backend, moussa, t2, GitLinkSource.BRANCHE, today, "Jeu de données des tests E2E portabilité");
        commit(backend, moussa, null, null, today, "Mise à jour des dépendances Spring Boot");
        commit(backend, cheikh, t6, GitLinkSource.MERGE_REQUEST, today, "Diagrammes de séquence du flux PORT IN");
        commit(backend, null, null, null, today.minusDays(2), "Correctif configuration recette");
        estimationDemo(numflex, moussa, fatou, cheikh);
        reportsDemo();
        log.info("Données de démonstration créées. Mot de passe de tous les comptes : {}", DEMO_PASSWORD);
    }

    private static final String PAPE_EMAIL = "pape@yas-itfactory.sn";
    /** Premier e-mail utilisé pour ce compte, renommé au démarrage. */
    private static final String OLD_PAPE_EMAIL = "papa@yas-itfactory.sn";

    /** Profils et technologies des développeurs de démonstration (s'ils n'en ont pas encore). */
    private void demoSkills() {
        setSkills("moussa@yas-itfactory.sn", DevProfile.BACKEND, Skill.JAVA, Skill.TALEND);
        setSkills("fatou@yas-itfactory.sn", DevProfile.FULLSTACK, Skill.PHP);
        setSkills("cheikh@yas-itfactory.sn", DevProfile.BACKEND, Skill.JAVA, Skill.PYTHON);
    }

    private void setSkills(String email, DevProfile profile, Skill... skills) {
        users.findByEmailIgnoreCase(email).filter(u -> u.getDevProfile() == null).ifPresent(u -> {
            u.setDevProfile(profile);
            u.setSkills(java.util.List.of(skills));
        });
    }

    private static final String BITEYE_EMAIL = "biteye@yas-itfactory.sn";

    /** Les architectes sont rattachés au Head of Office. */
    private AppUser papa(AppUser head) {
        return user("Pape Samba", "Ndour", "C101", "Architecte logiciel", PAPE_EMAIL, Role.ARCHITECTE, head);
    }

    /**
     * Base de démonstration créée avant la hiérarchie des managers : Toussaint BASSENE devient Head of Office,
     * Demba FALL le manager des développeurs, rattaché à Mouhamadou Moustapha BITEYE ; les architectes passent
     * sous le Head of Office.
     */
    private void upgradeManagers() {
        if (users.findByEmailIgnoreCase(BITEYE_EMAIL).isPresent()) {
            return;
        }
        AppUser head = users.findByEmailIgnoreCase("head@yas-itfactory.sn").orElse(null);
        AppUser pm = users.findByEmailIgnoreCase("pm@yas-itfactory.sn").orElse(null);
        if (head == null || pm == null || users.existsByMatriculeIgnoreCase("P002")) {
            return;
        }
        head.setFirstName("Toussaint");
        head.setLastName("Bassene");
        pm.setFirstName("Demba");
        pm.setLastName("Fall");
        pm.setProfil("Manager");
        AppUser biteye = user("Mouhamadou Moustapha", "Biteye", "P002", "Manager", BITEYE_EMAIL, Role.PM, null);
        pm.setPm(biteye);
        users.findByRoleAndActiveTrue(Role.ARCHITECTE).forEach(a -> a.setPm(head));
        log.info("Hiérarchie de démonstration : développeurs → Demba FALL → Mouhamadou Moustapha BITEYE ; architectes → Toussaint BASSENE");
    }

    /**
     * Base de démonstration créée avant l'ajout des rôles Architecte / Lead dev : on ajoute Pape Samba NDOUR
     * (architecte de NumFlex) et Moussa devient lead dev, sans toucher au reste des données.
     */
    private void upgradeDemo() {
        if (users.findByEmailIgnoreCase(PAPE_EMAIL).isPresent()) {
            return;
        }
        AppUser old = users.findByEmailIgnoreCase(OLD_PAPE_EMAIL).orElse(null);
        if (old != null) {
            old.setFirstName("Pape Samba");
            old.setEmail(PAPE_EMAIL);
            log.info("Compte architecte renommé : {} → {}", OLD_PAPE_EMAIL, PAPE_EMAIL);
            return;
        }
        AppUser papa = papa(users.findByEmailIgnoreCase("head@yas-itfactory.sn").orElse(null));
        AppUser moussa = users.findByEmailIgnoreCase("moussa@yas-itfactory.sn").orElse(null);
        if (moussa != null && moussa.getRole() == Role.CONSULTANT) {
            moussa.setRole(Role.LEAD_DEV);
        }
        Project numflex = projects.findByNameIgnoreCase("NumFlex").orElse(null);
        if (numflex != null) {
            if (numflex.getArchitect() == null) {
                numflex.setArchitect(papa);
            }
            AppUser fatou = users.findByEmailIgnoreCase("fatou@yas-itfactory.sn").orElse(null);
            AppUser cheikh = users.findByEmailIgnoreCase("cheikh@yas-itfactory.sn").orElse(null);
            if (numflex.getMembers().isEmpty()) {
                for (AppUser dev : new AppUser[]{fatou, cheikh}) {
                    if (dev != null) {
                        numflex.getMembers().add(dev);
                    }
                }
            }
            if (numflex.getLeadDev() == null && moussa != null) {
                numflex.setLeadDev(moussa);
                if (fatou != null && cheikh != null) {
                    estimationDemo(numflex, moussa, fatou, cheikh);
                }
            }
        }
        log.info("Démo complétée : {} (architecte NumFlex), mot de passe {}", PAPE_EMAIL, DEMO_PASSWORD);
    }

    /** Tâches assignées par le lead dev : l'une attend l'estimation du développeur, l'autre la validation du lead dev. */
    private void estimationDemo(Project numflex, AppUser moussa, AppUser fatou, AppUser cheikh) {
        Task toEstimate = task("Mise en cache des réponses ARTP", numflex, cheikh, moussa, TaskStatus.A_FAIRE, 20);
        toEstimate.setEstimateStatus(EstimateStatus.A_ESTIMER);
        Task proposed = task("Écran de suivi des demandes PORT IN", numflex, fatou, moussa, TaskStatus.A_FAIRE, 18);
        proposed.setEstimateStatus(EstimateStatus.PROPOSEE);
        proposed.setProposedEstimateDays(4.0);
        comment(proposed, fatou, "Estimation : 4 jours (maquette déjà validée, reste l'API de recherche).", 0);
    }

    static final String AMINATA_EMAIL = "aminata@yas-itfactory.sn";

    /**
     * Cas de test du tableau de bord et de la cloche : 5 développeurs sous Demba FALL (Moussa, Fatou, Cheikh,
     * Aminata, Ibrahima) et 3 architectes sous le Head of Office (Pape Samba, Mariama, Ousmane), chacun avec un
     * profil de fiches différent sur les 4 dernières semaines échues (S-1 = semaine dernière) et le mois précédent :
     *
     * <pre>
     * Développeurs (vus par le manager)    S-1                 S-2               S-3        S-4      Mensuel
     *   Aminata DIALLO   exemplaire        soumis (à valider)  validé            validé     validé   validé
     *   Ibrahima NDIAYE  retards, renvois  à corriger          validé en retard  manquant   validé   soumis (à valider, HoO)
     *   Fatou SOW        à valider         soumis (à valider)  historique existant
     *   Cheikh FALL      soumis en retard  soumis lundi (à valider)
     *   Moussa THIOUNE   oubli             manquant
     * Architectes (vus par le Head of Office)
     *   Pape Samba NDOUR à valider         soumis (à valider)  validé            validé     validé   soumis (à valider)
     *   Mariama BA       retard            validé              validé en retard  manquant   validé   validé
     *   Ousmane GUEYE    renvoi, oubli     manquant            à corriger        validé     validé   —
     * </pre>
     * De S-5 à S-11, les nouveaux (Aminata, Ibrahima, Mariama, Ousmane) et Pape Samba ont des rapports validés à l'heure.
     * Créé une seule fois (si Aminata n'existe pas) ; ne remplace jamais un rapport existant.
     */
    private void reportsDemo() {
        if (users.findByEmailIgnoreCase(AMINATA_EMAIL).isPresent()) {
            return;
        }
        AppUser head = users.findByEmailIgnoreCase("head@yas-itfactory.sn").orElse(null);
        AppUser pm = users.findByEmailIgnoreCase("pm@yas-itfactory.sn").orElse(null);
        AppUser moussa = users.findByEmailIgnoreCase("moussa@yas-itfactory.sn").orElse(null);
        AppUser fatou = users.findByEmailIgnoreCase("fatou@yas-itfactory.sn").orElse(null);
        AppUser cheikh = users.findByEmailIgnoreCase("cheikh@yas-itfactory.sn").orElse(null);
        AppUser pape = users.findByEmailIgnoreCase(PAPE_EMAIL).orElse(null);
        Project numflex = projects.findByNameIgnoreCase("NumFlex").orElse(null);
        Project crm = projects.findByNameIgnoreCase("CRM Entreprise").orElse(null);
        Project interne = projects.findByNameIgnoreCase("Interne YAS IT-FACTORY").orElse(null);
        if (head == null || pm == null || numflex == null || crm == null || interne == null) {
            return;
        }
        AppUser aminata = user("Aminata", "Diallo", "C110", "Développeuse Angular", AMINATA_EMAIL, Role.CONSULTANT, pm);
        aminata.setDevProfile(DevProfile.FRONTEND);
        AppUser ibrahima = user("Ibrahima", "Ndiaye", "C111", "Développeur Java", "ibrahima@yas-itfactory.sn", Role.CONSULTANT, pm);
        ibrahima.setDevProfile(DevProfile.BACKEND);
        ibrahima.setSkills(java.util.List.of(Skill.JAVA, Skill.TALEND));
        AppUser mariama = user("Mariama", "Ba", "C121", "Architecte solutions", "mariama@yas-itfactory.sn", Role.ARCHITECTE, head);
        AppUser ousmane = user("Ousmane", "Gueye", "C122", "Architecte data", "ousmane@yas-itfactory.sn", Role.ARCHITECTE, head);
        crm.getMembers().add(aminata);
        numflex.getMembers().add(ibrahima);
        if (crm.getArchitect() == null) {
            crm.setArchitect(mariama);
        }
        if (interne.getArchitect() == null) {
            interne.setArchitect(ousmane);
        }

        LocalDate today = LocalDate.now();
        LocalDate thisWeek = sn.yas.itfactory.timesheet.service.Period.weekly(today).start();
        sn.yas.itfactory.timesheet.service.Period month =
                sn.yas.itfactory.timesheet.service.Period.monthly(today).previous();

        // Journées saisies (du lundi au vendredi) : le mois précédent et les 11 dernières semaines.
        LocalDate from = month.start().isBefore(thisWeek.minusWeeks(11)) ? month.start() : thisWeek.minusWeeks(11);
        workDays(aminata, crm, from, thisWeek.minusDays(1), "Intégration des écrans Angular du CRM", "Revue UX avec le métier");
        workDays(ibrahima, numflex, from, thisWeek.minusDays(1), "Développement des services Java PORT IN", "Correctifs de recette");
        workDays(mariama, crm, from, thisWeek.minusDays(1), "Architecture d'intégration CRM / SI clients", "Atelier avec les équipes");
        workDays(ousmane, interne, from, thisWeek.minusDays(1), "Modèle de données et flux Talend", "Revue de la gouvernance data");
        if (pape != null) {
            workDays(pape, numflex, from, thisWeek.minusDays(1), "Dossier d'architecture NumFlex / ARTP", "Revue des documents de l'équipe");
        }
        for (AppUser u : new AppUser[]{fatou, cheikh, moussa}) {
            if (u != null) {
                workDays(u, u == fatou ? crm : numflex, thisWeek.minusWeeks(1), thisWeek.minusDays(1),
                        "Développement et revue de code " + (u == fatou ? "CRM" : "NumFlex"), "RAS");
            }
        }

        // S = semaines en arrière. ok = vendredi 16 h, late = lundi suivant 9 h.
        for (int w = 5; w <= 11; w++) {
            weekly(aminata, thisWeek, w, ReportStatus.VALIDE, false, pm, null);
            weekly(ibrahima, thisWeek, w, ReportStatus.VALIDE, false, pm, null);
            weekly(mariama, thisWeek, w, ReportStatus.VALIDE, false, head, null);
            weekly(ousmane, thisWeek, w, ReportStatus.VALIDE, false, head, null);
            if (pape != null) {
                weekly(pape, thisWeek, w, ReportStatus.VALIDE, false, head, null);
            }
        }
        weekly(aminata, thisWeek, 1, ReportStatus.SOUMIS, false, null, null);
        weekly(aminata, thisWeek, 2, ReportStatus.VALIDE, false, pm, null);
        weekly(aminata, thisWeek, 3, ReportStatus.VALIDE, false, pm, null);
        weekly(aminata, thisWeek, 4, ReportStatus.VALIDE, false, pm, null);
        monthly(aminata, month, ReportStatus.VALIDE, head);

        weekly(ibrahima, thisWeek, 1, ReportStatus.REJETE, false, pm, "Il manque le détail de mardi et mercredi.");
        weekly(ibrahima, thisWeek, 2, ReportStatus.VALIDE, true, pm, null);
        weekly(ibrahima, thisWeek, 4, ReportStatus.VALIDE, false, pm, null);
        monthly(ibrahima, month, ReportStatus.SOUMIS, null);

        if (fatou != null) {
            weekly(fatou, thisWeek, 1, ReportStatus.SOUMIS, false, null, null);
        }
        if (cheikh != null) {
            weekly(cheikh, thisWeek, 1, ReportStatus.SOUMIS, true, null, null);
        }
        // Moussa : rien pour S-1 (manquant).

        if (pape != null) {
            weekly(pape, thisWeek, 1, ReportStatus.SOUMIS, false, null, null);
            weekly(pape, thisWeek, 2, ReportStatus.VALIDE, false, head, null);
            weekly(pape, thisWeek, 3, ReportStatus.VALIDE, false, head, null);
            weekly(pape, thisWeek, 4, ReportStatus.VALIDE, false, head, null);
            monthly(pape, month, ReportStatus.SOUMIS, null);
        }
        weekly(mariama, thisWeek, 1, ReportStatus.VALIDE, false, head, null);
        weekly(mariama, thisWeek, 2, ReportStatus.VALIDE, true, head, null);
        weekly(mariama, thisWeek, 4, ReportStatus.VALIDE, false, head, null);
        monthly(mariama, month, ReportStatus.VALIDE, head);

        weekly(ousmane, thisWeek, 2, ReportStatus.REJETE, false, head, "Merci de préciser les ateliers de la semaine.");
        weekly(ousmane, thisWeek, 3, ReportStatus.VALIDE, false, head, null);
        weekly(ousmane, thisWeek, 4, ReportStatus.VALIDE, false, head, null);
        log.info("Démo du tableau de bord : 5 développeurs, 3 architectes et leurs fiches (mot de passe {})", DEMO_PASSWORD);
    }

    /** Une ligne par jour ouvré sans saisie, en alternant deux descriptions. */
    private void workDays(AppUser u, Project p, LocalDate from, LocalDate to, String a, String b) {
        java.util.Set<LocalDate> done = new java.util.HashSet<>();
        entries.findByConsultantIdAndWorkDateBetweenOrderByWorkDateAscIdAsc(u.getId(), from, to)
                .forEach(e -> done.add(e.getWorkDate()));
        for (LocalDate d = from; !d.isAfter(to); d = d.plusDays(1)) {
            if (d.getDayOfWeek() != DayOfWeek.SATURDAY && d.getDayOfWeek() != DayOfWeek.SUNDAY && !done.contains(d)) {
                entry(u, d, null, d.getDayOfMonth() % 2 == 0 ? a : b, p, null);
            }
        }
    }

    private void weekly(AppUser u, LocalDate thisWeek, int weeksAgo, ReportStatus status, boolean late,
                        AppUser reviewer, String reviewComment) {
        LocalDate saturday = thisWeek.minusWeeks(weeksAgo);
        LocalDateTime at = late ? saturday.plusDays(9).atTime(9, 0) : saturday.plusDays(6).atTime(16, 0);
        demoReport(u, new sn.yas.itfactory.timesheet.service.Period(ReportType.HEBDO, saturday, saturday.plusDays(6)),
                status, at, reviewer, reviewComment);
    }

    private void monthly(AppUser u, sn.yas.itfactory.timesheet.service.Period month, ReportStatus status, AppUser reviewer) {
        demoReport(u, month, status, month.end().atTime(17, 0), reviewer, null);
    }

    private void demoReport(AppUser u, sn.yas.itfactory.timesheet.service.Period period, ReportStatus status,
                            LocalDateTime submittedAt, AppUser reviewer, String reviewComment) {
        if (reports.findByConsultantIdAndTypeAndPeriodStart(u.getId(), period.type(), period.start()).isPresent()) {
            return;
        }
        Report r = new Report();
        r.setType(period.type());
        r.setConsultant(u);
        r.setPeriodStart(period.start());
        r.setPeriodEnd(period.end());
        r.setStatus(status);
        r.setSubmittedAt(submittedAt.atZone(DAKAR).toInstant());
        r.setSentAt(r.getSubmittedAt());
        if (reviewer != null) {
            r.setReviewer(reviewer);
            r.setReviewComment(reviewComment);
            r.setReviewedAt(submittedAt.plusHours(20).atZone(DAKAR).toInstant());
        }
        reports.save(r);
    }

    /** Priorité, date de début (en jours dans le passé), charge estimée et avancement. */
    private void plan(Task t, TaskPriority priority, int startedDaysAgo, Double estimate, int progress) {
        t.setPriority(priority);
        t.setStartDate(LocalDate.now().minusDays(startedDaysAgo));
        t.setCreatedAt(t.getStartDate().minusDays(1).atTime(9, 0).atZone(DAKAR).toInstant());
        t.setEstimateDays(estimate);
        t.setProgress(progress);
    }

    private void comment(Task t, AppUser author, String text, int daysAgo) {
        TaskActivity a = new TaskActivity();
        a.setTask(t);
        a.setAuthor(author);
        a.setKind(TaskActivityKind.COMMENTAIRE);
        a.setText(text);
        a.setCreatedAt(LocalDate.now().minusDays(daysAgo).atTime(10, 30).atZone(DAKAR).toInstant());
        activities.save(a);
    }

    /**
     * Dix semaines d'historique pour que le tableau de bord ait une tendance : tâches terminées
     * (dans les délais ou non) et rapports hebdo soumis à l'heure, en retard ou manquants.
     */
    private void history(Project numflex, Project crm, AppUser moussa, AppUser fatou, AppUser cheikh, AppUser pm) {
        String[][] done = {
                {"Analyse des flux PORT IN", "numflex", "moussa"}, {"Maquettes de l'espace client", "crm", "fatou"},
                {"Choix de la file de messages", "numflex", "cheikh"}, {"API d'éligibilité ARTP", "numflex", "moussa"},
                {"Import des comptes entreprises", "crm", "fatou"}, {"Revue de sécurité des API", "numflex", "cheikh"},
                {"Tableau de bord des demandes", "numflex", "moussa"}, {"Gestion des rôles CRM", "crm", "fatou"},
                {"Schéma de la base NumFlex", "numflex", "cheikh"}, {"Notifications SMS", "numflex", "moussa"},
                {"Recherche multicritère", "crm", "fatou"}, {"Plan de reprise d'activité", "numflex", "cheikh"},
                {"Journalisation des échanges ARTP", "numflex", "moussa"}, {"Fusion des doublons de contacts", "crm", "fatou"},
                {"Dossier d'exploitation", "numflex", "cheikh"}, {"Correctifs de recette lot 1", "numflex", "moussa"},
        };
        TaskPriority[] priorities = TaskPriority.values();
        LocalDate today = LocalDate.now();
        for (int i = 0; i < done.length; i++) {
            AppUser who = switch (done[i][2]) { case "moussa" -> moussa; case "fatou" -> fatou; default -> cheikh; };
            int createdAgo = 70 - i * 4;
            int duration = 4 + (i % 4) * 2;
            Task t = task(done[i][0], done[i][1].equals("crm") ? crm : numflex, who, pm, TaskStatus.TERMINEE, null);
            t.setPriority(priorities[i % priorities.length]);
            t.setStartDate(today.minusDays(createdAgo));
            t.setDueDate(today.minusDays(createdAgo - duration));
            t.setEstimateDays((double) duration - 1);
            t.setCreatedAt(today.minusDays(createdAgo + 1).atTime(9, 0).atZone(DAKAR).toInstant());
            // Une tâche sur trois est livrée avec deux jours de retard.
            int finishedAgo = Math.max(1, createdAgo - duration - (i % 3 == 0 ? 2 : 0));
            t.setCompletedAt(today.minusDays(finishedAgo).atTime(17, 0).atZone(DAKAR).toInstant());
        }
        for (int i = 0; i < 4; i++) {
            Task t = task(new String[]{"Documentation API publique", "Export PDF des fiches clients",
                    "Tests de charge PORT OUT", "Revue des habilitations"}[i], i % 2 == 0 ? numflex : crm,
                    i % 2 == 0 ? moussa : fatou, pm, TaskStatus.A_FAIRE, 14 + i * 5);
            plan(t, i == 2 ? TaskPriority.HAUTE : TaskPriority.NORMALE, -3 - i * 3, 3.0 + i, 0);
        }

        // Rapports hebdo des semaines passées (la dernière semaine reste à soumettre).
        LocalDate thisWeek = sn.yas.itfactory.timesheet.service.Period.weekly(today).start();
        AppUser[] team = {moussa, fatou, cheikh};
        for (int w = 2; w <= 10; w++) {
            LocalDate saturday = thisWeek.minusWeeks(w);
            for (int c = 0; c < team.length; c++) {
                int pattern = (w + c * 3) % 7;
                if (pattern == 0) {
                    continue; // jamais soumis
                }
                Report r = new Report();
                r.setType(ReportType.HEBDO);
                r.setConsultant(team[c]);
                r.setPeriodStart(saturday);
                r.setPeriodEnd(saturday.plusDays(6));
                r.setStatus(ReportStatus.VALIDE);
                // Vendredi 16 h (à l'heure) ou lundi suivant 9 h (en retard).
                LocalDateTime at = pattern == 3 ? saturday.plusDays(9).atTime(9, 0) : saturday.plusDays(6).atTime(16, 0);
                r.setSubmittedAt(at.atZone(DAKAR).toInstant());
                r.setReviewer(pm);
                r.setReviewedAt(at.plusHours(20).atZone(DAKAR).toInstant());
                reports.save(r);
            }
        }
    }

    private AppUser user(String first, String last, String matricule, String profil, String email, Role role, AppUser pm) {
        AppUser u = new AppUser();
        u.setFirstName(first);
        u.setLastName(last);
        u.setMatricule(matricule);
        u.setProfil(profil);
        u.setEmail(email);
        u.setRole(role);
        u.setPm(pm);
        u.setPasswordHash(encoder.encode(DEMO_PASSWORD));
        return users.save(u);
    }

    private Project project(String name, String bu, AppUser pm) {
        Project p = new Project();
        p.setName(name);
        p.setBusinessUnit(bu);
        p.setPm(pm);
        return projects.save(p);
    }

    private Task task(String title, Project p, AppUser assignee, AppUser by, TaskStatus status, Integer dueInDays) {
        Task t = new Task();
        t.setTitle(title);
        t.setProject(p);
        t.setAssignee(assignee);
        t.setCreatedBy(by);
        t.setStatus(status);
        t.setDueDate(dueInDays == null ? null : LocalDate.now().plusDays(dueInDays));
        t.setPriority(TaskPriority.NORMALE);
        return tasks.save(t);
    }

    private void commit(GitRepo repo, AppUser author, Task task, GitLinkSource source, LocalDate day, String title) {
        GitCommit c = new GitCommit();
        c.setRepo(repo);
        c.setSha(Long.toHexString(Double.doubleToLongBits(Math.random())) + "00000000");
        c.setTitle(title);
        c.setMessage(title);
        c.setAuthorName(author == null ? "Prestataire externe" : author.getFullName());
        c.setAuthorEmail(author == null ? "dev.externe@prestataire.sn" : author.getEmail());
        c.setAuthoredAt(day.atTime(11, 0).atZone(DAKAR).toInstant());
        c.setWorkDate(day);
        c.setWebUrl(repo.getWebUrl() + "/-/commit/" + c.getSha());
        c.setConsultant(author);
        c.setTask(task);
        c.setLinkSource(source);
        gitCommits.save(c);
    }

    private void entry(AppUser c, LocalDate d, Task t, String description, String comment) {
        entry(c, d, t, description, t.getProject(), comment);
    }

    private void entry(AppUser c, LocalDate d, Task t, String description, Project p, String comment) {
        WorkEntry e = new WorkEntry();
        e.setConsultant(c);
        e.setWorkDate(d);
        e.setTask(t);
        e.setProject(p);
        e.setDescription(description);
        e.setComment(comment);
        entries.save(e);
    }
}
