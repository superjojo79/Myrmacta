package be.myrmacta.infrastructure.flowable;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import be.myrmacta.domain.workflow.model.InvalidBpmnException;
import be.myrmacta.domain.workflow.portal.model.PortalIdentityField;
import be.myrmacta.domain.workflow.portal.model.PortalRecipientSource;
import be.myrmacta.domain.workflow.portal.model.PortalTaskDefinition;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/** TODO §68 (portail-externe) — T-PARSE-1 à 6. */
class FlowableBpmnParserAdapterPortalTest {

    private final FlowableBpmnParserAdapter adapter = new FlowableBpmnParserAdapter();

    @Test
    void externalTask_isAcceptedAndExtracted() {
        // T-PARSE-1
        byte[] xml = bpmn("<userTask id=\"tiers\" name=\"Réponse du tiers\" flowable:formKey=\"user-question:avis\""
                + " flowable:portalRecipient=\"dossierField:contactEmail\" flowable:portalLinkTtlDays=\"10\"/>");

        assertThat(adapter.extractProcessDefinitionKey(xml)).isEqualTo("testProcess");
        List<PortalTaskDefinition> tasks = adapter.extractPortalTasks(xml);
        assertThat(tasks).containsExactly(new PortalTaskDefinition(
                "tiers", "avis",
                new PortalRecipientSource(PortalRecipientSource.Kind.DOSSIER_FIELD, "contactEmail"), 10));
    }

    @Test
    void externalTaskInSubProcess_isExtracted_andTtlIsOptional() {
        byte[] xml = bpmn("<subProcess id=\"sub\"><startEvent id=\"s2\"/>"
                + "<userTask id=\"tiers\" flowable:formKey=\"user-question:avis\" flowable:portalRecipient=\"variable:mail\"/>"
                + "<endEvent id=\"e2\"/><sequenceFlow sourceRef=\"s2\" targetRef=\"tiers\"/>"
                + "<sequenceFlow sourceRef=\"tiers\" targetRef=\"e2\"/></subProcess>", "sub");

        adapter.extractProcessDefinitionKey(xml);
        assertThat(adapter.extractPortalTasks(xml)).singleElement().satisfies(t -> {
            assertThat(t.taskDefinitionKey()).isEqualTo("tiers");
            assertThat(t.ttlDays()).isNull();
            assertThat(t.recipientSource().kind()).isEqualTo(PortalRecipientSource.Kind.PROCESS_VARIABLE);
        });
    }

    /** TODO §104 (T1, T2) — {@code portalIdentityField} marque une tâche externe à authentification forte. */
    @Test
    void externalTaskWithAnIdentityField_requiresStrongAuthentication() {
        byte[] xml = bpmn("<userTask id=\"tiers\" flowable:formKey=\"user-question:avis\""
                + " flowable:portalRecipient=\"dossierField:contactEmail\""
                + " flowable:portalIdentityField=\"dossierField:nrnDemandeur\"/>");

        adapter.extractProcessDefinitionKey(xml);
        assertThat(adapter.extractPortalTasks(xml)).singleElement().satisfies(t -> {
            assertThat(t.identityField()).isEqualTo(new PortalIdentityField("nrnDemandeur"));
            assertThat(t.requiresStrongAuthentication()).isTrue();
        });
        byte[] plain = bpmn("<userTask id=\"tiers\" flowable:formKey=\"user-question:avis\""
                + " flowable:portalRecipient=\"dossierField:contactEmail\"/>");
        assertThat(adapter.extractPortalTasks(plain).getFirst().requiresStrongAuthentication()).isFalse();
    }

    @ParameterizedTest
    @ValueSource(strings = {
            // le NRN ne transite jamais par une variable de process (N2)
            "flowable:portalIdentityField=\"variable:nrn\"",
            "flowable:portalIdentityField=\"nrn\"",
            "flowable:portalIdentityField=\"dossierField:\"",
            "flowable:portalIdentityField=\"dossierField:a.b\""})
    void invalidIdentityField_isRejected(String attribute) {
        byte[] xml = bpmn("<userTask id=\"tiers\" flowable:formKey=\"user-question:avis\""
                + " flowable:portalRecipient=\"dossierField:email\" " + attribute + "/>");
        assertThatThrownBy(() -> adapter.extractProcessDefinitionKey(xml))
                .isInstanceOf(InvalidBpmnException.class)
                .hasMessageContaining("portalIdentityField");
    }

