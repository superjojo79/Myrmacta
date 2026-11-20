package be.myrmacta.domain.ebox.model;

import be.myrmacta.domain.ebox.model.EboxSendAuditEntry.Outcome;
import java.util.Objects;

/**
 * TODO §155 — ce que le process reçoit après un envoi : l'issue, la voie retenue et, si elle la réclame, la voie de secours à
 * suivre.
 *
 * @param fallbackChannel nulle si la voie de secours n'est pas demandée
 * @param replayed vrai si la clé d'idempotence avait déjà été traitée : rien n'a été rappelé
 */
public record EboxSendOutcome(Outcome outcome, EboxDelivery delivery, FallbackChannel fallbackChannel, String providerMessageId,
        EboxProofOfDispatch proof, boolean replayed) {

    public EboxSendOutcome {
        Objects.requireNonNull(outcome, "outcome");
        Objects.requireNonNull(delivery, "delivery");
        if (!delivery.needsFallback()) {
            fallbackChannel = null;
        }
    }
}
