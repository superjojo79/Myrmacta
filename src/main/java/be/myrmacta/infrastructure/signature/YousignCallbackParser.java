package be.myrmacta.infrastructure.signature;

import be.myrmacta.domain.signature.model.SignatureOutcome;
import be.myrmacta.domain.signature.port.out.SignaturePort;
import be.myrmacta.domain.signature.port.out.SignaturePort.SignatureCallbackEvent;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Corps JSON déjà authentifié d'un webhook Yousign (TODO §109, D-SIGNATURE-WEBHOOK).
 * Seuls les événements qui changent l'issue de la demande sont traduits. Les autres événements
 * publiés par Yousign (rappel, {@code signer.notified}, …) ne closent rien : l'appelant répond
 * quand même 2xx, Yousign rejouant tout ce qui n'est pas un succès.
 *
 * <p>Noms relus le 2026-10-28 sur la référence publique des webhooks. {@code rejected} est le refus
 * d'un approbateur (statut {@code rejected}), distinct du refus d'un signataire ({@code declined}).
 */
public final class YousignCallbackParser {

    static final String REFERENCE_PREFIX = "yousign:";

    private static final Map<String, SignatureOutcome> TERMINAL = Map.of(
            "signature_request.done", SignatureOutcome.SIGNED,
            "signature_request.declined", SignatureOutcome.DECLINED,
            "signature_request.expired", SignatureOutcome.EXPIRED,
            "signature_request.canceled", SignatureOutcome.CANCELLED,
            "signature_request.rejected", SignatureOutcome.FAILED);

    /** Événements de la référence publique qui ne portent pas une issue de demande. */
    private static final Set<String> INFORMATIONAL = Set.of(
            "signature_request.activated",
            "signature_request.approved",
            "signature_request.deleted",
            "signature_request.permanently_deleted",
            "signature_request.reactivated",
            "signature_request.reminder_executed",
            "signature_request.automatic_reminder_executed",
            "signature_request.paused",
            "signature_request.resumed",
            "signer.declined",
            "signer.done",
            "signer.error",
            "signer.identification_blocked",
            "signer.identification_failed",
            "signer.identification_expired",
            "signer.identification_succeeded",
            "signer.identity_saved",
            "signer.link_opened",
            "signer.notified",
            "signer.sender_contacted",
            "signer.notification_delivery_failed",
            "approver.approved",
            "approver.notified",
            "approver.rejected",
            "approver.notification_delivery_failed",
            "contact.created");

    private YousignCallbackParser() {}

    public static Optional<SignatureCallbackEvent> parse(Object payload) {
        if (!(payload instanceof Map<?, ?> body)) {
            throw new IllegalArgumentException("Yousign event must be a JSON object");
        }
        Object name = body.get("event_name");
        if (!(name instanceof String eventName) || eventName.isBlank()) {
            throw new IllegalArgumentException("Yousign event_name is required");
        }
        if (INFORMATIONAL.contains(eventName)) {
            return Optional.empty();
        }
        SignatureOutcome outcome = TERMINAL.get(eventName);
        if (outcome == null) {
            throw new IllegalArgumentException("unsupported Yousign event: " + eventName);
        }
        return Optional.of(new SignatureCallbackEvent(reference(body), outcome, Map.of()));
    }

    private static String reference(Map<?, ?> body) {
        if (!(body.get("data") instanceof Map<?, ?> data)
                || !(data.get("signature_request") instanceof Map<?, ?> request)
                || !(request.get("id") instanceof String id)) {
            throw new IllegalArgumentException("Yousign signature_request.id is required");
        }
        try {
            return REFERENCE_PREFIX + UUID.fromString(id);
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException("Yousign signature_request.id is not a UUID");
        }
    }
}
