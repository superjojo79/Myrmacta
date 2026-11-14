package be.myrmacta.domain.signature.usecase;

import static be.myrmacta.domain.signature.SignatureFixtures.NOW;
import static be.myrmacta.domain.signature.SignatureFixtures.TENANT;
import static be.myrmacta.domain.signature.SignatureFixtures.external;
import static be.myrmacta.domain.signature.SignatureFixtures.internal;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import be.myrmacta.domain.document.port.out.DocumentStoragePort;
import be.myrmacta.domain.dossier.model.Dossier;
import be.myrmacta.domain.dossier.model.DossierStatus;
import be.myrmacta.domain.dossier.port.out.DossierRepository;
import be.myrmacta.domain.signature.InMemorySignatureRepository;
import be.myrmacta.domain.signature.InMemorySignedUploadRepository;
import be.myrmacta.domain.signature.SignatureAccessResolver;
import be.myrmacta.domain.signature.SignatureFixtures;
import be.myrmacta.domain.signature.SignatureWorkingDocuments;
import be.myrmacta.domain.signature.model.InvalidCardSignatureInputException;
import be.myrmacta.domain.signature.model.SignatureMode;
import be.myrmacta.domain.signature.model.SignatureProgression;
import be.myrmacta.domain.signature.model.SignatureRequest;
import be.myrmacta.domain.signature.model.SignatureStatus;
import be.myrmacta.domain.signature.model.Signer;
import be.myrmacta.domain.signature.model.SignedUpload;
import be.myrmacta.domain.signature.model.SignedUploadEvidence;
import be.myrmacta.domain.signature.model.SignedUploadRejectedException;
import be.myrmacta.domain.signature.model.SignedUploadRejection;
import be.myrmacta.domain.signature.model.SigningSessionExpiredException;
import be.myrmacta.domain.signature.port.in.CardSignatureUseCase.CardInput;
import be.myrmacta.domain.signature.port.in.CardSignatureUseCase.Completion;
import be.myrmacta.domain.signature.port.in.CardSignatureUseCase.Prepared;
import be.myrmacta.domain.signature.port.in.DecideSignatureUseCase;
import be.myrmacta.domain.signature.port.in.SubmitSignedDocumentUseCase;
import be.myrmacta.domain.signature.port.out.SignaturePort;
import be.myrmacta.domain.workflow.portal.model.PortalAccessDeniedException;
import be.myrmacta.domain.workflow.portal.port.out.PortalTokenPort;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/**
 * TODO §138 (D-EID-BRIDGE) — signature dans la page : préparation sans écriture, achèvement par le chemin du
 * téléversement, document changé entre les deux, session expirée, entrées illisibles, fournisseur sans prise en charge.
 */
class CardSignatureUseCaseImplTest {

    private static final byte[] SOURCE = "%PDF-source".getBytes();
    private static final byte[] CERT = {1, 2, 3};
    private static final byte[] SIGNATURE = {9, 9};

    private final InMemorySignatureRepository repository = new InMemorySignatureRepository();
    private final InMemorySignedUploadRepository uploads = new InMemorySignedUploadRepository(repository);
    private final PortalTokenPort tokenPort = mock(PortalTokenPort.class);
    private final DossierRepository dossiers = mock(DossierRepository.class);
    private final SignaturePort port = mock(SignaturePort.class);
    private final DocumentStoragePort storage = mock(DocumentStoragePort.class);
    private final SubmitSignedDocumentUseCase submit = mock(SubmitSignedDocumentUseCase.class);
    private final Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
    private CardSignatureUseCaseImpl useCase;
    private SignatureRequest request;
    private List<Signer> signers;

