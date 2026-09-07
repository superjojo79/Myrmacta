package com.flowforge.domain.connector.usecase;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.withSettings;

import com.flowforge.domain.connector.model.CallbackConnector;
import com.flowforge.domain.connector.model.CallbackResolution;
import com.flowforge.domain.connector.model.ConnectorDefinition;
import com.flowforge.domain.connector.model.ConnectorInstanceData;
import com.flowforge.domain.connector.model.ConnectorValidationException;
import com.flowforge.domain.connector.model.UnknownConnectorException;
import com.flowforge.domain.connector.port.out.ConnectorCatalog;
import com.flowforge.domain.dossier.port.in.CreateDossierUseCase;
import com.flowforge.domain.dossier.port.in.GetDossierUseCase;
import com.flowforge.domain.workflow.port.out.OutboxRepository;
import com.flowforge.domain.workflow.port.out.WorkflowEnginePort;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class CallbackConnectorRuntimeImplTest {

    @Mock
    private ConnectorCatalog catalog;

    @Mock
    private WorkflowEnginePort workflowEnginePort;

    @Mock
    private CreateDossierUseCase createDossier;

    @Mock
    private GetDossierUseCase getDossier;

    @Mock
    private OutboxRepository outboxRepository;

    private ConnectorDefinition callbackDefinition;
    private CallbackConnector callbackConnector;
    private CallbackConnectorRuntimeImpl runtime;

    @BeforeEach
    void setUp() {
        callbackDefinition =
                mock(ConnectorDefinition.class, withSettings().extraInterfaces(CallbackConnector.class));
        callbackConnector = (CallbackConnector) callbackDefinition;
        runtime = new CallbackConnectorRuntimeImpl(catalog, workflowEnginePort);
    }

    @Test
    void resolvesAndCorrelates_withoutTouchingDossierPorts() {
        var instance = instance();
        when(catalog.get("callback")).thenReturn(callbackDefinition);
        var resolution = new CallbackResolution(
                "callbackReceived", "correlationKey", "42", Map.of("result", "approved"));
        when(callbackConnector.resolve(eq(instance), any())).thenReturn(resolution);

        runtime.onIncomingCallback(instance, Map.of("orderId", "42"));

        verify(workflowEnginePort)
                .correlateMessage("callbackReceived", "correlationKey", "42", Map.of("result", "approved"));
        verifyNoInteractions(createDossier, getDossier, outboxRepository);
    }

    @Test
    void nonCallbackDefinition_rejected() {
        var instance = instance();
        ConnectorDefinition triggerOnly = mock(ConnectorDefinition.class);
        when(catalog.get("callback")).thenReturn(triggerOnly);

        assertThatThrownBy(() -> runtime.onIncomingCallback(instance, Map.of()))
                .isInstanceOf(ConnectorValidationException.class)
                .hasMessageContaining("CALLBACK");
        verifyNoInteractions(workflowEnginePort);
    }

    @Test
    void reservedVariableKeys_rejectedBeforeCorrelate() {
        var instance = instance();
        when(catalog.get("callback")).thenReturn(callbackDefinition);
        when(callbackConnector.resolve(eq(instance), any()))
                .thenReturn(new CallbackResolution(
                        "callbackReceived", "correlationKey", "42", Map.of("tenantId", "evil")));

        assertThatThrownBy(() -> runtime.onIncomingCallback(instance, Map.of("orderId", "42")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("reserved");
        verifyNoInteractions(workflowEnginePort);
    }

    @Test
    void unknownType_propagates() {
        var instance = instance();
        when(catalog.get("callback")).thenThrow(new UnknownConnectorException("callback"));

        assertThatThrownBy(() -> runtime.onIncomingCallback(instance, Map.of()))
                .isInstanceOf(UnknownConnectorException.class);
        verifyNoInteractions(workflowEnginePort);
    }

    private static ConnectorInstanceData instance() {
        return new ConnectorInstanceData(
                UUID.randomUUID(), "tenant_dev", "n", "callback", true, Map.of());
    }
}
