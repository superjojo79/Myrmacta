package be.myrmacta.domain.ebox.model;

import java.util.Locale;

/** TODO §155 (D-EBOX-SEND-MODEL) — une seule langue par message, choisie depuis le dossier. */
public enum EboxLanguage {
    FR,
    NL,
    DE;

    /** Code de la spec ({@code LocalizedString}) : {@code fr}, {@code nl} ou {@code de}. */
    public String code() {
        return name().toLowerCase(Locale.ROOT);
    }

    public static EboxLanguage of(String code) {
        if (code == null) {
            throw new IllegalArgumentException("language is required (fr, nl or de)");
        }
        return switch (code.strip().toLowerCase(Locale.ROOT)) {
            case "fr" -> FR;
            case "nl" -> NL;
            case "de" -> DE;
            default -> throw new IllegalArgumentException("language must be fr, nl or de");
        };
    }
}
