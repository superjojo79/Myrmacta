package be.myrmacta.domain.connector.model;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.Objects;

/**
 * TODO §129 (D-TRIGGER-LABEL) — libellé lisible d'un dossier créé par un déclencheur.
 * Préfère un champ métier ({@code objet}, {@code subject}, {@code expediteur}, {@code from}) ; sinon
 * {@code type — date} UTC. Jamais {@code trigger:<uuid>} (réservé à {@code createdBy}).
 */
public final class TriggerDossierLabel {

    public static final int MAX_CHARS = 200;

    private static final String[] PREFERRED_FIELDS = {"objet", "subject", "expediteur", "from"};

    private TriggerDossierLabel() {}

    public static String resolve(String dossierTypeCode, Map<String, Object> fields, Clock clock) {
        Objects.requireNonNull(clock, "clock");
        if (fields != null) {
            for (String key : PREFERRED_FIELDS) {
                String text = plainText(fields.get(key));
                if (text != null) {
                    return truncate(text);
                }
            }
        }
        String type = dossierTypeCode == null || dossierTypeCode.isBlank() ? "dossier" : dossierTypeCode.strip();
        String date = LocalDate.ofInstant(clock.instant(), ZoneOffset.UTC).toString();
        return truncate(type + " — " + date);
    }

    private static String plainText(Object value) {
        if (!(value instanceof String raw)) {
            return null;
        }
        String text = raw.strip();
        return text.isEmpty() ? null : text;
    }

    private static String truncate(String label) {
        return label.length() > MAX_CHARS ? label.substring(0, MAX_CHARS) : label;
    }
}
