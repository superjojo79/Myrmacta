package com.flowforge.infrastructure.portal;

import com.flowforge.domain.notification.port.out.EmailPort;
import com.flowforge.domain.workflow.portal.model.PortalLink;
import com.flowforge.domain.workflow.portal.port.out.PortalInviteSenderPort;
import com.flowforge.infrastructure.config.PortalProperties;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.Objects;
import org.springframework.stereotype.Component;

/**
 * D-PORTAL-INVITE-DELIVERY / D-PORTAL-EMAIL-TEMPLATE — e-mail texte fixe en français, envoyé
 * directement par {@link EmailPort} (même transport SMTP que le canal EMAIL) mais <strong>sans</strong>
 * passer par l'outbox, qui persisterait le corps — donc le jeton — en base. Le jeton est placé dans
 * le fragment d'URL ({@code #}) : jamais transmis au serveur ni journalisé par le reverse-proxy
 * (D-PORTAL-CSRF-SESSION).
 */
@Component
class EmailPortalInviteSender implements PortalInviteSenderPort {

    private static final DateTimeFormatter DATE =
            DateTimeFormatter.ofPattern("d MMMM yyyy", Locale.FRENCH).withZone(ZoneId.of("Europe/Paris"));

    private final EmailPort emailPort;
    private final PortalProperties properties;

    EmailPortalInviteSender(EmailPort emailPort, PortalProperties properties) {
        this.emailPort = Objects.requireNonNull(emailPort, "emailPort");
        this.properties = Objects.requireNonNull(properties, "properties");
    }

    @Override
    public void send(PortalLink link, String token, InviteContext context) {
        String taskName = singleLine(context.taskName(), "Demande d'informations");
        String reference = singleLine(context.dossierReference(), "");
        String subject = reference.isEmpty() ? taskName : taskName + " — dossier " + reference;
        String url = properties.publicBaseUrl() + "/portal/#" + token;
        String body = """
                Bonjour,

                Une réponse de votre part est attendue : « %s »%s.

                Accédez au formulaire sécurisé avec ce lien personnel, valable jusqu'au %s :
                %s

                Ce lien vous est personnel : ne le transférez pas. Vous pouvez l'utiliser plusieurs fois
                jusqu'à l'envoi de votre réponse. Si vous n'êtes pas concerné(e), ignorez ce message.
                """.formatted(
                taskName,
                reference.isEmpty() ? "" : " (dossier " + reference + ")",
                DATE.format(context.expiresAt()),
                url);
        emailPort.send(link.recipientEmail(), subject, body);
    }

    /** Pas de retour à la ligne dans l'objet ni le corps (injection d'en-têtes / mise en forme trompeuse). */
    private static String singleLine(String value, String fallback) {
        if (value == null || value.isBlank()) {
            return fallback;
        }
        return value.replaceAll("[\\r\\n\\t]+", " ").strip();
    }
}
