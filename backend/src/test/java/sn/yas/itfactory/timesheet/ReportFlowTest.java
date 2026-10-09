package sn.yas.itfactory.timesheet;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import sn.yas.itfactory.timesheet.config.DataSeeder;

import java.io.ByteArrayInputStream;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ReportFlowTest {

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

    @Test
    void consultantSubmitsWeeklyReportAndPmValidatesIt() throws Exception {
        String consultant = login("cheikh@yas-itfactory.sn");
        String pm = login("pm@yas-itfactory.sn");
        String today = LocalDate.now().toString();

        JsonNode projects = json.readTree(mvc.perform(get("/api/projects").header("Authorization", consultant))
                .andReturn().getResponse().getContentAsString());
        long projectId = projects.get(0).get("id").asLong();

        mvc.perform(post("/api/entries").header("Authorization", consultant).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"workDate\":\"" + today + "\",\"projectId\":" + projectId
                                + ",\"description\":\"Revue d'architecture\",\"comment\":\"OK\"}"))
                .andExpect(status().isCreated());

        JsonNode ts = json.readTree(mvc.perform(post("/api/reports/submit?type=HEBDO&date=" + today)
                        .header("Authorization", consultant).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"comment\":\"Semaine chargée\"}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString());
        assertThat(ts.get("report").get("status").asText()).isEqualTo("SOUMIS");
        assertThat(ts.get("editable").asBoolean()).isFalse();

        // Saisie verrouillée une fois soumis.
        mvc.perform(post("/api/entries").header("Authorization", consultant).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"workDate\":\"" + today + "\",\"projectId\":" + projectId + ",\"description\":\"x\"}"))
                .andExpect(status().isConflict());

        // Le consultant ne peut pas valider son propre rapport ; le PM, oui.
        long reportId = ts.get("report").get("id").asLong();
        mvc.perform(post("/api/reports/" + reportId + "/approve").header("Authorization", consultant))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/reports/" + reportId + "/approve").header("Authorization", pm))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("VALIDE"));

        JsonNode team = json.readTree(mvc.perform(get("/api/reports/team?type=HEBDO").header("Authorization", pm))
                .andReturn().getResponse().getContentAsString());
        // Moussa, Fatou, Cheikh, Aminata, Ibrahima ; les architectes sont rattachés au Head of Office.
        assertThat(team.size()).isEqualTo(5);
    }

    @Test
    void managerOfManagerSeesTheWholeTeam() throws Exception {
        String biteye = login("biteye@yas-itfactory.sn");
        JsonNode team = json.readTree(mvc.perform(get("/api/reports/team?type=HEBDO").header("Authorization", biteye))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        java.util.List<String> names = new java.util.ArrayList<>();
        team.forEach(r -> names.add(r.get("consultant").get("email").asText()));
        // Demba FALL (son collaborateur direct) et les développeurs de Demba FALL, mais pas l'architecte.
        assertThat(names).containsExactlyInAnyOrder("pm@yas-itfactory.sn", "moussa@yas-itfactory.sn",
                "fatou@yas-itfactory.sn", "cheikh@yas-itfactory.sn", "aminata@yas-itfactory.sn", "ibrahima@yas-itfactory.sn");

        JsonNode users = json.readTree(mvc.perform(get("/api/users").header("Authorization", login("admin@yas-itfactory.sn")))
                .andReturn().getResponse().getContentAsString());
        java.util.Map<String, JsonNode> byEmail = new java.util.HashMap<>();
        users.forEach(u -> byEmail.put(u.get("email").asText(), u));
        assertThat(byEmail.get("pape@yas-itfactory.sn").get("pmName").asText()).isEqualTo("Toussaint BASSENE");

        long fatouId = byEmail.get("fatou@yas-itfactory.sn").get("id").asLong();
        mvc.perform(get("/api/reports/timesheet?type=HEBDO&consultantId=" + fatouId).header("Authorization", biteye))
                .andExpect(status().isOk());
        long biteyeId = byEmail.get("biteye@yas-itfactory.sn").get("id").asLong();
        mvc.perform(get("/api/reports/timesheet?type=HEBDO&consultantId=" + biteyeId).header("Authorization", login("pm@yas-itfactory.sn")))
                .andExpect(status().isForbidden());

        String admin = login("admin@yas-itfactory.sn");
        // Un architecte ne peut être rattaché qu'au Head of Office.
        JsonNode pape = byEmail.get("pape@yas-itfactory.sn");
        mvc.perform(put("/api/users/" + pape.get("id").asLong()).header("Authorization", admin).contentType(MediaType.APPLICATION_JSON)
                        .content(userJson(pape, byEmail.get("pm@yas-itfactory.sn").get("id").asLong())))
                .andExpect(status().isBadRequest());
        // Pas de boucle : BITEYE ne peut pas passer sous Demba FALL, qui est déjà sous lui.
        JsonNode b = byEmail.get("biteye@yas-itfactory.sn");
        mvc.perform(put("/api/users/" + biteyeId).header("Authorization", admin).contentType(MediaType.APPLICATION_JSON)
                        .content(userJson(b, byEmail.get("pm@yas-itfactory.sn").get("id").asLong())))
                .andExpect(status().isBadRequest());
    }

    @Test
    void managerCreatesADeveloperForHisTeam() throws Exception {
        String demba = login("pm@yas-itfactory.sn");
        String admin = login("admin@yas-itfactory.sn");
        JsonNode users = json.readTree(mvc.perform(get("/api/users").header("Authorization", admin))
                .andReturn().getResponse().getContentAsString());
        java.util.Map<String, Long> ids = new java.util.HashMap<>();
        users.forEach(u -> ids.put(u.get("email").asText(), u.get("id").asLong()));
        JsonNode projects = json.readTree(mvc.perform(get("/api/projects").header("Authorization", demba))
                .andReturn().getResponse().getContentAsString());
        long numflex = 0;
        for (JsonNode p : projects) {
            if (p.get("name").asText().equals("NumFlex")) {
                numflex = p.get("id").asLong();
            }
        }
        String dev = "{\"firstName\":\"Awa\",\"lastName\":\"Diop\",\"matricule\":\"C150\",\"email\":\"awa@yas-itfactory.sn\","
                + "\"password\":\"Secret1\",\"role\":\"%s\",\"pmId\":%d,\"devProfile\":\"FULLSTACK\","
                + "\"skills\":[\"PYTHON\",\"JAVA\"],\"projectIds\":[" + numflex + "]}";

        // Un manager ne crée que des développeurs, rattachés à lui ou à un manager sous lui.
        mvc.perform(post("/api/users").header("Authorization", demba).contentType(MediaType.APPLICATION_JSON)
                        .content(dev.formatted("PM", ids.get("pm@yas-itfactory.sn"))))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/api/users").header("Authorization", demba).contentType(MediaType.APPLICATION_JSON)
                        .content(dev.formatted("CONSULTANT", ids.get("biteye@yas-itfactory.sn"))))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/api/users").header("Authorization", login("fatou@yas-itfactory.sn")).contentType(MediaType.APPLICATION_JSON)
                        .content(dev.formatted("CONSULTANT", ids.get("pm@yas-itfactory.sn"))))
                .andExpect(status().isForbidden());

        JsonNode created = json.readTree(mvc.perform(post("/api/users").header("Authorization", demba)
                        .contentType(MediaType.APPLICATION_JSON).content(dev.formatted("CONSULTANT", ids.get("pm@yas-itfactory.sn"))))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString());
        assertThat(created.get("devProfile").asText()).isEqualTo("FULLSTACK");
        assertThat(created.get("skills").toString()).isEqualTo("[\"JAVA\",\"PYTHON\"]");
        assertThat(created.get("projects").get(0).get("name").asText()).isEqualTo("NumFlex");

        // Elle fait partie de l'équipe de Demba FALL… et de celle de son manager.
        JsonNode team = json.readTree(mvc.perform(get("/api/users/team").header("Authorization", login("biteye@yas-itfactory.sn")))
                .andReturn().getResponse().getContentAsString());
        assertThat(team.toString()).contains("awa@yas-itfactory.sn");
        // Compte désactivé pour ne pas fausser les effectifs des autres tests.
        mvc.perform(put("/api/users/" + created.get("id").asLong()).header("Authorization", demba).contentType(MediaType.APPLICATION_JSON)
                        .content(dev.formatted("CONSULTANT", ids.get("pm@yas-itfactory.sn")).replace("}", ",\"active\":false}")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.active").value(false));
        // Un manager ne modifie pas l'architecte, rattaché au Head of Office.
        JsonNode pape = null;
        for (JsonNode u : users) {
            if (u.get("email").asText().equals("pape@yas-itfactory.sn")) {
                pape = u;
            }
        }
        mvc.perform(put("/api/users/" + pape.get("id").asLong()).header("Authorization", demba).contentType(MediaType.APPLICATION_JSON)
                        .content(userJson(pape, ids.get("head@yas-itfactory.sn"))))
                .andExpect(status().isBadRequest());
    }

    private static String userJson(JsonNode u, long managerId) {
        return "{\"firstName\":\"" + u.get("firstName").asText() + "\",\"lastName\":\"" + u.get("lastName").asText()
                + "\",\"matricule\":\"" + u.get("matricule").asText() + "\",\"email\":\"" + u.get("email").asText()
                + "\",\"role\":\"" + u.get("role").asText() + "\",\"pmId\":" + managerId + ",\"active\":true}";
    }

    @Test
    void monthlyExcelFollowsTheYasTemplate() throws Exception {
        String consultant = login("moussa@yas-itfactory.sn");
        byte[] xlsx = mvc.perform(get("/api/reports/excel?type=MENSUEL&date=2026-06-03").header("Authorization", consultant))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Disposition",
                        org.hamcrest.Matchers.containsString("Fiche%20de%20temps%20Mai%20-%20Juin%202026.xlsx")))
                .andReturn().getResponse().getContentAsByteArray();
        try (XSSFWorkbook wb = new XSSFWorkbook(new ByteArrayInputStream(xlsx))) {
            Sheet sh = wb.getSheet("Fiche temps Mensuel");
            assertThat(sh.getRow(0).getCell(0).getStringCellValue()).isEqualTo("Fiche de temps");
            assertThat(sh.getRow(1).getCell(1).getStringCellValue()).isEqualTo("Moussa THIOUNE");
            assertThat(sh.getRow(2).getCell(1).getStringCellValue()).isEqualTo("C060");
            assertThat(sh.getRow(6).getCell(3).getStringCellValue()).isEqualTo("Liste des tâches");
            assertThat(sh.getRow(7).getCell(0).getLocalDateTimeCellValue().toLocalDate()).isEqualTo(LocalDate.of(2026, 5, 20));
            // 31 jours du 20 mai au 19 juin, puis le cadre de signature.
            assertThat(sh.getRow(37).getCell(0).getLocalDateTimeCellValue().toLocalDate()).isEqualTo(LocalDate.of(2026, 6, 19));
            assertThat(sh.getRow(39).getCell(0).getStringCellValue()).isEqualTo("Signature Consultant");
        }
    }

    @Test
    void consultantCannotSeeAnotherConsultantsTimesheet() throws Exception {
        String fatou = login("fatou@yas-itfactory.sn");
        JsonNode me = json.readTree(mvc.perform(get("/api/auth/me").header("Authorization", login("moussa@yas-itfactory.sn")))
                .andReturn().getResponse().getContentAsString());
        mvc.perform(get("/api/reports/timesheet?type=MENSUEL&consultantId=" + me.get("id").asLong()).header("Authorization", fatou))
                .andExpect(status().isForbidden());
    }
}
