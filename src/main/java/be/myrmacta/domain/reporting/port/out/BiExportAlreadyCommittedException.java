package be.myrmacta.domain.reporting.port.out;

/**
 * TODO §79 (D5) — un autre exécutant a déjà enregistré l'export de ce créneau : le commit de celui-ci est refusé (rien n'est
 * écrit) et l'appelant retire ses fichiers sans signaler d'erreur.
 */
public class BiExportAlreadyCommittedException extends RuntimeException {

    public BiExportAlreadyCommittedException(String tenantId) {
        super("BI export already committed by another executor for tenant " + tenantId);
    }
}
