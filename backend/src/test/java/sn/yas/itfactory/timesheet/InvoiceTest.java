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

import javax.imageio.ImageIO;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Facture mensuelle : saisie du montant, calcul brut / retenue, signature, envoi au Head of Office. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class InvoiceTest {

    @Autowired
    MockMvc mvc;
    @Autowired
    ObjectMapper json;

    private String login(String email) throws Exception {
        String body = mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"password\":\"" + DataSeeder.DEMO_PASSWORD + "\"}"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return "Bearer " + json.readTree(body).get("token").asText();
    }

    private JsonNode call(org.springframework.test.web.servlet.RequestBuilder rb, int expected) throws Exception {
        String body = mvc.perform(rb).andExpect(status().is(expected)).andReturn().getResponse().getContentAsString();
        return body.isEmpty() ? null : json.readTree(body);
    }

    private static String signature() throws Exception {
        BufferedImage img = new BufferedImage(300, 120, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        g.setColor(new Color(0x1d, 0x3f, 0xbf));
        g.setStroke(new BasicStroke(3));
        g.drawLine(150, 5, 160, 115);
        g.drawLine(60, 80, 260, 40);
        g.drawLine(110, 60, 120, 100);
        g.dispose();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(img, "png", out);
        return "data:image/png;base64," + Base64.getEncoder().encodeToString(out.toByteArray());
    }

    @Test
    void consultantSendsSignedInvoiceToHeadOfOffice() throws Exception {
        String moussa = login("moussa@yas-itfactory.sn");
        String head = login("head@yas-itfactory.sn");
        String fatou = login("fatou@yas-itfactory.sn");

        // Proposition du mois : numéro et dates calculés.
        JsonNode draft = call(get("/api/invoices/month/2026-09").header("Authorization", moussa), 200);
        assertThat(draft.get("id").isNull()).isTrue();
        assertThat(draft.get("number").asText()).isEqualTo("CONST-IT-C060-09-2026");
        assertThat(draft.get("periodEnd").asText()).isEqualTo("2026-09-30");

        JsonNode saved = call(put("/api/invoices").header("Authorization", moussa).contentType(MediaType.APPLICATION_JSON)
                .content("{\"month\":\"2026-09\",\"invoiceDate\":\"2026-09-21\",\"title\":\"Consultant Développeur Java\","
                        + "\"netAmount\":800000,\"computerDeduction\":0}"), 200);
        assertThat(saved.get("grossAmount").asLong()).isEqualTo(842105);
        assertThat(saved.get("withholding").asLong()).isEqualTo(42105);
        assertThat(saved.get("netToPay").asLong()).isEqualTo(800000);
        assertThat(saved.get("objectLine").asText()).isEqualTo("Consultant Développeur Java - Periode du 01 au 30 Septembre 2026");
        long id = saved.get("id").asLong();

        // Sans signature ni coordonnées : refusé.
        call(post("/api/invoices/" + id + "/submit").header("Authorization", moussa), 400);
        call(put("/api/invoices/profile").header("Authorization", moussa).contentType(MediaType.APPLICATION_JSON)
                .content("{\"phone\":\"770000000\",\"invoiceEmail\":\"moussa.perso@example.sn\",\"address\":\"Yoff / Sicap Layenne\"}"), 200);
        call(post("/api/invoices/" + id + "/submit").header("Authorization", moussa), 400);
        JsonNode profile = call(put("/api/invoices/profile/signature").header("Authorization", moussa)
                .contentType(MediaType.APPLICATION_JSON).content("{\"dataUrl\":\"" + signature() + "\"}"), 200);
        assertThat(profile.get("hasSignature").asBoolean()).isTrue();

        JsonNode sent = call(post("/api/invoices/" + id + "/submit").header("Authorization", moussa), 200);
        assertThat(sent.get("status").asText()).isEqualTo("SOUMISE");
        assertThat(sent.get("editable").asBoolean()).isFalse();
        // Plus modifiable une fois envoyée.
        call(put("/api/invoices").header("Authorization", moussa).contentType(MediaType.APPLICATION_JSON)
                .content("{\"month\":\"2026-09\",\"invoiceDate\":\"2026-09-21\",\"title\":\"x\",\"netAmount\":1}"), 409);

        byte[] pdf = mvc.perform(get("/api/invoices/" + id + "/pdf").header("Authorization", head))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Disposition", org.hamcrest.Matchers.containsString("Facture_CONST-IT-C060-09-2026.pdf")))
                .andReturn().getResponse().getContentAsByteArray();
        assertThat(new String(pdf, 0, 5)).isEqualTo("%PDF-");
        Files.createDirectories(Path.of("target"));
        Files.write(Path.of("target/facture-test.pdf"), pdf);
        // Un autre consultant n'y a pas accès.
        mvc.perform(get("/api/invoices/" + id + "/pdf").header("Authorization", fatou)).andExpect(status().isForbidden());
        mvc.perform(get("/api/invoices/team?month=2026-09").header("Authorization", fatou)).andExpect(status().isForbidden());

        // Le Head of Office voit la facture et la renvoie.
        JsonNode team = call(get("/api/invoices/team?month=2026-09").header("Authorization", head), 200);
        boolean listed = false;
        for (JsonNode r : team) {
            listed |= r.get("invoice") != null && !r.get("invoice").isNull() && r.get("invoice").get("id").asLong() == id;
        }
        assertThat(listed).isTrue();
        call(post("/api/invoices/" + id + "/reject").header("Authorization", head).contentType(MediaType.APPLICATION_JSON)
                .content("{}"), 400);
        JsonNode rejected = call(post("/api/invoices/" + id + "/reject").header("Authorization", head)
                .contentType(MediaType.APPLICATION_JSON).content("{\"comment\":\"Mettre la date du 30\"}"), 200);
        assertThat(rejected.get("status").asText()).isEqualTo("REJETEE");

        // Corrigée puis renvoyée, puis validée.
        call(put("/api/invoices").header("Authorization", moussa).contentType(MediaType.APPLICATION_JSON)
                .content("{\"month\":\"2026-09\",\"invoiceDate\":\"2026-09-30\",\"title\":\"Consultant Développeur Java\","
                        + "\"netAmount\":800000,\"computerDeduction\":50000}"), 200);
        JsonNode resent = call(post("/api/invoices/" + id + "/submit").header("Authorization", moussa), 200);
        assertThat(resent.get("netToPay").asLong()).isEqualTo(750000);
        JsonNode ok = call(post("/api/invoices/" + id + "/approve").header("Authorization", head)
                .contentType(MediaType.APPLICATION_JSON).content("{}"), 200);
        assertThat(ok.get("status").asText()).isEqualTo("VALIDEE");

        // La facture du mois suivant reprend l'intitulé et le montant.
        JsonNode next = call(get("/api/invoices/month/2026-10").header("Authorization", moussa), 200);
        assertThat(next.get("title").asText()).isEqualTo("Consultant Développeur Java");
        assertThat(next.get("netAmount").asLong()).isEqualTo(800000);
        assertThat(next.get("periodEnd").asText()).isEqualTo("2026-10-31");
    }
}
