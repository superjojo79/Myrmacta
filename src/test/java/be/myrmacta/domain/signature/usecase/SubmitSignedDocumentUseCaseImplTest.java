package be.myrmacta.domain.signature.usecase;

import static be.myrmacta.domain.signature.SignatureFixtures.NOW;
import static be.myrmacta.domain.signature.SignatureFixtures.TENANT;
import static be.myrmacta.domain.signature.SignatureFixtures.external;
import static be.myrmacta.domain.signature.SignatureFixtures.internal;
import static be.myrmacta.domain.signature.SignatureFixtures.request;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import be.myrmacta.domain.document.port.out.DocumentStoragePort;
import be.myrmacta.domain.dossier.model.Dossier;
import be.myrmacta.domain.dossier.model.DossierStatus;
import be.myrmacta.domain.dossier.port.in.AddDossierElementUseCase;
import be.myrmacta.domain.dossier.port.out.DossierElementRepository;
import be.myrmacta.domain.dossier.port.out.DossierRepository;
import be.myrmacta.domain.dossier.port.out.DossierWorkflowLinkRepository;
import be.myrmacta.domain.notification.port.in.SendNotificationUseCase;
import be.myrmacta.domain.signature.InMemorySignatureRepository;
import be.myrmacta.domain.signature.InMemorySignedUploadRepository;
import be.myrmacta.domain.signature.SignatureAccessResolver;
import be.myrmacta.domain.signature.SignatureFixtures;
import be.myrmacta.domain.signature.SignatureNotifier;
import be.myrmacta.domain.signature.SignatureOutcomeHandler;
import be.myrmacta.domain.signature.SignatureWorkingDocuments;
import be.myrmacta.domain.signature.model.SignatureMode;
import be.myrmacta.domain.signature.model.SignatureProgression;
import be.myrmacta.domain.signature.model.SignatureRequest;
import be.myrmacta.domain.signature.model.SignatureStatus;
import be.myrmacta.domain.signature.model.Signer;
import be.myrmacta.domain.signature.model.SignerStatus;
import be.myrmacta.domain.signature.model.SignedUpload;
import be.myrmacta.domain.signature.model.SignedUploadEvidence;
import be.myrmacta.domain.signature.model.SignedUploadRejectedException;
import be.myrmacta.domain.signature.model.SignedUploadRejection;
import be.myrmacta.domain.signature.model.SignedUploadTooLargeException;
import be.myrmacta.domain.signature.model.SignedUploadVerdict;
import be.myrmacta.domain.signature.port.in.DecideSignatureUseCase;
import be.myrmacta.domain.signature.port.in.SubmitSignedDocumentUseCase.Upload;
import be.myrmacta.domain.signature.port.out.SignatureAvailabilityPort;
import be.myrmacta.domain.signature.port.out.SignatureMetricsPort;
import be.myrmacta.domain.signature.port.out.SignatureOutcomeListener;
import be.myrmacta.domain.signature.port.out.SignaturePort;
import be.myrmacta.domain.workflow.port.out.WorkflowParticipantPort;
import be.myrmacta.domain.workflow.portal.model.PortalAccessDeniedException;
import be.myrmacta.domain.workflow.portal.port.out.PortalTokenPort;
import java.time.Clock;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Supplier;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/**
 * TODO §138 (D-EID-UPLOAD, D-EID-CHAIN) — téléversement d'un PDF signé : accès uniforme, taille, vérification
 * hors transaction, version dépassée, certificat déjà utilisé, progression séquentielle et parallèle.
 */
class SubmitSignedDocumentUseCaseImplTest {

    private static final byte[] SOURCE = "%PDF-source".getBytes();
    private static final byte[] SIGNED_1 = "%PDF-signed-1".getBytes();
    private static final byte[] SIGNED_2 = "%PDF-signed-2".getBytes();
    private static final long MAX = 1000;

