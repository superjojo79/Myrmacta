package com.flowforge.infrastructure.notification;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

class OutboundUrlGuardTest {

    private final OutboundUrlGuard strict = new OutboundUrlGuard(false);
    private final OutboundUrlGuard allowLocalHttp = new OutboundUrlGuard(true);

    @Test
    void httpsPublicHost_isAccepted() {
        assertThatCode(() -> strict.assertSafe("https://hooks.example.com/notify"))
                .doesNotThrowAnyException();
    }

    @Test
    void http_rejectedWhenLocalHttpNotAllowed() {
        assertThatThrownBy(() -> strict.assertSafe("http://hooks.example.com/notify"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("https");
    }

    @Test
    void httpPublicHost_rejectedEvenWhenLocalHttpAllowed() {
        assertThatThrownBy(() -> allowLocalHttp.assertSafe("http://hooks.example.com/notify"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("localhost");
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "http://localhost/hook",
                "http://LOCALHOST/hook",
                "http://127.0.0.1/hook",
                "http://[::1]/hook"
            })
    void httpLoopback_acceptedWhenLocalHttpAllowed(String url) {
        assertThatCode(() -> allowLocalHttp.assertSafe(url)).doesNotThrowAnyException();
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "http://localhost/hook",
                "http://127.0.0.1/hook",
                "http://[::1]/hook"
            })
    void httpLoopback_rejectedWhenLocalHttpNotAllowed(String url) {
        assertThatThrownBy(() -> strict.assertSafe(url))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"  ", "not-a-url", "://missing-scheme"})
    void nullBlankOrMalformed_rejected(String url) {
        assertThatThrownBy(() -> strict.assertSafe(url)).isInstanceOf(IllegalArgumentException.class);
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "https://localhost/hook",
                "https://LOCALHOST/hook",
                "https://127.0.0.1/hook",
                "https://[::1]/hook",
                "https://service.local/hook",
                "https://10.0.0.1/hook",
                "https://10.255.255.255/hook",
                "https://172.16.0.1/hook",
                "https://172.31.255.1/hook",
                "https://192.168.1.1/hook",
                "https://169.254.169.254/latest/meta-data",
                "https://0.0.0.0/hook",
                "http://10.0.0.1/hook",
                "http://192.168.1.1/hook"
            })
    void privateOrHttpsLoopbackHosts_rejected(String url) {
        assertThatThrownBy(() -> strict.assertSafe(url))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> allowLocalHttp.assertSafe(url))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void publicIpv4_isAccepted() {
        assertThatCode(() -> strict.assertSafe("https://8.8.8.8/hook")).doesNotThrowAnyException();
    }

    @Test
    void private172_15_isAccepted() {
        assertThatCode(() -> strict.assertSafe("https://172.15.0.1/hook")).doesNotThrowAnyException();
    }

    @Test
    void private172_32_isAccepted() {
        assertThatCode(() -> strict.assertSafe("https://172.32.0.1/hook")).doesNotThrowAnyException();
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "https://[::ffff:127.0.0.1]/hook",
                "https://[::ffff:10.0.0.1]/hook",
                "https://[::ffff:192.168.1.1]/hook",
                "https://[::ffff:169.254.169.254]/hook",
                "https://[fc00::1]/hook",
                "https://[fd12:3456:789a::1]/hook",
                "https://[fe80::1]/hook",
                "https://2130706433/hook",
                "https://2852039166/hook"
            })
    void ipv4MappedUlaLinkLocalAndDecimalPrivate_rejected(String url) {
        assertThatThrownBy(() -> strict.assertSafe(url))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> allowLocalHttp.assertSafe(url))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void decimalLoopback_acceptedAsHttpOnlyWhenLocalHttpAllowed() {
        assertThatCode(() -> allowLocalHttp.assertSafe("http://2130706433/hook"))
                .doesNotThrowAnyException();
        assertThatThrownBy(() -> strict.assertSafe("http://2130706433/hook"))
                .isInstanceOf(IllegalArgumentException.class);
    }
}

