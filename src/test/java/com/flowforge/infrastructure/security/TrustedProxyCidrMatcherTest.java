package com.flowforge.infrastructure.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import org.junit.jupiter.api.Test;

class TrustedProxyCidrMatcherTest {

    private static final List<String> DEFAULT_CIDRS =
            List.of("127.0.0.0/8", "::1/128", "10.0.0.0/8", "172.16.0.0/12", "192.168.0.0/16");

    @Test
    void matchesLoopbackAndRfc1918ByDefault() {
        var matcher = new TrustedProxyCidrMatcher(DEFAULT_CIDRS);

        assertThat(matcher.matches("127.0.0.1")).isTrue();
        assertThat(matcher.matches("::1")).isTrue();
        assertThat(matcher.matches("10.1.2.3")).isTrue();
        assertThat(matcher.matches("172.18.0.4")).isTrue();
        assertThat(matcher.matches("192.168.1.1")).isTrue();
    }

    @Test
    void rejectsPublicAddressByDefault() {
        var matcher = new TrustedProxyCidrMatcher(DEFAULT_CIDRS);

        assertThat(matcher.matches("203.0.113.10")).isFalse();
    }

    @Test
    void rejectsAddressJustOutsidePrefixBoundary() {
        var matcher = new TrustedProxyCidrMatcher(List.of("172.16.0.0/12"));

        assertThat(matcher.matches("172.31.255.255")).isTrue();
        assertThat(matcher.matches("172.32.0.0")).isFalse();
        assertThat(matcher.matches("172.15.255.255")).isFalse();
    }

    @Test
    void narrowedAllowlistRejectsAddressTrustedByRfc1918Default() {
        var matcher = new TrustedProxyCidrMatcher(List.of("203.0.113.5/32"));

        assertThat(matcher.matches("10.1.2.3")).isFalse();
        assertThat(matcher.matches("203.0.113.5")).isTrue();
        assertThat(matcher.matches("203.0.113.6")).isFalse();
    }

    @Test
    void rejectsBlankOrNullOrUnparsableLiteral() {
        var matcher = new TrustedProxyCidrMatcher(DEFAULT_CIDRS);

        assertThat(matcher.matches(null)).isFalse();
        assertThat(matcher.matches("")).isFalse();
        assertThat(matcher.matches("   ")).isFalse();
        assertThat(matcher.matches("not-an-ip")).isFalse();
    }

    @Test
    void rejectsAddressFamilyMismatch() {
        var matcher = new TrustedProxyCidrMatcher(List.of("10.0.0.0/8"));

        assertThat(matcher.matches("::1")).isFalse();
    }

    @Test
    void failsFastOnInvalidCidrLiteral() {
        assertThatThrownBy(() -> new TrustedProxyCidrMatcher(List.of("not-a-cidr/8")))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void failsFastOnOutOfRangePrefixLength() {
        assertThatThrownBy(() -> new TrustedProxyCidrMatcher(List.of("10.0.0.0/33")))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void hostWithoutPrefixMatchesExactAddressOnly() {
        var matcher = new TrustedProxyCidrMatcher(List.of("203.0.113.5"));

        assertThat(matcher.matches("203.0.113.5")).isTrue();
        assertThat(matcher.matches("203.0.113.6")).isFalse();
    }
}
