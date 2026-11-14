package be.myrmacta.domain.signature.usecase;

import static be.myrmacta.domain.signature.SignatureFixtures.NOW;
import static be.myrmacta.domain.signature.SignatureFixtures.TENANT;
import static be.myrmacta.domain.signature.SignatureFixtures.capabilities;
import static be.myrmacta.domain.signature.SignatureFixtures.external;
import static be.myrmacta.domain.signature.SignatureFixtures.request;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import be.myrmacta.domain.document.port.out.DocumentStoragePort;
import be.myrmacta.domain.dossier.model.Dossier;
import be.myrmacta.domain.dossier.model.DossierStatus;
import be.myrmacta.domain.dossier.port.out.DossierElementRepository;
import be.myrmacta.domain.dossier.port.out.DossierRepository;
import be.myrmacta.domain.signature.InMemorySignatureRepository;
import be.myrmacta.domain.signature.SignatureFixtures;
import be.myrmacta.domain.signature.SignatureAccessResolver;
import be.myrmacta.domain.signature.model.SignatureMode;
import be.myrmacta.domain.signature.model.SignatureRequest;
import be.myrmacta.domain.signature.model.SignerStatus;
import be.myrmacta.domain.signature.port.in.GetSignatureUseCase;
import be.myrmacta.domain.signature.port.out.SignaturePort;
import be.myrmacta.domain.workflow.portal.port.out.PortalTokenPort;
import java.time.Clock;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Contexte et document du portail : lien demandé à l'ouverture (TODO §109, D-SIGNATURE-LINK). */
class GetSignatureUseCaseImplTest {

    private final InMemorySignatureRepository repository = new InMemorySignatureRepository();
    private final PortalTokenPort tokenPort = mock(PortalTokenPort.class);
    private final DossierRepository dossiers = mock(DossierRepository.class);
    private final SignaturePort port = mock(SignaturePort.class);
    private final be.myrmacta.domain.signature.port.out.SignatureAvailabilityPort availability =
            mock(be.myrmacta.domain.signature.port.out.SignatureAvailabilityPort.class);
    private final DocumentStoragePort storage = mock(DocumentStoragePort.class);
    private final DossierElementRepository elements = mock(DossierElementRepository.class);
    private final be.myrmacta.domain.signature.InMemorySignedUploadRepository uploads =
            new be.myrmacta.domain.signature.InMemorySignedUploadRepository(repository);
    private GetSignatureUseCaseImpl useCase;
    private SignatureRequest request;

    @BeforeEach
    void setUp() {
        when(tokenPort.hash(any())).thenAnswer(inv -> "hash:" + inv.getArgument(0));
        request = request(UUID.randomUUID(), SignatureMode.PARALLEL, "proc-1",
                        be.myrmacta.domain.signature.SignatureWorkingDocuments.digestOf(SOURCE))
                .withProviderReference("yousign:1")
                .withSimulation(false, "ELECTRONIC");
        repository.insert(request, List.of(external(request.id(), 0, true, "a@example.org")
                .withStatus(SignerStatus.PENDING).withToken("hash:tok")));
        when(dossiers.findById(request.dossierId())).thenReturn(Optional.of(new Dossier(request.dossierId(), TENANT,
                "DOS-9", "SINISTRE", "Dossier", DossierStatus.OUVERT, Map.of(), null, 0L, "test", NOW, null, null,
                null, null, null)));
        SignatureAccessResolver resolver = new SignatureAccessResolver(repository, tokenPort, dossiers,
                Clock.fixed(NOW, ZoneOffset.UTC));
        when(elements.findByIdAndTenantId(request.sourceElementId(), TENANT)).thenReturn(Optional.of(
                new be.myrmacta.domain.dossier.model.DossierElement(request.sourceElementId(), request.dossierId(), TENANT,
                        be.myrmacta.domain.dossier.model.ElementType.PRODUCED_DOC, "Contrat", "storage/doc.pdf",
                        Map.of(), "system", NOW, null)));
        when(storage.retrieve(TENANT, "storage/doc.pdf")).thenReturn(SOURCE);
        when(storage.retrieve(TENANT, "work-1")).thenReturn(SIGNED);
        useCase = new GetSignatureUseCaseImpl(TENANT, resolver, elements, storage, port, uploads, availability);
    }

