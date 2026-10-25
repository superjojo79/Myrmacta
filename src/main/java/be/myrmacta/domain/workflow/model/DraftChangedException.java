package be.myrmacta.domain.workflow.model;

/** TODO §82 (D6) — le brouillon a changé depuis sa relecture (horodatage attendu différent). */
public class DraftChangedException extends RuntimeException {
    public DraftChangedException(String message) {
        super(message);
    }
}
