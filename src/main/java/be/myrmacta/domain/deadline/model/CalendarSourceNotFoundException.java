package be.myrmacta.domain.deadline.model;

import java.util.UUID;

/** TODO §121 (D-CAL-SOURCE) — aucune source de ce tenant avec cet identifiant (404). */
public class CalendarSourceNotFoundException extends RuntimeException {

    public CalendarSourceNotFoundException(UUID id) {
        super("Calendar source not found: " + id);
    }
}
