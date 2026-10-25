package be.myrmacta.domain.workflow.portal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import be.myrmacta.domain.dossier.model.Dossier;
import be.myrmacta.domain.dossier.port.out.DossierRepository;
import be.myrmacta.domain.workflow.portal.model.IdentityAssuranceLevel;
import be.myrmacta.domain.workflow.portal.model.IdentityMatch;
import be.myrmacta.domain.workflow.portal.model.PortalAccessDeniedException;
import be.myrmacta.domain.workflow.portal.model.PortalCredential;
import be.myrmacta.domain.workflow.portal.model.PortalIdentityField;
import be.myrmacta.domain.workflow.portal.model.PortalLink;
import be.myrmacta.domain.workflow.portal.model.PortalLinkIdentity;
import be.myrmacta.domain.workflow.portal.model.PortalLinkStatusReason;
import be.myrmacta.domain.workflow.portal.model.PortalRecipientSource;
import be.myrmacta.domain.workflow.portal.model.PortalTaskDefinition;
import be.myrmacta.domain.workflow.portal.port.out.PortalIdentityRepository;
import be.myrmacta.domain.workflow.portal.port.out.PortalLinkRepository;
import be.myrmacta.domain.workflow.portal.port.out.PortalTaskLookupPort;
import be.myrmacta.domain.workflow.portal.port.out.PortalTokenPort;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * TODO §104 (T3b, T9, D-BEAUTH-PROOF) — accès au portail par le jeton du lien ou par la preuve d'identité. Une tâche à
 * authentification forte n'est accessible en action (dépôt, soumission) qu'avec une preuve valable pour ce lien ; le
 * jeton seul ne donne que la vue réduite.
 */
class PortalAccessResolverIdentityTest {

    private static final Instant NOW = Instant.parse("2026-10-24T10:00:00Z");
    private static final PortalIdentityField FIELD = new PortalIdentityField("nrnDemandeur");

    private final PortalLinkRepository links = mock(PortalLinkRepository.class);
    private final PortalTaskLookupPort taskLookup = mock(PortalTaskLookupPort.class);
    private final DossierRepository dossiers = mock(DossierRepository.class);
    private final PortalIdentityRepository identities = mock(PortalIdentityRepository.class);
    private final PortalTokenPort tokenPort = new PortalTokenPort() {
        @Override
        public String newToken() {
            return "unused";
        }

        @Override
        public String hash(String token) {
            return "hash-" + token;
        }
    };
    private final PortalAccessResolver resolver = new PortalAccessResolver(links, tokenPort, taskLookup, dossiers,
            Clock.fixed(NOW, ZoneOffset.UTC), identities);
    private PortalLink link;

    @BeforeEach
    void strongAuthenticationTask() {
        link = PortalLink.pendingSend(UUID.randomUUID(), "tenant_dev", UUID.randomUUID(), "t", "pi", "avis", "a@b.org", 7,
                NOW, "x").activated("hash-tok", NOW);
        when(links.findByTokenHash("hash-tok")).thenReturn(Optional.of(link));
        when(links.findById(link.id())).thenReturn(Optional.of(link));
        when(links.transition(any(), any())).thenReturn(true);
        when(taskLookup.findOpenTask("t")).thenReturn(Optional.of(new PortalTaskLookupPort.OpenTask("t", "pi", "Avis", "c")));
        when(taskLookup.findExternalTaskDefinition("t")).thenReturn(Optional.of(new PortalTaskDefinition("t", "avis",
                PortalRecipientSource.parse("dossierField:email").orElseThrow(), null, FIELD)));
        when(dossiers.findById(link.dossierId())).thenReturn(Optional.of(mock(Dossier.class)));
    }

    private PortalLinkIdentity identity(Instant expiresAt, Instant submittedAt) {
        return new PortalLinkIdentity(link.id(), "tenant_dev", "hash-proof", "fas", IdentityAssuranceLevel.HIGH, "Alice",
                "Martin", "enc:v1:x", IdentityMatch.CONFORME, NOW.minusSeconds(60), expiresAt, submittedAt);
    }

    @Test
    void theLinkTokenAloneGivesTheReducedViewButNoAction_T3b() {
        var access = resolver.resolveAllowingPendingIdentity(PortalCredential.of("tok", null));
        assertThat(access.identityRequired()).isTrue();
        assertThat(access.identityField()).isEqualTo(FIELD);
        assertThat(access.identity()).isNull();

        assertThatThrownBy(() -> resolver.resolve(PortalCredential.of("tok", null)))
                .isInstanceOf(PortalAccessDeniedException.class);
    }

    @Test
    void aValidProofForThisLinkGivesFullAccess_D_BEAUTH_PROOF() {
        when(identities.findByProofHash("hash-proof")).thenReturn(Optional.of(identity(NOW.plus(Duration.ofHours(1)), null)));

        var access = resolver.resolve(PortalCredential.of(null, "proof"));

        assertThat(access.link().id()).isEqualTo(link.id());
        assertThat(access.identity().provider()).isEqualTo("fas");
        assertThatCode(() -> resolver.assertUsable(PortalCredential.of("ignored", "proof"))).doesNotThrowAnyException();
    }

    @Test
    void anExpiredSubmittedOrUnknownProofIsRefused_T9() {
        when(identities.findByProofHash("hash-expired")).thenReturn(Optional.of(identity(NOW, null)));
        when(identities.findByProofHash("hash-used")).thenReturn(Optional.of(identity(NOW.plusSeconds(60), NOW)));
        for (String proof : new String[] {"expired", "used", "unknown", " ", "x".repeat(129)}) {
            assertThatThrownBy(() -> resolver.resolveAllowingPendingIdentity(PortalCredential.of(null, proof)))
                    .as(proof).isInstanceOf(PortalAccessDeniedException.class);
        }
    }

    @Test
    void aProofIsWorthNothingOnceItsLinkIsNoLongerActive_T9() {
        when(identities.findByProofHash("hash-proof")).thenReturn(Optional.of(identity(NOW.plus(Duration.ofHours(1)), null)));
        when(links.findById(link.id())).thenReturn(Optional.of(link.revoked(NOW, "u", PortalLinkStatusReason.RESENT)));

        assertThatThrownBy(() -> resolver.resolve(PortalCredential.of(null, "proof")))
                .isInstanceOf(PortalAccessDeniedException.class);
    }

    @Test
    void aTaskWithoutIdentityFieldKeepsTheTokenAccess() {
        when(taskLookup.findExternalTaskDefinition("t")).thenReturn(Optional.of(new PortalTaskDefinition("t", "avis",
                PortalRecipientSource.parse("dossierField:email").orElseThrow(), null)));

        var access = resolver.resolve(PortalCredential.of("tok", null));

        assertThat(access.identityRequired()).isFalse();
        assertThatCode(() -> resolver.assertUsable(PortalCredential.of("tok", null))).doesNotThrowAnyException();
    }

    @Test
    void aCredentialNeverPrintsItsSecret() {
        assertThat(PortalCredential.of("secret-token", null).toString()).doesNotContain("secret-token");
        assertThat(PortalCredential.of(null, "secret-proof").toString()).doesNotContain("secret-proof");
    }
}