    /** TODO §106 (D-PIECE-PORTAL) — types de pièces proposés au tiers. */
    @Test
    void externalTaskWithPieceTypes_listsThemInOrder() {
        byte[] xml = bpmn("<userTask id=\"tiers\" flowable:formKey=\"user-question:avis\""
                + " flowable:portalRecipient=\"dossierField:contactEmail\""
                + " flowable:portalPieceTypes=\"preuveDomicile, certificat\"/>");

        adapter.extractProcessDefinitionKey(xml);
        assertThat(adapter.extractPortalTasks(xml)).singleElement()
                .satisfies(t -> assertThat(t.pieceTypeKeys()).containsExactly("preuveDomicile", "certificat"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "Preuve", "preuve,preuve", "preuve;certificat"})
    void invalidPieceTypes_areRejected(String value) {
        byte[] xml = bpmn("<userTask id=\"tiers\" flowable:formKey=\"user-question:avis\""
                + " flowable:portalRecipient=\"dossierField:email\" flowable:portalPieceTypes=\"" + value + "\"/>");
        assertThatThrownBy(() -> adapter.extractProcessDefinitionKey(xml))
                .isInstanceOf(InvalidBpmnException.class)
                .hasMessageContaining("portalPieceTypes");
    }

    @Test
    void pieceTypesWithoutRecipient_areRejected() {
        byte[] xml = bpmn("<userTask id=\"tiers\" flowable:formKey=\"user-question:avis\""
                + " flowable:portalPieceTypes=\"preuve\"/>");
        assertThatThrownBy(() -> adapter.extractProcessDefinitionKey(xml))
                .isInstanceOf(InvalidBpmnException.class)
                .hasMessageContaining("portalPieceTypes requires portalRecipient");
    }

    @Test
    void identityFieldWithoutRecipient_isRejected() {
        byte[] xml = bpmn("<userTask id=\"tiers\" flowable:formKey=\"user-question:avis\""
                + " flowable:portalIdentityField=\"dossierField:nrn\"/>");
        assertThatThrownBy(() -> adapter.extractProcessDefinitionKey(xml))
                .isInstanceOf(InvalidBpmnException.class)
                .hasMessageContaining("portalIdentityField requires portalRecipient");
    }

    @Test
    void internalTasks_areNotExtracted() {
        byte[] xml = bpmn("<userTask id=\"tiers\" flowable:formKey=\"user-question:avis\"/>");
        assertThat(adapter.extractPortalTasks(xml)).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(strings = {
            // T-PARSE-2 : formKey absent ou autre que user-question
            "flowable:portalRecipient=\"dossierField:email\"",
            "flowable:formKey=\"dossier-fields\" flowable:portalRecipient=\"dossierField:email\"",
            "flowable:formKey=\"user-question:\" flowable:portalRecipient=\"dossierField:email\"",
            "flowable:formKey=\"user-question:a b\" flowable:portalRecipient=\"dossierField:email\"",
            // T-PARSE-3 : source de destinataire invalide
            "flowable:formKey=\"user-question:avis\" flowable:portalRecipient=\"email\"",
            "flowable:formKey=\"user-question:avis\" flowable:portalRecipient=\"dossierField:\"",
            "flowable:formKey=\"user-question:avis\" flowable:portalRecipient=\"variable:a.b\"",
            // T-PARSE-4 : durée invalide
            "flowable:formKey=\"user-question:avis\" flowable:portalRecipient=\"dossierField:email\" flowable:portalLinkTtlDays=\"0\"",
            "flowable:formKey=\"user-question:avis\" flowable:portalRecipient=\"dossierField:email\" flowable:portalLinkTtlDays=\"-3\"",
            "flowable:formKey=\"user-question:avis\" flowable:portalRecipient=\"dossierField:email\" flowable:portalLinkTtlDays=\"sept\"",
            "flowable:formKey=\"user-question:avis\" flowable:portalRecipient=\"dossierField:email\" flowable:portalLinkTtlDays=\"366\"",
            // T-PARSE-5 : une tâche externe n'est pas assignée en interne
            "flowable:formKey=\"user-question:avis\" flowable:portalRecipient=\"dossierField:email\" flowable:assignee=\"u1\"",
            "flowable:formKey=\"user-question:avis\" flowable:portalRecipient=\"dossierField:email\" flowable:candidateGroups=\"g\"",
            "flowable:formKey=\"user-question:avis\" flowable:portalRecipient=\"dossierField:email\" flowable:candidateUsers=\"u1\"",
            // T-PARSE-6 : durée sans destinataire
            "flowable:formKey=\"user-question:avis\" flowable:portalLinkTtlDays=\"7\""
    })
    void invalidExternalTask_isRejected(String attributes) {
        byte[] xml = bpmn("<userTask id=\"tiers\" " + attributes + "/>");
        assertThatThrownBy(() -> adapter.extractProcessDefinitionKey(xml))
                .isInstanceOf(InvalidBpmnException.class)
                .hasMessageContaining("portal");
    }

    private static byte[] bpmn(String element) {
        return bpmn(element, "tiers");
    }

    private static byte[] bpmn(String element, String elementId) {
        return """
                <?xml version="1.0" encoding="UTF-8"?>
                <definitions xmlns="http://www.omg.org/spec/BPMN/20100524/MODEL"
                             xmlns:flowable="http://flowable.org/bpmn"
                             targetNamespace="http://myrmacta/test">
                  <process id="testProcess" isExecutable="true">
                    <startEvent id="start"/>
                    %s
                    <endEvent id="end"/>
                    <sequenceFlow sourceRef="start" targetRef="%s"/>
                    <sequenceFlow sourceRef="%s" targetRef="end"/>
                  </process>
                </definitions>
                """.formatted(element, elementId, elementId).getBytes(StandardCharsets.UTF_8);
    }
}
