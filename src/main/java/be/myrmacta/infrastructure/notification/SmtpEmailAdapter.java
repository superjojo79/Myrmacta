package be.myrmacta.infrastructure.notification;

import be.myrmacta.domain.notification.port.out.EmailPort;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import java.nio.charset.StandardCharsets;
import java.util.Objects;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Component;

@Component
public class SmtpEmailAdapter implements EmailPort {

    private final JavaMailSender mailSender;

    public SmtpEmailAdapter(JavaMailSender mailSender) {
        this.mailSender = Objects.requireNonNull(mailSender, "mailSender");
    }

    @Override
    public void send(String to, String subject, String body, EmailAttachmentContent attachment) {
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper =
                    new MimeMessageHelper(message, attachment != null, StandardCharsets.UTF_8.name());
            helper.setTo(to);
            helper.setSubject(subject);
            // Jamais une réponse à un message automatique : l'e-mail entrant (D3c) refuse ce qui porte cet en-tête (pas de boucle).
            message.setHeader("Auto-Submitted", "auto-generated");
            helper.setText(body, false);
            if (attachment != null) {
                // Issue #183 : nom contrôlé en amont (D-NOTIF-ATTACH-NAME), encodé pour les caractères non ASCII.
                helper.addAttachment(attachment.fileName(), new ByteArrayResource(attachment.content()),
                        attachment.contentType());
            }
            mailSender.send(message);
        } catch (MessagingException ex) {
            throw new IllegalStateException("Failed to build email message", ex);
        }
    }
}
