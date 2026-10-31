package be.myrmacta.infrastructure.notification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Arrays;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

class OutboundUrlGuardTest {

    /**
     * Deterministic, offline stand-in for real DNS resolution — literal-IP parsing below
     * performs no network I/O, so this map can be built without touching the network.
     */
    private static final Map<String, InetAddress[]> FAKE_DNS = Map.of(
            "hooks.example.com", addresses("93.184.216.34"),
            "evil.attacker.example", addresses("10.0.0.5"),
            "rebind.attacker.example", addresses("169.254.169.254"),
            "multi-answer.attacker.example", addresses("8.8.8.8", "192.168.1.1"));

    private static final OutboundUrlGuard.HostResolver FAKE_RESOLVER = host -> {
        InetAddress[] resolved = FAKE_DNS.get(host.toLowerCase(Locale.ROOT));
        if (resolved == null) {
            throw new UnknownHostException(host);
        }
        return resolved;
    };

    private final OutboundUrlGuard strict = new OutboundUrlGuard(false, FAKE_RESOLVER);
    private final OutboundUrlGuard allowLocalHttp = new OutboundUrlGuard(true, FAKE_RESOLVER);

    private static InetAddress[] addresses(String... literalIps) {
        return Arrays.stream(literalIps)
                .map(OutboundUrlGuardTest::literalAddress)
                .toArray(InetAddress[]::new);
    }

    private static InetAddress literalAddress(String literalIp) {
        try {
            // A literal IP string is parsed locally — InetAddress.getByName performs no
            // DNS query in this case, so building the fixture stays fully offline.
            return InetAddress.getByName(literalIp);
        } catch (UnknownHostException ex) {
            throw new AssertionError(ex);
        }
    }

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

    // --- DNS rebinding: a public-looking hostname resolving to a private/metadata address ---

