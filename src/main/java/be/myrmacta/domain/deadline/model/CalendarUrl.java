package be.myrmacta.domain.deadline.model;

import java.net.URI;
import java.util.Locale;

/**
 * TODO §121 (D-CAL-ICS-URL) — URL d'une source ICS. Elle porte souvent un jeton (lien « secret » d'un agenda) : c'est
 * un secret. Seule sa forme masquée, schéma et hôte, se montre ; {@link #toString()} et les messages d'erreur ne
 * disent jamais l'URL. Le schéma n'est contrôlé qu'ici (http ou https) ; la garde réseau décide ensuite de ce qui
 * peut être appelé.
 *
 * @param value l'URL entière, à ne jamais journaliser ni renvoyer
 * @param masked {@code scheme://hôte[:port]/…}
 */
public record CalendarUrl(String value, String masked, String host) {

    public static final int MAX_LENGTH = 2_048;

    public static CalendarUrl parse(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new IllegalArgumentException("calendar URL is required");
        }
        String url = raw.strip();
        if (url.length() > MAX_LENGTH) {
            throw new IllegalArgumentException("calendar URL is too long");
        }
        URI uri;
        try {
            uri = URI.create(url);
        } catch (IllegalArgumentException malformed) {
            throw new IllegalArgumentException("calendar URL is malformed");
        }
        String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(Locale.ROOT);
        if (!scheme.equals("https") && !scheme.equals("http")) {
            throw new IllegalArgumentException("calendar URL must be an https URL");
        }
        String host = uri.getHost();
        if (host == null || host.isBlank()) {
            throw new IllegalArgumentException("calendar URL has no host");
        }
        if (uri.getRawUserInfo() != null) {
            throw new IllegalArgumentException("calendar URL must not contain credentials");
        }
        String lowerHost = host.toLowerCase(Locale.ROOT);
        String masked = scheme + "://" + lowerHost + (uri.getPort() == -1 ? "" : ":" + uri.getPort()) + "/…";
        return new CalendarUrl(url, masked, lowerHost);
    }

    @Override
    public String toString() {
        return "CalendarUrl[" + masked + "]";
    }
}
