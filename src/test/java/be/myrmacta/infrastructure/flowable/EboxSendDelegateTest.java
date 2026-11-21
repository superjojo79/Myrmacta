package be.myrmacta.infrastructure.flowable;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import be.myrmacta.domain.connector.model.ConnectorInstanceData;
import be.myrmacta.domain.connector.port.out.ConnectorInstanceRepository;
import be.myrmacta.domain.document.port.out.DocumentStoragePort;
import be.myrmacta.domain.dossier.model.DossierElement;
import be.myrmacta.domain.dossier.model.ElementType;
import be.myrmacta.domain.dossier.port.in.AddDossierElementUseCase;
import be.myrmacta.domain.dossier.port.out.DossierElementRepository;
import be.myrmacta.domain.ebox.model.EboxDelivery;
import be.myrmacta.domain.ebox.model.EboxLanguage;
import be.myrmacta.domain.ebox.model.EboxProofOfDispatch;
import be.myrmacta.domain.ebox.model.EboxRecipient;
import be.myrmacta.domain.ebox.model.EboxSendAuditEntry.Outcome;
import be.myrmacta.domain.ebox.model.EboxSendOutcome;
import be.myrmacta.domain.ebox.model.FallbackChannel;
import be.myrmacta.domain.ebox.model.SendEboxCommand;
import be.myrmacta.domain.ebox.port.in.SendEboxMessageUseCase;
import be.myrmacta.domain.workflow.portal.model.PortalRecipientSource;
import be.myrmacta.domain.workflow.portal.port.out.PortalRecipientResolverPort;
import be.myrmacta.infrastructure.config.ConnectorProperties;
import be.myrmacta.infrastructure.config.TenantProperties;
import be.myrmacta.infrastructure.ebox.EboxRecipientHmac;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.flowable.bpmn.model.ExtensionAttribute;
import org.flowable.bpmn.model.MultiInstanceLoopCharacteristics;
import org.flowable.bpmn.model.ServiceTask;
import org.flowable.bpmn.model.UserTask;
import org.flowable.engine.delegate.DelegateExecution;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/** TODO §155 (lot 2, D-EBOX-SEND-BLOCK) — brique « Envoyer en eBox », seul chemin d'envoi (revue H1, H2, M1, B1). */
class EboxSendDelegateTest {

    private static final String TENANT = "tenant_dev";
    private static final UUID INSTANCE = UUID.fromString("00000000-0000-0000-0000-0000000000e5");
    private static final UUID DOSSIER = UUID.fromString("00000000-0000-0000-0000-00000000d055");
    private static final String REF = "tenant_dev/doc-1";
    private static final byte[] PDF = "%PDF-1.7 décision".getBytes(StandardCharsets.UTF_8);

    private final SendEboxMessageUseCase send = mock(SendEboxMessageUseCase.class);
    private final ConnectorInstanceRepository instances = mock(ConnectorInstanceRepository.class);
    private final PortalRecipientResolverPort sources = mock(PortalRecipientResolverPort.class);
    private final DossierElementRepository elements = mock(DossierElementRepository.class);
    private final DocumentStoragePort storage = mock(DocumentStoragePort.class);
    private final AddDossierElementUseCase addElement = mock(AddDossierElementUseCase.class);
    private final EboxRecipientHmac recipients = new EboxRecipientHmac(new ConnectorProperties(
            new ConnectorProperties.Crypto(Base64.getEncoder().encodeToString(new byte[32])), null, null, null, null, null));
    private final DelegateExecution execution = mock(DelegateExecution.class);
    private final EboxSendDelegate delegate = new EboxSendDelegate(send, instances, sources, elements, storage, addElement,
            recipients, new TenantProperties(TENANT));
    private final Map<String, String> attributes = new LinkedHashMap<>();
    private ServiceTask task;