    private final InMemorySignatureRepository repository = new InMemorySignatureRepository();
    private final InMemorySignedUploadRepository uploads = new InMemorySignedUploadRepository(repository);
    private final PortalTokenPort tokenPort = mock(PortalTokenPort.class);
    private final DossierRepository dossiers = mock(DossierRepository.class);
    private final SignaturePort port = mock(SignaturePort.class);
    private final DocumentStoragePort storage = mock(DocumentStoragePort.class);
    private final Map<String, byte[]> blobs = new HashMap<>();
    private final AddDossierElementUseCase addElement = mock(AddDossierElementUseCase.class);
    private final SignatureOutcomeListener listener = mock(SignatureOutcomeListener.class);
    private final SignatureAvailabilityPort availability = mock(SignatureAvailabilityPort.class);
    private final Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
    private SubmitSignedDocumentUseCaseImpl useCase;
    private SignatureRequest request;
    private int stored;
    private boolean inTransaction;

    @BeforeEach
    void setUp() {
        when(tokenPort.hash(any())).thenAnswer(inv -> "hash:" + inv.getArgument(0));
        Dossier dossier = mock(Dossier.class);
        when(dossier.status()).thenReturn(DossierStatus.OUVERT);
        when(dossiers.findById(any())).thenReturn(Optional.of(dossier));
        when(port.capabilities()).thenReturn(SignatureFixtures.eidCapabilities());
        when(availability.maxSignedUploadBytes()).thenReturn(MAX);
        when(availability.signedUploadEnabled()).thenReturn(true);
        when(storage.store(eq(TENANT), anyString(), any())).thenAnswer(inv -> {
            String ref = "blob-" + (++stored);
            blobs.put(ref, inv.getArgument(2));
            return ref;
        });
        when(storage.retrieve(eq(TENANT), anyString())).thenAnswer(inv -> blobs.get(inv.<String>getArgument(1)));
        SignatureNotifier notifier = new SignatureNotifier(TENANT, mock(DossierWorkflowLinkRepository.class),
                mock(WorkflowParticipantPort.class), mock(SendNotificationUseCase.class));
        SignatureAccessResolver resolver = new SignatureAccessResolver(repository, tokenPort, dossiers, clock);
        SignatureOutcomeHandler handler = new SignatureOutcomeHandler(TENANT, repository, port, storage,
                mock(DossierElementRepository.class), addElement, notifier, mock(SignatureMetricsPort.class), listener,
                clock, java.util.Locale.FRENCH);
        useCase = new SubmitSignedDocumentUseCaseImpl(TENANT, repository, uploads, resolver, handler, port,
                availability, storage, new SignatureTransaction0(), clock);
        blobs.put("source", SOURCE);
    }

    /** Transaction « en ligne » qui note quand elle est ouverte : la vérification doit se faire en dehors. */
    private final class SignatureTransaction0 implements be.myrmacta.domain.signature.port.out.SignatureTransaction {
        @Override
        public <T> T execute(Supplier<T> action) {
            inTransaction = true;
            try {
                return action.get();
            } finally {
                inTransaction = false;
            }
        }
    }

    private List<Signer> seed(SignatureMode mode, Signer... drafts) {
        request = new SignatureRequest(UUID.randomUUID(), TENANT, UUID.randomUUID(), "proc-" + System.nanoTime(),
                "signStep", UUID.randomUUID(), "source", SignatureWorkingDocuments.digestOf(SOURCE), "eid", "eid:x", mode,
                SignatureStatus.PENDING, null, false, "QES", NOW.plusSeconds(86_400), null, 0, NOW, null, "test", 0L);
        List<Signer> started = SignatureProgression.start(mode, List.of(drafts).stream()
                .map(d -> new Signer(d.id(), d.tenantId(), request.id(), d.position(), d.kind(), d.required(),
                        d.userId(), d.displayName(), d.email(), d.email() == null ? null : "hash:tok-" + d.position(),
                        d.status(), null, 0, null, null, 0L))
                .toList()).signers();
        repository.insert(request, started);
        return started;
    }

