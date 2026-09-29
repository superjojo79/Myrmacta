package com.flowforge.domain.signature.usecase;

import static com.flowforge.domain.signature.SignatureFixtures.NOW;
import static com.flowforge.domain.signature.SignatureFixtures.TENANT;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.flowforge.domain.document.port.out.DocumentStoragePort;
import com.flowforge.domain.dossier.model.DossierElement;
import com.flowforge.domain.dossier.model.DossierWorkflowLink;
import com.flowforge.domain.dossier.model.ElementType;
import com.flowforge.domain.dossier.port.out.DossierElementRepository;
import com.flowforge.domain.dossier.port.out.DossierWorkflowLinkRepository;
import com.flowforge.domain.notification.model.NotificationEvent;
import com.flowforge.domain.notification.port.in.SendNotificationUseCase;
import com.flowforge.domain.signature.InMemorySignatureRepository;
import com.flowforge.domain.signature.SignatureNotifier;
import com.flowforge.domain.signature.model.SignatureBlockDefinition;
import com.flowforge.domain.signature.model.SignatureBlockDefinition.SignerDefinition;
import com.flowforge.domain.signature.model.SignatureMode;
import com.flowforge.domain.signature.model.SignatureRequest;
import com.flowforge.domain.signature.model.SignatureStatus;
import com.flowforge.domain.signature.model.Signer;
import com.flowforge.domain.signature.model.SignerKind;
import com.flowforge.domain.signature.model.SignerStatus;
import com.flowforge.domain.signature.port.in.RequestSignatureUseCase;
import com.flowforge.domain.signature.port.out.SignatureAvailabilityPort;
import com.flowforge.domain.signature.port.out.SignatureConflictException;
import com.flowforge.domain.signature.port.out.SignaturePort;
import com.flowforge.domain.signature.port.out.SignatureRequestRepository;
import com.flowforge.domain.workflow.port.out.WorkflowParticipantPort;
import com.flowforge.domain.workflow.portal.model.PortalRecipientSource;
import com.flowforge.domain.workflow.portal.port.out.PortalRecipientResolverPort;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/** S-EMIT-1 à 9, S-INT-4, S-DEC-7 (émission) — TODO §72, M4. */
class RequestSignatureUseCaseImplTest {

    private static final String PROCESS = "proc-1";
    private static final String STORAGE_REF = "storage/contrat.pdf";

    private final InMemorySignatureRepository repository = new InMemorySignatureRepository();
    private final SignaturePort port = mock(SignaturePort.class);
    private final SignatureAvailabilityPort availability = mock(SignatureAvailabilityPort.class);
    private final Map<String, Object> values = new java.util.HashMap<>();
    private final PortalRecipientResolverPort resolver =
            (source, dossierId, process) -> Optional.ofNullable(values.get(source.name()));
    private final DossierElementRepository elements = mock(DossierElementRepository.class);
    private final DocumentStoragePort storage = mock(DocumentStoragePort.class);
    private final SendNotificationUseCase sendNotification = mock(SendNotificationUseCase.class);
    private final DossierWorkflowLinkRepository links = mock(DossierWorkflowLinkRepository.class);
    private final WorkflowParticipantPort participants = mock(WorkflowParticipantPort.class);
    private final com.flowforge.domain.signature.port.out.SignatureMetricsPort metrics =
            mock(com.flowforge.domain.signature.port.out.SignatureMetricsPort.class);
    private final com.flowforge.domain.signature.port.out.SignatureOutcomeListener listener =
            mock(com.flowforge.domain.signature.port.out.SignatureOutcomeListener.class);
    private final UUID dossierId = UUID.randomUUID();
    private RequestSignatureUseCaseImpl useCase;

    @BeforeEach
    void setUp() {
        when(port.code()).thenReturn("stub");
        when(port.capabilities()).thenReturn(new SignaturePort.SignatureProviderCapabilities(
                true, true, false, true, Set.of("SIMULATED")));
        when(port.submit(any())).thenAnswer(inv -> new SignaturePort.SignatureSubmission(
                "stub:" + inv.<SignaturePort.SubmitCommand>getArgument(0).requestId(), true, "SIMULATED", Map.of()));
        when(availability.defaultDeadlineDays()).thenReturn(14);
        when(availability.maxDeadlineDays()).thenReturn(90);
        when(availability.maxSigners()).thenReturn(10);
        when(availability.maxDocumentBytes()).thenReturn(1_000L);
        DossierElement doc = element(ElementType.PRODUCED_DOC, STORAGE_REF);
        when(elements.findByDossierIdOrderByAddedAtAsc(dossierId)).thenReturn(List.of(doc));
        when(storage.retrieve(TENANT, STORAGE_REF)).thenReturn("%PDF-1.4 contrat".getBytes());
        DossierWorkflowLink link = mock(DossierWorkflowLink.class);
        when(link.id()).thenReturn(UUID.randomUUID());
        when(links.findByProcessInstanceId(TENANT, PROCESS)).thenReturn(Optional.of(link));
        when(participants.findParticipantUserIds(link)).thenReturn(List.of("manager"));
        SignatureNotifier notifier = new SignatureNotifier(TENANT, links, participants, sendNotification);
        useCase = new RequestSignatureUseCaseImpl(TENANT, repository, port, availability, resolver, elements, storage,
                notifier, metrics, listener, Clock.fixed(NOW, ZoneOffset.UTC));
        values.put("generatedDocumentId", STORAGE_REF);
        values.put("email", "Alice@Example.org");
        values.put("email2", "bob@example.org");
        values.put("manager", "carol");
    }

