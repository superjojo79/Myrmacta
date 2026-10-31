package be.myrmacta.infrastructure.flowable;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import be.myrmacta.domain.connector.model.ConnectorCallContext;
import be.myrmacta.domain.connector.port.in.ExecuteConnectorUseCase;
import be.myrmacta.domain.dossier.model.Dossier;
import be.myrmacta.domain.dossier.model.DossierStatus;
import be.myrmacta.domain.dossier.model.DossierType;
import be.myrmacta.domain.dossier.model.FieldDefinition;
import be.myrmacta.domain.dossier.model.FieldType;
import be.myrmacta.domain.dossier.port.in.GetDossierUseCase;
import be.myrmacta.domain.dossier.port.out.DossierTypeRepository;
import be.myrmacta.domain.expression.model.ExpressionLimits;
import be.myrmacta.infrastructure.config.TenantProperties;
import be.myrmacta.infrastructure.expression.JsonataExpressionEngine;
import java.time.Clock;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.flowable.bpmn.model.ExtensionAttribute;
import org.flowable.bpmn.model.ServiceTask;
import org.flowable.engine.delegate.DelegateExecution;
import org.mockito.ArgumentCaptor;
import tools.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ConnectorServiceTaskTest {

    private static final ConnectorCallContext NO_DOSSIER = ConnectorCallContext.process(null);

    private ExecuteConnectorUseCase executeUseCase;
    private ConnectorServiceTask task;
    private DelegateExecution execution;

    @BeforeEach
    void setUp() {
        executeUseCase = mock(ExecuteConnectorUseCase.class);
        task = new ConnectorServiceTask(executeUseCase, new TenantProperties("tenant_dev"));
        execution = mock(DelegateExecution.class);
    }

    @Test
    void execute_invokesUseCaseAndStoresResult() {
        UUID id = UUID.randomUUID();
        when(execution.getVariable("connectorInstanceId")).thenReturn(id.toString());
        when(execution.getVariable("tenantId")).thenReturn(null);
        when(execution.getVariable("connectorInput")).thenReturn(Map.of("signalName", "s"));
        when(executeUseCase.execute(eq(id), eq("tenant_dev"), eq(Map.of("signalName", "s")), eq(NO_DOSSIER)))
                .thenReturn(Map.of("signaled", true));

        task.execute(execution);

        verify(executeUseCase).execute(id, "tenant_dev", Map.of("signalName", "s"), NO_DOSSIER);
        verify(execution).setVariable("connectorResult", Map.of("signaled", true));
    }

    @Test
    void execute_requiresConnectorInstanceId() {
        when(execution.getVariable("connectorInstanceId")).thenReturn(null);
        assertThatThrownBy(() -> task.execute(execution))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("connectorInstanceId");
    }

    @Test
    void execute_ignoresProcessTenantIdVariable() {
        UUID id = UUID.randomUUID();
        when(execution.getVariable("connectorInstanceId")).thenReturn(id);
        when(execution.getVariable("connectorInput")).thenReturn(null);
        when(executeUseCase.execute(id, "tenant_dev", Map.of(), NO_DOSSIER)).thenReturn(Map.of());

        task.execute(execution);

        verify(executeUseCase).execute(id, "tenant_dev", Map.of(), NO_DOSSIER);
    }

    @Test
    void execute_passesTheDossierOfTheExecution() {
        UUID id = UUID.randomUUID();
        UUID dossierId = UUID.randomUUID();
        when(execution.getVariable("connectorInstanceId")).thenReturn(id);
        when(execution.getVariable("dossierId")).thenReturn(dossierId.toString());

        task.execute(execution);

        verify(executeUseCase).execute(id, "tenant_dev", Map.of(), ConnectorCallContext.process(dossierId));
    }

    @Test
    void execute_aDossierIdThatIsNotAUuid_givesNoDossier() {
        UUID id = UUID.randomUUID();
        when(execution.getVariable("connectorInstanceId")).thenReturn(id);
        when(execution.getVariable("dossierId")).thenReturn("pas-un-uuid");

        task.execute(execution);

        verify(executeUseCase).execute(id, "tenant_dev", Map.of(), NO_DOSSIER);
    }

    // --- TODO §112 (D-ORCH-REQUEST) : requête construite par une expression littérale du BPMN -------------------------

    private static final Instant NOW = Instant.parse("2026-10-31T10:00:00Z");
    private static final UUID DOSSIER_ID = UUID.randomUUID();

    private final GetDossierUseCase getDossier = mock(GetDossierUseCase.class);
    private final DossierTypeRepository types = mock(DossierTypeRepository.class);

    private ConnectorServiceTask requestTask() {
        return new ConnectorServiceTask(executeUseCase, new TenantProperties("tenant_dev"),
                new JsonataExpressionEngine(ExpressionLimits.DEFAULTS, Clock.systemUTC()), getDossier, types,
                new ObjectMapper());
    }

    private DelegateExecution requestExecution(UUID connectorId, String request, Map<String, Object> variables) {
        ServiceTask element = new ServiceTask();
        element.setId("creerCompte");
        attribute(element, "connectorInstanceId", connectorId.toString());
        attribute(element, "connectorRequest", request);
        DelegateExecution exec = mock(DelegateExecution.class);
        Map<String, Object> all = new HashMap<>(variables);
        all.put("dossierId", DOSSIER_ID.toString());
        when(exec.getCurrentFlowElement()).thenReturn(element);
        when(exec.getCurrentActivityId()).thenReturn("creerCompte");
        when(exec.getVariables()).thenReturn(all);
        all.forEach((k, v) -> when(exec.getVariable(k)).thenReturn(v));
        Dossier dossier = new Dossier(DOSSIER_ID, "tenant_dev", "ONB-1", "ONBOARDING", "Arrivée", DossierStatus.EN_COURS,
                Map.of("login", "jdupont", "nom", "Dupont", "registreNational", "85.07.30-033.28"), null, 1L, "u", NOW,
                null, NOW, null, null, null);
        when(getDossier.get(eq(DOSSIER_ID), any())).thenReturn(dossier);
        DossierType type = mock(DossierType.class);
        when(type.fieldDefinitions()).thenReturn(List.of(
                new FieldDefinition("login", "Login", FieldType.STRING, true, null, null, null, false),
                new FieldDefinition("nom", "Nom", FieldType.STRING, true, null, null, null, false),
                new FieldDefinition("registreNational", "Registre national", FieldType.STRING, false, null, null, null,
                        true)));
        when(types.findByCode("tenant_dev", "ONBOARDING")).thenReturn(Optional.of(type));
        return exec;
    }

    private static void attribute(ServiceTask task, String name, String value) {
        ExtensionAttribute attribute = new ExtensionAttribute(name);
        attribute.setNamespace(DeployedActivityParameters.FLOWABLE_NAMESPACE);
        attribute.setValue(value);
        task.addAttribute(attribute);
    }

    @Test
    @SuppressWarnings("unchecked")
    void connectorRequest_buildsTheInputFromTheDossierAndThePreviousResult_andWinsOverConnectorInput() {
        UUID id = UUID.randomUUID();
        DelegateExecution exec = requestExecution(id,
                "{'method': 'POST', 'path': '/users/{login}', 'pathParams': {'login': dossier.fields.login},"
                        + " 'body': {'nom': dossier.fields.nom, 'matricule': connectorResult.json.id,"
                        + " 'rn': dossier.fields.registreNational}}",
                Map.of("connectorInput", Map.of("url", "https://evil.example.org"),
                        "connectorResult", Map.of("status", 201, "body", "{\"id\": \"E-42\"}")));
        when(executeUseCase.execute(any(), any(), any(), any())).thenReturn(Map.of("status", 201, "body", "{}"));

        requestTask().execute(exec);

        ArgumentCaptor<Map<String, Object>> input = ArgumentCaptor.forClass(Map.class);
        verify(executeUseCase).execute(eq(id), eq("tenant_dev"), input.capture(), eq(ConnectorCallContext.process(DOSSIER_ID)));
        assertThat(input.getValue()).containsEntry("method", "POST").containsEntry("path", "/users/{login}")
                .doesNotContainKey("url");
        assertThat((Map<String, Object>) input.getValue().get("pathParams")).containsEntry("login", "jdupont");
        // Un champ sensible n'est jamais lisible par l'expression (règle LC2) : la clé disparaît du corps.
        assertThat((Map<String, Object>) input.getValue().get("body"))
                .containsEntry("nom", "Dupont").containsEntry("matricule", "E-42").doesNotContainKey("rn");
    }

    @Test
    void connectorRequest_mustProduceAnObject() {
        UUID id = UUID.randomUUID();
        for (String request : List.of("'texte'", "[1, 2]", "dossier.fields.absent")) {
            DelegateExecution exec = requestExecution(id, request, Map.of());
            assertThatThrownBy(() -> requestTask().execute(exec))
                    .as(request)
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("creerCompte")
                    .hasMessageContaining("object");
        }
        verify(executeUseCase, never()).execute(any(), any(), any(), any());
    }

    @Test
    void connectorRequest_isRevalidatedAtExecution() {
        // Défense en profondeur : un BPMN déployé sans passer par le parseur ne fait pas évaluer une expression interdite.
        DelegateExecution exec = requestExecution(UUID.randomUUID(), "${evil}", Map.of());
        assertThatThrownBy(() -> requestTask().execute(exec)).hasMessageContaining("connectorRequest");
        verify(executeUseCase, never()).execute(any(), any(), any(), any());
    }

    @Test
    void connectorRequest_withoutTheExpressionEngine_failsClosed() {
        DelegateExecution exec = requestExecution(UUID.randomUUID(), "{'path': '/x'}", Map.of());
        assertThatThrownBy(() -> task.execute(exec)).isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("connectorRequest");
        verify(executeUseCase, never()).execute(any(), any(), any(), any());
    }
}