    private Signer ext(int pos, boolean required) {
        return external(UUID.randomUUID(), pos, required, "s" + pos + "@example.org");
    }

    private static SignedUploadEvidence evidence(String fingerprint) {
        return new SignedUploadEvidence("Alice Martin", "CN=Citizen CA", "01", fingerprint, NOW.minusSeconds(5), NOW,
                "PAdES-BASELINE-B", "RSA_SHA256", true, true, "OCSP", NOW);
    }

    private void accept(String fingerprint) {
        when(port.verifySignedUpload(any())).thenAnswer(inv -> {
            assertThat(inTransaction).as("verification outside of any transaction").isFalse();
            return SignedUploadVerdict.accepted(evidence(fingerprint));
        });
    }

    private List<SignerStatus> statuses() {
        return repository.findSigners(request.id()).stream().map(Signer::status).toList();
    }

    @Test
    void acceptedUploadStoresTheDocumentRecordsTheEvidenceAndSignsTheSigner() {
        seed(SignatureMode.PARALLEL, ext(0, true), ext(1, false));
        accept("f1");

        DecideSignatureUseCase.Result result = useCase.submitByToken("tok-0", Upload.of(SIGNED_1));

        assertThat(result.signerStatus()).isEqualTo(SignerStatus.SIGNED);
        assertThat(result.requestStatus()).isEqualTo(SignatureStatus.SIGNED);
        assertThat(uploads.uploads).hasSize(1);
        SignedUpload saved = uploads.uploads.getFirst();
        assertThat(saved.sequence()).isEqualTo(1);
        assertThat(saved.digest()).isEqualTo(SignatureWorkingDocuments.digestOf(SIGNED_1));
        assertThat(blobs.get(saved.storageRef())).isEqualTo(SIGNED_1);
        assertThat(saved.evidence().certificateFingerprint()).isEqualTo("f1");
        assertThat(statuses()).containsExactly(SignerStatus.SIGNED, SignerStatus.REVOKED);
        // le document final et la preuve sont produits par le répartiteur, pas ici
        verify(addElement, never()).add(any(), any());
        verify(listener, never()).outcomeRecorded(any());
    }

    @Test
    void theVerifierReceivesTheCurrentDocumentTheUploadAndTheCertificatesAlreadyUsed() {
        seed(SignatureMode.SEQUENTIAL, ext(0, true), ext(1, true));
        accept("f1");
        useCase.submitByToken("tok-0", Upload.of(SIGNED_1));
        accept("f2");

        useCase.submitByToken("tok-1", Upload.of(SIGNED_2));

        ArgumentCaptor<SignaturePort.SignedUploadCheck> check =
                ArgumentCaptor.forClass(SignaturePort.SignedUploadCheck.class);
        verify(port, org.mockito.Mockito.times(2)).verifySignedUpload(check.capture());
        assertThat(check.getAllValues().get(0).baseDocument()).isEqualTo(SOURCE);
        assertThat(check.getAllValues().get(0).usedCertificateFingerprints()).isEmpty();
        // D-EID-CHAIN : le second signe le PDF du premier
        assertThat(check.getAllValues().get(1).baseDocument()).isEqualTo(SIGNED_1);
        assertThat(check.getAllValues().get(1).signedDocument()).isEqualTo(SIGNED_2);
        assertThat(check.getAllValues().get(1).usedCertificateFingerprints()).containsExactly("f1");
        assertThat(uploads.uploads).extracting(SignedUpload::sequence).containsExactly(1, 2);
        assertThat(repository.findById(request.id()).orElseThrow().status()).isEqualTo(SignatureStatus.SIGNED);
    }

    @Test
    void sequentialModeActivatesTheNextSignerAfterAnUpload() {
        seed(SignatureMode.SEQUENTIAL, ext(0, true), ext(1, true));
        accept("f1");

        DecideSignatureUseCase.Result result = useCase.submitByToken("tok-0", Upload.of(SIGNED_1));

        assertThat(result.requestStatus()).isEqualTo(SignatureStatus.PENDING);
        assertThat(statuses()).containsExactly(SignerStatus.SIGNED, SignerStatus.PENDING);
    }

