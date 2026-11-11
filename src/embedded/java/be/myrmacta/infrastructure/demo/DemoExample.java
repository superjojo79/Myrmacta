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

    /** Un BPMN ou un DMN, ses noms, libellés et descriptions traduits. */
    String xml(String file) {
        String xml = read(dir.resolve(file));
        if (!english) {
            return xml;
        }
        // Une seule passe : un texte déjà traduit n'est jamais retraduit.
        Map<String, String> table = names();
        Matcher matcher = TRANSLATABLE.matcher(xml);
        StringBuilder out = new StringBuilder(xml.length());
        while (matcher.find()) {
            String text = matcher.group(2) != null ? matcher.group(2) : matcher.group(3);
            String translated = table.getOrDefault(text, text);
            String replacement = matcher.group(2) != null
                    ? " " + matcher.group(1) + "=\"" + translated + "\""
                    : "<description>" + translated + "</description>";
            matcher.appendReplacement(out, Matcher.quoteReplacement(replacement));
        }
        matcher.appendTail(out);
        return out.toString();
    }

    /** {@code name="…"} ou {@code label="…"} (groupes 1 et 2), ou {@code <description>…</description>} (groupe 3). */
    private static final Pattern TRANSLATABLE =
            Pattern.compile("\\s(name|label)=\"([^\"]*)\"|<description>([^<]*)</description>");

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
