package be.myrmacta.infrastructure.signature;

import be.myrmacta.domain.signature.model.SignatureOutcome;
import be.myrmacta.domain.signature.port.out.SignaturePort.SignatureCallbackEvent;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Corps JSON déjà authentifié d'un webhook DocuSign Connect (TODO §109, D-SIGNATURE-WEBHOOK).
 * Seuls les événements qui changent l'issue de la demande sont traduits. Les autres événements
 * publiés par DocuSign répondent 2xx sans clore la demande.
 *
 * <p>Noms relus le 2026-10-28 sur
 * <a href="https://developers.docusign.com/platform/webhooks/connect/event-triggers/">event-triggers</a>.
 * Il n'existe pas d'événement {@code envelope-expired} : l'expiration est un {@code envelope-voided}
 * dont le motif, relu sur le statut d'enveloppe de l'API, est {@code Envelope has expired.}
 */
public final class DocusignCallbackParser {

    static final String REFERENCE_PREFIX = "docusign:";
    static final String EXPIRED_REASON = "Envelope has expired.";

    private static final Set<String> INFORMATIONAL = Set.of(
            "envelope-sent",
            "envelope-delivered",
            "envelope-created",
            "envelope-resent",
            "envelope-corrected",
            "envelope-reminder-sent",
            "envelope-purge",
            "envelope-deleted",
            "envelope-discard",
            "recipient-sent",
            "recipient-delivered",
            "recipient-completed",
            "recipient-declined",
            "recipient-authenticationfailed",
            "recipient-autoresponded",
            "recipient-resent",
            "recipient-reassign",
            "recipient-delegate",
            "recipient-finish-later");

    private DocusignCallbackParser() {}

    public static Optional<SignatureCallbackEvent> parse(Object payload) {
        if (!(payload instanceof Map<?, ?> body)) {
            throw new IllegalArgumentException("DocuSign event must be a JSON object");
        }
        Object name = body.get("event");
        if (!(name instanceof String event) || event.isBlank()) {
            throw new IllegalArgumentException("DocuSign event is required");
        }
        if (INFORMATIONAL.contains(event)) {
            return Optional.empty();
        }
        SignatureOutcome outcome = outcome(event, voidedReason(body));
        if (outcome == null) {
            throw new IllegalArgumentException("unsupported DocuSign event: " + event);
        }
        return Optional.of(new SignatureCallbackEvent(reference(body), outcome, Map.of()));
    }

    private static SignatureOutcome outcome(String event, String voidedReason) {
        return switch (event) {
            case "envelope-completed" -> SignatureOutcome.SIGNED;
            case "envelope-declined" -> SignatureOutcome.DECLINED;
            case "envelope-voided" -> expired(voidedReason) ? SignatureOutcome.EXPIRED : SignatureOutcome.CANCELLED;
            case "envelope-removed" -> SignatureOutcome.FAILED;
            default -> null;
        };
    }

    static boolean expired(String voidedReason) {
        if (voidedReason == null) {
            return false;
        }
        String normalized = voidedReason.trim().toLowerCase(Locale.ROOT);
        String expected = EXPIRED_REASON.toLowerCase(Locale.ROOT);
        return normalized.equals(expected) || normalized.equals(expected.substring(0, expected.length() - 1));
    }

    private static String voidedReason(Map<?, ?> body) {
        if (!(body.get("data") instanceof Map<?, ?> data)) {
            return null;
        }
        if (data.get("envelopeSummary") instanceof Map<?, ?> summary
                && summary.get("voidedReason") instanceof String reason) {
            return reason;
        }
        return null;
    }

    private static String reference(Map<?, ?> body) {
        if (!(body.get("data") instanceof Map<?, ?> data) || !(data.get("envelopeId") instanceof String id)) {
            throw new IllegalArgumentException("DocuSign envelopeId is required");
        }
        try {
            return REFERENCE_PREFIX + UUID.fromString(id);
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException("DocuSign envelopeId is not a UUID");
        }
    }
}
