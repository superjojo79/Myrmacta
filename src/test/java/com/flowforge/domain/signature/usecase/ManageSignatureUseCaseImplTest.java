package com.flowforge.domain.signature.usecase;

import static com.flowforge.domain.signature.SignatureFixtures.NOW;
import static com.flowforge.domain.signature.SignatureFixtures.TENANT;
import static com.flowforge.domain.signature.SignatureFixtures.external;
import static com.flowforge.domain.signature.SignatureFixtures.request;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.flowforge.domain.dossier.model.AccessContext;
import com.flowforge.domain.dossier.model.Dossier;
import com.flowforge.domain.dossier.port.out.DossierRepository;
import com.flowforge.domain.dossier.DossierAccessPolicy;
import com.flowforge.domain.signature.InMemorySignatureRepository;
import com.flowforge.domain.signature.SignatureOutcomeHandler;
import com.flowforge.domain.signature.model.SignatureMode;
import com.flowforge.domain.signature.model.Signer;
import java.time.Clock;
import java.time.Duration;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Revue adversariale n°13 — le renvoi d'invitation ne peut pas inonder une adresse externe. */
class ManageSignatureUseCaseImplTest {

    private final InMemorySignatureRepository repository = new InMemorySignatureRepository();
    private final DossierRepository dossiers = mock(DossierRepository.class);
    private final DossierAccessPolicy policy = mock(DossierAccessPolicy.class);
    private final AccessContext.User actor = new AccessContext.User("manager", Set.of("ROLE_DOSSIER_MANAGER"));
    private ManageSignatureUseCaseImpl useCase;
    private UUID requestId;
    private UUID signerId;

    @BeforeEach
    void setUp() {
        Dossier dossier = mock(Dossier.class);
        when(dossier.tenantId()).thenReturn(TENANT);
        when(dossiers.findById(org.mockito.ArgumentMatchers.any())).thenReturn(Optional.of(dossier));
        useCase = new ManageSignatureUseCaseImpl(TENANT, repository, dossiers, policy,
                mock(SignatureOutcomeHandler.class), Clock.fixed(NOW, ZoneOffset.UTC), Duration.ofMinutes(1));
        var request = request(UUID.randomUUID(), SignatureMode.PARALLEL);
        requestId = request.id();
        Signer signer = external(requestId, 0, true, "a@example.org");
        signerId = signer.id();
        repository.insert(request, com.flowforge.domain.signature.model.SignatureProgression
                .start(SignatureMode.PARALLEL, List.of(signer)).signers());
    }

    private void invited(java.time.Instant at) {
        Signer s = repository.findSigners(requestId).getFirst();
        repository.updateSigner(s.withToken("hash").invited(at));
    }

    @Test
    void resendRightAfterAnInvitationIsRefused() {
        invited(NOW.minusSeconds(10));

        assertThatThrownBy(() -> useCase.resend(requestId, signerId, actor)).isInstanceOf(IllegalStateException.class);
        assertThat(repository.findSigners(requestId).getFirst().tokenHash()).isEqualTo("hash");
    }

    @Test
    void resendAfterTheMinimalIntervalInvalidatesTheOldLink() {
        invited(NOW.minusSeconds(120));

        useCase.resend(requestId, signerId, actor);

        assertThat(repository.findSigners(requestId).getFirst().tokenHash()).isNull();
    }

    @Test
    void resendWhileAnInvitationIsAlreadyWaitingToBeSentIsANoOp() {
        // pas encore invité : le job enverra un seul e-mail, quel que soit le nombre de clics
        useCase.resend(requestId, signerId, actor);
        useCase.resend(requestId, signerId, actor);

        assertThat(repository.findSigners(requestId).getFirst().inviteSentAt()).isNull();
    }
}
