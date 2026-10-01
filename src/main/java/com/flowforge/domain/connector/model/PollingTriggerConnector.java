package com.flowforge.domain.connector.model;

import java.util.ArrayList;
import java.util.List;

/**
 * TODO §87 (revue adversariale D3c/D3d, H2) — connecteur TRIGGER qui <b>interroge</b> une source (boîte e-mail, bus) : chaque
 * événement est remis à {@code delivery} <em>avant</em> que le message ne soit rangé ou acquitté à la source. Si la remise échoue,
 * le message n'est pas acquitté comme traité : il est rendu à la source (bus) ou rangé à part (boîte « refusés ») — jamais perdu en
 * silence. {@link #onEvent} ne sert que de repli (tests) : il acquitte avant l'enregistrement, ce que le runtime n'utilise pas.
 */
public interface PollingTriggerConnector extends TriggerConnector {

    void poll(ConnectorInstanceData instance, EventDelivery delivery);

    @FunctionalInterface
    interface EventDelivery {
        /** @throws RuntimeException l'événement n'a pas pu être pris en charge (le message ne sera pas acquitté comme traité) */
        void deliver(WorkflowTriggerEvent event);
    }

    @Override
    default List<WorkflowTriggerEvent> onEvent(ConnectorInstanceData instance, Object rawPayload) {
        List<WorkflowTriggerEvent> events = new ArrayList<>();
        poll(instance, events::add);
        return List.copyOf(events);
    }
}
