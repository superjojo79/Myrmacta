package be.myrmacta.infrastructure.security;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.List;

/**
 * Matches an IP literal against a configured allowlist of CIDR ranges
 * ({@code app.security.headers.trusted-proxy-cidrs}, adversarial review follow-up on
 * A4-HSTS-001). Replaces the original hardcoded {@code isLoopbackAddress() ||
 * isSiteLocalAddress()} check : that heuristic trusts <em>any</em> RFC1918 peer as the
 * reverse-proxy, which also covers typical Docker bridge / Kubernetes pod CIDRs -- fine for
 * the documented single-host Docker+Apache model, silently too broad if that model changes.
 * Parsing is eager (constructor time) so a misconfigured CIDR fails fast at boot rather than
 * silently never matching.
 */
final class TrustedProxyCidrMatcher {

    private final List<CidrRange> ranges;

    TrustedProxyCidrMatcher(List<String> cidrs) {
        this.ranges = cidrs.stream().map(CidrRange::parse).toList();
    }

    boolean matches(String ipLiteral) {
        if (ipLiteral == null || ipLiteral.isBlank()) {
            return false;
        }
        InetAddress address;
        try {
            address = InetAddress.getByName(ipLiteral);
        } catch (UnknownHostException ex) {
            return false;
        }
        return ranges.stream().anyMatch(range -> range.contains(address));
    }

    private record CidrRange(byte[] network, int prefixLength) {

        static CidrRange parse(String cidr) {
            String[] parts = cidr.split("/", 2);
            byte[] network;
            try {
                network = InetAddress.getByName(parts[0]).getAddress();
            } catch (UnknownHostException ex) {
                throw new IllegalArgumentException("Invalid CIDR literal: " + cidr, ex);
            }
            int prefixLength = parts.length == 2 ? Integer.parseInt(parts[1]) : network.length * 8;
            if (prefixLength < 0 || prefixLength > network.length * 8) {
                throw new IllegalArgumentException("Invalid CIDR prefix length: " + cidr);
            }
            return new CidrRange(network, prefixLength);
        }

        boolean contains(InetAddress candidate) {
            byte[] candidateBytes = candidate.getAddress();
            if (candidateBytes.length != network.length) {
                return false;
            }
            int fullBytes = prefixLength / 8;
            int remainingBits = prefixLength % 8;
            for (int i = 0; i < fullBytes; i++) {
                if (candidateBytes[i] != network[i]) {
                    return false;
                }
            }
            if (remainingBits == 0) {
                return true;
            }
            int mask = (0xFF << (8 - remainingBits)) & 0xFF;
            return (candidateBytes[fullBytes] & mask) == (network[fullBytes] & mask);
        }
    }
}
