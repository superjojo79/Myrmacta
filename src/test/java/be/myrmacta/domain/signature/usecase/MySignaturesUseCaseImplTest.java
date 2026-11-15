package be.myrmacta.domain.signature.usecase;

import static be.myrmacta.domain.signature.SignatureFixtures.NOW;
import static be.myrmacta.domain.signature.SignatureFixtures.TENANT;
import static be.myrmacta.domain.signature.SignatureFixtures.capabilities;
import static be.myrmacta.domain.signature.SignatureFixtures.internal;
import static be.myrmacta.domain.signature.SignatureFixtures.request;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import be.myrmacta.domain.document.port.out.DocumentStoragePort;
import be.myrmacta.domain.dossier.DossierAccessPolicy;
import be.myrmacta.domain.dossier.model.AccessContext;
import be.myrmacta.domain.dossier.model.Dossier;
import be.myrmacta.domain.dossier.model.DossierStatus;
import be.myrmacta.domain.dossier.port.out.DossierElementRepository;
import be.myrmacta.domain.dossier.port.out.DossierRepository;
import be.myrmacta.domain.dossier.port.out.DossierTypeAclRepository;
import be.myrmacta.domain.signature.InMemorySignatureRepository;
import be.myrmacta.domain.signature.model.SignatureMode;
import be.myrmacta.domain.signature.model.SignatureRequest;
import be.myrmacta.domain.signature.model.SignerStatus;
import be.myrmacta.domain.signature.port.in.DecideSignatureUseCase;
import be.myrmacta.domain.signature.port.in.MySignaturesUseCase;
import be.myrmacta.domain.signature.port.out.SignaturePort;
import java.time.Clock;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** « Mes signatures » : lien demandé à l'ouverture, seulement à son tour (TODO §109, D-SIGNATURE-LINK). */
class MySignaturesUseCaseImplTest {

    private final InMemorySignatureRepository repository = new InMemorySignatureRepository();
    private final DossierRepository dossiers = mock(DossierRepository.class);
    private final SignaturePort port = mock(SignaturePort.class);
    private final be.myrmacta.domain.signature.port.in.SubmitSignedDocumentUseCase submit =
            mock(be.myrmacta.domain.signature.port.in.SubmitSignedDocumentUseCase.class);
    private final be.myrmacta.domain.signature.port.in.CardSignatureUseCase card =
            mock(be.myrmacta.domain.signature.port.in.CardSignatureUseCase.class);
    private final be.myrmacta.domain.signature.port.out.SignatureAvailabilityPort availability =
            mock(be.myrmacta.domain.signature.port.out.SignatureAvailabilityPort.class);
    private MySignaturesUseCaseImpl useCase;
    private SignatureRequest request;
    private final AccessContext.User bob = AccessContext.user("bob", Set.of("TENANT_ADMIN"));

    @BeforeEach
    void setUp() {
        request = request(UUID.randomUUID(), SignatureMode.SEQUENTIAL).withProviderReference("yousign:1")
                .withSimulation(false, "ELECTRONIC");
        when(dossiers.findById(request.dossierId())).thenReturn(Optional.of(new Dossier(request.dossierId(), TENANT,
                "DOS-1", "SINISTRE", "Dossier", DossierStatus.OUVERT, Map.of(), null, 0L, "test", NOW, null, null,
                null, null, null)));
        useCase = new MySignaturesUseCaseImpl(TENANT, repository, dossiers, new DossierAccessPolicy(mock(DossierTypeAclRepository.class)),
                mock(DossierElementRepository.class), mock(DocumentStoragePort.class), mock(DecideSignatureUseCase.class),
                port, Clock.fixed(NOW, ZoneOffset.UTC),
                mock(be.myrmacta.domain.signature.port.out.SignedUploadRepository.class),
                submit, card, availability);
        when(port.capabilities()).thenReturn(capabilities(true));
    }

    @Test
    void myTurnReturnsTheProviderLink() {
        repository.insert(request, List.of(internal(request.id(), 0, true, "bob").withStatus(SignerStatus.PENDING)));
        when(port.signingUrl("yousign:1", 0)).thenReturn(Optional.of("https://yousign.app/signatures/abc"));

        List<MySignaturesUseCase.Item> items = useCase.list(bob);

        assertThat(items).hasSize(1);
        assertThat(items.getFirst().myTurn()).isTrue();
        assertThat(items.getFirst().hosted()).isTrue();
        assertThat(items.getFirst().signingUrl()).isNull();
        verify(port, never()).signingUrl(any(), anyInt());
        assertThat(useCase.signingUrl(bob, items.getFirst().signerId())).isEqualTo("https://yousign.app/signatures/abc");
    }

    @Test
    void waitingSignerDoesNotAskForALink() {
        repository.insert(request, List.of(internal(request.id(), 1, true, "bob").withStatus(SignerStatus.WAITING)));

        List<MySignaturesUseCase.Item> items = useCase.list(bob);

        assertThat(items.getFirst().myTurn()).isFalse();
        assertThat(items.getFirst().hosted()).isTrue();
        assertThat(items.getFirst().signingUrl()).isNull();
        verify(port, never()).signingUrl(any(), anyInt());
    }

