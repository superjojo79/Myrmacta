package be.myrmacta.domain.promotion.artifact;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * TODO §83 (D-PROMO-PARAMS) — remplace {@code {{nom}}} par le paramètre d'environnement du même nom, dans un texte, une table ou une
 * liste. Un paramètre absent lève {@link MissingParameterException} : l'artefact qui le porte est bloqué. Partagé par les
 * connecteurs et, TODO §117 (D-PROMO-GROUP), le groupe de l'IdP d'un groupe d'utilisateurs.
 */
public final class EnvironmentParameters {

    private static final Pattern PLACEHOLDER = Pattern.compile("\\{\\{([A-Za-z0-9_.-]{1,64})}}");

    private EnvironmentParameters() {}

    /** @param owner ce qui porte la valeur, pour le message (« connector 'X' », « user group 'Y' ») */
    @SuppressWarnings("unchecked")
    public static Object substitute(Object value, Map<String, String> parameters, String owner) {
        if (value instanceof String s) {
            Matcher m = PLACEHOLDER.matcher(s);
            StringBuilder sb = new StringBuilder();
            while (m.find()) {
                String replacement = parameters.get(m.group(1));
                if (replacement == null) {
                    throw new MissingParameterException(owner + " needs the environment parameter '" + m.group(1) + "'");
                }
                m.appendReplacement(sb, Matcher.quoteReplacement(replacement));
            }
            m.appendTail(sb);
            return sb.toString();
        }
        if (value instanceof Map<?, ?> map) {
            Map<String, Object> copy = new LinkedHashMap<>();
            ((Map<String, Object>) map).forEach((k, v) -> copy.put(k, substitute(v, parameters, owner)));
            return copy;
        }
        if (value instanceof List<?> list) {
            return list.stream().map(v -> substitute(v, parameters, owner)).toList();
        }
        return value;
    }

    public static final class MissingParameterException extends RuntimeException {
        MissingParameterException(String message) {
            super(message);
        }
    }
}
