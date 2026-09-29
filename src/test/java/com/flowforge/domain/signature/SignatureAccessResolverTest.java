package com.flowforge.domain.signature;

import static com.flowforge.domain.signature.SignatureFixtures.NOW;
import static com.flowforge.domain.signature.SignatureFixtures.TENANT;
import static com.flowforge.domain.signature.SignatureFixtures.external;
import static com.flowforge.domain.signature.SignatureFixtures.internal;
import static com.flowforge.domain.signature.SignatureFixtures.request;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.flowforge.domain.dossier.model.Dossier;
import com.flowforge.domain.dossier.port.out.DossierRepository;
import com.flowforge.domain.signature.SignatureAccessResolver.ResolvedAccess;
import com.flowforge.domain.signature.model.SignatureMode;
import com.flowforge.domain.signature.model.SignatureOutcome;
import com.flowforge.domain.signature.model.SignatureProgression;
import com.flowforge.domain.signature.model.SignatureRequest;
import com.flowforge.domain.signature.model.Signer;
import com.flowforge.domain.signature.model.SignerStatus;
import com.flowforge.domain.workflow.portal.model.PortalAccessDeniedException;
import com.flowforge.domain.workflow.portal.port.out.PortalTokenPort;
import java.time.Clock;
import java.time.Duration;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** S-ACCESS-2, S-ACCESS-7, S-DEC-5 (accès), S-INT-5 : résolution jeton → signataire (TODO §72, M5). */
class SignatureAccessResolverTest {

    private static final String TOKEN = "t".repeat(43);

    private final InMemorySignatureRepository repository = new InMemorySignatureRepository();
    private final PortalTokenPort tokenPort = mock(PortalTokenPort.class);
    private final DossierRepository dossiers = mock(DossierRepository.class);
    private SignatureAccessResolver resolver;
    private SignatureRequest request;
    private Signer signer;

    @BeforeEach
    void setUp() {
        when(tokenPort.hash(any())).thenAnswer(inv -> "hash:" + inv.getArgument(0));
        Dossier dossier = mock(Dossier.class);
        when(dossier.status()).thenReturn(com.flowforge.domain.dossier.model.DossierStatus.OUVERT);
        when(dossier.reference()).thenReturn("SIN-1");
        when(dossiers.findById(any())).thenReturn(Optional.of(dossier));
        resolver = new SignatureAccessResolver(repository, tokenPort, dossiers, Clock.fixed(NOW, ZoneOffset.UTC));
        request = request(UUID.randomUUID(), SignatureMode.PARALLEL);
        List<Signer> signers = SignatureProgression.start(SignatureMode.PARALLEL,
                        List.of(external(request.id(), 0, true, "a@example.org"))).signers();
        signer = signers.getFirst().withToken("hash:" + TOKEN);
        repository.insert(request, List.of(signer));
    }

    private void assertDenied(String token) {
        assertThatThrownBy(() -> resolver.resolve(token)).isInstanceOf(PortalAccessDeniedException.class);
    }

    @Test
    void validTokenResolvesTheSignerItsRequestAndItsDossier() {
        ResolvedAccess access = resolver.resolve(TOKEN);
        assertThat(access.signer().id()).isEqualTo(signer.id());
        assertThat(access.request().id()).isEqualTo(request.id());
        assertThat(access.dossier().reference()).isEqualTo("SIN-1");
    }

    @Test
    void tokenIsLookedUpByItsHashNeverByItsValue() {
        // S-ACCESS-7 : aucune comparaison directe du jeton ; recherche indexée sur le hash
        resolver.resolve(TOKEN);
        verify(tokenPort).hash(TOKEN);
    }

    @Test
    void missingBlankUnknownOrOverlongTokensAreDenied() {
        assertDenied(null);
        assertDenied("  ");
        assertDenied("inconnu");
        assertDenied("x".repeat(500));
    }

    @Test
    void signerThatAlreadyDecidedOrWasRevokedIsDenied() {
        repository.updateSigner(signer.decided(SignerStatus.SIGNED, NOW, null));
        assertDenied(TOKEN);
    }

    @Test
    void waitingSignerHasNoAccessYet() {
        // S-DEC-5, S-INT-5 (uniforme avant son tour)
        repository.updateSigner(signer.withStatus(SignerStatus.WAITING));
        assertDenied(TOKEN);
    }

    @Test
    void closedOrExpiredRequestIsDenied() {
        repository.updateRequest(request.closed(SignatureOutcome.CANCELLED, null, NOW));
        assertDenied(TOKEN);
    }

    @Test
    void requestPastItsDeadlineIsDeniedEvenBeforeTheExpiryJobRuns() {
        SignatureAccessResolver late = new SignatureAccessResolver(repository, tokenPort, dossiers,
                Clock.fixed(request.deadlineAt().plus(Duration.ofSeconds(1)), ZoneOffset.UTC));
        assertThatThrownBy(() -> late.resolve(TOKEN)).isInstanceOf(PortalAccessDeniedException.class);
    }

    @Test
    void internalSignerCannotBeReachedByToken() {
        SignatureRequest other = request(UUID.randomUUID(), SignatureMode.PARALLEL, "proc-other");
        Signer internalSigner = SignatureProgression.start(SignatureMode.PARALLEL,
                        List.of(internal(other.id(), 0, true, "bob"))).signers().getFirst().withToken("hash:other-token");
        repository.insert(other, List.of(internalSigner));
        assertDenied("other-token");
    }

    @Test
    void vanishedDossierIsDenied() {
        when(dossiers.findById(any())).thenReturn(Optional.empty());
        assertDenied(TOKEN);
    }

    @Test
    void allDenialsAreIndistinguishableByTheExceptionMessage() {
        String unknown = catchMessage("inconnu");
        repository.updateSigner(signer.decided(SignerStatus.SIGNED, NOW, null));
        assertThat(catchMessage(TOKEN)).isEqualTo(unknown);
        assertThat(TENANT).isNotBlank();
    }

    private String catchMessage(String token) {
        try {
            resolver.resolve(token);
            throw new AssertionError("expected a denial");
        } catch (PortalAccessDeniedException ex) {
            return ex.getMessage();
        }
    }
}
