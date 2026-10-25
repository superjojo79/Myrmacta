package be.myrmacta.domain.workflow.model;

/** TODO §82 (D6) — la publication directe n'est pas autorisée sur cette instance. */
public class DirectPublicationNotAllowedException extends RuntimeException {
    public DirectPublicationNotAllowedException(String message) {
        super(message);
    }
}
