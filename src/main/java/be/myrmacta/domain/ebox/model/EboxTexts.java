package be.myrmacta.domain.ebox.model;

import java.util.regex.Pattern;

/** Contrôles de texte partagés par le modèle d'envoi : présent, borné, sans caractère de contrôle. */
final class EboxTexts {

    private static final Pattern TEXT_CONTROL = Pattern.compile("[\\p{Cntrl}&&[^\\r\\n\\t]]");
    private static final Pattern LINE_CONTROL = Pattern.compile("\\p{Cntrl}");

    private EboxTexts() {}

    /** Une ligne : ni retour à la ligne ni caractère de contrôle. */
    static String line(String value, String name, int max) {
        String text = required(value, name, max);
        if (LINE_CONTROL.matcher(text).find()) {
            throw new IllegalArgumentException(name + " must not contain control characters");
        }
        return text;
    }

    /** Un texte libre : retours à la ligne et tabulations admis. */
    static String text(String value, String name, int max) {
        String text = required(value, name, max);
        if (TEXT_CONTROL.matcher(text).find()) {
            throw new IllegalArgumentException(name + " must not contain control characters");
        }
        return text;
    }

    static String optionalLine(String value, String name, int max) {
        return value == null || value.isBlank() ? null : line(value, name, max);
    }

    private static String required(String value, String name, int max) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " is required");
        }
        String text = value.strip();
        if (text.length() > max) {
            throw new IllegalArgumentException(name + " must be at most " + max + " characters");
        }
        return text;
    }
}
