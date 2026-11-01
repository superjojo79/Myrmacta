package be.myrmacta.domain.mailroom.model;

import java.util.Objects;
import java.util.UUID;

/** TODO §113 (D-MAILROOM-DEPOSIT) — point de dépôt de l'écran « Déposer » : une instance active du connecteur {@code mail-deposit}. */
public record DepositPoint(UUID id, String name, String dossierTypeCode) {

    public static final String CONNECTOR_TYPE = "mail-deposit";

    public DepositPoint {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(dossierTypeCode, "dossierTypeCode");
    }
}
