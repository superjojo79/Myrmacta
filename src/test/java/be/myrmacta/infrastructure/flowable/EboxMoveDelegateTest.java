package be.myrmacta.infrastructure.flowable;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import be.myrmacta.domain.connector.model.ConnectorCallContext;
import be.myrmacta.domain.connector.model.ConnectorInstanceData;
import be.myrmacta.domain.connector.port.in.ExecuteConnectorUseCase;
import be.myrmacta.domain.connector.port.out.ConnectorInstanceRepository;
import be.myrmacta.domain.dossier.port.out.DossierRepository;
import be.myrmacta.infrastructure.config.TenantProperties;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.flowable.bpmn.model.ExtensionAttribute;
import org.flowable.bpmn.model.ServiceTask;
import org.flowable.engine.delegate.DelegateExecution;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** TODO §151 (D-EBOX-MOVE) — brique « Classer le message eBox ». */
class EboxMoveDelegateTest {

    private static final String TENANT = "tenant_dev";
    private static final UUID INSTANCE = UUID.fromString("00000000-0000-0000-0000-0000000000eb");
    private static final UUID DOSSIER = UUID.fromString("00000000-0000-0000-0000-00000000d055");

    private final ExecuteConnectorUseCase execute = mock(ExecuteConnectorUseCase.class);
    private final ConnectorInstanceRepository instances = mock(ConnectorInstanceRepository.class);
    private final DossierRepository dossiers = mock(DossierRepository.class);
    private final DelegateExecution execution = mock(DelegateExecution.class);
    private final EboxMoveDelegate delegate = new EboxMoveDelegate(execute, instances, dossiers, new TenantProperties(TENANT));

    @BeforeEach
    void setUp() {
        when(instances.findByIdAndTenantId(INSTANCE, TENANT))
                .thenReturn(Optional.of(new ConnectorInstanceData(INSTANCE, TENANT, "eBox", "ebox-inbound", true, Map.of())));
        when(execution.getVariable("dossierId")).thenReturn(DOSSIER.toString());
        when(execution.getVariable("eboxRegistre")).thenReturn("socsec_v3");
        when(execution.getVariable("eboxMessageId")).thenReturn("m-1");
    }

    @Test
    void theMoveStepSendsTheLiteralTargetPartition() {
        element("myrmacta-traite");
        when(execute.execute(any(), any(), anyMap(), any())).thenReturn(Map.of("eboxClasse", true, "eboxDejaClasse", false));

        delegate.execute(execution);

        verify(execute).execute(INSTANCE, TENANT, Map.of("operation", "moveToPartition", "registry", "socsec_v3", "messageId", "m-1",
                "targetPartition", "myrmacta-traite"), ConnectorCallContext.process(DOSSIER));
        verify(execution).setVariable("eboxClasse", true);
        verify(execution).setVariable("eboxDejaClasse", false);
    }

    @Test
    void aReplayThatFindsTheMessageAlreadyMovedSaysSo() {
        element("myrmacta-traite");
        when(execute.execute(any(), any(), anyMap(), any())).thenReturn(Map.of("eboxClasse", true, "eboxDejaClasse", true));

        delegate.execute(execution);

        verify(execution).setVariable("eboxClasse", true);
        verify(execution).setVariable("eboxDejaClasse", true);
    }

    @Test
    void theMoveStepNeedsALiteralTarget() {
        element(null);
        when(execution.getVariable("eboxPartitionCible")).thenReturn("myrmacta");

        assertThatThrownBy(() -> delegate.execute(execution)).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("eboxPartitionCible");
        verify(execute, never()).execute(any(), any(), anyMap(), any());
    }

    private void element(String target) {
        ServiceTask task = new ServiceTask();
        task.setId("classer");
        attribute(task, "connectorInstanceId", INSTANCE.toString());
        if (target != null) {
            attribute(task, "eboxPartitionCible", target);
        }
        when(execution.getCurrentFlowElement()).thenReturn(task);
    }

    private static void attribute(ServiceTask task, String name, String value) {
        ExtensionAttribute attribute = new ExtensionAttribute(name);
        attribute.setNamespace(DeployedActivityParameters.FLOWABLE_NAMESPACE);
        attribute.setValue(value);
        task.addAttribute(attribute);
    }
}
