package be.myrmacta.domain.mailroom.model;

/** TODO §113 (D-MAILROOM-DEPOSIT) — un fichier refusé avant toute écriture (pas un PDF, trop volumineux, sans nom). */
public class DepositRefusedException extends RuntimeException {

    public DepositRefusedException(String reason) {
        super(reason);
    }
}
