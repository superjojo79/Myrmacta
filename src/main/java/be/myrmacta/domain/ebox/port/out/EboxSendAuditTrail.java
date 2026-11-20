package be.myrmacta.domain.ebox.port.out;

import be.myrmacta.domain.ebox.model.EboxRecipient;
import be.myrmacta.domain.ebox.model.EboxSendAuditEntry;
import java.util.Optional;

/**
 * TODO §155 (D-EBOX-SEND-AUDIT) — journal d'audit des envois eBox : on ajoute, on ne modifie ni ne supprime jamais (la base le
 * refuse aussi). Chaque ajout est validé dans sa propre transaction : l'appel a eu lieu, même si celle du process recule.
 */
public interface EboxSendAuditTrail {

    void append(EboxSendAuditEntry entry);

    /** Dernière ligne de cette clé d'idempotence pour ce tenant, et si elle visait {@code recipient}. */
    Optional<EboxSendAuditEntry.Summary> last(String tenantId, String idempotencyKey, EboxRecipient recipient);
}
