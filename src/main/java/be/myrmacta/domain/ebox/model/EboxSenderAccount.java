package be.myrmacta.domain.ebox.model;

import java.util.Objects;
import java.util.UUID;

/**
 * TODO §155 (D-EBOX-SEND-CHANNELS) — compte expéditeur : l'instance de connecteur du tenant qui porte la configuration et les
 * secrets d'une voie (identifiants OAuth, URL, mandat). Le domaine ne voit jamais les secrets.
 */
public record EboxSenderAccount(UUID connectorInstanceId) {

    public EboxSenderAccount {
        Objects.requireNonNull(connectorInstanceId, "connectorInstanceId");
    }
}
