package be.myrmacta.infrastructure.flowable;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.flowable.bpmn.model.ExtensionAttribute;
import org.flowable.bpmn.model.ServiceTask;
import org.flowable.engine.delegate.DelegateExecution;
import org.junit.jupiter.api.Test;

/** S-RT-1..S-RT-6 (assistant) — lecture des paramètres de brique : attribut littéral prioritaire, sinon variable. */
class DeployedActivityParametersTest {

    static ServiceTask taskWith(String name, String value) {
        ServiceTask task = new ServiceTask();
        task.setId("act");
        ExtensionAttribute attribute = new ExtensionAttribute();
        attribute.setName(name);
        attribute.setNamespace(DeployedActivityParameters.FLOWABLE_NAMESPACE);
        attribute.setNamespacePrefix("flowable");
        attribute.setValue(value);
        task.addAttribute(attribute);
        return task;
    }

    private final DelegateExecution execution = mock(DelegateExecution.class);

    @Test
    void variableAloneIsUsedAsBefore() {
        when(execution.getVariable("targetDossierStatus")).thenReturn("CLOTURE");

        assertThat(DeployedActivityParameters.get(execution, "targetDossierStatus")).isEqualTo("CLOTURE");
    }

    @Test
    void literalAttributeAloneIsUsed() {
        when(execution.getCurrentFlowElement()).thenReturn(taskWith("targetDossierStatus", "EN_COURS"));

        assertThat(DeployedActivityParameters.get(execution, "targetDossierStatus")).isEqualTo("EN_COURS");
    }

    @Test
    void attributeWinsOverTheVariable() {
        when(execution.getVariable("targetDossierStatus")).thenReturn("CLOTURE");
        when(execution.getCurrentFlowElement()).thenReturn(taskWith("targetDossierStatus", "EN_COURS"));

        assertThat(DeployedActivityParameters.get(execution, "targetDossierStatus")).isEqualTo("EN_COURS");
    }

    @Test
    void emptyCollectionVariableIsNotAValue() {
        when(execution.getVariable("notificationChannels")).thenReturn(java.util.List.of());
        assertThat(DeployedActivityParameters.get(execution, "notificationChannels")).isNull();
    }

    @Test
    void attributeIsStripped() {
        when(execution.getCurrentFlowElement()).thenReturn(taskWith("targetDossierStatus", " EN_COURS "));
        assertThat(DeployedActivityParameters.get(execution, "targetDossierStatus")).isEqualTo("EN_COURS");
    }

    @Test
    void blankVariableFallsBackToTheAttribute() {
        when(execution.getVariable("targetDossierStatus")).thenReturn("  ");
        when(execution.getCurrentFlowElement()).thenReturn(taskWith("targetDossierStatus", "EN_COURS"));

        assertThat(DeployedActivityParameters.get(execution, "targetDossierStatus")).isEqualTo("EN_COURS");
    }

    @Test
    void neitherYieldsNull() {
        assertThat(DeployedActivityParameters.get(execution, "targetDossierStatus")).isNull();
        when(execution.getCurrentFlowElement()).thenReturn(new ServiceTask());
        assertThat(DeployedActivityParameters.get(execution, "targetDossierStatus")).isNull();
    }

    @Test
    void attributeOfAnotherNameOrNamespaceIsIgnored() {
        when(execution.getCurrentFlowElement()).thenReturn(taskWith("targetWorkflowStatus", "X"));
        assertThat(DeployedActivityParameters.get(execution, "targetDossierStatus")).isNull();

        ServiceTask foreign = new ServiceTask();
        ExtensionAttribute other = new ExtensionAttribute();
        other.setName("targetDossierStatus");
        other.setNamespace("http://other.example.org");
        other.setValue("X");
        foreign.addAttribute(other);
        when(execution.getCurrentFlowElement()).thenReturn(foreign);
        assertThat(DeployedActivityParameters.get(execution, "targetDossierStatus")).isNull();
    }

    @Test
    void attributeValueIsNeverEvaluated() {
        // S-RT-6 : littéral seulement — une chaîne UEL est rendue telle quelle (le parseur la refuse au déploiement)
        when(execution.getCurrentFlowElement()).thenReturn(taskWith("targetDossierStatus", "${1+1}"));

        assertThat(DeployedActivityParameters.get(execution, "targetDossierStatus")).isEqualTo("${1+1}");
    }
}