    @BeforeEach
    void setUp() {
        when(instances.findByIdAndTenantId(INSTANCE, TENANT)).thenReturn(Optional.of(instance("ebox-outbound", true)));
        when(execution.getVariable("dossierId")).thenReturn(DOSSIER.toString());
        when(execution.getVariable("generatedDocumentId")).thenReturn(REF);
        when(execution.getCurrentActivityId()).thenReturn("envoiEbox");
        when(execution.getProcessInstanceId()).thenReturn("pi-1");
        dossierField("bce", "0212345678");
        dossierField("langue", "nl");
        when(elements.findByDossierIdOrderByAddedAtAsc(DOSSIER))
                .thenReturn(List.of(element(ElementType.PRODUCED_DOC, REF, "Décision \"permis\"/2026")));
        when(storage.retrieve(TENANT, REF)).thenReturn(PDF);
        when(send.send(any())).thenReturn(new EboxSendOutcome(Outcome.PUBLISHED, EboxDelivery.EBOX_AND_FALLBACK,
                FallbackChannel.PAPER, "msg-1", null, false));
        attributes.put("connectorInstanceId", INSTANCE.toString());
        attributes.put("eboxEnvoiDestinataire", "dossierField:bce");
        attributes.put("eboxEnvoiTypeDestinataire", "ENTREPRISE");
        attributes.put("eboxEnvoiDocument", "variable:generatedDocumentId");
        attributes.put("eboxEnvoiObjet", "Votre permis");
        attributes.put("eboxEnvoiApplication", "myrmacta-commune");
        attributes.put("eboxEnvoiTypeMessage", "decision");
        attributes.put("eboxEnvoiLangue", "dossierField:langue");
        attributes.put("eboxEnvoiSecours", "PAPER");
    }

    @Test
    void sendsTheProducedPdfThroughTheUseCaseAndSetsTheDocumentedVariables() {
        element();

        delegate.execute(execution);

        SendEboxCommand command = sent();
        assertThat(command.sender().connectorInstanceId()).isEqualTo(INSTANCE);
        assertThat(command.dossierId()).isEqualTo(DOSSIER);
        assertThat(command.idempotencyKey()).matches("myr1-[0-9a-f]{64}");
        assertThat(command.fallbackChannel()).isEqualTo(FallbackChannel.PAPER);
        assertThat(command.message().recipient()).isEqualTo(new EboxRecipient.Enterprise("0212345678"));
        assertThat(command.message().language()).isEqualTo(EboxLanguage.NL);
        assertThat(command.message().subject()).isEqualTo("Votre permis");
        assertThat(command.message().registered()).isFalse();
        assertThat(command.message().mainDocument().content()).isEqualTo(PDF);
        assertThat(command.message().mainDocument().fileName()).isEqualTo("Décision _permis__2026.pdf");
        assertThat(command.message().mainDocument().mediaType()).isEqualTo("application/pdf");
        verify(execution).setVariable("eboxIssue", "ENVOYE");
        verify(execution).setVariable("eboxLivraison", "EBOX_AND_FALLBACK");
        verify(execution).setVariable("eboxSecoursRequis", true);
        verify(execution).setVariable("eboxVoieSecours", "PAPER");
        verify(execution).setVariable("eboxEnvoiMessageId", "msg-1");
        verify(execution).setVariable("eboxEnvoiRejoue", false);
        verify(storage, never()).store(any(), any(), any());
    }

    @Test
    void theOutcomeTellsTheProcessWhatToDo() {
        assertThat(EboxSendDelegate.variables(new EboxSendOutcome(Outcome.FALLBACK, EboxDelivery.FALLBACK, FallbackChannel.EMAIL,
                null, null, false))).containsEntry("eboxIssue", "VOIE_SECOURS").containsEntry("eboxSecoursRequis", true)
                .containsEntry("eboxVoieSecours", "EMAIL").containsEntry("eboxEnvoiMessageId", "");
        assertThat(EboxSendDelegate.variables(new EboxSendOutcome(Outcome.DUPLICATE, EboxDelivery.EBOX, FallbackChannel.PAPER,
                "rm-1", null, true))).containsEntry("eboxIssue", "DEJA_ENVOYE").containsEntry("eboxSecoursRequis", false)
                .containsEntry("eboxVoieSecours", "").containsEntry("eboxEnvoiRejoue", true).doesNotContainKey("eboxPreuveDepot");
    }

