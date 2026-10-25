package be.myrmacta.domain.workflow.portal.model;

import java.util.Objects;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * Numéro de registre national (TODO §104, N3) : 11 chiffres une fois retirés les séparateurs usuels (points, tirets,
 * espaces). Donnée sensible : {@link #toString()} ne montre jamais les chiffres (N2). La clé de contrôle n'est pas
 * vérifiée : le fournisseur d'identité fait autorité, et une valeur saisie dans le dossier est comparée telle quelle.
 */
public record NationalRegisterNumber(String digits) {

    private static final Pattern DIGITS = Pattern.compile("\\d{11}");
    private static final Pattern SEPARATORS = Pattern.compile("[\\s.\\-]");

    public NationalRegisterNumber {
        Objects.requireNonNull(digits, "digits");
        if (!DIGITS.matcher(digits).matches()) {
            throw new IllegalArgumentException("a national register number has 11 digits");
        }
    }

    /** Vide si {@code raw} n'est pas un texte qui, sans séparateurs, fait exactement 11 chiffres. */
    public static Optional<NationalRegisterNumber> parse(Object raw) {
        if (!(raw instanceof String text)) {
            return Optional.empty();
        }
        String digits = SEPARATORS.matcher(text).replaceAll("");
        return DIGITS.matcher(digits).matches() ? Optional.of(new NationalRegisterNumber(digits)) : Optional.empty();
    }

    @Override
    public String toString() {
        return "NationalRegisterNumber[***]";
    }
}
