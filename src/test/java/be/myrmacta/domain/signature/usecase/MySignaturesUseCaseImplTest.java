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
                port, Clock.fixed(NOW, ZoneOffset.UTC));
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
        assertThat(items.getFirst().signingUrl()).isEqualTo("https://yousign.app/signatures/abc");
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
    void providerFailureLeavesTheLinkEmpty() {
        repository.insert(request, List.of(internal(request.id(), 0, true, "bob").withStatus(SignerStatus.PENDING)));
        when(port.signingUrl(any(), anyInt())).thenThrow(new IllegalStateException("https://evil.example/secret-token"));

        MySignaturesUseCase.Item item = useCase.list(bob).getFirst();

        assertThat(item.hosted()).isTrue();
        assertThat(item.signingUrl()).isNull();
        assertThat(item.toString()).doesNotContain("secret-token");
    }
}
