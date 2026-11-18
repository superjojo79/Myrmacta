package be.myrmacta.infrastructure.notification;

import be.myrmacta.domain.notification.port.out.EmailPort;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.AddressException;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;
import java.nio.charset.StandardCharsets;
import java.util.Objects;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Component;

@Component
public class SmtpEmailAdapter implements EmailPort {

    private static final String INVALID_SENDER =
            "MAIL_FROM (app.mail.from) must be a single bare e-mail address, e.g. noreply@example.org";

    private final JavaMailSender mailSender;
    private final InternetAddress from;

    /**
     * Issue #134 (D-MAIL-FROM, registre §149) : sans {@code From}, JavaMail signait {@code utilisateur@nom-du-poste}
     * (en-tête et enveloppe). L'expéditeur est une adresse nue ; {@code spring.mail.properties.mail.smtp.from} reprend
     * la même valeur pour l'enveloppe. Adresse invalide : démarrage refusé.
     */
    public SmtpEmailAdapter(JavaMailSender mailSender, @Value("${app.mail.from:noreply@myrmacta.invalid}") String from) {
        this.mailSender = Objects.requireNonNull(mailSender, "mailSender");
        this.from = parseSender(from);
    }

    private static InternetAddress parseSender(String from) {
        if (from == null) {
            throw new IllegalStateException(INVALID_SENDER);
        }
        try {
            InternetAddress address = new InternetAddress(from, true);
            String bare = address.getAddress();
            int at = bare == null ? -1 : bare.indexOf('@');
            if (address.getPersonal() != null || !from.equals(bare) || at <= 0 || at != bare.lastIndexOf('@')
                    || at == bare.length() - 1 || bare.chars().anyMatch(c -> c <= ' ' || c == 0x7f)) {
                throw new IllegalStateException(INVALID_SENDER);
            }
            return address;
        } catch (AddressException ex) {
            throw new IllegalStateException(INVALID_SENDER, ex);
        }
    }

    @Override
    public String send(
            String to, String subject, String body, String htmlBody, EmailAttachmentContent attachment) {
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper =
                    new MimeMessageHelper(message, attachment != null || htmlBody != null, StandardCharsets.UTF_8.name());
            helper.setFrom(from);
            helper.setTo(to);
            helper.setSubject(subject);
            // Jamais une réponse à un message automatique : l'e-mail entrant (D3c) refuse ce qui porte cet en-tête (pas de boucle).
            message.setHeader("Auto-Submitted", "auto-generated");
            if (htmlBody != null) {
                // Issue #19 : text/plain puis text/html, dans un multipart/alternative.
                helper.setText(body, htmlBody);
            } else {
                helper.setText(body, false);
            }
            if (attachment != null) {
                // Issue #183 : nom contrôlé en amont (D-NOTIF-ATTACH-NAME), encodé pour les caractères non ASCII.
                helper.addAttachment(attachment.fileName(), new ByteArrayResource(attachment.content()),
                        attachment.contentType());
            }
            mailSender.send(message);
            // TODO §113 — JavaMail pose le Message-ID à l'envoi : c'est lui qu'une réponse citera dans In-Reply-To.
            return message.getMessageID();
        } catch (MessagingException ex) {
            throw new IllegalStateException("Failed to build email message", ex);
        }
    }
}
