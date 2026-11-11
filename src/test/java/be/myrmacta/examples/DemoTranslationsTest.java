package be.myrmacta.examples;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

/**
 * TODO §127 (D-DEMO-LOCALE-FILES, lot 2 de #55) — la variante anglaise des exemples de la démo ne dérive pas du français :
 * chaque copie JSON de {@code en/} a la structure et les codes de son original (seuls les textes visibles changent, et les
 * expressions {@code ${…}} restent les mêmes ; {@code name} n'est un texte que pour un connecteur, ailleurs c'est un code) ; {@code en/names.json} traduit chaque nom, libellé et description des BPMN et
 * DMN de l'exemple, et rien d'autre : pas les codes (nom d'un message, d'une sortie DMN, champ {@code flowable:…}).
 */
class DemoTranslationsTest {

    private static final ObjectMapper JSON = JsonMapper.builder().build();
    private static final Set<String> TEXT_KEYS = Set.of("label", "helpText", "group", "subject", "textBody", "htmlBody");
    private static final Pattern TAG = Pattern.compile("<([A-Za-z][\\w.:-]*)((?:\\s+[\\w.:-]+\\s*=\\s*\"[^\"]*\")*)");
    private static final Pattern ATTRIBUTE = Pattern.compile("\\s(name|label)\\s*=\\s*\"([^\"]*)\"");
    /** Comme {@code DemoExample} : le {@code name} de ces éléments est un code, jamais traduit. */
    private static final Set<String> CODE_NAMED =
            Set.of("message", "signal", "error", "escalation", "output", "inputData", "variable");
    private static final Pattern DESCRIPTION = Pattern.compile("<description>([^<]*)</description>");
    private static final Pattern EXPRESSION = Pattern.compile("\\$\\{[^}]*}");

    /** Les fichiers qu'un paquet de la démo lit et qui portent du texte visible. */
    private static final Map<String, List<String>> REQUIRED = Map.of(
            "digital-mail-room", List.of("config/groupes.json", "config/type-dossier.json", "config/type-facture.json",
                    "config/type-reponse-facture.json", "config/workflow-config.json", "config/workflow-config-facture.json",
                    "config/workflow-config-reponse.json", "config/gabarit-accuse-reception.json",
                    "config/connecteur-depot.exemple.json"),
            "onboarding-collaborateur", List.of("config/type-dossier.json", "config/questions.json",
                    "config/workflow-config.json"));

    @ParameterizedTest
    @ValueSource(strings = {"digital-mail-room", "onboarding-collaborateur"})
    void eachEnglishCopyKeepsTheStructureAndTheCodesOfItsOriginal(String name) throws IOException {
        Path example = Path.of("exemples", name);
        for (String file : REQUIRED.get(name)) {
            assertThat(example.resolve("en").resolve(file)).as("traduction de " + file).isRegularFile();
        }
        List<Path> copies;
        try (Stream<Path> walk = Files.walk(example.resolve("en"))) {
            copies = walk.filter(p -> p.toString().endsWith(".json") && !p.getFileName().toString().equals("names.json"))
                    .toList();
        }
        for (Path copy : copies) {
            Path original = example.resolve(example.resolve("en").relativize(copy));
            assertThat(original).as("original de " + copy).isRegularFile();
            List<String> problems = new ArrayList<>();
            compare(JSON.readTree(original.toFile()), JSON.readTree(copy.toFile()), null, copy.toString(), problems);
            assertThat(problems).as(copy.toString()).isEmpty();
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"digital-mail-room", "onboarding-collaborateur"})
    void theNameTableTranslatesEveryNameOfTheProcessesAndTables_andNothingElse(String name) throws IOException {
        Path example = Path.of("exemples", name);
        Map<String, String> names = JSON.readValue(example.resolve("en/names.json").toFile(),
                new TypeReference<Map<String, String>>() {});
        Set<String> texts = new TreeSet<>();
        try (Stream<Path> files = Files.list(example)) {
            for (Path file : files.filter(p -> p.toString().endsWith(".bpmn20.xml") || p.toString().endsWith(".dmn")).toList()) {
                String xml = Files.readString(file);
                Matcher tag = TAG.matcher(xml);
                while (tag.find()) {
                    boolean codeName = nameIsCode(tag.group(1));
                    Matcher attribute = ATTRIBUTE.matcher(tag.group(2));
                    while (attribute.find()) {
                        if (!(codeName && attribute.group(1).equals("name")) && !attribute.group(2).isBlank()) {
                            texts.add(attribute.group(2));
                        }
                    }
                }
                collect(DESCRIPTION.matcher(xml), texts);
            }
        }
        assertThat(texts).as("textes sans traduction").isSubsetOf(names.keySet());
        // Un code (nom de message, de variable, champ flowable) n'a rien à faire dans la table : le traduire casserait le routage.
        assertThat(names.keySet()).as("traductions de textes absents des BPMN et DMN, ou de codes").isSubsetOf(texts);
        assertThat(names.values()).as("traductions vides").noneMatch(String::isBlank);
    }

    private static boolean nameIsCode(String element) {
        int colon = element.indexOf(':');
        return element.startsWith("flowable:") || CODE_NAMED.contains(colon < 0 ? element : element.substring(colon + 1));
    }

    private static void collect(Matcher matcher, Set<String> into) {
        while (matcher.find()) {
            if (!matcher.group(1).isBlank()) {
                into.add(matcher.group(1));
            }
        }
    }

    private static void compare(JsonNode fr, JsonNode en, String key, String at, List<String> problems) {
        if (fr.isObject()) {
            if (!en.isObject()) {
                problems.add(at + " : objet attendu");
                return;
            }
            Set<String> frKeys = new TreeSet<>(fr.propertyNames());
            Set<String> enKeys = new TreeSet<>(en.propertyNames());
            if (!frKeys.equals(enKeys)) {
                problems.add(at + " : clés " + enKeys + " au lieu de " + frKeys);
                return;
            }
            for (String child : frKeys) {
                // « name » est un code (champ, questionnaire), sauf pour un connecteur, où c'est le nom affiché.
                boolean text = child.equals("name") && fr.has("connectorType");
                compare(fr.get(child), en.get(child), text ? "label" : child, at + "." + child, problems);
            }
        } else if (fr.isArray()) {
            if (!en.isArray() || en.size() != fr.size()) {
                problems.add(at + " : tableau de " + fr.size() + " éléments attendu");
                return;
            }
            for (int i = 0; i < fr.size(); i++) {
                compare(fr.get(i), en.get(i), key, at + "[" + i + "]", problems);
            }
        } else if (key != null && (TEXT_KEYS.contains(key) || key.startsWith("_")) && fr.isString()) {
            if (!en.isString() || (en.asString().isBlank() && !fr.asString().isBlank())) {
                problems.add(at + " : texte traduit attendu");
            } else if (!expressions(fr.asString()).equals(expressions(en.asString()))) {
                problems.add(at + " : expressions ${…} différentes");
            }
        } else if (!fr.equals(en)) {
            problems.add(at + " : « " + en + " » au lieu de « " + fr + " » (seuls les textes visibles se traduisent)");
        }
    }

    private static List<String> expressions(String text) {
        List<String> found = new ArrayList<>();
        Matcher matcher = EXPRESSION.matcher(text);
        while (matcher.find()) {
            found.add(matcher.group());
        }
        return found;
    }
}
