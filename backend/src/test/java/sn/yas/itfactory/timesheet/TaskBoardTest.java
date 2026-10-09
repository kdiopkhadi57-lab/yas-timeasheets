package sn.yas.itfactory.timesheet;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import sn.yas.itfactory.timesheet.config.DataSeeder;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Priorité, charge, historique, commentaires et tableau de bord des tâches. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class TaskBoardTest {

    @Autowired
    MockMvc mvc;
    @Autowired
    ObjectMapper json;

    private String login(String email) throws Exception {
        String body = mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"password\":\"" + DataSeeder.DEMO_PASSWORD + "\"}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return "Bearer " + json.readTree(body).get("token").asText();
    }

    private JsonNode read(String auth, String url) throws Exception {
        return json.readTree(mvc.perform(get(url).header("Authorization", auth))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
    }

    @Test
    void taskKeepsAHistoryAndComments() throws Exception {
        String pm = login("pm@yas-itfactory.sn");
        String fatou = login("fatou@yas-itfactory.sn");
        String moussa = login("moussa@yas-itfactory.sn");
        long fatouId = read(fatou, "/api/auth/me").get("id").asLong();
        long projectId = read(pm, "/api/projects").get(0).get("id").asLong();
        String start = LocalDate.now().toString();
        String due = LocalDate.now().plusDays(5).toString();

        JsonNode created = json.readTree(mvc.perform(post("/api/tasks").header("Authorization", pm)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"Audit des performances\",\"projectId\":" + projectId + ",\"assigneeId\":" + fatouId
                                + ",\"priority\":\"HAUTE\",\"startDate\":\"" + start + "\",\"dueDate\":\"" + due
                                + "\",\"estimateDays\":4,\"progress\":0}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.priority").value("HAUTE"))
                .andExpect(jsonPath("$.estimateDays").value(4.0))
                .andReturn().getResponse().getContentAsString());
        long id = created.get("id").asLong();

        // Échéance avant le début : refusé.
        mvc.perform(put("/api/tasks/" + id).header("Authorization", pm).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"Audit des performances\",\"projectId\":" + projectId + ",\"startDate\":\"" + due
                                + "\",\"dueDate\":\"" + start + "\"}"))
                .andExpect(status().isBadRequest());

        mvc.perform(put("/api/tasks/" + id).header("Authorization", pm).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"Audit des performances\",\"projectId\":" + projectId + ",\"priority\":\"CRITIQUE\""
                                + ",\"startDate\":\"" + start + "\",\"dueDate\":\"" + due + "\",\"estimateDays\":4,\"progress\":50}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.progress").value(50));
        mvc.perform(patch("/api/tasks/" + id + "/status").header("Authorization", fatou).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"EN_COURS\"}"))
                .andExpect(status().isOk());

        JsonNode comment = json.readTree(mvc.perform(post("/api/tasks/" + id + "/comments").header("Authorization", fatou)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"text\":\"Premiers résultats demain.\"}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString());

        // Un consultant d'une autre tâche ne voit pas le fil ; le PM ne supprime pas le commentaire de Fatou.
        mvc.perform(get("/api/tasks/" + id + "/activity").header("Authorization", moussa)).andExpect(status().isForbidden());
        mvc.perform(delete("/api/tasks/" + id + "/comments/" + comment.get("id").asLong()).header("Authorization", pm))
                .andExpect(status().isForbidden());

        JsonNode activity = read(pm, "/api/tasks/" + id + "/activity");
        assertThat(activity).extracting(a -> a.get("kind").asText())
                .containsExactly("CREATION", "MODIFICATION", "STATUT", "COMMENTAIRE");
        assertThat(activity.get(1).get("text").asText()).contains("priorité Haute → Critique", "avancement 0 % → 50 %");
        assertThat(activity.get(2).get("text").asText()).isEqualTo("À faire → En cours");

        JsonNode mine = read(fatou, "/api/tasks");
        JsonNode task = null;
        for (JsonNode t : mine) {
            if (t.get("id").asLong() == id) {
                task = t;
            }
        }
        assertThat(task).isNotNull();
        assertThat(task.get("commentCount").asLong()).isEqualTo(1);

        mvc.perform(delete("/api/tasks/" + id).header("Authorization", pm)).andExpect(status().isNoContent());
    }

    @Test
    void dashboardFollowsDevelopersOrArchitects() throws Exception {
        String pm = login("pm@yas-itfactory.sn");
        JsonNode d = read(pm, "/api/dashboard?weeks=12");
        assertThat(d.get("audience").asText()).isEqualTo("DEV");
        // Moussa (lead dev), Fatou, Cheikh, Aminata, Ibrahima : les architectes sont rattachés au Head of Office.
        assertThat(d.get("members").size()).isEqualTo(5);
        assertThat(d.get("members")).allSatisfy(m -> assertThat(m.get("role").asText()).isIn("CONSULTANT", "LEAD_DEV"));
        assertThat(d.get("kpis").get("members").asInt()).isEqualTo(5);
        assertThat(d.get("kpis").get("submitted").asInt()).isGreaterThan(0);
        assertThat(d.get("compliance").size()).isGreaterThan(0);
        // Le rapport hebdo seedé « en retard » est compté comme soumis mais pas à l'heure.
        int submitted = 0;
        int onTime = 0;
        for (JsonNode w : d.get("compliance")) {
            submitted += w.get("submitted").asInt();
            onTime += w.get("onTime").asInt();
        }
        assertThat(onTime).isLessThan(submitted);

        // Le Head of Office suit les architectes.
        JsonNode head = read(login("head@yas-itfactory.sn"), "/api/dashboard");
        assertThat(head.get("audience").asText()).isEqualTo("ARCHITECTE");
        assertThat(head.get("members")).allSatisfy(m -> assertThat(m.get("role").asText()).isEqualTo("ARCHITECTE"));

        // Développeurs et lead devs n'ont pas de tableau de bord ; leur cloche reste vide.
        String fatou = login("fatou@yas-itfactory.sn");
        mvc.perform(get("/api/dashboard").header("Authorization", fatou)).andExpect(status().isForbidden());
        mvc.perform(get("/api/dashboard").header("Authorization", login("moussa@yas-itfactory.sn")))
                .andExpect(status().isForbidden());
        assertThat(read(fatou, "/api/reports/inbox").size()).isZero();

        // La cloche du manager : rapports hebdo soumis de son équipe, en attente de validation.
        assertThat(read(pm, "/api/reports/inbox")).allSatisfy(r -> {
            assertThat(r.get("status").asText()).isEqualTo("SOUMIS");
            assertThat(r.get("type").asText()).isEqualTo("HEBDO");
        });
    }
}
