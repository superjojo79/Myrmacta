package com.flowforge.domain.connector.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.withSettings;

import com.flowforge.domain.connector.model.ConnectorDefinition;
import com.flowforge.domain.connector.model.ConnectorInstanceData;
import com.flowforge.domain.connector.model.ConnectorValidationException;
import com.flowforge.domain.connector.model.TriggerConnector;
import com.flowforge.domain.connector.model.UnknownConnectorException;
import com.flowforge.domain.connector.model.WorkflowTriggerEvent;
import com.flowforge.domain.dossier.model.AccessContext;
import com.flowforge.domain.dossier.model.Dossier;
import com.flowforge.domain.dossier.model.DossierNotFoundException;
import com.flowforge.domain.dossier.model.DossierStatus;
import com.flowforge.domain.dossier.port.in.CreateDossierUseCase;
import com.flowforge.domain.dossier.port.in.GetDossierUseCase;
import com.flowforge.domain.connector.port.out.ConnectorCatalog;
import com.flowforge.domain.dossier.model.AccessContext;
import com.flowforge.domain.dossier.model.Dossier;
import com.flowforge.domain.dossier.model.DossierNotFoundException;
import com.flowforge.domain.dossier.model.DossierStatus;
import com.flowforge.domain.dossier.port.in.CreateDossierUseCase;
import com.flowforge.domain.dossier.port.in.GetDossierUseCase;
import com.flowforge.domain.workflow.model.TriggerOutboxPayload;
import com.flowforge.domain.workflow.port.out.OutboxRepository;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ConnectorTriggerRuntimeImplTest {

    @Mock
    private ConnectorCatalog catalog;

    @Mock
    private CreateDossierUseCase createDossier;

    @Mock
    private GetDossierUseCase getDossier;

    @Mock
    private OutboxRepository outboxRepository;

    private ConnectorDefinition triggerDefinition;
    private TriggerConnector triggerConnector;
    private ConnectorTriggerRuntimeImpl runtime;

    @BeforeEach
    void setUp() {
        triggerDefinition =
                mock(ConnectorDefinition.class, withSettings().extraInterfaces(TriggerConnector.class));
        triggerConnector = (TriggerConnector) triggerDefinition;
        runtime = new ConnectorTriggerRuntimeImpl(catalog, createDossier, getDossier, outboxRepository);
    }

    @Test
    void createsDossierAndAppendsOutbox_withoutStartingWorkflow() {
        var instance = instance();
        when(catalog.get("http-rest")).thenReturn(triggerDefinition);
        when(triggerConnector.onEvent(eq(instance), any()))
                .thenReturn(List.of(new WorkflowTriggerEvent(
                        "smokeProcess", "SINISTRE", Map.of("a", 1), Map.of("v", 2), null)));
        UUID dossierId = UUID.randomUUID();
        when(createDossier.create(any(), any())).thenReturn(dossier(dossierId));

        runtime.onIncomingEvent(instance, Map.of("x", 1));

        ArgumentCaptor<CreateDossierUseCase.CreateDossierCommand> cmd =
                ArgumentCaptor.forClass(CreateDossierUseCase.CreateDossierCommand.class);
        verify(createDossier).create(cmd.capture(), any(AccessContext.class));
        assertThat(cmd.getValue().createdBy()).isEqualTo("trigger:" + instance.id());
        assertThat(cmd.getValue().dossierTypeCode()).isEqualTo("SINISTRE");

        ArgumentCaptor<TriggerOutboxPayload> payload = ArgumentCaptor.forClass(TriggerOutboxPayload.class);
        verify(outboxRepository).savePending(payload.capture());
        assertThat(payload.getValue().dossierId()).isEqualTo(dossierId);
        assertThat(payload.getValue().processKey()).isEqualTo("smokeProcess");
        assertThat(payload.getValue().connectorInstanceId()).isEqualTo(instance.id());
    }

    @Test
    void existingReference_resolvesViaSystemAccess() {
        var instance = instance();
        when(catalog.get("http-rest")).thenReturn(triggerDefinition);
        when(triggerConnector.onEvent(eq(instance), any()))
                .thenReturn(List.of(new WorkflowTriggerEvent(
                        "smokeProcess", "SINISTRE", Map.of(), Map.of(), "DOS-1")));
        UUID dossierId = UUID.randomUUID();
        when(getDossier.getByReference(eq("tenant_dev"), eq("DOS-1"), any()))
                .thenReturn(dossier(dossierId));

        runtime.onIncomingEvent(instance, Map.of());

        verify(createDossier, never()).create(any(), any());
        verify(getDossier)
                .getByReference(
                        eq("tenant_dev"), eq("DOS-1"), eq(AccessContext.system("connector-trigger")));
        verify(outboxRepository).savePending(any());
    }

    @Test
    void unknownExistingReference_propagatesAndSkipsOutbox() {
        var instance = instance();
        when(catalog.get("http-rest")).thenReturn(triggerDefinition);
        when(triggerConnector.onEvent(eq(instance), any()))
                .thenReturn(List.of(new WorkflowTriggerEvent(
                        "smokeProcess", "SINISTRE", Map.of(), Map.of(), "MISSING")));
        when(getDossier.getByReference(any(), any(), any()))
                .thenThrow(new DossierNotFoundException("missing"));

        assertThatThrownBy(() -> runtime.onIncomingEvent(instance, Map.of()))
                .isInstanceOf(DossierNotFoundException.class);
        verify(outboxRepository, never()).savePending(any());
    }

    @Test
    void nonTriggerDefinition_rejected() {
        var instance = instance();
        ConnectorDefinition serviceOnly = mock(ConnectorDefinition.class);
        when(catalog.get("http-rest")).thenReturn(serviceOnly);

        assertThatThrownBy(() -> runtime.onIncomingEvent(instance, Map.of()))
                .isInstanceOf(ConnectorValidationException.class)
                .hasMessageContaining("TRIGGER");
    }

    @Test
    void unknownType_propagates() {
        var instance = instance();
        when(catalog.get("http-rest")).thenThrow(new UnknownConnectorException("http-rest"));
        assertThatThrownBy(() -> runtime.onIncomingEvent(instance, Map.of()))
                .isInstanceOf(UnknownConnectorException.class);
    }

    private static ConnectorInstanceData instance() {
        return new ConnectorInstanceData(
                UUID.randomUUID(), "tenant_dev", "n", "http-rest", true, Map.of());
    }

    private static Dossier dossier(UUID id) {
        return new Dossier(
                id,
                "tenant_dev",
                "DOS-1",
                "SINISTRE",
                "label",
                DossierStatus.OUVERT,
                Map.of(),
                null,
                0L,
                "system",
                java.time.Instant.parse("2024-01-01T00:00:00Z"),
                null,
                null,
                null,
                null,
                null);
    }
}
