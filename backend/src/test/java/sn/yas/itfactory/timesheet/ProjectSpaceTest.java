package sn.yas.itfactory.timesheet;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import sn.yas.itfactory.timesheet.config.DataSeeder;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Documents de projet (architecte → équipe) et estimation des tâches assignées par le lead dev. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ProjectSpaceTest {

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

    private JsonNode numflex(String auth) throws Exception {
        for (JsonNode s : read(auth, "/api/projects/spaces")) {
            if (s.get("project").get("name").asText().equals("NumFlex")) {
                return s;
            }
        }
        return null;
    }

    @Test
    void architectSharesDocumentsWithTheProjectTeam() throws Exception {
        String papa = login("pape@yas-itfactory.sn");
        String fatou = login("fatou@yas-itfactory.sn");
        JsonNode space = numflex(papa);
        assertThat(space).isNotNull();
        assertThat(space.get("myRoles").get(0).asText()).isEqualTo("ARCHITECTE");
        assertThat(space.get("canUpload").asBoolean()).isTrue();
        long projectId = space.get("project").get("id").asLong();

        MockMultipartFile spec = new MockMultipartFile("files", "../../Dossier d'architecture.docx",
                "application/vnd.openxmlformats-officedocument.wordprocessingml.document", "contenu word".getBytes());
        MockMultipartFile code = new MockMultipartFile("files", "PortOutClient.java", "text/x-java",
                "class PortOutClient {}".getBytes());
        JsonNode uploaded = json.readTree(mvc.perform(multipart("/api/projects/" + projectId + "/documents")
                        .file(spec).file(code).param("description", "Version 1").header("Authorization", papa))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString());
        assertThat(uploaded.size()).isEqualTo(2);
        // Le chemin envoyé par le navigateur est ignoré.
        assertThat(uploaded.get(0).get("fileName").asText()).isEqualTo("Dossier d'architecture.docx");
        long docId = uploaded.get(1).get("id").asLong();

        // Une développeuse du projet voit et télécharge, mais ne dépose ni ne supprime.
        JsonNode docs = read(fatou, "/api/projects/" + projectId + "/documents");
        assertThat(docs.size()).isEqualTo(2);
        assertThat(docs.get(0).get("canDelete").asBoolean()).isFalse();
        byte[] content = mvc.perform(get("/api/projects/" + projectId + "/documents/" + docId).header("Authorization", fatou))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Disposition", org.hamcrest.Matchers.containsString("PortOutClient.java")))
                .andReturn().getResponse().getContentAsByteArray();
        assertThat(new String(content, StandardCharsets.UTF_8)).isEqualTo("class PortOutClient {}");
        mvc.perform(multipart("/api/projects/" + projectId + "/documents").file(code).header("Authorization", fatou))
                .andExpect(status().isForbidden());
        mvc.perform(delete("/api/projects/" + projectId + "/documents/" + docId).header("Authorization", fatou))
                .andExpect(status().isForbidden());

        // Hors du projet : pas d'accès.
        JsonNode crm = null;
        for (JsonNode p : read(login("pm@yas-itfactory.sn"), "/api/projects")) {
            if (p.get("name").asText().equals("CRM Entreprise")) {
                crm = p;
            }
        }
        mvc.perform(get("/api/projects/" + crm.get("id").asLong() + "/documents").header("Authorization", papa))
                .andExpect(status().isForbidden());

        mvc.perform(delete("/api/projects/" + projectId + "/documents/" + docId).header("Authorization", papa))
                .andExpect(status().isNoContent());
        assertThat(read(fatou, "/api/projects/" + projectId + "/documents").size()).isEqualTo(1);
    }

    @Test
    void developerAsksAndLeadDevExplainsADocument() throws Exception {
        String pape = login("pape@yas-itfactory.sn");
        String fatou = login("fatou@yas-itfactory.sn");
        String lead = login("moussa@yas-itfactory.sn");
        long projectId = numflex(pape).get("project").get("id").asLong();
        MockMultipartFile tdr = new MockMultipartFile("files", "TDR portabilité.pdf", "application/pdf", "tdr".getBytes());
        JsonNode doc = json.readTree(mvc.perform(multipart("/api/projects/" + projectId + "/documents").file(tdr)
                        .param("category", "TDR").header("Authorization", pape))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString()).get(0);
        assertThat(doc.get("category").asText()).isEqualTo("TDR");
        String url = "/api/projects/" + projectId + "/documents/" + doc.get("id").asLong() + "/comments";

        mvc.perform(post(url).header("Authorization", fatou).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"text\":\"Le délai de portabilité est-il en jours ouvrés ?\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.authorRole").value("DEV"));
        mvc.perform(post(url).header("Authorization", lead).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"text\":\"Oui, 1 jour ouvré (section 3.2).\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.authorRole").value("LEAD_DEV"));

        JsonNode thread = read(fatou, url);
        assertThat(thread).extracting(c -> c.get("authorRole").asText()).containsExactly("DEV", "LEAD_DEV");
        assertThat(thread.get(1).get("canDelete").asBoolean()).isFalse();
        JsonNode listed = read(fatou, "/api/projects/" + projectId + "/documents");
        for (JsonNode d : listed) {
            if (d.get("id").asLong() == doc.get("id").asLong()) {
                assertThat(d.get("commentCount").asLong()).isEqualTo(2);
            }
        }
        // La suppression du document emporte sa discussion.
        mvc.perform(delete("/api/projects/" + projectId + "/documents/" + doc.get("id").asLong()).header("Authorization", pape))
                .andExpect(status().isNoContent());
    }

    @Test
    void leadDevAssignsDeveloperEstimatesAndLeadDevGrants() throws Exception {
        String lead = login("moussa@yas-itfactory.sn");
        String cheikh = login("cheikh@yas-itfactory.sn");
        JsonNode space = numflex(lead);
        assertThat(space.get("canAssign").asBoolean()).isTrue();
        long projectId = space.get("project").get("id").asLong();
        long cheikhId = read(cheikh, "/api/auth/me").get("id").asLong();

        JsonNode created = json.readTree(mvc.perform(post("/api/tasks").header("Authorization", lead)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"Client REST ARTP\",\"projectId\":" + projectId + ",\"assigneeId\":" + cheikhId
                                + ",\"status\":\"EN_COURS\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.estimateStatus").value("A_ESTIMER"))
                .andExpect(jsonPath("$.status").value("A_FAIRE"))
                .andExpect(jsonPath("$.active").value(false))
                .andReturn().getResponse().getContentAsString());
        long id = created.get("id").asLong();

        // Pas encore active : impossible de la démarrer.
        mvc.perform(patch("/api/tasks/" + id + "/status").header("Authorization", cheikh)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"EN_COURS\"}"))
                .andExpect(status().isBadRequest());
        // Seul le développeur assigné estime.
        mvc.perform(post("/api/tasks/" + id + "/estimate").header("Authorization", lead)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"days\":2}"))
                .andExpect(status().isForbidden());

        mvc.perform(post("/api/tasks/" + id + "/estimate").header("Authorization", cheikh)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"days\":3,\"comment\":\"Dont 1 j de tests\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estimateStatus").value("PROPOSEE"))
                .andExpect(jsonPath("$.canApproveEstimate").value(false));
        // Le développeur ne valide pas sa propre estimation.
        mvc.perform(post("/api/tasks/" + id + "/estimate/approve").header("Authorization", cheikh)
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isForbidden());
        // Refus sans motif : refusé.
        mvc.perform(post("/api/tasks/" + id + "/estimate/reject").header("Authorization", lead)
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest());

        mvc.perform(post("/api/tasks/" + id + "/estimate/approve").header("Authorization", lead)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"days\":2.5}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estimateStatus").value("ACCORDEE"))
                .andExpect(jsonPath("$.estimateDays").value(2.5))
                .andExpect(jsonPath("$.active").value(true));
        mvc.perform(patch("/api/tasks/" + id + "/status").header("Authorization", cheikh)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"EN_COURS\"}"))
                .andExpect(status().isOk());

        // Le lead dev voit toutes les tâches de son projet.
        boolean found = false;
        for (JsonNode t : read(lead, "/api/tasks?scope=projects")) {
            found |= t.get("id").asLong() == id;
        }
        assertThat(found).isTrue();

        JsonNode activity = read(cheikh, "/api/tasks/" + id + "/activity");
        assertThat(activity).extracting(a -> a.get("kind").asText()).contains("ESTIMATION");
        mvc.perform(delete("/api/tasks/" + id).header("Authorization", lead)).andExpect(status().isNoContent());
    }

    @Test
    void leadDevOnlyAssignsWithinTheProjectTeam() throws Exception {
        String lead = login("moussa@yas-itfactory.sn");
        long projectId = numflex(lead).get("project").get("id").asLong();
        long headId = read(login("head@yas-itfactory.sn"), "/api/auth/me").get("id").asLong();
        mvc.perform(post("/api/tasks").header("Authorization", lead).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"Hors équipe\",\"projectId\":" + projectId + ",\"assigneeId\":" + headId + "}"))
                .andExpect(status().isBadRequest());
    }
}
