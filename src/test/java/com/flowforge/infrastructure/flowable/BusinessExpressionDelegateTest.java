package com.flowforge.infrastructure.flowable;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.flowforge.domain.dossier.model.AccessContext;
import com.flowforge.domain.dossier.model.Dossier;
import com.flowforge.domain.dossier.model.DossierStatus;
import com.flowforge.domain.dossier.model.DossierType;
import com.flowforge.domain.dossier.model.FieldDefinition;
import com.flowforge.domain.dossier.model.FieldType;
import com.flowforge.domain.dossier.port.in.GetDossierUseCase;
import com.flowforge.domain.dossier.port.out.DossierTypeRepository;
import com.flowforge.domain.expression.model.ExpressionLimits;
import com.flowforge.domain.expression.port.out.ExpressionEnginePort;
import com.flowforge.domain.workflow.model.InvalidBpmnException;
import com.flowforge.infrastructure.config.TenantProperties;
import com.flowforge.infrastructure.expression.JsonataExpressionEngine;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.flowable.bpmn.model.ExtensionAttribute;
import org.flowable.bpmn.model.ServiceTask;
import org.flowable.engine.delegate.DelegateExecution;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

/** LC2 (TODO §75) — le delegate : masquage des champs sensibles, échec fermé, revalidation à l'exécution. */
class BusinessExpressionDelegateTest {

    private static final Instant NOW = Instant.parse("2026-09-30T10:00:00Z");
    private static final UUID DOSSIER_ID = UUID.randomUUID();

    private final ExpressionEnginePort engine = new JsonataExpressionEngine(ExpressionLimits.DEFAULTS, Clock.systemUTC());
    private final GetDossierUseCase getDossier = mock(GetDossierUseCase.class);
    private final DossierTypeRepository types = mock(DossierTypeRepository.class);
    private final TenantProperties tenant = new TenantProperties("tenant_dev");

    private BusinessExpressionDelegate delegate(ExpressionEnginePort engineToUse) {
        return new BusinessExpressionDelegate(engineToUse, getDossier, types, tenant, new ObjectMapper());
    }

    private static DelegateExecution execution(String formula, String formulaResult, Map<String, Object> variables) {
        ServiceTask task = new ServiceTask();
        task.setId("calc");
        attribute(task, "formula", formula);
        attribute(task, "formulaResult", formulaResult);
        DelegateExecution execution = mock(DelegateExecution.class);
        when(execution.getCurrentFlowElement()).thenReturn(task);
        when(execution.getCurrentActivityId()).thenReturn("calc");
        when(execution.getVariables()).thenReturn(variables);
        variables.forEach((k, v) -> when(execution.getVariable(k)).thenReturn(v));
        return execution;
    }

    private static void attribute(ServiceTask task, String name, String value) {
        if (value == null) {
            return;
        }
        ExtensionAttribute attribute = new ExtensionAttribute(name);
        attribute.setNamespace(DeployedActivityParameters.FLOWABLE_NAMESPACE);
        attribute.setValue(value);
        task.addAttribute(attribute);
    }

    private Dossier dossier() {
        return new Dossier(DOSSIER_ID, "tenant_dev", "DOS-1", "SINISTRE", "Sinistre", DossierStatus.OUVERT,
                Map.of("montant", 6000, "iban", "FR7630001007941234567890185"), null, 1L, "u", NOW, null, NOW, null, null,
                null);
    }

    private DossierType typeWithSensitiveIban() {
        DossierType type = mock(DossierType.class);
        when(type.fieldDefinitions()).thenReturn(List.of(
                new FieldDefinition("montant", "Montant", FieldType.NUMBER, false, null, null, null, false),
                new FieldDefinition("iban", "IBAN", FieldType.STRING, false, null, null, null, true)));
        return type;
    }

    @Test
    void theExpressionNeverSeesASensitiveFieldEvenWhenItAsksForItByName() {
        when(getDossier.get(any(UUID.class), any(AccessContext.class))).thenReturn(dossier());
        DossierType type = typeWithSensitiveIban();
        when(types.findByCode("tenant_dev", "SINISTRE")).thenReturn(Optional.of(type));
        DelegateExecution ok = execution("dossier.fields.montant > 5000", "fort",
                new java.util.HashMap<>(Map.of("dossierId", DOSSIER_ID.toString())));
        delegate(engine).execute(ok);
        verify(ok).setVariable("fort", Boolean.TRUE);

        DelegateExecution leak = execution("dossier.fields.iban", "copie",
                new java.util.HashMap<>(Map.of("dossierId", DOSSIER_ID.toString())));
        assertThatThrownBy(() -> delegate(engine).execute(leak)).hasMessageContaining("produced no value");
        verify(leak, never()).setVariable(any(), any());
    }

    @Test
    void mediumAnUnknownDossierTypeFailsClosedInsteadOfExposingEveryField() {
        // Constat 7 : `orElse(List.of())` ⇒ aucune définition ⇒ aucun champ masqué ⇒ tous les champs sensibles exposés.
        when(getDossier.get(any(UUID.class), any(AccessContext.class))).thenReturn(dossier());
        when(types.findByCode("tenant_dev", "SINISTRE")).thenReturn(Optional.empty());
        ExpressionEnginePort spy = mock(ExpressionEnginePort.class);
        DelegateExecution execution = execution("dossier.fields.iban", "copie",
                new java.util.HashMap<>(Map.of("dossierId", DOSSIER_ID.toString())));

        assertThatThrownBy(() -> delegate(spy).execute(execution))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("SINISTRE");
        verify(spy, never()).evaluate(any(), any());
        verify(execution, never()).setVariable(any(), any());
    }

    @Test
    void lowTheResultVariableIsRevalidatedAtExecutionNotOnlyAtDeployment() {
        // Constat 8 : un BPMN déployé sans passer par le parseur ne doit pas pouvoir écraser une variable réservée.
        List<String> accepted = new java.util.ArrayList<>();
        for (String reserved : List.of("dossierId", "dossierFields", "tenantId", "targetDossierStatus", "nrOfInstances")) {
            DelegateExecution execution = execution("1 + 1", reserved, new java.util.HashMap<>());
            try {
                delegate(engine).execute(execution);
                accepted.add(reserved);
            } catch (InvalidBpmnException expected) {
                assertThat(expected).hasMessageContaining("reserved");
            }
            verify(execution, never()).setVariable(any(), any());
        }
        assertThat(accepted).as("reserved names accepted as a result variable").isEmpty();
    }

    @Test
    void theFormulaAndItsResultVariableAreBothRequiredAtExecution() {
        assertThatThrownBy(() -> delegate(engine).execute(execution("1 + 1", null, new java.util.HashMap<>())))
                .isInstanceOf(RuntimeException.class);
        assertThatThrownBy(() -> delegate(engine).execute(execution(null, "x1", new java.util.HashMap<>())))
                .isInstanceOf(RuntimeException.class);
    }

    @Test
    void structuresAreStoredAsJsonTextAndScalarsAsIs() {
        DelegateExecution structure = execution("{\"a\": 1, \"b\": [1, 2]}", "obj", new java.util.HashMap<>());
        delegate(engine).execute(structure);
        verify(structure).setVariable("obj", "{\"a\":1,\"b\":[1,2]}");

        DelegateExecution number = execution("2 * 21", "reponse", new java.util.HashMap<>());
        delegate(engine).execute(number);
        verify(number).setVariable("reponse", 42L);

        DelegateExecution nothing = execution("null", "rien", new java.util.HashMap<>());
        delegate(engine).execute(nothing);
        verify(nothing).setVariable("rien", null);
    }
}