    @Test
    void aSignerWhoseTurnHasNotComeIsRefusedWithTheUniformDenial() {
        seed(SignatureMode.SEQUENTIAL, ext(0, true), ext(1, true));
        accept("f1");

        assertThatThrownBy(() -> useCase.submitByToken("tok-1", Upload.of(SIGNED_1)))
                .isInstanceOf(PortalAccessDeniedException.class);
        verify(port, never()).verifySignedUpload(any());
        assertThat(uploads.uploads).isEmpty();
    }

    @Test
    void optionalSignersMayUploadWithoutClosingTheRequest() {
        seed(SignatureMode.PARALLEL, ext(0, true), ext(1, false));
        accept("f1");

        DecideSignatureUseCase.Result result = useCase.submitByToken("tok-1", Upload.of(SIGNED_1));

        assertThat(result.signerStatus()).isEqualTo(SignerStatus.SIGNED);
        assertThat(result.requestStatus()).isEqualTo(SignatureStatus.PENDING);
        assertThat(statuses()).containsExactly(SignerStatus.PENDING, SignerStatus.SIGNED);
    }

    @Test
    void everyRejectionOfTheVerifierSurfacesWithItsReasonAndChangesNothing() {
        seed(SignatureMode.PARALLEL, ext(0, true));
        for (SignedUploadRejection rejection : SignedUploadRejection.values()) {
            when(port.verifySignedUpload(any())).thenReturn(SignedUploadVerdict.rejected(rejection));

            assertThatThrownBy(() -> useCase.submitByToken("tok-0", Upload.of(SIGNED_1)))
                    .isInstanceOfSatisfying(SignedUploadRejectedException.class,
                            ex -> assertThat(ex.rejection()).isEqualTo(rejection));
        }
        assertThat(uploads.uploads).isEmpty();
        assertThat(stored).isZero();
        assertThat(statuses()).containsExactly(SignerStatus.PENDING);
    }

    @Test
    void anEmptyFileIsNotAPdfAndNeverReachesTheVerifier() {
        seed(SignatureMode.PARALLEL, ext(0, true));

        assertThatThrownBy(() -> useCase.submitByToken("tok-0", Upload.of(new byte[0])))
                .isInstanceOfSatisfying(SignedUploadRejectedException.class,
                        ex -> assertThat(ex.rejection()).isEqualTo(SignedUploadRejection.NOT_A_PDF));
        verify(port, never()).verifySignedUpload(any());
    }

    @Test
    void anOversizedFileIsRefusedBeforeItIsReadOrVerified() {
        seed(SignatureMode.PARALLEL, ext(0, true));
        Upload huge = new Upload() {
            @Override
            public long size() {
                return MAX + 1;
            }

            @Override
            public byte[] read() {
                throw new AssertionError("the content must not be read");
            }
        };

        assertThatThrownBy(() -> useCase.submitByToken("tok-0", huge)).isInstanceOf(SignedUploadTooLargeException.class);
        verify(port, never()).verifySignedUpload(any());
    }

    @Test
    void accessIsResolvedBeforeTheFileIsRead() {
        seed(SignatureMode.PARALLEL, ext(0, true));
        Upload unreadable = new Upload() {
            @Override
            public long size() {
                return 10;
            }

            @Override
            public byte[] read() {
                throw new AssertionError("the content must not be read");
            }
        };

        assertThatThrownBy(() -> useCase.submitByToken("unknown", unreadable))
                .isInstanceOf(PortalAccessDeniedException.class);
        repository.updateRequest(request.closed(be.myrmacta.domain.signature.model.SignatureOutcome.CANCELLED, null, NOW));
        assertThatThrownBy(() -> useCase.submitByToken("tok-0", unreadable))
                .isInstanceOf(PortalAccessDeniedException.class);
    }

