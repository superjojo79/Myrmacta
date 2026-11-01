package be.myrmacta.domain.mailroom.model;

import java.util.UUID;

/** TODO §113 (D-MAILROOM-DEPOSIT) — sort d'un fichier déposé : le courrier créé, ou la raison du refus. */
public record DepositResult(String filename, UUID dossierId, String reference, String refusal) {

    public static DepositResult created(String filename, UUID dossierId, String reference) {
        return new DepositResult(filename, dossierId, reference, null);
    }

    public static DepositResult refused(String filename, String refusal) {
        return new DepositResult(filename, null, null, refusal);
    }
}
