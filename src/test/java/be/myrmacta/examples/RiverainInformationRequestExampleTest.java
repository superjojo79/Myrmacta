package be.myrmacta.examples;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

/**
 * Issue #19 (TODO §123) — l'e-mail « informations complémentaires » de l'exemple « autorisation riverain » vient d'un
 * gabarit de notification qui cite la motivation saisie à l'instruction. Le rendu étant strict, la variable citée est
 * obligatoire dans le questionnaire d'instruction. Le déroulé complet est joué par {@code RiverainInformationRequestExampleIT}.
 */
class RiverainInformationRequestExampleTest {

    private static final Path EXAMPLE = Path.of("exemples/autorisation-riverain");
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final String TEMPLATE_KEY = "demande-informations-riverain";

    @Test
    void theInformationRequestEmailOfTheVariantsWithoutPortalUsesTheTemplate() throws Exception {
        for (String bpmn : new String[] {
                "autorisation-riverain-sans-portail.bpmn20.xml",
                "autorisation-riverain-preverification.bpmn20.xml"}) {
            String element = serviceTask(Files.readString(EXAMPLE.resolve(bpmn)), "mailDemandeInfos");
            assertThat(element).as(bpmn).contains("flowable:notificationTemplate=\"" + TEMPLATE_KEY + "\"")
                    .contains("flowable:async=\"true\"")
                    .contains("flowable:notificationTo=\"dossierField:email\"")
                    .doesNotContain("notificationSubject").doesNotContain("notificationBody");
        }
    }

    @Test
    void theComplementTaskNoLongerAsksTheAgentToTellTheApplicantWhatIsExpected() throws Exception {
        for (String bpmn : new String[] {
                "autorisation-riverain-sans-portail.bpmn20.xml",
                "autorisation-riverain-preverification.bpmn20.xml"}) {
            String documentation = documentation(Files.readString(EXAMPLE.resolve(bpmn)), "complement");
            assertThat(documentation).as(bpmn).doesNotContain("Contacter le demandeur").contains("par e-mail");
        }
    }

    @Test
    void theTemplateQuotesTheMotivationWhichTheInstructionQuestionnaireMakesMandatory() throws Exception {
        JsonNode template = JSON.readTree(EXAMPLE.resolve("config/gabarit-demande-informations.json").toFile());
        assertThat(template.get("templateKey").asText()).isEqualTo(TEMPLATE_KEY);
        assertThat(template.get("textBody").asText()).contains("[[${variables.motivation}]]");
        assertThat(template.get("htmlBody").asText()).contains("th:text=\"${variables.motivation}\"");

        JsonNode instruction = null;
        for (JsonNode schema : JSON.readTree(EXAMPLE.resolve("config/questions.json").toFile())) {
            if ("instruction-autorisation-riverain".equals(schema.get("schemaKey").asText())) {
                instruction = schema;
            }
        }
        assertThat(instruction).isNotNull();
        JsonNode motivation = null;
        for (JsonNode field : instruction.get("fields")) {
            if ("motivation".equals(field.get("name").asText())) {
                motivation = field;
            }
        }
        assertThat(motivation).as("champ motivation").isNotNull();
        assertThat(motivation.get("required").asBoolean()).as("motivation obligatoire (rendu strict)").isTrue();
    }

    @Test
    void theDifficultiesNoteRecordsThatTheFixedTextsAreLifted() throws Exception {
        String difficulties = Files.readString(EXAMPLE.resolve("DIFFICULTES.md"));
        assertThat(difficulties).contains("**Levé le 2026-11-07, §123.**");
    }

    private static String serviceTask(String bpmn, String id) {
        Matcher matcher = Pattern.compile("<serviceTask id=\"" + id + "\"[^>]*>").matcher(bpmn);
        assertThat(matcher.find()).as("serviceTask " + id).isTrue();
        return matcher.group();
    }

    private static String documentation(String bpmn, String id) {
        Matcher matcher = Pattern.compile("<userTask id=\"" + id + "\"[^>]*><documentation>(.*?)</documentation>").matcher(bpmn);
        assertThat(matcher.find()).as("userTask " + id).isTrue();
        return matcher.group(1);
    }
}