    private DossierElement element(ElementType type, String ref) {
        return new DossierElement(UUID.randomUUID(), dossierId, TENANT, type, "Contrat", ref,
                Map.of("contentType", "application/pdf"), "system", NOW, null);
    }

    private static SignerDefinition ext(String source, boolean required) {
        return new SignerDefinition(PortalRecipientSource.parse("dossierField:" + source).orElseThrow(),
                SignerKind.EXTERNAL, required, source);
    }

    private static SignerDefinition internal(String source, boolean required) {
        return new SignerDefinition(PortalRecipientSource.parse("dossierField:" + source).orElseThrow(),
                SignerKind.INTERNAL, required, source);
    }

    private RequestSignatureUseCase.Command command(SignatureMode mode, SignerDefinition... defs) {
        return new RequestSignatureUseCase.Command(dossierId, PROCESS, new SignatureBlockDefinition("sign",
                PortalRecipientSource.parse("variable:generatedDocumentId").orElseThrow(), mode, null, List.of(defs)));
    }

    @Test
    void parallelRequestIsPersistedPendingWithItsSignersAndSubmittedOnce() {
        // S-EMIT-1, S-DEC-6 (émission)
        RequestSignatureUseCase.Result result =
                useCase.request(command(SignatureMode.PARALLEL, ext("email", true), ext("email2", false)));

        assertThat(result.status()).isEqualTo(SignatureStatus.PENDING);
        SignatureRequest stored = repository.findById(result.requestId()).orElseThrow();
        assertThat(stored.status()).isEqualTo(SignatureStatus.PENDING);
        assertThat(stored.providerCode()).isEqualTo("stub");
        assertThat(stored.providerReference()).isEqualTo("stub:" + stored.id());
        assertThat(stored.simulated()).isTrue();
        assertThat(stored.assuranceLevel()).isEqualTo("SIMULATED");
        assertThat(stored.sourceStorageRef()).isEqualTo(STORAGE_REF);
        assertThat(stored.deadlineAt()).isEqualTo(NOW.plusSeconds(14 * 86_400L));
        assertThat(repository.findSigners(stored.id()))
                .extracting(Signer::status).containsOnly(SignerStatus.PENDING);
        assertThat(repository.findSigners(stored.id())).extracting(Signer::email)
                .containsExactly("Alice@Example.org", "bob@example.org");
        verify(port, times(1)).submit(any());
        verify(sendNotification, never()).send(any(), anyString());
    }

    @Test
    void sequentialRequestActivatesOnlyTheFirstSigner() {
        RequestSignatureUseCase.Result result =
                useCase.request(command(SignatureMode.SEQUENTIAL, ext("email", true), ext("email2", true)));

        assertThat(repository.findSigners(result.requestId())).extracting(Signer::status)
                .containsExactly(SignerStatus.PENDING, SignerStatus.WAITING);
    }

    @Test
    void submittedCommandCarriesTheDocumentDigestAndTheOrderedSigners() {
        useCase.request(command(SignatureMode.PARALLEL, ext("email", true), internal("manager", false)));

        ArgumentCaptor<SignaturePort.SubmitCommand> captor = ArgumentCaptor.forClass(SignaturePort.SubmitCommand.class);
        verify(port).submit(captor.capture());
        SignaturePort.SubmitCommand submitted = captor.getValue();
        assertThat(submitted.documentDigest()).isEqualTo(RequestSignatureUseCaseImpl.sha256("%PDF-1.4 contrat".getBytes()));
        assertThat(submitted.document()).isEqualTo("%PDF-1.4 contrat".getBytes());
        assertThat(submitted.signers()).extracting(SignaturePort.SubmitSigner::position).containsExactly(0, 1);
        assertThat(submitted.signers()).extracting(SignaturePort.SubmitSigner::kind)
                .containsExactly(SignerKind.EXTERNAL, SignerKind.INTERNAL);
    }

