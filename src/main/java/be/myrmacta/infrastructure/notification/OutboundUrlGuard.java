package be.myrmacta.infrastructure.notification;

import java.net.InetAddress;
import java.net.URI;
import java.net.UnknownHostException;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import org.springframework.util.StringUtils;

/**
 * SSRF guard for outbound notification webhook URLs.
 *
 * <p>A13: profiles {@code test}/{@code local} may allow {@code http} only to loopback
 * ({@code localhost}, {@code 127.0.0.1}, {@code ::1}). Public {@code http} hosts remain
 * rejected. HTTPS to private/loopback/metadata hosts is always rejected.
 *
 * <p>Literal hosts (dotted/decimal IPv4, IPv6, IPv4-mapped) are checked directly. A DNS
 * name is additionally <b>resolved</b> and every returned address is checked against the
 * same private/loopback/link-local ranges — a literal-only hostname check would let an
 * attacker point a public-looking domain at an internal address (DNS rebinding) and sail
 * straight through. This narrows, but does not eliminate, TOCTOU rebinding between this
 * check and the actual connection; {@link #resolveSafe} returns the validated
 * {@link PinnedEndpoint} so callers can connect to that IP only (D-44).
 */
public class OutboundUrlGuard {

    private static final Set<String> LOOPBACK_HOSTS = Set.of("localhost", "127.0.0.1", "::1");

    @FunctionalInterface
    public interface HostResolver {
        InetAddress[] resolve(String host) throws UnknownHostException;
    }

    private final boolean allowLocalHttp;
    private final HostResolver resolver;

    public OutboundUrlGuard(boolean allowLocalHttp) {
        this(allowLocalHttp, InetAddress::getAllByName);
    }

    public OutboundUrlGuard(boolean allowLocalHttp, HostResolver resolver) {
        this.allowLocalHttp = allowLocalHttp;
        this.resolver = Objects.requireNonNull(resolver, "resolver");
    }

    public record PinnedEndpoint(URI original, InetAddress address) {
        public String originalHost() {
            return original.getHost();
        }

        /**
         * URL de connexion : l'hôte remplacé par l'IP figée, <b>tout le reste en composants bruts</b>. TODO §112 (revue) :
         * le constructeur multi-arguments de {@link URI} prend des composants décodés — {@code %2F} y redevenait {@code /}
         * et {@code %26} {@code &}, ce qui laissait une valeur encodée sortir de son segment de chemin ou de son
         * paramètre de requête.
         */
        public String requestUrl() {
            String host = address.getHostAddress();
            if (address instanceof java.net.Inet6Address && !host.startsWith("[")) {
                host = "[" + host + "]";
            }
            StringBuilder out = new StringBuilder(original.getScheme()).append("://");
            if (original.getRawUserInfo() != null) {
                out.append(original.getRawUserInfo()).append('@');
            }
            out.append(host);
            if (original.getPort() != -1) {
                out.append(':').append(original.getPort());
            }
            if (original.getRawPath() != null) {
                out.append(original.getRawPath());
            }
            if (original.getRawQuery() != null) {
                out.append('?').append(original.getRawQuery());
            }
            if (original.getRawFragment() != null) {
                out.append('#').append(original.getRawFragment());
            }
            return out.toString();
        }
    }

    public void assertSafe(String url) {
        resolveSafe(url);
    }

