package be.myrmacta.infrastructure.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class PortalPropertiesTest {

    private static PortalProperties props(boolean enabled, String url) {
        return new PortalProperties(enabled, url, null, null, null, null, null, 0);
    }

    @Test
    void disabledByDefault_needsNoBaseUrl() {
        PortalProperties p = props(false, null);
        assertThat(p.enabled()).isFalse();
        assertThat(p.defaultLinkTtlDays()).isEqualTo(7);
        assertThat(p.maxLinkTtlDays()).isEqualTo(30);
        assertThat(p.send().maxAttempts()).isEqualTo(5);
        assertThat(p.rateLimit().perIpPerMinute()).isEqualTo(30);
        assertThat(p.maxBodyBytes()).isEqualTo(65536);
    }

    @Test
    void enabledWithoutBaseUrl_failsFast() {
        // T-SEC-3
        assertThatThrownBy(() -> props(true, null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("APP_PORTAL_PUBLIC_BASE_URL");
        assertThatThrownBy(() -> props(true, "  ")).isInstanceOf(IllegalStateException.class);
    }

    @ParameterizedTest
    @ValueSource(strings = {"myrmacta.example.org", "ftp://x.org", "https://x.org/a?b=1", "https://x.org/#f",
            "https://user:pw@x.org", "javascript:alert(1)"})
    void enabledWithInvalidBaseUrl_failsFast(String url) {
        assertThatThrownBy(() -> props(true, url)).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void baseUrl_trailingSlashesAreStripped() {
        assertThat(props(true, "https://ff.example.org/tenant-a/").publicBaseUrl())
                .isEqualTo("https://ff.example.org/tenant-a");
    }

    @Test
    void defaultTtlAboveMax_failsFast() {
        assertThatThrownBy(() -> new PortalProperties(
                        false, null, Duration.ofDays(40), Duration.ofDays(30), null, null, null, 0))
                .isInstanceOf(IllegalStateException.class);
    }
}