    @Test
    void hostedContextReturnsTheSigningLinkForThisSigner() {
        when(port.capabilities()).thenReturn(capabilities(true));
        when(port.signingUrl("yousign:1", 0)).thenReturn(Optional.of("https://yousign.app/signatures/abc"));

        GetSignatureUseCase.Context context = useCase.context("tok");

        assertThat(context.hosted()).isTrue();
        assertThat(context.signingUrl()).isEqualTo("https://yousign.app/signatures/abc");
        assertThat(context.dossierReference()).isEqualTo("DOS-9");
        assertThat(context.simulated()).isFalse();
    }

    @Test
    void providerFailureLeavesTheLinkEmptyWithoutEchoingIt() {
        when(port.capabilities()).thenReturn(capabilities(true));
        when(port.signingUrl(any(), anyInt())).thenThrow(new IllegalStateException("https://evil.example/secret-token"));

        GetSignatureUseCase.Context context = useCase.context("tok");

        assertThat(context.hosted()).isTrue();
        assertThat(context.signingUrl()).isNull();
        assertThat(context.toString()).doesNotContain("secret-token");
    }

    @Test
    void stubContextHasNoSigningLink() {
        when(port.capabilities()).thenReturn(capabilities(false));

        GetSignatureUseCase.Context context = useCase.context("tok");

        assertThat(context.hosted()).isFalse();
        assertThat(context.signingUrl()).isNull();
        verify(port, never()).signingUrl(any(), anyInt());
    }

    private static final byte[] SOURCE = "%PDF-source".getBytes();
    private static final byte[] SIGNED = "%PDF-source-signed".getBytes();

    private void upload(String ref, byte[] content, int sequence) {
        uploads.save(new be.myrmacta.domain.signature.model.SignedUpload(UUID.randomUUID(), TENANT, request.id(),
                UUID.randomUUID(), sequence, ref, be.myrmacta.domain.signature.SignatureWorkingDocuments.digestOf(content),
                new be.myrmacta.domain.signature.model.SignedUploadEvidence("A", "CA", "1", "fp" + sequence, null, NOW,
                        "PAdES", "RSA", true, true, "OCSP", NOW),
                NOW));
    }

    @Test
    void signedUploadProviderContextAnnouncesTheUpload() {
        when(port.capabilities()).thenReturn(SignatureFixtures.eidCapabilities());

        GetSignatureUseCase.Context context = useCase.context("tok");

        assertThat(context.signedUpload()).isTrue();
        assertThat(context.hosted()).isFalse();
    }

    @Test
    void contextListsTheOperatorCardBridgesOnlyForAProviderThatTakesUploads() {
        when(availability.cardBridges()).thenReturn(List.of("beidconnect", "web-eid"));

        when(port.capabilities()).thenReturn(SignatureFixtures.eidCapabilities());
        assertThat(useCase.context("tok").cardBridges()).containsExactly("beidconnect", "web-eid");

        when(port.capabilities()).thenReturn(capabilities(true));
        assertThat(useCase.context("tok").cardBridges()).isEmpty();

        when(port.capabilities()).thenReturn(SignatureFixtures.eidCapabilities());
        when(availability.cardBridges()).thenReturn(List.of());
        assertThat(useCase.context("tok").cardBridges()).isEmpty();
    }

    @Test
    void theDocumentIsTheSourceThenTheLastAcceptedUpload() {
        when(port.capabilities()).thenReturn(SignatureFixtures.eidCapabilities());
        assertThat(useCase.document("tok").content()).isEqualTo(SOURCE);

        upload("work-1", SIGNED, 1);

        GetSignatureUseCase.DocumentContent current = useCase.document("tok");
        assertThat(current.content()).isEqualTo(SIGNED);
        assertThat(current.contentType()).isEqualTo("application/pdf");
    }

    @Test
    void aTamperedWorkingDocumentClosesTheAccess() {
        when(port.capabilities()).thenReturn(SignatureFixtures.eidCapabilities());
        upload("work-1", "%PDF-other".getBytes(), 1);

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> useCase.document("tok"))
                .isInstanceOf(be.myrmacta.domain.workflow.portal.model.PortalAccessDeniedException.class);
    }

    @Test
    void otherProvidersKeepServingTheSourceWithoutReadingUploads() {
        when(port.capabilities()).thenReturn(capabilities(true));
        upload("work-1", SIGNED, 1);

        assertThat(useCase.document("tok").content()).isEqualTo(SOURCE);
    }
}
