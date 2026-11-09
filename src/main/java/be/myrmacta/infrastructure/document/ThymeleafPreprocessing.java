package be.myrmacta.infrastructure.document;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Détection du pré-traitement Thymeleaf {@code __…__} (issue #43). La même expression que
 * {@code StandardExpressionPreprocessor} : un groupe interne vide ({@code ________}) n'est pas une expression.
 */
public final class ThymeleafPreprocessing {

    private static final Pattern EXECUTABLE = Pattern.compile("__(.*?)__", Pattern.DOTALL);

    private ThymeleafPreprocessing() {}

    /** Vrai lorsqu'au moins un fragment {@code __…__} a un contenu non vide après trim. */
    public static boolean containsExecutable(String decoded) {
        if (decoded == null || decoded.indexOf('_') < 0) {
            return false;
        }
        Matcher matcher = EXECUTABLE.matcher(decoded);
        while (matcher.find()) {
            if (!matcher.group(1).trim().isEmpty()) {
                return true;
            }
        }
        return false;
    }
}
