package be.myrmacta.infrastructure.security;

import be.myrmacta.infrastructure.config.SecurityHeadersProperties;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import java.util.List;
import java.util.regex.Pattern;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * Adresse du client pour les limiteurs anonymes (portail, rapports CSP) — revue adversariale du
 * portail, constat C2.
 *
 * <p>{@code server.forward-headers-strategy: framework} fait réécrire {@code getRemoteAddr()} par
 * {@code ForwardedHeaderFilter}, qui <strong>préfère</strong> l'en-tête RFC 7239 {@code Forwarded}
 * à {@code X-Forwarded-For} et fait confiance à n'importe quel pair. Le proxy documenté
 * (docs/guides/DEPLOIEMENT.md) écrase {@code X-Forwarded-For} mais laisse passer {@code Forwarded} : un client
 * choisissait donc la clé de sa propre limite. Règle ici, indépendante de ces réécritures :
 *
 * <ul>
 *   <li>pair TCP (sous les wrappers) hors de {@code app.security.headers.trusted-proxy-cidrs} :
 *       c'est le client, aucun en-tête n'est lu ;
 *   <li>pair de confiance : entrée la plus à droite de {@code X-Forwarded-For} qui n'est pas
 *       elle-même un proxy de confiance (le proxy ajoute l'adresse qu'il voit à droite ; ce qui est
 *       à gauche vient du client). {@code Forwarded} n'est jamais lu ;
 *   <li>seuls des littéraux IP sont acceptés — jamais de résolution DNS d'un nom fourni par le
 *       client ; une entrée non littérale arrête la lecture et le pair sert de clé.
 * </ul>
 */
@Component
public class ClientIpResolver {

    private static final Pattern IPV4 = Pattern.compile("\\d{1,3}(\\.\\d{1,3}){3}");
    private static final Pattern IPV6 = Pattern.compile("[0-9A-Fa-f:.]*:[0-9A-Fa-f:.]*");

    private final TrustedProxyCidrMatcher trustedProxies;

    @Autowired
    public ClientIpResolver(SecurityHeadersProperties properties) {
        this(properties.trustedProxyCidrs());
    }

    ClientIpResolver(List<String> trustedProxyCidrs) {
        this.trustedProxies = new TrustedProxyCidrMatcher(trustedProxyCidrs);
    }

    public String resolve(HttpServletRequest request) {
        HttpServletRequest raw = unwrap(request);
        String peer = raw.getRemoteAddr();
        if (!trustedProxies.matches(peer)) {
            return peer;
        }
        String forwardedFor = raw.getHeader("X-Forwarded-For");
        if (forwardedFor == null || forwardedFor.isBlank()) {
            return peer;
        }
        String[] hops = forwardedFor.split(",");
        String leftmostTrusted = peer;
        for (int i = hops.length - 1; i >= 0; i--) {
            String hop = hops[i].strip();
            if (!isIpLiteral(hop)) {
                return peer;
            }
            if (!trustedProxies.matches(hop)) {
                return hop;
            }
            leftmostTrusted = hop;
        }
        return leftmostTrusted;
    }

    static boolean isIpLiteral(String value) {
        return value.length() <= 45 && (IPV4.matcher(value).matches() || IPV6.matcher(value).matches());
    }

    private static HttpServletRequest unwrap(HttpServletRequest request) {
        HttpServletRequest current = request;
        while (current instanceof HttpServletRequestWrapper wrapper
                && wrapper.getRequest() instanceof HttpServletRequest inner) {
            current = inner;
        }
        return current;
    }
}
