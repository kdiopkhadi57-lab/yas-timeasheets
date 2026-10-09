package sn.yas.itfactory.timesheet;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.support.TransactionTemplate;
import sn.yas.itfactory.timesheet.config.DataSeeder;
import sn.yas.itfactory.timesheet.domain.*;
import sn.yas.itfactory.timesheet.gitlab.GitLabApi;
import sn.yas.itfactory.timesheet.repository.*;
import sn.yas.itfactory.timesheet.service.GitSyncService;
import sn.yas.itfactory.timesheet.service.TaskRefs;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:gittest;DB_CLOSE_DELAY=-1")
@AutoConfigureMockMvc
@ActiveProfiles("test")
class GitSyncTest {

    static {
        // Comme TimesheetApplication.main : les jours de travail se calculent à l'heure de Dakar.
        TimeZone.setDefault(TimeZone.getTimeZone("Africa/Dakar"));
    }

    @Autowired GitSyncService sync;
    @Autowired FakeGitLab gitlab;
    @Autowired TaskRefs refs;
    @Autowired TaskRepository tasks;
    @Autowired AppUserRepository users;
    @Autowired GitRepoRepository repos;
    @Autowired GitCommitRepository commits;
    @Autowired TransactionTemplate tx;
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;

    @TestConfiguration
    static class Config {
        @Bean
        @Primary
        FakeGitLab fakeGitLab() {
            return new FakeGitLab();
        }
    }

    /** GitLab en mémoire : seul le dépôt 1001 a des données ; tout appel sur un autre dépôt échoue. */
    static class FakeGitLab implements GitLabApi {
        final List<Project> projects = new ArrayList<>();
        final List<Commit> commits = new ArrayList<>();
        final Map<MergeRequest, List<Commit>> mrs = new LinkedHashMap<>();
        final Map<String, List<Commit>> branches = new LinkedHashMap<>();

        public List<Project> projects(List<String> groups) { return projects; }
        public List<Commit> commits(long id, OffsetDateTime since) { check(id); return commits; }
        public List<MergeRequest> mergeRequests(long id, OffsetDateTime after) { check(id); return new ArrayList<>(mrs.keySet()); }
        public List<Commit> mergeRequestCommits(long id, long iid) {
            check(id);
            return mrs.entrySet().stream().filter(e -> e.getKey().iid() == iid).findFirst().orElseThrow().getValue();
        }
        public List<Branch> branches(long id) {
            check(id);
            List<Branch> all = new ArrayList<>(branches.keySet().stream().map(Branch::new).toList());
            all.add(new Branch("develop"));
            return all;
        }
        public List<Commit> compare(long id, String from, String to) { check(id); return branches.get(to); }

        private static void check(long id) {
            if (id != 1001) {
                throw new IllegalStateException("dépôt non rattaché interrogé : " + id);
            }
        }
    }

    private static GitLabApi.Commit commit(String sha, String title, String email, int daysAgo) {
        return new GitLabApi.Commit(sha, title, title, email, email, OffsetDateTime.now().minusDays(daysAgo), "https://git/" + sha);
    }

    private Task task(String title) {
        return tasks.findAll().stream().filter(t -> t.getTitle().equals(title)).findFirst().orElseThrow();
    }

    private GitCommit stored(String sha) {
        return tx.execute(s -> {
            GitCommit c = commits.findAll().stream().filter(x -> x.getSha().equals(sha)).findFirst().orElse(null);
            if (c != null) {
                // Initialise les relations paresseuses pour les assertions hors transaction.
                if (c.getTask() != null) c.getTask().getId();
                if (c.getConsultant() != null) c.getConsultant().getEmail();
            }
            return c;
        });
    }

    @Test
    void taskReferencesAreFoundInBranchesTitlesAndMessages() {
        assertThat(refs.find("feature/ts-42-export-excel")).contains(42L);
        assertThat(refs.find("TS-7: correction")).contains(7L);
        assertThat(refs.find(null, "[TS-12] titre de MR")).contains(12L);
        assertThat(refs.find("ATS-5 n'est pas une référence", "TS-")).isEmpty();
        assertThat(refs.strip("TS-42 : Ajout de l'export")).isEqualTo("Ajout de l'export");
    }

