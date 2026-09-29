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

    private static final DateTimeFormatter DATE =
            DateTimeFormatter.ofPattern("d MMMM yyyy", Locale.FRENCH).withZone(ZoneId.of("Europe/Paris"));

    private final EmailPort emailPort;
    private final PortalProperties portalProperties;

    public EmailSignatureInviteSender(EmailPort emailPort, PortalProperties portalProperties) {
        this.emailPort = Objects.requireNonNull(emailPort, "emailPort");
        this.portalProperties = Objects.requireNonNull(portalProperties, "portalProperties");
    }

    @Override
    public void send(Signer signer, String token, InviteContext context) {
        String reference = singleLine(context.dossierReference());
        String subject = (context.simulated() ? "[SIMULATION] " : "")
                + (reference.isEmpty() ? "Signature demandée" : "Signature demandée — dossier " + reference);
        String url = portalProperties.publicBaseUrl() + "/portal/#" + FRAGMENT_PREFIX + token;
        String body = """
                Bonjour,

                Votre signature est demandée sur un document%s.
                %s
                Consultez le document puis signez ou refusez depuis ce lien personnel, valable jusqu'au %s :
                %s

                Ce lien vous est personnel : ne le transférez pas. Si vous n'êtes pas concerné(e), ignorez ce message.
                """.formatted(reference.isEmpty() ? "" : " (dossier " + reference + ")",
                context.simulated() ? "\nSIMULATION — cette signature n'a aucune valeur juridique.\n" : "",
                DATE.format(context.deadlineAt()), url);
        emailPort.send(signer.email(), subject, body);
    }

    /** Pas de retour à la ligne dans l'objet ni le corps (injection d'en-têtes). */
    private static String singleLine(String value) {
        return value == null ? "" : value.replaceAll("[\\r\\n\\t]+", " ").strip();
    }
}
