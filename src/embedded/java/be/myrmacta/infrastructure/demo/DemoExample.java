package be.myrmacta.infrastructure.demo;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * TODO §127 (D-DEMO-LOCALE, D-DEMO-LOCALE-FILES, lot 2 de #55) — un exemple de la démo, lu dans la langue de la démo
 * ({@code app.demo.locale} : {@code fr}, la langue des exemples, ou {@code en}).
 * <ul>
 *   <li>JSON : en anglais, la copie traduite {@code en/<chemin>} si elle existe, sinon l'original ;</li>
 *   <li>BPMN et DMN : l'original, dont les attributs {@code name} et {@code label} et les éléments {@code description}
 *       sont traduits par {@code en/names.json} (texte français exact vers texte anglais) ;</li>
 *   <li>textes écrits dans un paquet : {@link #text(String, String)}.</li>
 * </ul>
 */
final class DemoExample {

    static final Set<String> LOCALES = Set.of("fr", "en");

    private final Path dir;
    private final boolean english;
    private final ObjectMapper json;
    private Map<String, String> names;

    DemoExample(Path dir, String locale, ObjectMapper json) {
        if (!LOCALES.contains(locale)) {
            throw new IllegalArgumentException("demo: unknown locale '" + locale + "' (app.demo.locale: fr or en)");
        }
        this.dir = dir;
        this.english = "en".equals(locale);
        this.json = json;
    }

    boolean english() {
        return english;
    }

    Path dir() {
        return dir;
    }

    /** Un fichier JSON, traduit s'il a une copie anglaise. */
    JsonNode json(String file) {
        Path translated = dir.resolve("en").resolve(file);
        return json.readTree(read(english && Files.isRegularFile(translated) ? translated : dir.resolve(file)));
    }

    /**
     * Un BPMN ou un DMN, ses noms, libellés et descriptions traduits. Le {@code name} d'un élément qui le lit comme un code
     * (message, signal, sortie ou donnée d'entrée DMN, élément {@code flowable:…}) n'est jamais traduit : le renommer
     * casserait le routage. La traduction est un texte brut, échappé pour le XML.
     */
    String xml(String file) {
        String xml = read(dir.resolve(file));
        if (!english) {
            return xml;
        }
        // Une seule passe : un texte déjà traduit n'est jamais retraduit.
        Map<String, String> table = names();
        Matcher matcher = DESCRIPTION_OR_TAG.matcher(xml);
        StringBuilder out = new StringBuilder(xml.length());
        while (matcher.find()) {
            String replacement = matcher.group(1) != null
                    ? "<description>" + translate(matcher.group(1), table) + "</description>"
                    : "<" + matcher.group(2) + attributes(matcher.group(2), matcher.group(3), table);
            matcher.appendReplacement(out, Matcher.quoteReplacement(replacement));
        }
        matcher.appendTail(out);
        return out.toString();
    }

    /** {@code <description>…</description>} (groupe 1), ou le début d'une balise ouvrante : nom (2), attributs (3). */
    private static final Pattern DESCRIPTION_OR_TAG = Pattern.compile(
            "<description>([^<]*)</description>|<([A-Za-z][\\w.:-]*)((?:\\s+[\\w.:-]+\\s*=\\s*(?:\"[^\"]*\"|'[^']*'))*)");

    private static final Pattern ATTRIBUTE = Pattern.compile("(\\s)(name|label)(\\s*=\\s*)\"([^\"]*)\"");

    /** Éléments dont le {@code name} est un code lu par le moteur (nom local, sans préfixe d'espace de noms). */
    static final Set<String> CODE_NAMED = Set.of("message", "signal", "error", "escalation", "output", "inputData", "variable");

    static boolean nameIsCode(String element) {
        int colon = element.indexOf(':');
        return element.startsWith("flowable:") || CODE_NAMED.contains(colon < 0 ? element : element.substring(colon + 1));
    }

    private static String attributes(String element, String attributes, Map<String, String> table) {
        boolean codeName = nameIsCode(element);
        Matcher matcher = ATTRIBUTE.matcher(attributes);
        StringBuilder out = new StringBuilder(attributes.length());
        while (matcher.find()) {
            String text = matcher.group(4);
            String value = "name".equals(matcher.group(2)) && codeName ? text : translate(text, table);
            matcher.appendReplacement(out, Matcher.quoteReplacement(
                    matcher.group(1) + matcher.group(2) + matcher.group(3) + "\"" + value + "\""));
        }
        matcher.appendTail(out);
        return out.toString();
    }

    private static String translate(String text, Map<String, String> table) {
        String translated = table.get(text);
        return translated == null ? text : escape(translated);
    }

    private static String escape(String text) {
        return text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;");
    }

    byte[] bytes(String file) {
        return xml(file).getBytes(StandardCharsets.UTF_8);
    }

    /** Un texte écrit dans un paquet, dans la langue de la démo. */
    String text(String french, String english) {
        return this.english ? english : french;
    }

    private Map<String, String> names() {
        if (names == null) {
            Path table = dir.resolve("en/names.json");
            names = Files.isRegularFile(table)
                    ? json.readValue(read(table), new TypeReference<Map<String, String>>() {})
                    : Map.of();
        }
        return names;
    }

    private static String read(Path file) {
        try {
            return Files.readString(file);
        } catch (IOException ex) {
            throw new UncheckedIOException(ex);
        }
    }
}