    /**
     * Validates the URL then returns the IP chosen for the connection (D-44 pin).
     */
    public PinnedEndpoint resolveSafe(String url) {
        if (!StringUtils.hasText(url)) {
            throw new IllegalArgumentException("Webhook URL must not be null or blank");
        }
        URI uri;
        try {
            uri = URI.create(url.trim());
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException("Webhook URL is malformed", ex);
        }
        String scheme = uri.getScheme();
        if (scheme == null) {
            throw new IllegalArgumentException("Webhook URL is malformed");
        }
        String schemeLower = scheme.toLowerCase(Locale.ROOT);
        boolean http = "http".equals(schemeLower);
        boolean https = "https".equals(schemeLower);
        if (!http && !https) {
            throw new IllegalArgumentException("Webhook URL must use https");
        }

        String host = uri.getHost();
        if (host == null || host.isBlank()) {
            host = extractAuthorityHost(url.trim());
        }
        if (host == null || host.isBlank()) {
            throw new IllegalArgumentException("Webhook URL is malformed");
        }
        String normalizedHost = stripIpv6Brackets(host).toLowerCase(Locale.ROOT);
        boolean loopback = isLoopbackHost(normalizedHost);

        if (http) {
            if (!allowLocalHttp || !loopback) {
                throw new IllegalArgumentException(
                        allowLocalHttp
                                ? "Webhook http URL is only allowed for localhost in test/local profiles"
                                : "Webhook URL must use https");
            }
            return new PinnedEndpoint(uri, loopbackAddress(normalizedHost));
        }

        if (isBlockedHost(normalizedHost)) {
            throw new IllegalArgumentException("Webhook URL host is not allowed");
        }
        if (isLiteralAddress(normalizedHost)) {
            InetAddress literal;
            try {
                literal = InetAddress.getByName(normalizedHost);
            } catch (UnknownHostException ex) {
                throw new IllegalArgumentException("Webhook URL host cannot be resolved: " + host, ex);
            }
            // TODO §121 (revue de #33) : la graphie de l'hôte ne suffit pas (plusieurs graphies IPv6 désignent la même
            // adresse). Ce qui compte est l'adresse que l'on fige : elle subit le même contrôle qu'une adresse résolue.
            if (isBlockedResolvedAddress(literal)) {
                throw new IllegalArgumentException("Webhook URL host is not allowed");
            }
            return new PinnedEndpoint(uri, literal);
        }
        InetAddress[] addresses = resolveAll(normalizedHost);
        for (InetAddress address : addresses) {
            if (isBlockedResolvedAddress(address)) {
                throw new IllegalArgumentException(
                        "Webhook URL host resolves to a disallowed network: " + host);
            }
        }
        return new PinnedEndpoint(uri, addresses[0]);
    }

    private InetAddress[] resolveAll(String host) {
        InetAddress[] addresses;
        try {
            addresses = resolver.resolve(host);
        } catch (UnknownHostException ex) {
            throw new IllegalArgumentException("Webhook URL host cannot be resolved: " + host, ex);
        }
        if (addresses == null || addresses.length == 0) {
            throw new IllegalArgumentException("Webhook URL host cannot be resolved: " + host);
        }
        return addresses;
    }

    private static InetAddress loopbackAddress(String normalizedHost) {
        try {
            if ("::1".equals(normalizedHost)) {
                return InetAddress.getByName("::1");
            }
            return InetAddress.getByName("127.0.0.1");
        } catch (UnknownHostException ex) {
            throw new IllegalStateException(ex);
        }
    }

    private static boolean isLiteralAddress(String normalizedHost) {
        return normalizedHost.contains(":") || extractIpv4(normalizedHost) != null;
    }

    private static boolean isBlockedResolvedAddress(InetAddress address) {
        if (address.isLoopbackAddress()
                || address.isLinkLocalAddress()
                || address.isSiteLocalAddress()
                || address.isAnyLocalAddress()
                || address.isMulticastAddress()) {
            return true;
        }
        byte[] bytes = address.getAddress();
        if (bytes.length == 4) {
            int b0 = bytes[0] & 0xff;
            int b1 = bytes[1] & 0xff;
            if (b0 == 0) {
                return true;
            }
            if (b0 == 100 && b1 >= 64 && b1 <= 127) {
                return true;
            }
            int b2 = bytes[2] & 0xff;
            // 198.18.0.0/15 : bancs d'essai, que des proxys « fake-IP » utilisent en interne.
            if (b0 == 198 && (b1 == 18 || b1 == 19)) {
                return true;
            }
            // 192.0.0.0/24 : protocole IETF. 240.0.0.0/4 : réservé, diffusion 255.255.255.255 comprise.
            if ((b0 == 192 && b1 == 0 && b2 == 0) || b0 >= 240) {
                return true;
            }
        }
        if (bytes.length == 16) {
            // IPv6 unique local addresses fc00::/7 — not covered by isSiteLocalAddress().
            if ((bytes[0] & 0xfe) == 0xfc) {
                return true;
            }
            // NAT64 à usage local 64:ff9b:1::/48 (RFC 8215) : l'IPv4 n'y est pas toujours aux mêmes octets, tout est refusé.
            if (bytes[0] == 0x00 && bytes[1] == 0x64 && (bytes[2] & 0xff) == 0xff && (bytes[3] & 0xff) == 0x9b
                    && bytes[4] == 0x00 && bytes[5] == 0x01) {
                return true;
            }
            // Teredo 2001::/32 : l'adresse du serveur et du client sont obfusquées dans l'adresse.
            if (bytes[0] == 0x20 && bytes[1] == 0x01 && bytes[2] == 0x00 && bytes[3] == 0x00) {
                return true;
            }
            return embedsBlockedIpv4(bytes);
        }
        return false;
    }

