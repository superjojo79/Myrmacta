package be.myrmacta.infrastructure.web.portal;

import be.myrmacta.infrastructure.config.PortalProperties;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

/**
 * Le limiteur pré-auth du portail compte par minute calendaire : avec l'horloge système, un test de
 * quota qui chevauche une frontière de minute voit son compteur remis à zéro (pas de 429, ou moins
 * de refus qu'attendu). Une horloge figée garde toutes les requêtes du contexte dans la même
 * fenêtre — les tests doivent donc utiliser une IP distincte par scénario de quota.
 *
 * <p>Dans ce paquet parce que {@link PortalPreAuthRateLimiter} et son constructeur à horloge sont
 * package-private ; importée par les tests du portail (tâche externe et signature).
 */
@TestConfiguration
public class FixedClockPortalRateLimiterConfig {

    private static final Clock FIXED = Clock.fixed(Instant.parse("2026-01-01T00:00:30Z"), ZoneOffset.UTC);

    @Bean
    @Primary
    PortalPreAuthRateLimiter fixedClockPortalPreAuthRateLimiter(PortalProperties properties) {
        return new PortalPreAuthRateLimiter(properties, FIXED);
    }
}
