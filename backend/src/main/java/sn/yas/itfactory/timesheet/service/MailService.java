package sn.yas.itfactory.timesheet.service;

import jakarta.mail.internet.MimeMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;
import sn.yas.itfactory.timesheet.config.AppProperties;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Collection;
import java.util.List;
import java.util.Objects;

/**
 * Envoi des e-mails. Si app.mail.enabled=false (développement), le message est journalisé
 * et le message (message.txt, message.html) et ses pièces jointes sont déposés dans data/outbox pour pouvoir les ouvrir.
 */
@Service
public class MailService {

    private static final Logger log = LoggerFactory.getLogger(MailService.class);

    public record Attachment(String fileName, byte[] content) {
    }

    private final ObjectProvider<JavaMailSender> sender;
    private final AppProperties properties;

    public MailService(ObjectProvider<JavaMailSender> sender, AppProperties properties) {
        this.sender = sender;
        this.properties = properties;
    }

    /** @return true si le message est parti (ou a été déposé dans l'outbox en développement). */
    public boolean send(Collection<String> to, Collection<String> cc, String subject, String html, List<Attachment> attachments) {
        List<String> recipients = to.stream().filter(Objects::nonNull).distinct().toList();
        if (recipients.isEmpty()) {
            log.warn("E-mail « {} » non envoyé : aucun destinataire", subject);
            return false;
        }
        String body = html + "<p style='color:#888;font-size:12px'>— YAS IT-FACTORY · Fiches de temps<br>"
                + "<a href='" + properties.frontendUrl() + "'>" + properties.frontendUrl() + "</a></p>";
        if (!properties.mail().enabled()) {
            return writeToOutbox(recipients, cc, subject, body, attachments);
        }
        try {
            JavaMailSender mailSender = sender.getObject();
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
            helper.setFrom(properties.mail().from());
            helper.setTo(recipients.toArray(String[]::new));
            if (cc != null && !cc.isEmpty()) {
                helper.setCc(cc.stream().filter(Objects::nonNull).distinct().toArray(String[]::new));
            }
            helper.setSubject(subject);
            helper.setText(body, true);
            for (Attachment a : attachments) {
                helper.addAttachment(a.fileName(), new ByteArrayResource(a.content()));
            }
            mailSender.send(message);
            log.info("E-mail « {} » envoyé à {}", subject, recipients);
            return true;
        } catch (Exception e) {
            log.error("Échec d'envoi de l'e-mail « {} » à {}", subject, recipients, e);
            return false;
        }
    }

    private boolean writeToOutbox(List<String> to, Collection<String> cc, String subject, String body, List<Attachment> attachments) {
        log.info("[MAIL désactivé] À: {} | Cc: {} | Objet: {} | Pièces jointes: {}", to, cc, subject,
                attachments.stream().map(Attachment::fileName).toList());
        try {
            Path dir = Path.of("data", "outbox",
                    LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss-SSS")));
            Files.createDirectories(dir);
            Files.writeString(dir.resolve("message.txt"), "À: " + to + "\nCc: " + cc + "\nObjet: " + subject + "\n");
            Files.writeString(dir.resolve("message.html"), body);
            for (Attachment a : attachments) {
                Files.write(dir.resolve(a.fileName()), a.content());
            }
        } catch (IOException e) {
            log.warn("Impossible d'écrire dans l'outbox", e);
        }
        return true;
    }
}