    @Test
    void hostnameResolvingToPrivateAddress_isRejected() {
        assertThatThrownBy(() -> strict.assertSafe("https://evil.attacker.example/hook"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("disallowed network");
    }

    @Test
    void hostnameResolvingToMetadataAddress_isRejected() {
        assertThatThrownBy(() -> strict.assertSafe("https://rebind.attacker.example/hook"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("disallowed network");
    }

    @Test
    void hostnameWithOneBlockedAmongMultipleResolvedAddresses_isRejected() {
        // A DNS name may return several A/AAAA records — every one must be checked, not
        // just the first, otherwise round-robin/rebinding across answers slips through.
        assertThatThrownBy(() -> strict.assertSafe("https://multi-answer.attacker.example/hook"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("disallowed network");
    }

    @Test
    void ftpAndFileSchemes_rejected() {
        assertThatThrownBy(() -> strict.assertSafe("ftp://hooks.example.com/hook"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("https");
        assertThatThrownBy(() -> strict.assertSafe("file:///etc/passwd"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void unknownHost_isRejected() {
        assertThatThrownBy(() -> strict.assertSafe("https://no-such.example/hook"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("cannot be resolved");
    }

    @Test
    void emptyDnsAnswer_isRejected() {
        OutboundUrlGuard guard = new OutboundUrlGuard(false, host -> new InetAddress[0]);
        assertThatThrownBy(() -> guard.assertSafe("https://hooks.example.com/hook"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("cannot be resolved");
        OutboundUrlGuard nullGuard = new OutboundUrlGuard(false, host -> null);
        assertThatThrownBy(() -> nullGuard.assertSafe("https://hooks.example.com/hook"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("cannot be resolved");
    }

    @Test
    void resolveSafe_preservesUserInfoQueryAndFragmentOnPinnedUrl() {
        OutboundUrlGuard.PinnedEndpoint pinned =
                strict.resolveSafe("https://user:pass@hooks.example.com/notify?x=1#frag");
        assertThat(pinned.requestUrl()).isEqualTo("https://user:pass@93.184.216.34/notify?x=1#frag");
        assertThat(pinned.originalHost()).isEqualTo("hooks.example.com");
    }

    @Test
    void resolveSafe_keepsPercentEncodingOnPinnedUrl() {
        // TODO §112 (revue) : un %2F ou un %26 décodé ferait sortir une valeur de son segment ou de son paramètre.
        OutboundUrlGuard.PinnedEndpoint pinned = strict.resolveSafe(
                "https://hooks.example.com:8443/users/a%2F..%2Fadmin%3Fx/g?q=A%26B%3Dc&r=%23#f%20g");
        assertThat(pinned.requestUrl())
                .isEqualTo("https://93.184.216.34:8443/users/a%2F..%2Fadmin%3Fx/g?q=A%26B%3Dc&r=%23#f%20g");
    }

    @Test
    void resolveSafe_ipv6Literal_wrapsHostInRequestUrl() {
        OutboundUrlGuard.PinnedEndpoint pinned = strict.resolveSafe("https://[2001:4860:4860::8888]/hook");
        assertThat(pinned.requestUrl()).isEqualTo("https://[2001:4860:4860:0:0:0:0:8888]/hook");
        assertThat(pinned.originalHost()).isEqualTo("[2001:4860:4860::8888]");
    }

    @Test
    void resolveSafe_pinsValidatedDnsAddressWithoutSecondLookup() {
        AtomicInteger lookups = new AtomicInteger();
        OutboundUrlGuard.HostResolver counting = host -> {
            lookups.incrementAndGet();
            return FAKE_RESOLVER.resolve(host);
        };
        OutboundUrlGuard guard = new OutboundUrlGuard(false, counting);
        OutboundUrlGuard.PinnedEndpoint pinned = guard.resolveSafe("https://hooks.example.com/notify");
        assertThat(lookups).hasValue(1);
        assertThat(pinned.address().getHostAddress()).isEqualTo("93.184.216.34");
        assertThat(pinned.requestUrl()).isEqualTo("https://93.184.216.34/notify");
        assertThat(pinned.originalHost()).isEqualTo("hooks.example.com");
    }

    @Test
    void resolveSafe_rebindingSecondLookupDoesNotChangePinnedIp() throws Exception {
        AtomicInteger lookups = new AtomicInteger();
        OutboundUrlGuard.HostResolver flipping = host -> {
            int n = lookups.incrementAndGet();
            if (n == 1) {
                return addresses("93.184.216.34");
            }
            return addresses("198.51.100.10");
        };
        OutboundUrlGuard guard = new OutboundUrlGuard(false, flipping);
        OutboundUrlGuard.PinnedEndpoint first = guard.resolveSafe("https://hooks.example.com/notify");
        OutboundUrlGuard.PinnedEndpoint second = guard.resolveSafe("https://hooks.example.com/notify");
        assertThat(first.address().getHostAddress()).isEqualTo("93.184.216.34");
        assertThat(second.address().getHostAddress()).isEqualTo("198.51.100.10");
        assertThat(first.requestUrl()).isEqualTo("https://93.184.216.34/notify");
        assertThat(lookups).hasValue(2);
    }

    @Test
    void reject_cgnatLiteral() {
        assertThatThrownBy(() -> strict.assertSafe("https://100.64.0.1/meta"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void reject_zeroSlashEight() {
        assertThatThrownBy(() -> strict.assertSafe("https://0.0.0.1/meta"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    // --- DNS-resolved IPv4-mapped IPv6 (AAAA record embedding a private/metadata IPv4) ---
    //
    // A literal ::ffff:x.x.x.x host in the URL is already unwrapped by extractIpv4() before the
    // private-range check. But a hostname whose DNS AAAA record resolves to an IPv4-mapped
    // Inet6Address bypassed isBlockedResolvedAddress(): InetAddress#isLoopbackAddress() /
    // #isSiteLocalAddress() do not recognize IPv4-mapped addresses, and the manual octet checks
    // only ran for bytes.length == 4 (a real Inet4Address), never for a 16-byte IPv4-mapped one —
    // so a dual-stack connect to that address would actually reach the embedded IPv4 host.

    @ParameterizedTest
    @ValueSource(
            strings = {
                "mapped-loopback.attacker.example",
                "mapped-private10.attacker.example",
                "mapped-private192.attacker.example",
                "mapped-metadata.attacker.example",
                "mapped-cgnat.attacker.example",
                "mapped-zero.attacker.example"
            })
    void hostnameResolvingToIpv4MappedIpv6Address_isRejected(String host) throws Exception {
        java.util.Map<String, InetAddress> mapped = java.util.Map.of(
                "mapped-loopback.attacker.example", InetAddress.getByName("::ffff:127.0.0.1"),
                "mapped-private10.attacker.example", InetAddress.getByName("::ffff:10.0.0.5"),
                "mapped-private192.attacker.example", InetAddress.getByName("::ffff:192.168.1.1"),
                "mapped-metadata.attacker.example", InetAddress.getByName("::ffff:169.254.169.254"),
                "mapped-cgnat.attacker.example", InetAddress.getByName("::ffff:100.64.0.1"),
                "mapped-zero.attacker.example", InetAddress.getByName("::ffff:0.0.0.1"));
        OutboundUrlGuard guard =
                new OutboundUrlGuard(false, h -> new InetAddress[] {mapped.get(h.toLowerCase(Locale.ROOT))});
        assertThatThrownBy(() -> guard.assertSafe("https://" + host + "/hook"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("disallowed network");
    }
}
