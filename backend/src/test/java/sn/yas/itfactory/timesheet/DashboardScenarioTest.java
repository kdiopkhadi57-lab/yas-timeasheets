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
import sn.yas.itfactory.timesheet.service.Period;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Cas de test du tableau de bord et de la cloche sur les données de démonstration :
 * 5 développeurs suivis par Demba FALL, 3 architectes suivis par le Head of Office (voir DataSeeder.reportsDemo).
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class DashboardScenarioTest {

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

    private static Map<String, JsonNode> byName(JsonNode members) {
        Map<String, JsonNode> out = new HashMap<>();
        members.forEach(m -> out.put(m.get("fullName").asText(), m));
        return out;
    }

    private static List<String> names(JsonNode reports, String type) {
        List<String> out = new ArrayList<>();
        reports.forEach(r -> {
            if (type == null || r.get("type").asText().equals(type)) {
                out.add(r.get("consultantName").asText());
            }
        });
        return out;
    }

    private static final LocalDate LAST_WEEK = Period.weekly(LocalDate.now()).start().minusWeeks(1);

    @Test
    void scenariosOfDevelopersArchitectsAndNotifications() throws Exception {
        String pm = login("pm@yas-itfactory.sn");
        String head = login("head@yas-itfactory.sn");

        // ---- Cas 1 : le manager voit ses 5 développeurs, pas les architectes.
        JsonNode devs = read(pm, "/api/dashboard?weeks=12");
        Map<String, JsonNode> d = byName(devs.get("members"));
        assertThat(d.keySet()).containsExactlyInAnyOrder("Moussa THIOUNE", "Fatou SOW", "Cheikh FALL",
                "Aminata DIALLO", "Ibrahima NDIAYE");

        // Cas 2 : Aminata, exemplaire — 11 rapports hebdo + 1 fiche mensuelle, aucun retard, la dernière à valider.
        JsonNode aminata = d.get("Aminata DIALLO");
        assertThat(aminata.get("weekly").asInt()).isEqualTo(11);
        assertThat(aminata.get("monthly").asInt()).isEqualTo(1);
        assertThat(aminata.get("submitted").asInt()).isEqualTo(12);
        assertThat(aminata.get("late").asInt()).isZero();
        assertThat(aminata.get("rejected").asInt()).isZero();
        assertThat(aminata.get("pending").asInt()).isEqualTo(1);

        // Cas 3 : Ibrahima — une semaine manquante, une en retard, une renvoyée, sa fiche mensuelle attend le HoO.
        JsonNode ibrahima = d.get("Ibrahima NDIAYE");
        assertThat(ibrahima.get("weekly").asInt()).isEqualTo(10);
        assertThat(ibrahima.get("late").asInt()).isEqualTo(1);
        assertThat(ibrahima.get("rejected").asInt()).isEqualTo(1);
        assertThat(ibrahima.get("missing").asInt()).isEqualTo(aminata.get("missing").asInt() + 1);

        // Cas 4 : Cheikh a soumis la semaine dernière en retard (lundi) ; Moussa ne l'a pas soumise.
        assertThat(d.get("Cheikh FALL").get("late").asInt()).isGreaterThanOrEqualTo(1);
        assertThat(d.get("Moussa THIOUNE").get("missing").asInt()).isGreaterThanOrEqualTo(1);
        assertThat(devs.get("kpis").get("missingLastWeek").asInt()).isGreaterThanOrEqualTo(1);
        assertThat(devs.get("kpis").get("submitted").asInt()).isGreaterThanOrEqualTo(30);

        // ---- Cas 5 : le Head of Office voit les 3 architectes.
        JsonNode archis = read(head, "/api/dashboard?weeks=12");
        Map<String, JsonNode> a = byName(archis.get("members"));
        assertThat(a.keySet()).containsExactlyInAnyOrder("Pape Samba NDOUR", "Mariama BA", "Ousmane GUEYE");
        assertThat(a.get("Pape Samba NDOUR").get("pending").asInt()).isEqualTo(2);
        assertThat(a.get("Mariama BA").get("late").asInt()).isEqualTo(1);
        assertThat(a.get("Mariama BA").get("missing").asInt()).isGreaterThanOrEqualTo(1);
        assertThat(a.get("Ousmane GUEYE").get("rejected").asInt()).isEqualTo(1);
        assertThat(a.get("Ousmane GUEYE").get("missing").asInt())
                .isEqualTo(a.get("Pape Samba NDOUR").get("missing").asInt() + 1);

        // Filtre projet : CRM → seuls les membres du projet.
        long crm = 0;
        for (JsonNode p : read(pm, "/api/projects")) {
            if (p.get("name").asText().equals("CRM Entreprise")) {
                crm = p.get("id").asLong();
            }
        }
        assertThat(byName(read(pm, "/api/dashboard?projectId=" + crm).get("members")).keySet())
                .contains("Aminata DIALLO").doesNotContain("Ibrahima NDIAYE");

        // ---- Cas 6 : cloche du manager — les rapports hebdo à valider de ses développeurs.
        JsonNode pmInbox = read(pm, "/api/reports/inbox");
        assertThat(names(pmInbox, "HEBDO")).contains("Aminata DIALLO", "Fatou SOW", "Cheikh FALL")
                .doesNotContain("Ibrahima NDIAYE", "Pape Samba NDOUR", "Mariama BA");
        assertThat(names(pmInbox, "MENSUEL")).isEmpty();

        // Cas 7 : le manager de Demba FALL reçoit aussi ceux de l'équipe en dessous de lui.
        assertThat(names(read(login("biteye@yas-itfactory.sn"), "/api/reports/inbox"), "HEBDO"))
                .contains("Aminata DIALLO", "Fatou SOW");

        // Cas 8 : cloche du Head of Office — rapports hebdo des architectes et fiches mensuelles de tous.
        JsonNode headInbox = read(head, "/api/reports/inbox");
        assertThat(names(headInbox, "HEBDO")).contains("Pape Samba NDOUR").doesNotContain("Aminata DIALLO", "Fatou SOW");
        assertThat(names(headInbox, "MENSUEL")).contains("Pape Samba NDOUR", "Ibrahima NDIAYE");

        // Cas 9 : développeur, lead dev, architecte : pas de tableau de bord, cloche vide.
        for (String email : List.of("aminata@yas-itfactory.sn", "moussa@yas-itfactory.sn", "mariama@yas-itfactory.sn")) {
            String who = login(email);
            mvc.perform(get("/api/dashboard").header("Authorization", who)).andExpect(status().isForbidden());
            assertThat(read(who, "/api/reports/inbox").size()).isZero();
        }

        // ---- Cas 10 : clic sur Aminata → liste de ses fiches → détail de la semaine dernière.
        long aminataId = aminata.get("id").asLong();
        JsonNode history = read(pm, "/api/reports/history?consultantId=" + aminataId);
        assertThat(history.size()).isEqualTo(12);
        JsonNode sheet = read(pm, "/api/reports/timesheet?type=HEBDO&date=" + LAST_WEEK + "&consultantId=" + aminataId);
        assertThat(sheet.get("report").get("status").asText()).isEqualTo("SOUMIS");
        assertThat(sheet.get("entryCount").asInt()).isEqualTo(5);
        // Un manager ne voit pas la fiche d'un architecte.
        mvc.perform(get("/api/reports/history?consultantId=" + a.get("Mariama BA").get("id").asLong())
                .header("Authorization", pm)).andExpect(status().isForbidden());

        // ---- Cas 11 : valider depuis la cloche fait baisser le compteur.
        long aminataWeek = 0;
        for (JsonNode r : pmInbox) {
            if (r.get("consultantName").asText().equals("Aminata DIALLO")) {
                aminataWeek = r.get("id").asLong();
            }
        }
        mvc.perform(post("/api/reports/" + aminataWeek + "/approve").header("Authorization", pm)
                .contentType(MediaType.APPLICATION_JSON).content("{}")).andExpect(status().isOk());
        JsonNode after = read(pm, "/api/reports/inbox");
        assertThat(after.size()).isEqualTo(pmInbox.size() - 1);
        assertThat(names(after, null)).doesNotContain("Aminata DIALLO");
        assertThat(byName(read(pm, "/api/dashboard").get("members")).get("Aminata DIALLO").get("pending").asInt()).isZero();

        // Cas 12 : la fiche mensuelle d'Ibrahima — le manager ne peut pas la valider, le Head of Office la renvoie.
        long ibrahimaMonth = 0;
        for (JsonNode r : headInbox) {
            if (r.get("type").asText().equals("MENSUEL") && r.get("consultantName").asText().equals("Ibrahima NDIAYE")) {
                ibrahimaMonth = r.get("id").asLong();
            }
        }
        mvc.perform(post("/api/reports/" + ibrahimaMonth + "/approve").header("Authorization", pm)
                .contentType(MediaType.APPLICATION_JSON).content("{}")).andExpect(status().isForbidden());
        mvc.perform(post("/api/reports/" + ibrahimaMonth + "/reject").header("Authorization", head)
                .contentType(MediaType.APPLICATION_JSON).content("{\"comment\":\"Il manque le 12 septembre.\"}"))
                .andExpect(status().isOk());
        assertThat(read(head, "/api/reports/inbox").size()).isEqualTo(headInbox.size() - 1);
    }
}