    /** Revue M1 : une brique synchrone ou multi-instance (BPMN déployé sans le contrôle) n'envoie jamais. */
    @Test
    void theStepMustBeAnAsynchronousSingleServiceTask() {
        element();
        task.setAsynchronous(false);
        assertThatThrownBy(() -> delegate.execute(execution)).isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("asynchronous");

        element();
        MultiInstanceLoopCharacteristics loop = new MultiInstanceLoopCharacteristics();
        loop.setLoopCardinality("2");
        task.setLoopCharacteristics(loop);
        assertThatThrownBy(() -> delegate.execute(execution)).isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("multi-instance");

        when(execution.getCurrentFlowElement()).thenReturn(new UserTask());
        assertThatThrownBy(() -> delegate.execute(execution)).isInstanceOf(IllegalStateException.class);
        verify(send, never()).send(any());
    }

    /**
     * Revue H2 puis M1' : chaque destinataire a sa clé, le tour de boucle n'y entre pas (une liste réordonnée ou complétée au
     * rebouclage ne renvoie pas aux destinataires déjà servis) ; le NISS n'entre jamais en clair.
     */
    @Test
    void theKeyNamesTheDossierStepDocumentAndRecipient() {
        String first = EboxSendDelegate.idempotencyKey(DOSSIER, "envoiEbox", REF, "0212345678");
        assertThat(EboxSendDelegate.idempotencyKey(DOSSIER, "envoiEbox", REF, "0212345678")).isEqualTo(first);
        assertThat(EboxSendDelegate.idempotencyKey(DOSSIER, "envoiEbox", REF, "0207654321")).isNotEqualTo(first);
        assertThat(EboxSendDelegate.idempotencyKey(DOSSIER, "envoiEbox", "tenant_dev/doc-2", "0212345678")).isNotEqualTo(first);
        assertThat(EboxSendDelegate.idempotencyKey(DOSSIER, "autre", REF, "0212345678")).isNotEqualTo(first);
        assertThat(EboxSendDelegate.idempotencyKey(UUID.randomUUID(), "envoiEbox", REF, "0212345678")).isNotEqualTo(first);
        assertThat(first).hasSize(69);
    }

    @Test
    void theLoopTurnDoesNotEnterTheKeyAndTheCitizenIsHashed() {
        attributes.put("eboxEnvoiTypeDestinataire", "CITOYEN");
        attributes.put("eboxEnvoiDestinataire", "variable:niss");
        when(execution.getVariable("niss")).thenReturn("85073003328");
        when(execution.getVariable("loopCounter")).thenReturn(1);
        when(execution.getVariableLocal("loopCounter")).thenReturn(1);
        element();

        delegate.execute(execution);

        String hashed = recipients.citizen(TENANT, "85073003328");
        assertThat(hashed).doesNotContain("85073003328").hasSize(64);
        assertThat(sent().idempotencyKey()).isEqualTo(EboxSendDelegate.idempotencyKey(DOSSIER, "envoiEbox", REF, hashed));
    }

    /** Revue H2 : dans un sous-processus multi-instance, les variables vont au tour, pas au process. */
    @Test
    void insideAMultiInstanceSubProcessTheVariablesAreLocalToTheTurn() {
        DelegateExecution root = mock(DelegateExecution.class);
        DelegateExecution turn = mock(DelegateExecution.class);
        DelegateExecution child = mock(DelegateExecution.class);
        when(execution.getParent()).thenReturn(child);
        when(child.getParent()).thenReturn(turn);
        when(turn.getParent()).thenReturn(root);
        when(root.isMultiInstanceRoot()).thenReturn(true);
        when(turn.getVariableLocal("loopCounter")).thenReturn(0);
        element();

        delegate.execute(execution);

        verify(turn).setVariableLocal("eboxIssue", "ENVOYE");
        verify(turn).setVariableLocal("eboxSecoursRequis", true);
        verify(turn).setVariableLocal("eboxEnvoiRejoue", false);
        verify(execution, never()).setVariable(anyString(), any());
        verify(child, never()).setVariableLocal(anyString(), any());
    }

    /** Revue B3' : une variable locale {@code loopCounter} posée hors d'un tour de multi-instance ne déplace pas les variables. */
    @Test
    void aLoopCounterOutsideAMultiInstanceTurnIsIgnored() {
        DelegateExecution parent = mock(DelegateExecution.class);
        DelegateExecution grandParent = mock(DelegateExecution.class);
        when(execution.getParent()).thenReturn(parent);
        when(parent.getVariableLocal("loopCounter")).thenReturn(3);
        when(parent.getParent()).thenReturn(grandParent);
        element();

        delegate.execute(execution);

        verify(execution).setVariable("eboxIssue", "ENVOYE");
        verify(parent, never()).setVariableLocal(anyString(), any());
    }