    @Test
    void anExpiredRequestIsRefusedWithTheUniformDenial() {
        seed(SignatureMode.PARALLEL, ext(0, true));
        SignatureRequest later = request;
        SubmitSignedDocumentUseCaseImpl lateUseCase = new SubmitSignedDocumentUseCaseImpl(TENANT, repository, uploads,
                new SignatureAccessResolver(repository, tokenPort, dossiers,
                        Clock.fixed(later.deadlineAt().plusSeconds(1), ZoneOffset.UTC)),
                mock(SignatureOutcomeHandler.class), port, availability, storage, new SignatureTransaction0(),
                Clock.fixed(later.deadlineAt().plusSeconds(1), ZoneOffset.UTC));

        assertThatThrownBy(() -> lateUseCase.submitByToken("tok-0", Upload.of(SIGNED_1)))
                .isInstanceOf(PortalAccessDeniedException.class);
    }

    @Test
    void aProviderWithoutUploadsRefusesWithTheUniformDenial() {
        seed(SignatureMode.PARALLEL, ext(0, true));
        when(port.capabilities()).thenReturn(SignatureFixtures.capabilities(false));

        assertThatThrownBy(() -> useCase.submitByToken("tok-0", Upload.of(SIGNED_1)))
                .isInstanceOf(PortalAccessDeniedException.class);
    }

    @Test
    void aDocumentThatChangedDuringTheVerificationIsStaleAndLeavesNoBlob() {
        seed(SignatureMode.PARALLEL, ext(0, true), ext(1, true));
        // pendant la vérification de l'un, l'autre téléverse et change le document courant
        when(port.verifySignedUpload(any())).thenAnswer(inv -> {
            uploads.save(new SignedUpload(UUID.randomUUID(), TENANT, request.id(),
                    repository.findSigners(request.id()).get(1).id(), 1, "other", SignatureWorkingDocuments.digestOf(SIGNED_2),
                    evidence("f-other"), NOW));
            return SignedUploadVerdict.accepted(evidence("f1"));
        });

        assertThatThrownBy(() -> useCase.submitByToken("tok-0", Upload.of(SIGNED_1)))
                .isInstanceOfSatisfying(SignedUploadRejectedException.class,
                        ex -> assertThat(ex.rejection()).isEqualTo(SignedUploadRejection.STALE_DOCUMENT));
        assertThat(uploads.uploads).hasSize(1);
        assertThat(stored).isZero();
        assertThat(statuses()).containsExactly(SignerStatus.PENDING, SignerStatus.PENDING);
    }

    @Test
    void aCertificateUsedMeanwhileIsADuplicateSignerEvenWhenTheDocumentIsUnchanged() {
        seed(SignatureMode.PARALLEL, ext(0, true), ext(1, true));
        when(port.verifySignedUpload(any())).thenAnswer(inv -> {
            // un certificat apparaît sur la demande sans changer le document courant (même empreinte)
            uploads.save(new SignedUpload(UUID.randomUUID(), TENANT, request.id(),
                    repository.findSigners(request.id()).get(1).id(), 1, "source", SignatureWorkingDocuments.digestOf(SOURCE),
                    evidence("f1"), NOW));
            return SignedUploadVerdict.accepted(evidence("f1"));
        });

        assertThatThrownBy(() -> useCase.submitByToken("tok-0", Upload.of(SIGNED_1)))
                .isInstanceOfSatisfying(SignedUploadRejectedException.class,
                        ex -> assertThat(ex.rejection()).isEqualTo(SignedUploadRejection.DUPLICATE_SIGNER));
        assertThat(stored).isZero();
    }

