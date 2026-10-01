package com.flowforge.domain.reporting.port.out;

import com.flowforge.domain.reporting.model.SlaAlertState;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface SlaAlertStateRepository {

    /** Deletes every row of the tenant, then inserts {@code states}. */
    void replaceAll(String tenantId, List<SlaAlertState> states);

    Optional<SlaAlertState> find(String tenantId, UUID dossierId);

    SlaAlertState save(SlaAlertState state);

    /**
     * Compare-and-set (TODO §79, D5) : passe l'état de {@code expectedPrevious} à {@code next} si, et seulement si, la ligne a
     * encore ce statut. Renvoie {@code false} quand un autre exécutant l'a déjà fait — l'appelant ne notifie alors pas, ce qui
     * évite que deux pods envoient la même alerte pour la même transition.
     */
    boolean transition(SlaAlertState expectedPrevious, SlaAlertState next);
}
