package be.myrmacta.domain.deadline.model;

import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;

/**
 * TODO §121 (D-CAL-SOURCE) — jeu nommé de jours fermés, rechargeable d'un bloc. Une source ne produit que des
 * jours {@code CLOSED} ; une saisie manuelle la surcharge (D-CAL-OVERRIDE). L'URL d'une source {@code ICS_URL}
 * est un secret : elle reste dans l'adaptateur de persistance et n'est jamais portée par le domaine.
 */
public record CalendarSource(UUID id, String name, Kind kind, List<CalendarEntry> closedDays, Instant loadedAt,
                             String loadedBy, String contentSha256, String lastError) {

    public static final int MAX_NAME = 80;
    /** Garde-fou ajouté en §121 : un fichier de 5 000 événements récurrents ne doit pas gonfler la mémoire. */
    public static final int MAX_ENTRIES = 10_000;

    public CalendarSource {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(kind, "kind");
        name = canonicalName(name);
        closedDays = closedDays == null ? List.of() : closedDays.stream().sorted().toList();
        if (closedDays.size() > MAX_ENTRIES) {
            throw new IllegalArgumentException("a source holds at most " + MAX_ENTRIES + " entries");
        }
        if (closedDays.stream().anyMatch(entry -> entry.nature() != CalendarEntry.Nature.CLOSED)) {
            throw new IllegalArgumentException("a source only holds CLOSED entries");
        }
    }

    public static CalendarSource create(String name, Kind kind, List<CalendarEntry> closedDays, Instant at, String by,
                                        String contentSha256) {
        return new CalendarSource(UUID.randomUUID(), name, kind, closedDays, at, by, contentSha256, null);
    }

    /** Recharge : mêmes identité, nom et type ; les jours, la date, l'auteur et l'empreinte sont remplacés. */
    public CalendarSource reloaded(List<CalendarEntry> days, Instant at, String by, String sha256) {
        return new CalendarSource(id, name, kind, days, at, by, sha256, null);
    }

    /** Dernier chargement en échec : les jours restent ceux du chargement précédent. */
    public CalendarSource failed(String error) {
        return new CalendarSource(id, name, kind, closedDays, loadedAt, loadedBy, contentSha256, error);
    }

    public String nameKey() {
        return name.toLowerCase(Locale.ROOT);
    }

    private static String canonicalName(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new IllegalArgumentException("source name is required");
        }
        String name = raw.strip();
        if (name.length() > MAX_NAME) {
            throw new IllegalArgumentException("source name is at most " + MAX_NAME + " characters");
        }
        return name;
    }

    public enum Kind {
        ICS_FILE,
        ICS_URL,
        BE_FEDERAL;

        public static Kind parse(String raw) {
            if (raw == null || raw.isBlank()) {
                throw new IllegalArgumentException("source kind is required");
            }
            try {
                return valueOf(raw.strip().toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException invalid) {
                throw new IllegalArgumentException("unknown source kind: " + raw.strip());
            }
        }
    }
}