    @Test
    void aRegisteredMailAttachesTheProofOnTheFirstSendOnly() {
        attributes.put("eboxEnvoiLangue", "fr");
        attributes.put("eboxEnvoiRecommande", "OUI");
        EboxProofOfDispatch proof = new EboxProofOfDispatch("myr1-abc", "rm-1", "b".repeat(64), "{\"signature\":\"s\"}");
        when(send.send(any())).thenReturn(new EboxSendOutcome(Outcome.PUBLISHED, EboxDelivery.EBOX, FallbackChannel.PAPER, "rm-1",
                proof, false));
        when(storage.store(TENANT, "application/json", "{\"signature\":\"s\"}".getBytes(StandardCharsets.UTF_8)))
                .thenReturn("tenant_dev/proof-1");
        element();

        delegate.execute(execution);

        assertThat(sent().message().language()).isEqualTo(EboxLanguage.FR);
        assertThat(sent().message().registered()).isTrue();
        ArgumentCaptor<AddDossierElementUseCase.AddCommand> added = ArgumentCaptor.forClass(AddDossierElementUseCase.AddCommand.class);
        verify(addElement).add(added.capture(), any());
        assertThat(added.getValue().dossierId()).isEqualTo(DOSSIER);
        assertThat(added.getValue().type()).isEqualTo(ElementType.ATTACHMENT);
        assertThat(added.getValue().storageRef()).isEqualTo("tenant_dev/proof-1");
        assertThat(added.getValue().label()).startsWith("Preuve de dépôt eBox");
        assertThat(added.getValue().metadata()).containsEntry("eboxIdempotencyKey", sent().idempotencyKey());
        verify(execution, never()).setVariable(eq("eboxPreuveDepot"), any());
    }

    /** Revue B1 : un rejeu ne verse pas une seconde preuve, mais la verse si la tentative qui a déposé a reculé. */
    @Test
    void aReplayAttachesTheProofOnlyWhenTheDossierDoesNotHoldItYet() {
        attributes.put("eboxEnvoiRecommande", "OUI");
        EboxProofOfDispatch proof = new EboxProofOfDispatch("myr1-abc", "rm-1", "b".repeat(64), "{\"signature\":\"s\"}");
        when(send.send(any())).thenReturn(new EboxSendOutcome(Outcome.PUBLISHED, EboxDelivery.EBOX, FallbackChannel.PAPER, "rm-1",
                proof, true));
        when(storage.store(any(), any(), any())).thenReturn("tenant_dev/proof-1");
        element();

        delegate.execute(execution);

        String key = sent().idempotencyKey();
        ArgumentCaptor<AddDossierElementUseCase.AddCommand> added = ArgumentCaptor.forClass(AddDossierElementUseCase.AddCommand.class);
        verify(addElement).add(added.capture(), any());
        assertThat(added.getValue().metadata()).containsEntry("eboxIdempotencyKey", key);
        verify(execution).setVariable("eboxEnvoiRejoue", true);

        when(elements.findByDossierIdOrderByAddedAtAsc(DOSSIER)).thenReturn(List.of(
                element(ElementType.PRODUCED_DOC, REF, "Décision"),
                new DossierElement(UUID.randomUUID(), DOSSIER, TENANT, ElementType.ATTACHMENT, "Preuve de dépôt eBox — envoiEbox",
                        "tenant_dev/proof-1", Map.of("eboxIdempotencyKey", key), "system", Instant.parse("2026-11-20T09:01:00Z"),
                        "envoiEbox")));
        org.mockito.Mockito.clearInvocations(addElement, storage);

        delegate.execute(execution);

        verify(storage, never()).store(any(), any(), any());
        verify(addElement, never()).add(any(), any());
    }

