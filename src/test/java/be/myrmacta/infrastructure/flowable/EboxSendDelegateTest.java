package be.myrmacta.infrastructure.flowable;

import static org.assertj.core.api.Assertions.assertThat;
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
import be.myrmacta.domain.document.port.out.DocumentStoragePort;
import be.myrmacta.domain.dossier.model.ElementType;
import be.myrmacta.domain.dossier.port.in.AddDossierElementUseCase;
import be.myrmacta.domain.workflow.portal.model.PortalRecipientSource;
import be.myrmacta.domain.workflow.portal.port.out.PortalRecipientResolverPort;
import be.myrmacta.infrastructure.config.TenantProperties;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.flowable.bpmn.model.ExtensionAttribute;
import org.flowable.bpmn.model.ServiceTask;
import org.flowable.engine.delegate.DelegateExecution;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/** TODO §155 (lot 2, D-EBOX-SEND-BLOCK) — brique « Envoyer en eBox ». */
class EboxSendDelegateTest {

    private static final String TENANT = "tenant_dev";
    private static final UUID INSTANCE = UUID.fromString("00000000-0000-0000-0000-0000000000e5");
    private static final UUID DOSSIER = UUID.fromString("00000000-0000-0000-0000-00000000d055");

    private final ExecuteConnectorUseCase execute = mock(ExecuteConnectorUseCase.class);
    private final ConnectorInstanceRepository instances = mock(ConnectorInstanceRepository.class);
    private final PortalRecipientResolverPort sources = mock(PortalRecipientResolverPort.class);
    private final DocumentStoragePort storage = mock(DocumentStoragePort.class);
    private final AddDossierElementUseCase addElement = mock(AddDossierElementUseCase.class);
    private final DelegateExecution execution = mock(DelegateExecution.class);
    private final EboxSendDelegate delegate =
            new EboxSendDelegate(execute, instances, sources, storage, addElement, new TenantProperties(TENANT));
    private final Map<String, String> attributes = new LinkedHashMap<>();
    private final Map<String, Object> result = new HashMap<>();

    @BeforeEach
    void setUp() {
        when(instances.findByIdAndTenantId(INSTANCE, TENANT)).thenReturn(Optional.of(instance("ebox-outbound")));
        when(execution.getVariable("dossierId")).thenReturn(DOSSIER.toString());
        when(execution.getVariable("generatedDocumentId")).thenReturn("tenant_dev/doc-1");
        when(execution.getCurrentActivityId()).thenReturn("envoiEbox");
        when(execution.getProcessInstanceId()).thenReturn("pi-1");
        when(sources.resolve(new PortalRecipientSource(PortalRecipientSource.Kind.DOSSIER_FIELD, "bce"), DOSSIER, "pi-1"))
                .thenReturn(Optional.of("0212345678"));
        when(sources.resolve(new PortalRecipientSource(PortalRecipientSource.Kind.DOSSIER_FIELD, "langue"), DOSSIER, "pi-1"))
                .thenReturn(Optional.of("nl"));
        attributes.put("connectorInstanceId", INSTANCE.toString());
        attributes.put("eboxEnvoiDestinataire", "dossierField:bce");
        attributes.put("eboxEnvoiTypeDestinataire", "ENTREPRISE");
        attributes.put("eboxEnvoiDocument", "variable:generatedDocumentId");
        attributes.put("eboxEnvoiObjet", "Votre permis");
        attributes.put("eboxEnvoiApplication", "myrmacta-commune");
        attributes.put("eboxEnvoiTypeMessage", "decision");
        attributes.put("eboxEnvoiLangue", "dossierField:langue");
        attributes.put("eboxEnvoiSecours", "PAPER");
        result.putAll(Map.of("eboxIssue", "ENVOYE", "eboxLivraison", "EBOX_AND_FALLBACK", "eboxSecoursRequis", true,
                "eboxVoieSecours", "PAPER", "eboxEnvoiMessageId", "msg-1", "eboxEnvoiRejoue", false));
        when(execute.execute(any(), any(), anyMap(), any())).thenReturn(result);
    }

    @Test
    void sendsThroughTheEboxConnectorAndSetsTheDocumentedVariables() {
        element();

        delegate.execute(execution);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, Object>> input = ArgumentCaptor.forClass(Map.class);
        verify(execute).execute(eq(INSTANCE), eq(TENANT), input.capture(), eq(ConnectorCallContext.processWithBuiltRequest(DOSSIER)));
        assertThat(input.getValue()).containsEntry("recipientType", "ENTREPRISE").containsEntry("recipient", "0212345678")
                .containsEntry("documentRef", "tenant_dev/doc-1").containsEntry("subject", "Votre permis")
                .containsEntry("senderApplicationId", "myrmacta-commune").containsEntry("messageTypeId", "decision")
                .containsEntry("language", "nl").containsEntry("registered", false).containsEntry("fallbackChannel", "PAPER");
        assertThat((String) input.getValue().get("idempotencyKey")).matches("myr1-[0-9a-f]{64}");
        verify(execution).setVariable("eboxIssue", "ENVOYE");
        verify(execution).setVariable("eboxLivraison", "EBOX_AND_FALLBACK");
        verify(execution).setVariable("eboxSecoursRequis", true);
        verify(execution).setVariable("eboxVoieSecours", "PAPER");
        verify(execution).setVariable("eboxEnvoiMessageId", "msg-1");
        verify(execution).setVariable("eboxEnvoiRejoue", false);
        verify(storage, never()).store(any(), any(), any());
    }

