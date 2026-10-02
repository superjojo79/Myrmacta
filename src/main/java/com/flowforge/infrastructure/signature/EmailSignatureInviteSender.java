package com.flowforge.infrastructure.signature;

import com.flowforge.domain.notification.port.out.EmailPort;
import com.flowforge.domain.signature.model.Signer;
import com.flowforge.domain.signature.port.out.SignatureInviteSenderPort;
import com.flowforge.infrastructure.config.PortalProperties;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.Objects;

/**
 * E-mail texte fixe en français, envoyé directement par {@link EmailPort} sans passer par l'outbox
 * (qui persisterait le jeton). Le jeton est placé dans le fragment d'URL, après le marqueur
 * {@code signature:} qui aiguille le portail vers l'écran de signature : jamais transmis au serveur
 * ni journalisé par le reverse-proxy (D-PORTAL-CSRF-SESSION).
 */
public final class EmailSignatureInviteSender implements SignatureInviteSenderPort {

    public static final String FRAGMENT_PREFIX = "signature:";

    private static final ZoneId ZONE = ZoneId.of("Europe/Paris");

    private final EmailPort emailPort;
    private final PortalProperties portalProperties;
    private final boolean english;
    private final DateTimeFormatter date;

    public EmailSignatureInviteSender(EmailPort emailPort, PortalProperties portalProperties) {
        this(emailPort, portalProperties, Locale.FRENCH);
    }

    /** @param locale langue du message : celle du tenant (le signataire externe n'a pas de profil) ; français ou anglais */
    public EmailSignatureInviteSender(EmailPort emailPort, PortalProperties portalProperties, Locale locale) {
        this.emailPort = Objects.requireNonNull(emailPort, "emailPort");
        this.portalProperties = Objects.requireNonNull(portalProperties, "portalProperties");
        this.english = Locale.ENGLISH.getLanguage().equals(Objects.requireNonNull(locale, "locale").getLanguage());
        this.date = english
                ? DateTimeFormatter.ofPattern("d MMMM yyyy", Locale.UK).withZone(ZONE)
                : DateTimeFormatter.ofPattern("d MMMM yyyy", Locale.FRENCH).withZone(ZONE);
    }

    @Override
    public void send(Signer signer, String token, InviteContext context) {
        String reference = singleLine(context.dossierReference());
        String simulation = context.simulated() ? "[SIMULATION] " : "";
        // Le français garde son lien exact ; l'anglais demande au portail l'anglais (le portail n'a pas de session ni de profil).
        String url = portalProperties.publicBaseUrl() + "/portal/" + (english ? "?lang=en" : "") + "#" + FRAGMENT_PREFIX + token;
        String subject;
        String body;
        if (english) {
            subject = simulation + (reference.isEmpty() ? "Signature requested" : "Signature requested — dossier " + reference);
            body = """
                    Hello,

                    Your signature is requested on a document%s.
                    %s
                    View the document, then sign or decline from this personal link, valid until %s:
                    %s

                    This link is personal to you: do not forward it. If this does not concern you, ignore this message.
                    """.formatted(reference.isEmpty() ? "" : " (dossier " + reference + ")",
                    context.simulated() ? "\nSIMULATION — this signature has no legal value.\n" : "",
                    date.format(context.deadlineAt()), url);
        } else {
            subject = simulation + (reference.isEmpty() ? "Signature demandée" : "Signature demandée — dossier " + reference);
            body = """
                    Bonjour,

                    Votre signature est demandée sur un document%s.
                    %s
                    Consultez le document puis signez ou refusez depuis ce lien personnel, valable jusqu'au %s :
                    %s

                    Ce lien vous est personnel : ne le transférez pas. Si vous n'êtes pas concerné(e), ignorez ce message.
                    """.formatted(reference.isEmpty() ? "" : " (dossier " + reference + ")",
                    context.simulated() ? "\nSIMULATION — cette signature n'a aucune valeur juridique.\n" : "",
                    date.format(context.deadlineAt()), url);
        }
        emailPort.send(signer.email(), subject, body);
    }

    /** Pas de retour à la ligne dans l'objet ni le corps (injection d'en-têtes). */
    private static String singleLine(String value) {
        return value == null ? "" : value.replaceAll("[\\r\\n\\t]+", " ").strip();
    }
}