    @BeforeEach
    void setUp() {
        when(tokenPort.hash(any())).thenAnswer(inv -> "hash:" + inv.getArgument(0));
        Dossier dossier = mock(Dossier.class);
        when(dossier.status()).thenReturn(DossierStatus.OUVERT);
        when(dossiers.findById(any())).thenReturn(Optional.of(dossier));
        when(port.capabilities()).thenReturn(SignatureFixtures.eidCapabilities());
        when(storage.retrieve(TENANT, "source")).thenReturn(SOURCE);
        when(port.prepareCardSignature(any())).thenReturn(new SignaturePort.CardSignatureDigest(new byte[] {5}, "SHA-256"));
        when(port.assembleCardSignature(any(), any())).thenReturn("%PDF-assembled".getBytes());
        when(submit.submitByToken(any(), any())).thenReturn(new DecideSignatureUseCase.Result(
                UUID.randomUUID(), be.myrmacta.domain.signature.model.SignerStatus.SIGNED, SignatureStatus.SIGNED));
        request = new SignatureRequest(UUID.randomUUID(), TENANT, UUID.randomUUID(), "proc-1", "signStep",
                UUID.randomUUID(), "source", SignatureWorkingDocuments.digestOf(SOURCE), "eid", "eid:x",
                SignatureMode.PARALLEL, SignatureStatus.PENDING, null, false, "QES", NOW.plusSeconds(86_400), null, 0, NOW,
                null, "test", 0L);
        signers = SignatureProgression.start(SignatureMode.PARALLEL, List.of(
                        new Signer(UUID.randomUUID(), TENANT, request.id(), 0, be.myrmacta.domain.signature.model.SignerKind.EXTERNAL,
                                true, null, "Client", "a@example.org", "hash:tok", be.myrmacta.domain.signature.model.SignerStatus.WAITING,
                                null, 0, null, null, 0L),
                        internal(request.id(), 1, true, "bob")))
                .signers();
        repository.insert(request, signers);
        useCase = new CardSignatureUseCaseImpl(TENANT, new SignatureAccessResolver(repository, tokenPort, dossiers, clock),
                port, storage, uploads, submit, clock);
    }

    private static CardInput input() {
        return new CardInput(CERT, List.of(new byte[] {4}));
    }

    private Completion completion(Instant signingTime, String documentDigest) {
        return new Completion(CERT, List.of(new byte[] {4}), signingTime, documentDigest, SIGNATURE);
    }

    private String sourceDigest() {
        return SignatureWorkingDocuments.digestOf(SOURCE);
    }

    @Test
    void prepareFixesTheSigningTimeToTheServerSecondAndAnswersTheCurrentDocumentDigestWithoutWriting() {
        Instant withMillis = NOW.plusMillis(789);
        CardSignatureUseCaseImpl later = new CardSignatureUseCaseImpl(TENANT,
                new SignatureAccessResolver(repository, tokenPort, dossiers, Clock.fixed(withMillis, ZoneOffset.UTC)), port,
                storage, uploads, submit, Clock.fixed(withMillis, ZoneOffset.UTC));

        Prepared prepared = later.prepareByToken("tok", input());

        assertThat(prepared.digest()).containsExactly(5);
        assertThat(prepared.hashFunction()).isEqualTo("SHA-256");
        assertThat(prepared.signingTime()).isEqualTo(NOW);
        assertThat(prepared.documentDigest()).isEqualTo(sourceDigest());
        ArgumentCaptor<SignaturePort.CardSignatureInput> given = ArgumentCaptor.forClass(SignaturePort.CardSignatureInput.class);
        verify(port).prepareCardSignature(given.capture());
        assertThat(given.getValue().baseDocument()).isEqualTo(SOURCE);
        assertThat(given.getValue().signingTime()).isEqualTo(NOW);
        assertThat(uploads.uploads).isEmpty();
        assertThat(repository.findSigners(request.id())).isEqualTo(signers);
        verify(submit, never()).submitByToken(any(), any());
    }

