package be.myrmacta.domain.dossier.model;

import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * TODO §105 (issue #211, lot P, D-PIECE-TYPES) — type de pièce justificative déclaré sur un type de dossier (« Preuve de
 * domicile »). Le type est choisi au dépôt d'une pièce (métadonnée {@link #METADATA_KEY} de l'élément
 * {@link ElementType#ATTACHMENT}) ; une pièce sans type, ou dont le type a été retiré, est « non classée ».
 * {@code sensitive} interdit qu'un traitement automatisé (pré-vérification IA) lise son contenu.
 */
public record PieceType(String key, String label, boolean sensitive) {

    /** Métadonnée de l'élément {@code ATTACHMENT} qui porte la clé du type choisi. */
    public static final String METADATA_KEY = "pieceKey";
    public static final int MAX_PER_DOSSIER_TYPE = 30;
    public static final int MAX_LABEL_LENGTH = 100;
    private static final Pattern KEY = Pattern.compile("^[a-z][a-zA-Z0-9_]{0,59}$");

    public PieceType {
        Objects.requireNonNull(key, "key");
        Objects.requireNonNull(label, "label");
        if (!isValidKey(key)) {
            throw new IllegalArgumentException("pieceType key must match " + KEY.pattern() + ", was: " + key);
        }
        label = label.strip();
        if (label.isEmpty() || label.length() > MAX_LABEL_LENGTH) {
            throw new IllegalArgumentException(
                    "pieceType label must be 1 to " + MAX_LABEL_LENGTH + " characters: " + key);
        }
    }

    public static boolean isValidKey(String key) {
        return key != null && KEY.matcher(key).matches();
    }

    /** D-PIECE-TYPES — au plus {@link #MAX_PER_DOSSIER_TYPE} types, clés uniques. */
    public static void requireValid(List<PieceType> pieceTypes) {
        if (pieceTypes.size() > MAX_PER_DOSSIER_TYPE) {
            throw new IllegalArgumentException("at most " + MAX_PER_DOSSIER_TYPE + " pieceTypes per dossier type");
        }
        Set<String> keys = new HashSet<>();
        for (PieceType type : pieceTypes) {
            Objects.requireNonNull(type, "pieceType");
            if (!keys.add(type.key())) {
                throw new IllegalArgumentException("Duplicate pieceType key: " + type.key());
            }
        }
    }
}
