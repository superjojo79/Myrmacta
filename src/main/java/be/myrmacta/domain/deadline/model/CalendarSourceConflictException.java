package be.myrmacta.domain.deadline.model;

/** TODO §121 (D-CAL-SOURCE, D-CAL-ICS-PREVIEW) — nom déjà pris, ou contenu changé depuis l'aperçu (409). */
public class CalendarSourceConflictException extends RuntimeException {

    public CalendarSourceConflictException(String message) {
        super(message);
    }
}