    @Test
    void prepareBasesTheDigestOnTheLastAcceptedUpload() {
        byte[] signedByOther = "%PDF-source-signed".getBytes();
        when(storage.retrieve(TENANT, "work-1")).thenReturn(signedByOther);
        uploads.save(new SignedUpload(UUID.randomUUID(), TENANT, request.id(), signers.get(1).id(), 1, "work-1",
                SignatureWorkingDocuments.digestOf(signedByOther),
                new SignedUploadEvidence("A", "CA", "1", "fp", null, NOW, "PAdES", "RSA", true, true, "OCSP", NOW), NOW));

        Prepared prepared = useCase.prepareByToken("tok", input());

        assertThat(prepared.documentDigest()).isEqualTo(SignatureWorkingDocuments.digestOf(signedByOther));
        ArgumentCaptor<SignaturePort.CardSignatureInput> given = ArgumentCaptor.forClass(SignaturePort.CardSignatureInput.class);
        verify(port).prepareCardSignature(given.capture());
        assertThat(given.getValue().baseDocument()).isEqualTo(signedByOther);
    }

    @Test
    void completeAssemblesThenSendsThePdfThroughTheUploadPath() {
        Instant signingTime = NOW.minus(Duration.ofMinutes(5));

        DecideSignatureUseCase.Result result = useCase.completeByToken("tok", completion(signingTime, sourceDigest()));

        assertThat(result.signerStatus()).isEqualTo(be.myrmacta.domain.signature.model.SignerStatus.SIGNED);
        ArgumentCaptor<SignaturePort.CardSignatureInput> given = ArgumentCaptor.forClass(SignaturePort.CardSignatureInput.class);
        verify(port).assembleCardSignature(given.capture(), any());
        assertThat(given.getValue().signingTime()).isEqualTo(signingTime);
        assertThat(given.getValue().baseDocument()).isEqualTo(SOURCE);
        ArgumentCaptor<SubmitSignedDocumentUseCase.Upload> upload =
                ArgumentCaptor.forClass(SubmitSignedDocumentUseCase.Upload.class);
        verify(submit).submitByToken(org.mockito.ArgumentMatchers.eq("tok"), upload.capture());
        assertThat(upload.getValue().read()).isEqualTo("%PDF-assembled".getBytes());
    }

    @Test
    void internalSignerCompletesByIdentityAndAnotherUserIsRefused() {
        Signer bob = signers.get(1);
        when(submit.submitInternal(any(), any(), any())).thenReturn(new DecideSignatureUseCase.Result(
                request.id(), be.myrmacta.domain.signature.model.SignerStatus.SIGNED, SignatureStatus.SIGNED));

        assertThatThrownBy(() -> useCase.prepareInternal("mallory", bob.id(), input()))
                .isInstanceOf(PortalAccessDeniedException.class);
        assertThatThrownBy(() -> useCase.completeInternal("mallory", bob.id(), completion(NOW, sourceDigest())))
                .isInstanceOf(PortalAccessDeniedException.class);
        assertThat(useCase.prepareInternal("bob", bob.id(), input()).documentDigest()).isEqualTo(sourceDigest());
        useCase.completeInternal("bob", bob.id(), completion(NOW, sourceDigest()));

        verify(submit).submitInternal(org.mockito.ArgumentMatchers.eq("bob"), org.mockito.ArgumentMatchers.eq(bob.id()), any());
    }

    @Test
    void aDocumentThatChangedBetweenPrepareAndCompleteIsStaleAndNothingIsAssembled() {
        assertThatThrownBy(() -> useCase.completeByToken("tok", completion(NOW, "0".repeat(64))))
                .isInstanceOfSatisfying(SignedUploadRejectedException.class,
                        ex -> assertThat(ex.rejection()).isEqualTo(SignedUploadRejection.STALE_DOCUMENT));
        verify(port, never()).assembleCardSignature(any(), any());
        verify(submit, never()).submitByToken(any(), any());
    }

    @Test
    void aSessionOlderThanFifteenMinutesOrInTheFutureIsExpired() {
        for (Instant bad : List.of(NOW.minus(Duration.ofMinutes(15)).minusSeconds(1), NOW.plus(Duration.ofMinutes(1)).plusSeconds(1))) {
            assertThatThrownBy(() -> useCase.completeByToken("tok", completion(bad, sourceDigest())))
                    .isInstanceOf(SigningSessionExpiredException.class);
        }
        verify(port, never()).assembleCardSignature(any(), any());
        // les bornes elles-mêmes passent
        useCase.completeByToken("tok", completion(NOW.minus(Duration.ofMinutes(15)), sourceDigest()));
        useCase.completeByToken("tok", completion(NOW.plus(Duration.ofMinutes(1)), sourceDigest()));
    }

