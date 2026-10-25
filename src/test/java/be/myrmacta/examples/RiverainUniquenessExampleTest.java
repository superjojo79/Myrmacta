package be.myrmacta.examples;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

/**
 * TODO §102 (issue #184, lot D) — l'exemple « autorisation riverain » impose l'unicité de la plaque normalisée
 * et ne demande plus à l'agent de la vérifier à la main. R7 attend un refus à la création.
 */
class RiverainUniquenessExampleTest {

    private static final Path EXAMPLE = Path.of("exemples/autorisation-riverain");
    private static final ObjectMapper JSON = new ObjectMapper();

    @Test
    void normalizedPlateIsUniqueAmongOpenDossiers() throws Exception {
        JsonNode field = field("plaqueNormalisee");
        assertThat(field.path("unique").asBoolean())
                .as("plaqueNormalisee unique")
                .isTrue();
        assertThat(field.path("formula").asText()).contains("paysImmatriculation");
        assertThat(field.path("searchable").asBoolean()).isTrue();
        assertThat(field.path("helpText").asText())
                .doesNotContain("Rechercher sur ce champ pour détecter");
    }

    @Test
    void instructionQuestionNoLongerAsksTheAgentToDeclareTheCheck() throws Exception {
        String questions = Files.readString(EXAMPLE.resolve("config/questions.json"));
        assertThat(questions).doesNotContain("doublonVerifie");
        assertThat(questions).doesNotContain("J'ai vérifié qu'aucune autre demande");
    }

    @Test
    void instructionTaskNoLongerSendsTheAgentSearchingForADuplicate() throws Exception {
        for (String bpmn : new String[] {
                "autorisation-riverain-sans-portail.bpmn20.xml",
                "autorisation-riverain.bpmn20.xml"}) {
            String documentation = instructionDocumentation(Files.readString(EXAMPLE.resolve(bpmn)));
            assertThat(documentation).doesNotContain("recherche sur");
            assertThat(documentation).contains("refusée à la création");
        }
    }

    @Test
    void recipeExpectsTheDuplicateToBeRefused() throws Exception {
        String readme = Files.readString(EXAMPLE.resolve("README.md"));
        assertThat(readme).contains("La création d'un second dossier ouvert pour la même clé est refusée");
        assertThat(readme).doesNotContain("Les deux dossiers doivent remonter");
        String difficulties = Files.readString(EXAMPLE.resolve("DIFFICULTES.md"));
        assertThat(difficulties).contains("**Levé (issue #184, TODO §102).**");
    }

    private static JsonNode field(String name) throws Exception {
        JsonNode definitions = JSON.readTree(EXAMPLE.resolve("config/type-dossier.json").toFile())
                .path("fieldDefinitions");
        for (JsonNode field : definitions) {
            if (name.equals(field.path("name").asText())) return field;
        }
        throw new AssertionError("champ absent : " + name);
    }

    private static String instructionDocumentation(String bpmn) {
        int task = bpmn.indexOf("id=\"instruction\"");
        assertThat(task).isPositive();
        int start = bpmn.indexOf("<documentation>", task);
        int end = bpmn.indexOf("</documentation>", start);
        return bpmn.substring(start, end);
    }
}