    @Test
    void parametersAreLiteralAndTheInstanceAnEnabledEboxSender() {
        attributes.remove("eboxEnvoiObjet");
        element();
        when(execution.getVariable("eboxEnvoiObjet")).thenReturn("Forgé");
        assertThatThrownBy(() -> delegate.execute(execution)).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("eboxEnvoiObjet");

        attributes.put("eboxEnvoiObjet", "Votre permis");
        element();
        when(instances.findByIdAndTenantId(INSTANCE, TENANT)).thenReturn(Optional.of(instance("ebox-inbound", true)));
        assertThatThrownBy(() -> delegate.execute(execution)).isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("not an ebox-outbound");

        when(instances.findByIdAndTenantId(INSTANCE, TENANT)).thenReturn(Optional.of(instance("ebox-outbound", false)));
        assertThatThrownBy(() -> delegate.execute(execution)).isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("disabled");
        verify(send, never()).send(any());
    }

    @Test
    void theDocumentMustBeAProducedPdfOfThisDossier() {
        element();
        when(execution.getVariable("generatedDocumentId")).thenReturn("tenant_dev/other");
        assertThatThrownBy(() -> delegate.execute(execution)).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("produced document of this dossier").hasMessageNotContaining("tenant_dev/other");

        when(execution.getVariable("generatedDocumentId")).thenReturn(REF);
        when(elements.findByDossierIdOrderByAddedAtAsc(DOSSIER)).thenReturn(List.of(element(ElementType.ATTACHMENT, REF, "Pièce")));
        assertThatThrownBy(() -> delegate.execute(execution)).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("produced document");

        when(elements.findByDossierIdOrderByAddedAtAsc(DOSSIER)).thenReturn(List.of(element(ElementType.PRODUCED_DOC, REF, "x")));
        when(storage.retrieve(TENANT, REF)).thenReturn("<html>".getBytes(StandardCharsets.US_ASCII));
        assertThatThrownBy(() -> delegate.execute(execution)).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("not a PDF");
        verify(send, never()).send(any());
    }

    @Test
    void anInvalidRequestIsRefusedWithoutEchoingTheNationalNumber() {
        attributes.put("eboxEnvoiTypeDestinataire", "CITOYEN");
        attributes.put("eboxEnvoiDestinataire", "variable:niss");
        when(execution.getVariable("niss")).thenReturn("85073003329");
        element();
        assertThatThrownBy(() -> delegate.execute(execution)).isInstanceOf(IllegalArgumentException.class)
                .hasMessageNotContaining("85073003329");

        when(sources.resolve(new PortalRecipientSource(PortalRecipientSource.Kind.DOSSIER_FIELD, "bce"), DOSSIER, "pi-1"))
                .thenReturn(Optional.empty());
        attributes.put("eboxEnvoiTypeDestinataire", "ENTREPRISE");
        attributes.put("eboxEnvoiDestinataire", "dossierField:bce");
        element();
        assertThatThrownBy(() -> delegate.execute(execution)).isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("eboxEnvoiDestinataire source dossierField:bce");

        when(execution.getVariable("dossierId")).thenReturn(null);
        assertThatThrownBy(() -> delegate.execute(execution)).isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("dossierId");
        verify(send, never()).send(any());
    }

    private SendEboxCommand sent() {
        ArgumentCaptor<SendEboxCommand> captor = ArgumentCaptor.forClass(SendEboxCommand.class);
        verify(send).send(captor.capture());
        return captor.getValue();
    }

    private void dossierField(String name, String value) {
        when(sources.resolve(new PortalRecipientSource(PortalRecipientSource.Kind.DOSSIER_FIELD, name), DOSSIER, "pi-1"))
                .thenReturn(Optional.of(value));
    }

    private void element() {
        task = new ServiceTask();
        task.setId("envoiEbox");
        task.setAsynchronous(true);
        attributes.forEach((name, value) -> {
            ExtensionAttribute attribute = new ExtensionAttribute(name);
            attribute.setNamespace(DeployedActivityParameters.FLOWABLE_NAMESPACE);
            attribute.setValue(value);
            task.addAttribute(attribute);
        });
        when(execution.getCurrentFlowElement()).thenReturn(task);
    }

    private static DossierElement element(ElementType type, String ref, String label) {
        return new DossierElement(UUID.randomUUID(), DOSSIER, TENANT, type, label, ref, Map.of(), "system",
                Instant.parse("2026-11-20T09:00:00Z"), "step");
    }

    private static ConnectorInstanceData instance(String type, boolean enabled) {
        return new ConnectorInstanceData(INSTANCE, TENANT, "eBox", type, enabled, Map.of());
    }
}