    @Test
    void syncLinksCommitsToTasksAndConsultants() throws Exception {
        long api = task("Intégration des API de désactivation PORT OUT").getId();
        long e2e = task("Tests End-to-end du workflow").getId();
        long archi = task("Architecture cible NumFlex / ARTP").getId();
        String moussa = "moussa@yas-itfactory.sn";

        gitlab.projects.add(new GitLabApi.Project(1001, "numflex/numflex-backoffice", "https://git/numflex/numflex-backoffice",
                "develop", new GitLabApi.Namespace("numflex", "numflex")));
        gitlab.projects.add(new GitLabApi.Project(1002, "NUMBER-ONE/number-one-backend", "https://git/n1",
                "main", new GitLabApi.Namespace("NUMBER-ONE", "NUMBER-ONE")));

        GitLabApi.Commit byMessage = commit("a1", "TS-" + api + " Appel API désactivation", moussa, 1);
        GitLabApi.Commit inMr = commit("a2", "Jeu de données E2E", moussa, 1);
        gitlab.commits.add(byMessage);
        gitlab.commits.add(inMr);
        gitlab.commits.add(commit("a3", "Merge branch 'feature/x' into 'develop'", moussa, 1));
        gitlab.commits.add(commit("a4", "Correctif recette", "ext@prestataire.sn", 2));
        gitlab.commits.add(commit("a5", "Refactoring", "moussa.perso@gmail.com", 0));
        // La MR contient aussi a1 : la référence dans le message reste prioritaire.
        gitlab.mrs.put(new GitLabApi.MergeRequest(7, "Tests E2E", "feature/ts-" + e2e + "-e2e", "opened",
                new GitLabApi.Author("moussa.t", "Moussa"), "https://git/mr/7", OffsetDateTime.now(), null), List.of(byMessage, inMr));
        gitlab.branches.put("feature/ts-" + e2e + "-e2e", List.of(inMr));
        gitlab.branches.put("feature/TS-" + archi + "-archi", List.of(commit("a7", "Diagrammes de séquence", "cheikh@yas-itfactory.sn", 0)));

        GitSyncService.Status status = sync.syncAll();
        assertThat(status.lastError()).isNull();

        GitRepo numflex = repos.findByGitlabId(1001L).orElseThrow();
        assertThat(numflex.getProject().getName()).isEqualTo("NumFlex");
        assertThat(repos.findByGitlabId(1002L).orElseThrow().getProject()).isNull();

        assertThat(stored("a1").getTask().getId()).isEqualTo(api);
        assertThat(stored("a1").getLinkSource()).isEqualTo(GitLinkSource.MESSAGE);
        assertThat(stored("a1").getConsultant().getEmail()).isEqualTo(moussa);
        assertThat(stored("a2").getTask().getId()).isEqualTo(e2e);
        assertThat(stored("a2").getLinkSource()).isEqualTo(GitLinkSource.MERGE_REQUEST);
        assertThat(stored("a3")).as("commit de merge ignoré").isNull();
        assertThat(stored("a4").getConsultant()).isNull();
        assertThat(stored("a7").getTask().getId()).isEqualTo(archi);
        assertThat(stored("a7").getLinkSource()).isEqualTo(GitLinkSource.BRANCHE);
        assertThat(stored("a5").getConsultant()).isNull();

        // Deuxième passage : rien de nouveau.
        long count = commits.count();
        assertThat(sync.syncAll().newCommits()).isZero();
        assertThat(commits.count()).isEqualTo(count);

        // L'admin déclare l'e-mail perso de Moussa : ses commits passés lui sont rattachés.
        String admin = login("admin@yas-itfactory.sn");
        AppUser m = users.findByEmailIgnoreCase(moussa).orElseThrow();
        mvc.perform(put("/api/users/" + m.getId()).header("Authorization", admin).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"firstName\":\"Moussa\",\"lastName\":\"Thioune\",\"matricule\":\"C060\",\"email\":\"" + moussa
                                + "\",\"role\":\"CONSULTANT\",\"pmId\":" + m.getPm().getId()
                                + ",\"gitIdentities\":\"moussa.perso@gmail.com, moussa.t\"}"))
                .andExpect(status().isOk());
        assertThat(stored("a5").getConsultant().getEmail()).isEqualTo(moussa);

        // Le consultant voit les commits de sa tâche et la branche à créer.
        String consultant = login(moussa);
        JsonNode git = json.readTree(mvc.perform(get("/api/tasks/" + e2e + "/git").header("Authorization", consultant))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        assertThat(git.get("ref").asText()).isEqualTo("TS-" + e2e);
        assertThat(git.get("branchName").asText()).startsWith("feature/TS-" + e2e + "-tests-end-to-end");
        assertThat(git.get("mergeRequests").get(0).get("iid").asLong()).isEqualTo(7);

        // Suggestions : les commits du jour non encore saisis, regroupés par tâche.
        LocalDate today = LocalDate.now();
        JsonNode suggestions = json.readTree(mvc.perform(get("/api/git/suggestions?from=" + today.minusDays(6) + "&to=" + today)
                        .header("Authorization", consultant))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        assertThat(suggestions.findValuesAsText("description")).anyMatch(d -> d.lines().anyMatch("Refactoring"::equals));

        // Le PM voit l'activité de son équipe ; un consultant n'y a pas accès.
        mvc.perform(get("/api/git/activity?type=MENSUEL&date=" + today).header("Authorization", consultant))
                .andExpect(status().isForbidden());
        JsonNode activity = json.readTree(mvc.perform(get("/api/git/activity?type=MENSUEL&date=" + today)
                        .header("Authorization", login("pm@yas-itfactory.sn")))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        assertThat(activity.get("rows").findValuesAsText("consultantName")).contains("Moussa THIOUNE", "Cheikh FALL");
    }

    private String login(String email) throws Exception {
        String body = mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"password\":\"" + DataSeeder.DEMO_PASSWORD + "\"}"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return "Bearer " + json.readTree(body).get("token").asText();
    }
}