    @Test
    void internalSignerOnItsTurnIsNotifiedInTheInbox() {
        // S-INT-6 (émission)
        useCase.request(command(SignatureMode.PARALLEL, ext("email", true), internal("manager", true)));

        ArgumentCaptor<NotificationEvent> captor = ArgumentCaptor.forClass(NotificationEvent.class);
        verify(sendNotification).send(captor.capture(), anyString());
        assertThat(captor.getValue().userId()).isEqualTo("carol");
        assertThat(captor.getValue().payload()).containsEntry("type", SignatureNotifier.SIGNATURE_REQUESTED);
    }

    @Test
    void invalidOrMissingAddressFailsTheRequestWithoutCallingTheProvider() {
        // S-EMIT-2
        values.put("email", "pas-une-adresse");
        RequestSignatureUseCase.Result result = useCase.request(command(SignatureMode.PARALLEL, ext("email", true)));

        assertThat(result.status()).isEqualTo(SignatureStatus.FAILED);
        assertThat(result.reason()).isEqualTo("NO_ADDRESS");
        assertThat(repository.findById(result.requestId()).orElseThrow().status()).isEqualTo(SignatureStatus.FAILED);
        verify(port, never()).submit(any());
        verify(sendNotification).send(any(), anyString());
        verify(listener).outcomeRecorded(result.requestId());
        verify(metrics).outcome(com.flowforge.domain.signature.model.SignatureOutcome.FAILED);

        values.remove("email");
        assertThat(useCase.request(command(SignatureMode.PARALLEL, ext("email", true))).reason())
                .isEqualTo("NO_ADDRESS");
    }

    @Test
    void invalidInternalUserIdIsTreatedLikeAMissingAddress() {
        // S-INT-4
        values.put("manager", "   ");
        assertThat(useCase.request(command(SignatureMode.PARALLEL, internal("manager", true))).reason())
                .isEqualTo("NO_ADDRESS");
        values.put("manager", "x".repeat(129));
        assertThat(useCase.request(command(SignatureMode.PARALLEL, internal("manager", true))).reason())
                .isEqualTo("NO_ADDRESS");
    }

    @Test
    void unknownDocumentFailsWithSourceNotFound() {
        // S-EMIT-3, S-EMIT-7
        values.put("generatedDocumentId", "storage/inconnu.pdf");
        assertThat(useCase.request(command(SignatureMode.PARALLEL, ext("email", true))).reason())
                .isEqualTo("SOURCE_NOT_FOUND");
        values.remove("generatedDocumentId");
        assertThat(useCase.request(command(SignatureMode.PARALLEL, ext("email", true))).reason())
                .isEqualTo("SOURCE_NOT_FOUND");
    }

    @Test
    void nonProducedDocElementIsNotAcceptedAsSource() {
        when(elements.findByDossierIdOrderByAddedAtAsc(dossierId))
                .thenReturn(List.of(element(ElementType.ATTACHMENT, STORAGE_REF)));
        assertThat(useCase.request(command(SignatureMode.PARALLEL, ext("email", true))).reason())
                .isEqualTo("SOURCE_NOT_FOUND");
    }

    @Test
    void unreadableOrEmptyStorageFailsWithSourceNotFound() {
        when(storage.retrieve(TENANT, STORAGE_REF)).thenThrow(new IllegalStateException("gone"));
        assertThat(useCase.request(command(SignatureMode.PARALLEL, ext("email", true))).reason())
                .isEqualTo("SOURCE_NOT_FOUND");
    }

    @Test
    void oversizedDocumentIsRefused() {
        // S-EMIT-9
        when(storage.retrieve(TENANT, STORAGE_REF)).thenReturn(new byte[1_001]);
        assertThat(useCase.request(command(SignatureMode.PARALLEL, ext("email", true))).reason())
                .isEqualTo("SOURCE_TOO_LARGE");
        verify(port, never()).submit(any());
    }

    @Test
    void moreSignersThanTheTenantLimitAreRefusedAtRuntime() {
        // revue adversariale n°13 : la borne a pu baisser depuis le déploiement du BPMN
        when(availability.maxSigners()).thenReturn(1);
        assertThat(useCase.request(command(SignatureMode.PARALLEL, ext("email", true), ext("email2", true))).reason())
                .isEqualTo("TOO_MANY_SIGNERS");
        verify(port, never()).submit(any());
    }

    @Test
    void deadlineIsClampedToTheTenantCeiling() {
        when(availability.maxDeadlineDays()).thenReturn(5);
        useCase.request(command(SignatureMode.PARALLEL, ext("email", true)));

        ArgumentCaptor<SignaturePort.SubmitCommand> captor = ArgumentCaptor.forClass(SignaturePort.SubmitCommand.class);
        verify(port).submit(captor.capture());
        assertThat(captor.getValue().deadlineAt()).isEqualTo(NOW.plus(java.time.Duration.ofDays(5)));
    }

