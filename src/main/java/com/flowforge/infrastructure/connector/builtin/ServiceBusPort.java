package com.flowforge.infrastructure.connector.builtin;

import java.util.List;

/**
 * TODO §87 (D3d) — réception d'une file ou d'un abonnement Azure Service Bus en verrouillage de lecture (peek-lock) : un message
 * reçu reste verrouillé pour nous ; {@link #complete} le retire de la file, {@link #abandon} le rend (le courtier le met en file
 * « lettres mortes » après {@code MaxDeliveryCount}).
 */
interface ServiceBusPort {

    /** @throws RuntimeException transport ou autorisation en échec : rien n'est retiré de la file */
    List<Received> receive(Endpoint endpoint, int maxMessages, int waitSeconds, long maxMessageBytes);

    void complete(Endpoint endpoint, Received message);

    void abandon(Endpoint endpoint, Received message);

    /** {@code baseUrl} sans barre finale ; {@code authorization} : valeur de l'en-tête (SAS ou Bearer), jamais journalisée. */
    record Endpoint(String baseUrl, String entityPath, java.util.function.Supplier<String> authorization) {
        @Override
        public String toString() {
            return "Endpoint[" + baseUrl + "/" + entityPath + "]";
        }
    }

    /** {@code lockUrl} : adresse de verrou renvoyée par le courtier (vérifiée comme appartenant à l'hôte du connecteur). */
    record Received(String body, String messageId, int deliveryCount, String lockUrl) {}
}