    @Test
    void theKeyIsStablePerDossierStepDocumentAndLoopTurn() {
        String first = EboxSendDelegate.idempotencyKey(execution, DOSSIER, "tenant_dev/doc-1");
        assertThat(EboxSendDelegate.idempotencyKey(execution, DOSSIER, "tenant_dev/doc-1")).isEqualTo(first);
        assertThat(EboxSendDelegate.idempotencyKey(execution, DOSSIER, "tenant_dev/doc-2")).isNotEqualTo(first);
        assertThat(EboxSendDelegate.idempotencyKey(execution, UUID.randomUUID(), "tenant_dev/doc-1")).isNotEqualTo(first);
        when(execution.getVariableLocal("loopCounter")).thenReturn(1);
        assertThat(EboxSendDelegate.idempotencyKey(execution, DOSSIER, "tenant_dev/doc-1")).isNotEqualTo(first);
        assertThat(first).hasSizeLessThanOrEqualTo(100);
    }

    @Test
    void aLiteralLanguageAndARegisteredMailAttachTheProofToTheDossier() {
        attributes.put("eboxEnvoiLangue", "fr");
        attributes.put("eboxEnvoiRecommande", "OUI");
        result.put("eboxPreuveDepot", "{\"signature\":\"s\"}");
        when(storage.store(TENANT, "application/json", "{\"signature\":\"s\"}".getBytes(StandardCharsets.UTF_8)))
                .thenReturn("tenant_dev/proof-1");
        element();

        delegate.execute(execution);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, Object>> input = ArgumentCaptor.forClass(Map.class);
        verify(execute).execute(eq(INSTANCE), eq(TENANT), input.capture(), any());
        assertThat(input.getValue()).containsEntry("language", "fr").containsEntry("registered", true);
        ArgumentCaptor<AddDossierElementUseCase.AddCommand> added = ArgumentCaptor.forClass(AddDossierElementUseCase.AddCommand.class);
        verify(addElement).add(added.capture(), any());
        assertThat(added.getValue().dossierId()).isEqualTo(DOSSIER);
        assertThat(added.getValue().type()).isEqualTo(ElementType.ATTACHMENT);
        assertThat(added.getValue().storageRef()).isEqualTo("tenant_dev/proof-1");
        assertThat(added.getValue().label()).startsWith("Preuve de dépôt eBox");
        verify(execution, never()).setVariable(eq("eboxPreuveDepot"), any());
    }

    @Test
    void parametersAreLiteralAndTheConnectorAnEboxSender() {
        attributes.remove("eboxEnvoiObjet");
        element();
        when(execution.getVariable("eboxEnvoiObjet")).thenReturn("Forgé");
        assertThatThrownBy(() -> delegate.execute(execution)).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("eboxEnvoiObjet");

        attributes.put("eboxEnvoiObjet", "Votre permis");
        when(instances.findByIdAndTenantId(INSTANCE, TENANT)).thenReturn(Optional.of(instance("ebox-inbound")));
        element();
        assertThatThrownBy(() -> delegate.execute(execution)).isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("not an ebox-outbound");
        verify(execute, never()).execute(any(), any(), anyMap(), any());
    }

    @Test
    void anEmptySourceFailsWithoutNamingTheValue() {
        when(sources.resolve(new PortalRecipientSource(PortalRecipientSource.Kind.DOSSIER_FIELD, "bce"), DOSSIER, "pi-1"))
                .thenReturn(Optional.empty());
        element();
        assertThatThrownBy(() -> delegate.execute(execution)).isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("eboxEnvoiDestinataire source dossierField:bce");

        when(execution.getVariable("dossierId")).thenReturn(null);
        assertThatThrownBy(() -> delegate.execute(execution)).isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("dossierId");
        verify(execute, never()).execute(any(), any(), anyMap(), any());
    }

    private void element() {
        ServiceTask task = new ServiceTask();
        task.setId("envoiEbox");
        attributes.forEach((name, value) -> {
            ExtensionAttribute attribute = new ExtensionAttribute(name);
            attribute.setNamespace(DeployedActivityParameters.FLOWABLE_NAMESPACE);
            attribute.setValue(value);
            task.addAttribute(attribute);
        });
        when(execution.getCurrentFlowElement()).thenReturn(task);
    }

    private static ConnectorInstanceData instance(String type) {
        return new ConnectorInstanceData(INSTANCE, TENANT, "eBox", type, true, Map.of());
    }
}
