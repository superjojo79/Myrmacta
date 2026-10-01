package com.flowforge.domain.connector.port.out;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/**
 * Tracks the last cron firing of a {@code scheduler}-type connector instance. Deliberately kept
 * separate from {@link ConnectorInstanceRepository}/{@code ConnectorInstanceData} — this state is
 * relevant only to one connector type, not the shared connector instance model.
 */
public interface ConnectorScheduleStateRepository {

    Optional<Instant> findLastTriggeredAt(UUID connectorInstanceId);

    /** Upsert: creates the row on first firing, overwrites the timestamp otherwise. */
    void upsert(UUID connectorInstanceId, Instant triggeredAt);

    /**
     * TODO §79 (D5) : prend le créneau — compare-and-set de {@code last_triggered_at} de {@code expectedLast} (null = jamais
     * déclenché : insertion) vers {@code claimedAt}. Un seul des exécutants concurrents obtient {@code true} et déclenche ;
     * les autres sautent le créneau, donc pas de dossier ni de workflow en double pour une même échéance.
     */
    boolean claimTrigger(UUID connectorInstanceId, Instant expectedLast, Instant claimedAt);

    /**
     * Rend le créneau pris par {@link #claimTrigger} quand le déclenchement a échoué (réessai au prochain tour) : remet
     * {@code previousLast} (null = supprime la ligne) seulement si elle vaut encore {@code claimedAt}.
     */
    void releaseTrigger(UUID connectorInstanceId, Instant previousLast, Instant claimedAt);
}