    @Test
    void unreadableOrOutOfBoundsInputIsRefusedWithoutDetail() {
        assertThatThrownBy(() -> useCase.prepareByToken("tok", new CardInput(null, List.of())))
                .isInstanceOf(InvalidCardSignatureInputException.class);
        assertThatThrownBy(() -> useCase.prepareByToken("tok", new CardInput(new byte[0], List.of())))
                .isInstanceOf(InvalidCardSignatureInputException.class);
        assertThatThrownBy(() -> useCase.prepareByToken("tok", new CardInput(new byte[9000], List.of())))
                .isInstanceOf(InvalidCardSignatureInputException.class);
        assertThatThrownBy(() -> useCase.prepareByToken("tok",
                        new CardInput(CERT, java.util.Collections.nCopies(9, new byte[] {1}))))
                .isInstanceOf(InvalidCardSignatureInputException.class);
        assertThatThrownBy(() -> useCase.completeByToken("tok", new Completion(CERT, List.of(), NOW, sourceDigest(), new byte[0])))
                .isInstanceOf(InvalidCardSignatureInputException.class);
        assertThatThrownBy(() -> useCase.completeByToken("tok",
                        new Completion(CERT, List.of(), NOW, sourceDigest(), new byte[2000])))
                .isInstanceOf(InvalidCardSignatureInputException.class);
        verify(port, never()).prepareCardSignature(any());
    }

    @Test
    void anIllegalArgumentFromThePortalBecomesTheDetaillessRefusal() {
        when(port.prepareCardSignature(any())).thenThrow(new IllegalArgumentException("certificate CN=Secret"));
        when(port.assembleCardSignature(any(), any())).thenThrow(new IllegalArgumentException("signature garbage"));

        assertThatThrownBy(() -> useCase.prepareByToken("tok", input()))
                .isInstanceOf(InvalidCardSignatureInputException.class)
                .hasMessageNotContaining("Secret");
        assertThatThrownBy(() -> useCase.completeByToken("tok", completion(NOW, sourceDigest())))
                .isInstanceOf(InvalidCardSignatureInputException.class)
                .hasMessageNotContaining("garbage");
    }

    @Test
    void theUniformDenialAppliesLikeTheUpload() {
        assertThatThrownBy(() -> useCase.prepareByToken("unknown", input())).isInstanceOf(PortalAccessDeniedException.class);
        assertThatThrownBy(() -> useCase.completeByToken("unknown", completion(NOW, sourceDigest())))
                .isInstanceOf(PortalAccessDeniedException.class);
        repository.updateRequest(request.closed(be.myrmacta.domain.signature.model.SignatureOutcome.CANCELLED, null, NOW));
        assertThatThrownBy(() -> useCase.prepareByToken("tok", input())).isInstanceOf(PortalAccessDeniedException.class);
        verify(port, never()).prepareCardSignature(any());
    }

    @Test
    void aProviderWithoutUploadsDoesNotSignWithACard() {
        when(port.capabilities()).thenReturn(SignatureFixtures.capabilities(false));

        assertThatThrownBy(() -> useCase.prepareByToken("tok", input())).isInstanceOf(PortalAccessDeniedException.class);
        assertThatThrownBy(() -> useCase.completeByToken("tok", completion(NOW, sourceDigest())))
                .isInstanceOf(PortalAccessDeniedException.class);
        verify(port, never()).prepareCardSignature(any());
        verify(port, never()).assembleCardSignature(any(), any());
    }

    @Test
    void signerFixtureIsExternalWithAToken() {
        assertThat(external(request.id(), 0, true, "x@example.org")).isNotNull();
    }
}