    /**
     * Une IPv6 qui porte une IPv4 la rejoint par une passerelle ou une pile double : IPv4-compatible ({@code ::/96}),
     * NAT64 ({@code 64:ff9b::/96}) et 6to4 ({@code 2002::/16}). L'IPv4 portée subit le contrôle d'une IPv4. Les formes
     * IPv4-mappées ({@code ::ffff:0:0/96}) sont déjà converties en IPv4 par le JDK.
     */
    private static boolean embedsBlockedIpv4(byte[] bytes) {
        boolean zeroPrefix = true;
        for (int i = 0; i < 12; i++) {
            zeroPrefix &= bytes[i] == 0;
        }
        boolean compatible = zeroPrefix;
        boolean nat64 = bytes[0] == 0x00 && bytes[1] == 0x64 && (bytes[2] & 0xff) == 0xff && (bytes[3] & 0xff) == 0x9b;
        for (int i = 4; i < 12 && nat64; i++) {
            nat64 &= bytes[i] == 0;
        }
        boolean sixToFour = (bytes[0] & 0xff) == 0x20 && bytes[1] == 0x02;
        // IPv4 traduite (SIIT, RFC 6145) : ::ffff:0:a.b.c.d, soit 0:0:0:0:ffff:0:a.b.c.d.
        boolean siit = (bytes[8] & 0xff) == 0xff && (bytes[9] & 0xff) == 0xff && bytes[10] == 0 && bytes[11] == 0;
        for (int i = 0; i < 8 && siit; i++) {
            siit &= bytes[i] == 0;
        }
        byte[] embedded;
        if (compatible || nat64 || siit) {
            embedded = new byte[] {bytes[12], bytes[13], bytes[14], bytes[15]};
        } else if (sixToFour) {
            embedded = new byte[] {bytes[2], bytes[3], bytes[4], bytes[5]};
        } else {
            return false;
        }
        try {
            return isBlockedResolvedAddress(InetAddress.getByAddress(embedded));
        } catch (UnknownHostException impossible) {
            return true;
        }
    }

    private static String extractAuthorityHost(String url) {
        int schemeIdx = url.indexOf("://");
        if (schemeIdx < 0) {
            return null;
        }
        int start = schemeIdx + 3;
        int end = url.length();
        for (int i = start; i < url.length(); i++) {
            char c = url.charAt(i);
            if (c == '/' || c == '?' || c == '#') {
                end = i;
                break;
            }
        }
        String authority = url.substring(start, end);
        int at = authority.lastIndexOf('@');
        if (at >= 0) {
            authority = authority.substring(at + 1);
        }
        if (authority.startsWith("[")) {
            int close = authority.indexOf(']');
            if (close > 0) {
                return authority.substring(0, close + 1);
            }
        }
        int colon = authority.indexOf(':');
        return colon >= 0 ? authority.substring(0, colon) : authority;
    }

