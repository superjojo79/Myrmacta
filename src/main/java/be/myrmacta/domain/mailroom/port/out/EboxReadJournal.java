package be.myrmacta.domain.mailroom.port.out;

import be.myrmacta.domain.mailroom.model.EboxTakenMessage;
import java.time.LocalDate;
import java.util.Collection;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * TODO §150 (D-EBOX-JOURNAL, D-EBOX-WINDOW) — journal de lecture de l'eBox Enterprise : messages déjà pris en charge (par eBox,
 * quelle que soit l'instance qui les a lus) et curseur de lecture de chaque instance.
 */
public interface EboxReadJournal {

    /** Parmi {@code messageIds}, ceux que cette eBox a déjà pris en charge pour ce registre. */
    Set<String> alreadyTaken(String tenantId, String enterpriseNumber, String registryId, Collection<String> messageIds);

    /**
     * TODO §151 (D-EBOX-MOVE) — l'inscription d'un message : la partition d'où il a été lu et l'instance qui l'a remis ; vide si
     * cette eBox ne l'a pas pris en charge.
     */
    Optional<EboxTakenMessage> taken(String tenantId, String enterpriseNumber, String registryId, String messageId);

    /**
     * Inscrit le message, dans la transaction de l'appelant. Faux s'il était déjà inscrit (une autre instance l'a pris entre la
     * lecture et la remise) : l'appelant annule alors sa remise.
     */
    boolean recordTaken(EboxTakenMessage message);

    /** Dernier jour lu entièrement par cette instance, pour ce registre et cette partition. */
    Optional<LocalDate> readUntil(UUID connectorInstanceId, String registryId, String partition);

    void advance(UUID connectorInstanceId, String registryId, String partition, LocalDate readUntil);
}