    @Test
    void nonPdfDocumentIsRefusedBeforeAnyEmission() {
        when(storage.retrieve(TENANT, STORAGE_REF)).thenReturn("<html><script>x</script></html>".getBytes());
        assertThat(useCase.request(command(SignatureMode.PARALLEL, ext("email", true))).reason())
                .isEqualTo("SOURCE_NOT_PDF");
        verify(port, never()).submit(any());
    }

    @Test
    void sameResolvedIdentityTwiceFailsWithDuplicateSigner() {
        // S-EMIT-6
        values.put("email2", "  alice@example.ORG");
        RequestSignatureUseCase.Result result =
                useCase.request(command(SignatureMode.PARALLEL, ext("email", true), ext("email2", true)));

        assertThat(result.status()).isEqualTo(SignatureStatus.FAILED);
        assertThat(result.reason()).isEqualTo("DUPLICATE_SIGNER");
        verify(port, never()).submit(any());
    }

    @Test
    void providerErrorFailsTheRequestAndRevokesItsSigners() {
        // S-EMIT-8
        org.mockito.Mockito.doThrow(new IllegalStateException("provider down")).when(port).submit(any());
        RequestSignatureUseCase.Result result =
                useCase.request(command(SignatureMode.PARALLEL, ext("email", true), ext("email2", true)));

        assertThat(result.status()).isEqualTo(SignatureStatus.FAILED);
        assertThat(result.reason()).isEqualTo("PROVIDER_ERROR");
        SignatureRequest stored = repository.findById(result.requestId()).orElseThrow();
        assertThat(stored.status()).isEqualTo(SignatureStatus.FAILED);
        assertThat(stored.isDispatchPending()).isTrue();
        assertThat(repository.findSigners(stored.id())).extracting(Signer::status).containsOnly(SignerStatus.REVOKED);
        verify(sendNotification).send(any(), anyString());
    }

    @Test
    void providerWithoutOrderedSigningRefusesSequentialMode() {
        when(port.capabilities()).thenReturn(new SignaturePort.SignatureProviderCapabilities(
                false, true, true, true, Set.of()));
        assertThat(useCase.request(command(SignatureMode.SEQUENTIAL, ext("email", true), ext("email2", true))).reason())
                .isEqualTo("MODE_UNSUPPORTED");
        assertThat(useCase.request(command(SignatureMode.PARALLEL, ext("email", true))).status())
                .isEqualTo(SignatureStatus.PENDING);
    }

    @Test
    void providerWithoutOptionalSignersRefusesThem() {
        when(port.capabilities()).thenReturn(new SignaturePort.SignatureProviderCapabilities(
                true, false, true, true, Set.of()));
        assertThat(useCase.request(command(SignatureMode.PARALLEL, ext("email", true), ext("email2", false))).reason())
                .isEqualTo("OPTIONAL_UNSUPPORTED");
    }

    @Test
    void replayedDelegateReturnsTheExistingPendingRequestWithoutResubmitting() {
        // S-EMIT-5
        RequestSignatureUseCase.Result first = useCase.request(command(SignatureMode.PARALLEL, ext("email", true)));
        RequestSignatureUseCase.Result replay = useCase.request(command(SignatureMode.PARALLEL, ext("email", true)));

        assertThat(replay.requestId()).isEqualTo(first.requestId());
        assertThat(replay.status()).isEqualTo(SignatureStatus.PENDING);
        assertThat(repository.requests).hasSize(1);
        verify(port, times(1)).submit(any());
    }

    @Test
    void concurrentInsertConflictResolvesToTheWinner() {
        // S-EMIT-5 (course entre deux exécutions)
        SignatureRequestRepository racing = mock(SignatureRequestRepository.class);
        SignatureRequest winner = SignatureFixturesHolder.request();
        when(racing.findPendingByProcessAndActivity(PROCESS, "sign"))
                .thenReturn(Optional.empty(), Optional.of(winner));
        org.mockito.Mockito.doThrow(new SignatureConflictException("race", null)).when(racing).insert(any(), any());
        SignatureNotifier notifier = new SignatureNotifier(TENANT, links, participants, sendNotification);
        RequestSignatureUseCaseImpl racingUseCase = new RequestSignatureUseCaseImpl(TENANT, racing, port, availability,
                resolver, elements, storage, notifier, metrics, listener, Clock.fixed(NOW, ZoneOffset.UTC));

        RequestSignatureUseCase.Result result =
                racingUseCase.request(command(SignatureMode.PARALLEL, ext("email", true)));

        assertThat(result.requestId()).isEqualTo(winner.id());
        verify(port, never()).submit(any());
    }

    /** Petite fabrique locale pour la course. */
    private static final class SignatureFixturesHolder {
        static SignatureRequest request() {
            return com.flowforge.domain.signature.SignatureFixtures.request(UUID.randomUUID(), SignatureMode.PARALLEL);
        }
    }
}
