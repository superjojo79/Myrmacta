package be.myrmacta.infrastructure.portal;

import be.myrmacta.domain.notification.port.out.EmailPort;
import be.myrmacta.domain.workflow.portal.model.PortalLink;
import be.myrmacta.domain.workflow.portal.port.out.PortalInviteSenderPort;
import be.myrmacta.infrastructure.config.I18nProperties;
import be.myrmacta.infrastructure.config.PortalProperties;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.Objects;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * D-PORTAL-INVITE-DELIVERY / D-PORTAL-EMAIL-TEMPLATE — e-mail texte fixe, en français ou en anglais selon la langue par défaut du tenant (D7), envoyé
 * directement par {@link EmailPort} (même transport SMTP que le canal EMAIL) mais <strong>sans</strong>
 * passer par l'outbox, qui persisterait le corps — donc le jeton — en base. Le jeton est placé dans
 * le fragment d'URL ({@code #}) : jamais transmis au serveur ni journalisé par le reverse-proxy
 * (D-PORTAL-CSRF-SESSION).
 */
@Component
class EmailPortalInviteSender implements PortalInviteSenderPort {

    private static final ZoneId ZONE = ZoneId.of("Europe/Paris");

    private final EmailPort emailPort;
    private final PortalProperties properties;
    private final boolean english;
    private final DateTimeFormatter date;

    /** Langue du message = langue par défaut du tenant (D7) : le destinataire externe n'a pas de profil. */
    @Autowired
    EmailPortalInviteSender(EmailPort emailPort, PortalProperties properties, I18nProperties i18n) {
        this(emailPort, properties, i18n.locale());
    }

    EmailPortalInviteSender(EmailPort emailPort, PortalProperties properties) {
        this(emailPort, properties, Locale.FRENCH);
    }

    EmailPortalInviteSender(EmailPort emailPort, PortalProperties properties, Locale locale) {
        this.emailPort = Objects.requireNonNull(emailPort, "emailPort");
        this.properties = Objects.requireNonNull(properties, "properties");
        this.english = Locale.ENGLISH.getLanguage().equals(Objects.requireNonNull(locale, "locale").getLanguage());
        this.date = english
                ? DateTimeFormatter.ofPattern("d MMMM yyyy", Locale.UK).withZone(ZONE)
                : DateTimeFormatter.ofPattern("d MMMM yyyy", Locale.FRENCH).withZone(ZONE);
    }

    @Override
    public void send(PortalLink link, String token, InviteContext context) {
        String taskName = singleLine(context.taskName(), english ? "Request for information" : "Demande d'informations");
        String reference = singleLine(context.dossierReference(), "");
        String subject = reference.isEmpty() ? taskName : taskName + " — dossier " + reference;
        // Le français garde son lien exact ; l'anglais demande au portail l'anglais (page publique, sans session ni profil).
        String url = properties.publicBaseUrl() + "/portal/" + (english ? "?lang=en" : "") + "#" + token;
        String body = english
                ? """
                Hello,

                A reply from you is expected: « %s »%s.

                Open the secure form with this personal link, valid until %s:
                %s

                This link is personal to you: do not forward it. You can use it several times
                until you send your reply. If this does not concern you, ignore this message.
                """.formatted(taskName, reference.isEmpty() ? "" : " (dossier " + reference + ")", date.format(context.expiresAt()), url)
                : """
                Bonjour,

                Une réponse de votre part est attendue : « %s »%s.

                Accédez au formulaire sécurisé avec ce lien personnel, valable jusqu'au %s :
                %s

                Ce lien vous est personnel : ne le transférez pas. Vous pouvez l'utiliser plusieurs fois
                jusqu'à l'envoi de votre réponse. Si vous n'êtes pas concerné(e), ignorez ce message.
                """.formatted(taskName, reference.isEmpty() ? "" : " (dossier " + reference + ")", date.format(context.expiresAt()), url);
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
