package be.myrmacta.domain.ebox.model;

/**
 * TODO §155 (D-EBOX-SEND-AUDIT) — l'eBox a refusé la publication (400, 403, 404, 413…) ou l'appel n'est certainement pas parti
 * (adresse refusée, connexion impossible) : rien n'a été publié, rien n'est journalisé (ATREQ04), l'étape du process échoue.
 */
public class EboxPublicationRejectedException extends RuntimeException {

    private final int status;

    public EboxPublicationRejectedException(int status) {
        super("ebox refused the publication (HTTP " + status + ")");
        this.status = status;
    }

    public EboxPublicationRejectedException(String message) {
        super(message);
        this.status = 0;
    }

    /** Code HTTP du refus ; 0 si l'appel n'est pas parti. */
    public int status() {
        return status;
    }
}
