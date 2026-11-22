package be.myrmacta.infrastructure.flowable;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import be.myrmacta.domain.connector.model.ConnectorCallContext;
import be.myrmacta.domain.connector.model.ConnectorInstanceData;
import be.myrmacta.domain.connector.port.in.ExecuteConnectorUseCase;
import be.myrmacta.domain.connector.port.out.ConnectorInstanceRepository;
import be.myrmacta.domain.dossier.model.Dossier;
import be.myrmacta.domain.dossier.model.DossierStatus;
import be.myrmacta.domain.dossier.port.out.DossierRepository;
import be.myrmacta.infrastructure.config.TenantProperties;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.flowable.bpmn.model.ExtensionAttribute;
import org.flowable.bpmn.model.ServiceTask;
import org.flowable.engine.delegate.DelegateExecution;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** TODO §150 (D-EBOX-ACTION) — brique « Marquer l'action eBox comme faite ». */
class EboxActionDoneDelegateTest {

    private static final String TENANT = "tenant_dev";
    private static final UUID INSTANCE = UUID.fromString("00000000-0000-0000-0000-0000000000eb");
    private static final UUID DOSSIER = UUID.fromString("00000000-0000-0000-0000-00000000d055");

    private final ExecuteConnectorUseCase execute = mock(ExecuteConnectorUseCase.class);
    private final ConnectorInstanceRepository instances = mock(ConnectorInstanceRepository.class);
    private final DossierRepository dossiers = mock(DossierRepository.class);
    private final DelegateExecution execution = mock(DelegateExecution.class);
    private final EboxActionDoneDelegate delegate =
            new EboxActionDoneDelegate(execute, instances, dossiers, new TenantProperties(TENANT));

    @BeforeEach
    void setUp() {
        when(instances.findByIdAndTenantId(INSTANCE, TENANT)).thenReturn(Optional.of(instance("ebox-inbound")));
        when(execution.getVariable("dossierId")).thenReturn(DOSSIER.toString());
        when(execute.execute(any(), any(), anyMap(), any())).thenReturn(Map.of("eboxActionExecutee", true));
    }

    @Test
    void theMessageOfTheMailProcessIsConfirmedThroughTheEboxInstance() {
        element(INSTANCE.toString(), "act-1");
        when(execution.getVariable("eboxRegistre")).thenReturn("socsec_v3");
        when(execution.getVariable("eboxMessageId")).thenReturn("m-1");

        delegate.execute(execution);

        verify(execute).execute(INSTANCE, TENANT,
                Map.of("operation", "markActionExecuted", "registry", "socsec_v3", "messageId", "m-1", "actionId", "act-1"),
                ConnectorCallContext.process(DOSSIER));
        verify(execution).setVariable("eboxActionExecutee", true);
    }

    @Test
    void aBusinessDossierGivesTheMessageThroughItsFields() {
        element(INSTANCE.toString(), null);
        when(dossiers.findById(DOSSIER)).thenReturn(Optional.of(dossier(Map.of("eboxRegistre", "socsec_v3", "eboxMessageId", "m-2"))));

        delegate.execute(execution);

        verify(execute).execute(INSTANCE, TENANT, Map.of("operation", "markActionExecuted", "registry", "socsec_v3", "messageId", "m-2"),
                ConnectorCallContext.process(DOSSIER));
    }

    @Test
    void theConnectorMustBeALiteralEboxInstance() {
        element(null, null);
        when(execution.getVariable("connectorInstanceId")).thenReturn(INSTANCE.toString());
        assertThatThrownBy(() -> delegate.execute(execution)).isInstanceOf(IllegalArgumentException.class);

        UUID other = UUID.randomUUID();
        element(other.toString(), null);
        when(instances.findByIdAndTenantId(other, TENANT)).thenReturn(Optional.of(instance("http-rest")));
        assertThatThrownBy(() -> delegate.execute(execution)).isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("not an ebox-inbound");
        verify(execute, never()).execute(any(), any(), anyMap(), any());
    }

    @Test
    void withoutMessageTheStepFails() {
        element(INSTANCE.toString(), null);
        when(dossiers.findById(DOSSIER)).thenReturn(Optional.of(dossier(Map.of())));

        assertThatThrownBy(() -> delegate.execute(execution)).isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("no eBox message");
        verify(execute, never()).execute(any(), eq(TENANT), anyMap(), any());
    }

    private void element(String instanceId, String actionId) {
        ServiceTask task = new ServiceTask();
        task.setId("actionFaite");
        if (instanceId != null) {
            attribute(task, "connectorInstanceId", instanceId);
        }
        if (actionId != null) {
            attribute(task, "eboxActionId", actionId);
        }
        when(execution.getCurrentFlowElement()).thenReturn(task);
    }

    private static void attribute(ServiceTask task, String name, String value) {
        ExtensionAttribute attribute = new ExtensionAttribute(name);
        attribute.setNamespace(DeployedActivityParameters.FLOWABLE_NAMESPACE);
        attribute.setValue(value);
        task.addAttribute(attribute);
    }

    private static ConnectorInstanceData instance(String type) {
        return new ConnectorInstanceData(INSTANCE, TENANT, "eBox", type, true, Map.of());
    }

    private static Dossier dossier(Map<String, Object> fields) {
        Instant now = Instant.parse("2026-11-20T09:00:00Z");
        return new Dossier(DOSSIER, TENANT, "DOS-1", "FACTURE", "Facture", DossierStatus.OUVERT, fields, null, 0L, "trigger", now, "trigger",
                now, null, null, null);
    }
}
