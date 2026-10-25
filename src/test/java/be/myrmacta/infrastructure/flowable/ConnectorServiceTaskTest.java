package be.myrmacta.infrastructure.flowable;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import be.myrmacta.domain.connector.model.ConnectorCallContext;
import be.myrmacta.domain.connector.port.in.ExecuteConnectorUseCase;
import be.myrmacta.infrastructure.config.TenantProperties;
import java.util.Map;
import java.util.UUID;
import org.flowable.engine.delegate.DelegateExecution;
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
}
