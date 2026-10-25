package be.myrmacta.infrastructure.config;

import be.myrmacta.domain.precheck.port.out.PieceTextExtractorPort;
import be.myrmacta.domain.precheck.port.out.PrecheckModelPort;
import be.myrmacta.infrastructure.precheck.PrecheckInfrastructure;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * TODO §106 (issue #211) — pré-vérification IA. Le modèle n'est construit que si {@code app.precheck.enabled=true} ;
 * sinon tout appel est refusé sans requête sortante.
 */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(PrecheckProperties.class)
public class PrecheckConfig {

    @Bean
    PrecheckModelPort precheckModelPort(PrecheckProperties properties) {
        return PrecheckInfrastructure.model(properties);
    }

    @Bean
    PieceTextExtractorPort pieceTextExtractorPort() {
        return PrecheckInfrastructure.textExtractor();
    }
}