    @Test
    void listingDoesNotCallTheProviderOncePerRequest() {
        for (int index = 0; index < 3; index++) {
            SignatureRequest pending = request(UUID.randomUUID(), SignatureMode.PARALLEL, "proc-list-" + index)
                    .withProviderReference("yousign:" + index)
                    .withSimulation(false, "ELECTRONIC");
            when(dossiers.findById(pending.dossierId())).thenReturn(Optional.of(new Dossier(pending.dossierId(), TENANT,
                    "DOS-" + index, "SINISTRE", "Dossier", DossierStatus.OUVERT, Map.of(), null, 0L, "test", NOW, null,
                    null, null, null, null)));
            repository.insert(pending, List.of(internal(pending.id(), 0, true, "bob").withStatus(SignerStatus.PENDING)));
        }

        assertThat(useCase.list(bob)).hasSize(3);
        verify(port, never()).signingUrl(any(), anyInt());
    }

    @Test
    void providerFailureLeavesTheLinkEmpty() {
        repository.insert(request, List.of(internal(request.id(), 0, true, "bob").withStatus(SignerStatus.PENDING)));
        when(port.signingUrl(any(), anyInt())).thenThrow(new IllegalStateException("https://evil.example/secret-token"));

        UUID signerId = useCase.list(bob).getFirst().signerId();

        assertThat(useCase.signingUrl(bob, signerId)).isNull();
    }

    @Test
    void signedUploadProviderFlagsItsItemsAndDelegatesTheUploadForTheSignerOnHisTurn() {
        when(port.capabilities()).thenReturn(be.myrmacta.domain.signature.SignatureFixtures.eidCapabilities());
        repository.insert(request, List.of(internal(request.id(), 0, true, "bob").withStatus(SignerStatus.PENDING)));
        when(availability.signedUploadEnabled()).thenReturn(true);
        MySignaturesUseCase.Item item = useCase.list(bob).getFirst();
        assertThat(item.signedUpload()).isTrue();
        when(availability.signedUploadEnabled()).thenReturn(false);
        assertThat(useCase.list(bob).getFirst().signedUpload()).isFalse();
        when(availability.cardBridges()).thenReturn(List.of("web-eid"));
        assertThat(useCase.list(bob).getFirst().cardBridges()).containsExactly("web-eid");
        assertThat(item.hosted()).isFalse();
        var upload = be.myrmacta.domain.signature.port.in.SubmitSignedDocumentUseCase.Upload.of("%PDF-".getBytes());

        useCase.submitSigned(bob, item.signerId(), upload);

        verify(submit).submitInternal("bob", item.signerId(), upload);
    }

    @Test
    void cardSignatureStepsDelegateForTheSignerOnHisTurnAndRefuseOthers() {
        repository.insert(request, List.of(internal(request.id(), 0, true, "bob").withStatus(SignerStatus.PENDING)));
        UUID signerId = repository.findSigners(request.id()).getFirst().id();
        var input = new be.myrmacta.domain.signature.port.in.CardSignatureUseCase.CardInput(new byte[] {1}, List.of());
        var completion = new be.myrmacta.domain.signature.port.in.CardSignatureUseCase.Completion(
                new byte[] {1}, List.of(), NOW, "d", new byte[] {2});

        useCase.prepareCardSignature(bob, signerId, input);
        useCase.completeCardSignature(bob, signerId, completion);

        verify(card).prepareInternal("bob", signerId, input);
        verify(card).completeInternal("bob", signerId, completion);
        AccessContext.User eve = AccessContext.user("eve", Set.of("TENANT_ADMIN"));
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> useCase.prepareCardSignature(eve, signerId, input))
                .isInstanceOf(be.myrmacta.domain.workflow.portal.model.PortalAccessDeniedException.class);
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> useCase.completeCardSignature(eve, signerId, completion))
                .isInstanceOf(be.myrmacta.domain.workflow.portal.model.PortalAccessDeniedException.class);
    }

    @Test
    void anotherUserOrAWaitingSignerCannotUpload() {
        repository.insert(request, List.of(internal(request.id(), 1, true, "bob").withStatus(SignerStatus.WAITING)));
        UUID signerId = repository.findSigners(request.id()).getFirst().id();
        var upload = be.myrmacta.domain.signature.port.in.SubmitSignedDocumentUseCase.Upload.of("%PDF-".getBytes());

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> useCase.submitSigned(bob, signerId, upload))
                .isInstanceOf(be.myrmacta.domain.workflow.portal.model.PortalAccessDeniedException.class);
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> useCase.submitSigned(
                        AccessContext.user("eve", Set.of("TENANT_ADMIN")), signerId, upload))
                .isInstanceOf(be.myrmacta.domain.workflow.portal.model.PortalAccessDeniedException.class);
        verify(submit, never()).submitInternal(any(), any(), any());
    }
}
