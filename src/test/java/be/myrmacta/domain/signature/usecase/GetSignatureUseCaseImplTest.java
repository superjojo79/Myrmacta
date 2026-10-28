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

/** Contexte du portail : lien demandé à l'ouverture (TODO §109, D-SIGNATURE-LINK). */
class GetSignatureUseCaseImplTest {

    private final InMemorySignatureRepository repository = new InMemorySignatureRepository();
    private final PortalTokenPort tokenPort = mock(PortalTokenPort.class);
    private final DossierRepository dossiers = mock(DossierRepository.class);
    private final SignaturePort port = mock(SignaturePort.class);
    private GetSignatureUseCaseImpl useCase;
    private SignatureRequest request;

    @BeforeEach
    void setUp() {
        when(tokenPort.hash(any())).thenAnswer(inv -> "hash:" + inv.getArgument(0));
        request = request(UUID.randomUUID(), SignatureMode.PARALLEL).withProviderReference("yousign:1")
                .withSimulation(false, "ELECTRONIC");
        repository.insert(request, List.of(external(request.id(), 0, true, "a@example.org")
                .withStatus(SignerStatus.PENDING).withToken("hash:tok")));
        when(dossiers.findById(request.dossierId())).thenReturn(Optional.of(new Dossier(request.dossierId(), TENANT,
                "DOS-9", "SINISTRE", "Dossier", DossierStatus.OUVERT, Map.of(), null, 0L, "test", NOW, null, null,
                null, null, null)));
        SignatureAccessResolver resolver = new SignatureAccessResolver(repository, tokenPort, dossiers,
                Clock.fixed(NOW, ZoneOffset.UTC));
        useCase = new GetSignatureUseCaseImpl(TENANT, resolver, mock(DossierElementRepository.class),
                mock(DocumentStoragePort.class), port);
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
}
