package com.flowforge.infrastructure.security;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

/**
 * C2 (revue adversariale du portail) — la clé des limiteurs anonymes ne doit dépendre d'aucun
 * en-tête que le client contrôle : ni {@code Forwarded} (RFC 7239, préféré par
 * {@code ForwardedHeaderFilter} et non supprimé par le proxy documenté), ni {@code X-Forwarded-For}
 * quand le pair TCP n'est pas le reverse-proxy de confiance.
 */
class ClientIpResolverTest {

    private final ClientIpResolver resolver = new ClientIpResolver(List.of("127.0.0.0/8", "10.0.0.0/8"));

    private static MockHttpServletRequest from(String peer) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr(peer);
        return request;
    }

    /** Ce que fait {@code ForwardedHeaderFilter} : l'adresse vue par l'application est réécrite. */
    private static HttpServletRequest rewrittenBy(MockHttpServletRequest raw, String spoofed) {
        return new HttpServletRequestWrapper(raw) {
            @Override
            public String getRemoteAddr() {
                return spoofed;
            }
        };
    }

    @Test
    void untrustedPeer_isTheClient_whateverTheForwardingHeaders() {
        MockHttpServletRequest raw = from("203.0.113.9");
        raw.addHeader("X-Forwarded-For", "192.0.2.1");
        raw.addHeader("Forwarded", "for=192.0.2.2");

        assertThat(resolver.resolve(rewrittenBy(raw, "192.0.2.2"))).isEqualTo("203.0.113.9");
    }

    @Test
    void trustedProxy_usesTheRightmostUntrustedXForwardedFor_andIgnoresForwarded() {
        MockHttpServletRequest raw = from("127.0.0.1");
        raw.addHeader("X-Forwarded-For", "192.0.2.66, 198.51.100.8, 10.1.2.3");
        raw.addHeader("Forwarded", "for=192.0.2.2");

        assertThat(resolver.resolve(rewrittenBy(raw, "192.0.2.2"))).isEqualTo("198.51.100.8");
    }

    @Test
    void trustedProxyWithoutXForwardedFor_isTheClient() {
        assertThat(resolver.resolve(from("10.9.9.9"))).isEqualTo("10.9.9.9");
    }

    @Test
    void chainOfTrustedAddressesOnly_resolvesToTheOriginalClient() {
        MockHttpServletRequest raw = from("127.0.0.1");
        raw.addHeader("X-Forwarded-For", "10.0.0.5, 10.0.0.6");

        assertThat(resolver.resolve(raw)).isEqualTo("10.0.0.5");
    }

    @Test
    void nonLiteralEntries_areNeverResolved_andStopTheWalk() {
        // Un nom d'hôte ne doit jamais déclencher de résolution DNS ; l'entrée la plus à droite
        // non fiable qui n'est pas une IP laisse le pair comme clé.
        MockHttpServletRequest raw = from("127.0.0.1");
        raw.addHeader("X-Forwarded-For", "192.0.2.1, attacker.example.org");

        assertThat(resolver.resolve(raw)).isEqualTo("127.0.0.1");
    }
}
