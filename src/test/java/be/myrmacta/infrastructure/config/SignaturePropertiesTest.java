package be.myrmacta.infrastructure.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import org.junit.jupiter.api.Test;

/** T1.8 : valeurs par défaut et validation de {@code app.signature.*} (TODO §72). */
class SignaturePropertiesTest {

    private static SignatureProperties of(String provider, Duration def, Duration max) {
        return new SignatureProperties(provider, null, 0, def, max, 0, null, null, null, null);
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
        assertThat(of("yousign", null, null).provider()).isEqualTo("yousign");
        assertThat(of(" DocuSign ", null, null).provider()).isEqualTo("docusign");
        assertThatThrownBy(() -> of("universign", null, null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("universign");
    }

    @Test
    void deadlinesMustBeCoherent() {
        assertThatThrownBy(() -> of("none", Duration.ofHours(2), Duration.ofDays(5)))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> of("none", Duration.ofDays(30), Duration.ofDays(10)))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void eidProviderIsKnownAndItsUploadLimitDefaultsToTheDocumentLimitPlusAMargin() {
        // TODO §138
        SignatureProperties p = of("EID", null, null);

        assertThat(p.provider()).isEqualTo("eid");
        assertThat(p.eidSelected()).isTrue();
        assertThat(p.eid().trustAnchorsFile()).isNull();
        assertThat(p.eid().maxUploadBytes()).isEqualTo(p.maxDocumentBytes() + 2L * 1024 * 1024);
        // cohérent avec la limite multipart globale (25 Mo) du gabarit
        assertThat(p.eid().maxUploadBytes()).isLessThan(25L * 1024 * 1024);
        assertThat(p.eid().revocation().timeout()).isEqualTo(Duration.ofSeconds(10));
        assertThat(p.eid().revocation().maxResponseBytes()).isEqualTo(1024 * 1024);
        // D-EID-TIMESTAMP : pas de TSA par défaut
        assertThat(p.eid().timestamp().enabled()).isFalse();
        assertThat(p.eid().timestamp().timeout()).isEqualTo(Duration.ofSeconds(10));
        assertThat(p.eid().timestamp().maxResponseBytes()).isEqualTo(64 * 1024);
    }

    @Test
    void eidBlockIsKeptAndValidated() {
        SignatureProperties p = new SignatureProperties("eid", null, 0, null, null, 1000, null, null, null,
                new SignatureProperties.Eid(" /etc/anchors.pem ", 5000,
                        new SignatureProperties.Revocation(Duration.ofSeconds(3), 2048), null));

        assertThat(p.eid().trustAnchorsFile()).isEqualTo("/etc/anchors.pem");
        assertThat(p.eid().maxUploadBytes()).isEqualTo(5000);
        assertThat(p.eid().revocation().timeout()).isEqualTo(Duration.ofSeconds(3));
        assertThat(p.eid().revocation().maxResponseBytes()).isEqualTo(2048);

        assertThatThrownBy(() -> new SignatureProperties("eid", null, 0, null, null, 1000, null, null, null,
                        new SignatureProperties.Eid(null, 500, null, null)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("max-upload-bytes");
        assertThatThrownBy(() -> new SignatureProperties.Revocation(Duration.ZERO, 0))
                .isInstanceOf(IllegalStateException.class);
        assertThat(new SignatureProperties.Timestamp(" https://tsa.example/tsp ", null, 0).url()).isEqualTo("https://tsa.example/tsp");
        assertThat(new SignatureProperties.Timestamp(" ", null, 0).enabled()).isFalse();
        assertThatThrownBy(() -> new SignatureProperties.Timestamp("https://tsa.example", Duration.ZERO, 0))
                .isInstanceOf(IllegalStateException.class);
    }
}
