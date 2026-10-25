package be.myrmacta.domain.dossier.model;

/** TODO §81 (D2) — recherche invalide (champ inconnu, non cherchable, opérateur ou valeur incompatibles) : mappée en 400. */
public class InvalidDossierSearchException extends IllegalArgumentException {
    public InvalidDossierSearchException(String message) {
        super(message);
    }
}