    private static boolean isLoopbackHost(String normalizedHost) {
        if (LOOPBACK_HOSTS.contains(normalizedHost)) {
            return true;
        }
        String ipv4 = extractIpv4(normalizedHost);
        return ipv4 != null && isLoopbackIpv4(ipv4);
    }

    private static boolean isBlockedHost(String normalizedHost) {
        if (isLoopbackHost(normalizedHost)) {
            return true;
        }
        if (normalizedHost.endsWith(".local")) {
            return true;
        }
        if (isBlockedIpv6(normalizedHost)) {
            return true;
        }
        String ipv4 = extractIpv4(normalizedHost);
        return ipv4 != null && isBlockedIpv4Literal(ipv4);
    }

    private static boolean isBlockedIpv6(String host) {
        if (!host.contains(":")) {
            return false;
        }
        // IPv4-mapped / IPv4-compatible handled via extractIpv4
        if (host.startsWith("fe80:") || host.equals("fe80::") || host.startsWith("fe80::")) {
            return true;
        }
        // Unique local addresses fc00::/7
        if (host.startsWith("fc") || host.startsWith("fd")) {
            // fc../fd.. only when followed by hex digit or ':'
            if (host.length() >= 2) {
                char c0 = host.charAt(0);
                char c1 = host.charAt(1);
                if ((c0 == 'f') && (c1 == 'c' || c1 == 'd')) {
                    return host.length() == 2 || host.charAt(2) == ':' || isHex(host.charAt(2));
                }
            }
        }
        return false;
    }

    private static boolean isHex(char c) {
        return (c >= '0' && c <= '9') || (c >= 'a' && c <= 'f');
    }

    /**
     * Returns dotted IPv4 if host is dotted quad, IPv4-mapped IPv6, or decimal IPv4.
     */
    private static String extractIpv4(String host) {
        if (host.matches("\\d+")) {
            try {
                long value = Long.parseLong(host);
                if (value < 0 || value > 0xffff_ffffL) {
                    return null;
                }
                return String.format(
                        "%d.%d.%d.%d",
                        (value >> 24) & 0xff, (value >> 16) & 0xff, (value >> 8) & 0xff, value & 0xff);
            } catch (NumberFormatException ex) {
                return null;
            }
        }
        String mapped = host;
        int mappedIdx = host.lastIndexOf(":ffff:");
        if (mappedIdx >= 0) {
            mapped = host.substring(mappedIdx + 6);
        } else if (host.startsWith("::ffff:")) {
            mapped = host.substring(7);
        }
        if (mapped.matches("\\d+\\.\\d+\\.\\d+\\.\\d+")) {
            return mapped;
        }
        return null;
    }

    private static boolean isLoopbackIpv4(String host) {
        String[] parts = host.split("\\.");
        if (parts.length != 4) {
            return false;
        }
        try {
            return Integer.parseInt(parts[0]) == 127;
        } catch (NumberFormatException ex) {
            return false;
        }
    }

    private static String stripIpv6Brackets(String host) {
        if (host.startsWith("[") && host.endsWith("]") && host.length() > 2) {
            return host.substring(1, host.length() - 1);
        }
        return host;
    }

    private static boolean isBlockedIpv4Literal(String host) {
        String[] parts = host.split("\\.");
        if (parts.length != 4) {
            return false;
        }
        int[] octets = new int[4];
        for (int i = 0; i < 4; i++) {
            try {
                octets[i] = Integer.parseInt(parts[i]);
            } catch (NumberFormatException ex) {
                return false;
            }
            if (octets[i] < 0 || octets[i] > 255) {
                return false;
            }
        }
        if (octets[0] == 100 && octets[1] >= 64 && octets[1] <= 127) {
            return true;
        }
        if (octets[0] == 0) {
            return true;
        }
        if (octets[0] == 10) {
            return true;
        }
        if (octets[0] == 127) {
            return true;
        }
        if (octets[0] == 169 && octets[1] == 254) {
            return true;
        }
        if (octets[0] == 192 && octets[1] == 168) {
            return true;
        }
        if (octets[0] == 172 && octets[1] >= 16 && octets[1] <= 31) {
            return true;
        }
        return false;
    }
}
