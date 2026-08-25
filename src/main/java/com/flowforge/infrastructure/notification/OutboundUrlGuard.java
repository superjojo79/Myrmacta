package com.flowforge.infrastructure.notification;

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

        public String requestUrl() {
            String host = address.getHostAddress();
            if (address instanceof java.net.Inet6Address && !host.startsWith("[")) {
                host = "[" + host + "]";
            }
            try {
                return new URI(
                                original.getScheme(),
                                original.getUserInfo(),
                                host,
                                original.getPort(),
                                original.getPath(),
                                original.getQuery(),
                                original.getFragment())
                        .toString();
            } catch (java.net.URISyntaxException ex) {
                throw new IllegalArgumentException("Webhook URL is malformed", ex);
            }
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
            try {
                return new PinnedEndpoint(uri, InetAddress.getByName(normalizedHost));
            } catch (UnknownHostException ex) {
                throw new IllegalArgumentException("Webhook URL host cannot be resolved: " + host, ex);
            }
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
        // IPv6 unique local addresses fc00::/7 — not covered by isSiteLocalAddress().
        return bytes.length == 16 && (bytes[0] & 0xfe) == 0xfc;
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
        if (octets[0] == 0 && octets[1] == 0 && octets[2] == 0 && octets[3] == 0) {
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
