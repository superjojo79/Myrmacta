package be.myrmacta.support;

import be.myrmacta.infrastructure.notification.OutboundUrlGuard;
import java.net.InetAddress;
import java.util.Arrays;
import java.util.Locale;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.core.env.Environment;

/**
 * {@link OutboundUrlGuard} dont la résolution DNS est figée — sans elle, tout test plein contexte
 * qui enregistre un webhook {@code https://example.com/...} dépend du résolveur du poste, et
 * échoue (400 « host cannot be resolved ») derrière un DNS qui filtre les domaines réservés
 * (box, DNS d'entreprise). Tout nom d'hôte résout vers {@link #PUBLIC_TEST_ADDRESS} : adresse
 * publique au sens du guard (non bloquée), mais TEST-NET-3 (RFC 5737), jamais routée. Les règles
 * SSRF (schéma, littéraux, plages privées) restent celles du guard réel, même calcul de profil
 * que {@code NotificationConfig}.
 */
@TestConfiguration
public class StubPublicDnsTestConfiguration {

    public static final String PUBLIC_TEST_ADDRESS = "203.0.113.10";

    @Bean
    @Primary
    OutboundUrlGuard stubPublicDnsOutboundUrlGuard(Environment environment) {
        boolean allowLocalHttp = Arrays.stream(environment.getActiveProfiles())
                .map(profile -> profile.toLowerCase(Locale.ROOT))
                .anyMatch(profile -> profile.equals("test") || profile.equals("local"));
        return new OutboundUrlGuard(
                allowLocalHttp, host -> new InetAddress[] {InetAddress.getByName(PUBLIC_TEST_ADDRESS)});
    }
}
