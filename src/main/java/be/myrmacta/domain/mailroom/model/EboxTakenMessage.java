package be.myrmacta.domain.mailroom.model;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * TODO §150 (D-EBOX-JOURNAL) — message eBox pris en charge : l'eBox lue (numéro BCE), le registre qui l'a servi, son
 * identifiant, la partition lue et l'instance de connecteur qui l'a remis.
 */
public record EboxTakenMessage(
        String tenantId,
        String enterpriseNumber,
        String registryId,
        String messageId,
        String partition,
        UUID connectorInstanceId,
        Instant takenAt) {

    public EboxTakenMessage {
        Objects.requireNonNull(tenantId, "tenantId");
        Objects.requireNonNull(enterpriseNumber, "enterpriseNumber");
        Objects.requireNonNull(registryId, "registryId");
        Objects.requireNonNull(messageId, "messageId");
        Objects.requireNonNull(partition, "partition");
        Objects.requireNonNull(connectorInstanceId, "connectorInstanceId");
        Objects.requireNonNull(takenAt, "takenAt");
    }
}
