package com.flowforge.infrastructure.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import org.junit.jupiter.api.Test;

/** T1.8 : valeurs par défaut et validation de {@code app.signature.*} (TODO §72). */
class SignaturePropertiesTest {

    private static SignatureProperties of(String provider, Duration def, Duration max) {
        return new SignatureProperties(provider, null, 0, def, max, 0, null, null, null);
    }

    @Test
    void defaultsAreSafe() {
        SignatureProperties p = of(null, null, null);
        assertThat(p.provider()).isEqualTo("none");
        assertThat(p.providerActive()).isFalse();
        assertThat(p.stub().allowOutsideLocal()).isFalse();
        assertThat(p.maxSigners()).isEqualTo(10);
        assertThat(p.defaultDeadlineDays()).isEqualTo(14);
        assertThat(p.maxDeadlineDays()).isEqualTo(90);
        assertThat(p.maxDocumentBytes()).isPositive();
        assertThat(p.dispatch().maxAttempts()).isEqualTo(20);
    }

    @Test
    void providerIsNormalizedAndValidated() {
        assertThat(of(" Stub ", null, null).stubSelected()).isTrue();
        assertThatThrownBy(() -> of("docusign", null, null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("docusign");
    }

    @Test
    void deadlinesMustBeCoherent() {
        assertThatThrownBy(() -> of("none", Duration.ofHours(2), Duration.ofDays(5)))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> of("none", Duration.ofDays(30), Duration.ofDays(10)))
                .isInstanceOf(IllegalStateException.class);
    }
}
