package com.flowforge.infrastructure.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.flowforge.domain.workflow.portal.model.IdentityAssuranceLevel;
import com.flowforge.infrastructure.config.PortalStrongAuthProperties.Claims;
import com.flowforge.infrastructure.config.PortalStrongAuthProperties.Keys;
import com.flowforge.infrastructure.config.PortalStrongAuthProperties.Provider;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** TODO §103 (E1, D-BEAUTH-TTL, K5) — réglages de l'authentification forte, vérifiés au démarrage. */
class PortalStrongAuthPropertiesTest {

    private static final Keys KEYS = new Keys("/run/secrets/portal-signing.pem", "k1", null, null, null, null);

    private static Provider provider() {
        return new Provider(null, "https://idp.example/realms/fas", "client", null, null,
                new Claims("egovNRN", "givenName", "surname", "loa"),
                Map.of(IdentityAssuranceLevel.HIGH, List.of("high"), IdentityAssuranceLevel.SUBSTANTIAL, List.of("substantial")), null);
    }

    @Test
    void disabledByDefaultWithASixtyMinuteIdentity() {
        var properties = new PortalStrongAuthProperties(false, null, null, null, null);
        assertThat(properties.enabled()).isFalse();
        assertThat(properties.identityTtl()).isEqualTo(Duration.ofMinutes(60));
    }

    @Test
    void theIdentityLifetimeIsCappedAtFourHours() {
        assertThat(new PortalStrongAuthProperties(false, Duration.ofHours(4), null, null, null).identityTtl())
                .isEqualTo(Duration.ofHours(4));
        assertThatThrownBy(() -> new PortalStrongAuthProperties(false, Duration.ofHours(5), null, null, null))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("4 hours");
        assertThatThrownBy(() -> new PortalStrongAuthProperties(false, Duration.ZERO, null, null, null))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void enabledRequiresACompleteProviderAndASigningKey() {
        assertThatThrownBy(() -> new PortalStrongAuthProperties(true, null, KEYS, null, Map.of()))
                .hasMessageContaining("at least one provider");
        assertThatThrownBy(() -> new PortalStrongAuthProperties(true, null, KEYS, null, Map.of("google", provider())))
                .hasMessageContaining("unknown provider");
        assertThatThrownBy(() -> new PortalStrongAuthProperties(true, null, null, null, Map.of("fas", provider())))
                .hasMessageContaining("signing-key-file");
        Provider withoutLevels = new Provider(null, "https://idp", "c", null, null, new Claims("n", null, null, "l"), Map.of(), null);
        assertThatThrownBy(() -> new PortalStrongAuthProperties(true, null, KEYS, null, Map.of("fas", withoutLevels)))
                .hasMessageContaining("levels.HIGH");
        Provider withoutClaims = new Provider(null, "https://idp", "c", null, null, null,
                Map.of(IdentityAssuranceLevel.HIGH, List.of("h")), null);
        assertThatThrownBy(() -> new PortalStrongAuthProperties(true, null, KEYS, null, Map.of("fas", withoutClaims)))
                .hasMessageContaining("claims.nrn");

        assertThat(new PortalStrongAuthProperties(true, null, KEYS, null, Map.of("fas", provider())).enabled()).isTrue();
    }

    @Test
    void providersFollowTheConfiguredOrderWithDefaultLabels() {
        var properties = new PortalStrongAuthProperties(true, null, KEYS, null, Map.of("itsme", provider(), "fas", provider()));
        assertThat(properties.orderedProviders().keySet()).containsExactly("fas", "itsme");
        var reversed = new PortalStrongAuthProperties(true, null, KEYS, List.of("itsme", "fas"),
                Map.of("itsme", provider(), "fas", provider()));
        assertThat(reversed.orderedProviders().keySet()).containsExactly("itsme", "fas");
        assertThat(provider().displayLabel("fas")).isEqualTo("eID, itsme et autres (FAS)");
        assertThat(provider().displayLabel("itsme")).isEqualTo("itsme");
    }

    /** T4, K5 — une valeur de niveau inconnue vaut LOW, donc un refus. */
    @Test
    void anUnknownLevelValueIsLow() {
        assertThat(provider().levelOf("high")).isEqualTo(IdentityAssuranceLevel.HIGH);
        assertThat(provider().levelOf("substantial")).isEqualTo(IdentityAssuranceLevel.SUBSTANTIAL);
        assertThat(provider().levelOf("low")).isEqualTo(IdentityAssuranceLevel.LOW);
        assertThat(provider().levelOf(null)).isEqualTo(IdentityAssuranceLevel.LOW);
    }
}