    @Test
    void aFailureAfterTheStorageDeletesTheBlob() {
        seed(SignatureMode.PARALLEL, ext(0, true));
        accept("f1");
        // les suites de la fermeture échouent après le stockage : la transaction est annulée, le blob ne sera jamais référencé
        SignatureOutcomeHandler failing = mock(SignatureOutcomeHandler.class);
        org.mockito.Mockito.doThrow(new IllegalStateException("db down")).when(failing).afterClosed(any(), any());
        SubmitSignedDocumentUseCaseImpl failingUseCase = new SubmitSignedDocumentUseCaseImpl(TENANT, repository,
                uploads, new SignatureAccessResolver(repository, tokenPort, dossiers, clock), failing, port, availability,
                storage, new SignatureTransaction0(), clock);

        assertThatThrownBy(() -> failingUseCase.submitByToken("tok-0", Upload.of(SIGNED_1)))
                .isInstanceOf(IllegalStateException.class);

        verify(storage).delete(TENANT, "blob-1");
    }

    @Test
    void internalSignerUploadsByIdentityAndAnotherUserIsRefused() {
        List<Signer> signers = seed(SignatureMode.PARALLEL, internal(UUID.randomUUID(), 0, true, "bob"));
        accept("f1");

        assertThatThrownBy(() -> useCase.submitInternal("mallory", signers.getFirst().id(), Upload.of(SIGNED_1)))
                .isInstanceOf(PortalAccessDeniedException.class);
        assertThat(uploads.uploads).isEmpty();

        DecideSignatureUseCase.Result result = useCase.submitInternal("bob", signers.getFirst().id(), Upload.of(SIGNED_1));

        assertThat(result.requestStatus()).isEqualTo(SignatureStatus.SIGNED);
        assertThat(uploads.uploads).hasSize(1);
    }

    @Test
    void whenTheUploadIsDisabledTheAccessDenialStillComesFirstAndNothingIsReadNorVerified() {
        List<Signer> signers = seed(SignatureMode.PARALLEL, ext(0, true), internal(UUID.randomUUID(), 1, true, "bob"));
        when(availability.signedUploadEnabled()).thenReturn(false);
        Upload unreadable = new Upload() {
            @Override
            public long size() {
                return 10;
            }

            @Override
            public byte[] read() {
                throw new AssertionError("the content must not be read");
            }
        };

        // refus d'accès d'abord (jeton inconnu, autre utilisateur), puis seulement le refus du parcours
        assertThatThrownBy(() -> useCase.submitByToken("unknown", unreadable)).isInstanceOf(PortalAccessDeniedException.class);
        assertThatThrownBy(() -> useCase.submitInternal("mallory", signers.get(1).id(), unreadable))
                .isInstanceOf(PortalAccessDeniedException.class);
        assertThatThrownBy(() -> useCase.submitByToken("tok-0", unreadable))
                .isInstanceOf(be.myrmacta.domain.signature.model.SignedUploadDisabledException.class);
        assertThatThrownBy(() -> useCase.submitInternal("bob", signers.get(1).id(), unreadable))
                .isInstanceOf(be.myrmacta.domain.signature.model.SignedUploadDisabledException.class);
        verify(port, never()).verifySignedUpload(any());
        assertThat(uploads.uploads).isEmpty();
    }

    @Test
    void theInternalPathOfTheCardSignatureIgnoresTheSwitchAndStillVerifiesAndRecords() {
        List<Signer> signers = seed(SignatureMode.PARALLEL, ext(0, true), internal(UUID.randomUUID(), 1, true, "bob"));
        when(availability.signedUploadEnabled()).thenReturn(false);
        accept("f1");

        DecideSignatureUseCase.Result result = useCase.submitAssembledByToken("tok-0", Upload.of(SIGNED_1));

        assertThat(result.signerStatus()).isEqualTo(SignerStatus.SIGNED);
        accept("f2");
        useCase.submitAssembledInternal("bob", signers.get(1).id(), Upload.of(SIGNED_2));
        verify(port, org.mockito.Mockito.times(2)).verifySignedUpload(any());
        assertThat(uploads.uploads).hasSize(2);
    }
}
