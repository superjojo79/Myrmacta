package be.myrmacta.domain.workflow.portal;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import be.myrmacta.domain.dossier.port.out.DossierRepository;
import be.myrmacta.domain.workflow.portal.model.PortalAccessDeniedException;
import be.myrmacta.domain.workflow.portal.model.PortalLink;
import be.myrmacta.domain.workflow.portal.port.out.PortalLinkRepository;
import be.myrmacta.domain.workflow.portal.port.out.PortalTaskLookupPort;
import be.myrmacta.domain.workflow.portal.port.out.PortalTokenPort;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * F-PORTAL-1 (revue Phase 9) — contrôle du jeton <em>avant</em> la lecture d'un corps d'upload :
 * sans effet de bord (ni {@code last_accessed_at}, ni lecture de la tâche ou du dossier), le
 * contrôle complet restant fait par {@link PortalAccessResolver#resolve} dans le use case.
 */
class PortalAccessResolverTest {

    private static final Instant NOW = Instant.parse("2026-09-24T10:00:00Z");

    private final PortalLinkRepository repository = mock(PortalLinkRepository.class);
    private final PortalTaskLookupPort taskLookup = mock(PortalTaskLookupPort.class);
    private final DossierRepository dossiers = mock(DossierRepository.class);
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
    private final PortalAccessResolver resolver =
            new PortalAccessResolver(repository, tokenPort, taskLookup, dossiers, Clock.fixed(NOW, ZoneOffset.UTC));

    private PortalLink active(Instant sentAt) {
        return PortalLink.pendingSend(UUID.randomUUID(), "tenant_dev", UUID.randomUUID(), "t", "pi", "avis",
                "a@b.org", 7, sentAt, "x").activated("hash-tok", sentAt);
    }

    @Test
    void usableToken_passesWithoutAnySideEffect() {
        when(repository.findByTokenHash("hash-tok")).thenReturn(Optional.of(active(NOW)));

        assertThatCode(() -> resolver.assertUsableToken("tok")).doesNotThrowAnyException();
        verifyNoInteractions(taskLookup, dossiers);
        verify(repository, never()).transition(any(), any());
    }

    @Test
    void missingMalformedUnknownOrExpiredToken_isDenied() {
        when(repository.findByTokenHash("hash-old")).thenReturn(Optional.of(active(NOW.minusSeconds(8 * 86_400L))));

        assertThatThrownBy(() -> resolver.assertUsableToken(null)).isInstanceOf(PortalAccessDeniedException.class);
        assertThatThrownBy(() -> resolver.assertUsableToken(" ")).isInstanceOf(PortalAccessDeniedException.class);
        assertThatThrownBy(() -> resolver.assertUsableToken("x".repeat(129)))
                .isInstanceOf(PortalAccessDeniedException.class);
        assertThatThrownBy(() -> resolver.assertUsableToken("unknown"))
                .isInstanceOf(PortalAccessDeniedException.class);
        assertThatThrownBy(() -> resolver.assertUsableToken("old")).isInstanceOf(PortalAccessDeniedException.class);
    }
}
