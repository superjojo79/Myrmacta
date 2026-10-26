package be.myrmacta.infrastructure.config;

import be.myrmacta.domain.document.port.out.DocumentStoragePort;
import be.myrmacta.domain.dossier.DossierAccessPolicy;
import be.myrmacta.domain.dossier.DossierFieldRules;
import be.myrmacta.domain.dossier.port.out.DossierElementRepository;
import be.myrmacta.domain.dossier.port.out.DossierRepository;
import be.myrmacta.domain.dossier.port.out.DossierTypeRepository;
import be.myrmacta.domain.precheck.PrecheckBudget;
import be.myrmacta.domain.precheck.PrecheckEngine;
import be.myrmacta.domain.precheck.port.in.ManageDossierPrechecksUseCase;
import be.myrmacta.domain.precheck.port.in.RunDossierPrecheckUseCase;
import be.myrmacta.domain.precheck.port.out.ModelOutputReaderPort;
import be.myrmacta.domain.precheck.port.out.PieceTextExtractorPort;
import be.myrmacta.domain.precheck.port.out.PrecheckAvailabilityPort;
import be.myrmacta.domain.precheck.port.out.PrecheckModelPort;
import be.myrmacta.domain.precheck.port.out.PrecheckRunRepository;
import be.myrmacta.domain.precheck.port.out.PrecheckVariablesPort;
import be.myrmacta.domain.precheck.usecase.ManageDossierPrechecksUseCaseImpl;
import be.myrmacta.domain.precheck.usecase.RunDossierPrecheckUseCaseImpl;
import be.myrmacta.infrastructure.precheck.PrecheckInfrastructure;
import java.time.Clock;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import tools.jackson.databind.ObjectMapper;

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

    @Bean
    PrecheckAvailabilityPort precheckAvailabilityPort(PrecheckProperties properties) {
        return properties::enabled;
    }

    @Bean
    ModelOutputReaderPort modelOutputReaderPort(ObjectMapper mapper) {
        return PrecheckInfrastructure.outputReader(mapper);
    }

    @Bean
    PrecheckBudget precheckBudget(PrecheckProperties properties, PrecheckRunRepository runRepository) {
        return new PrecheckBudget(properties.maxTokensPerDay(), runRepository, Clock.systemUTC());
    }

    @Bean
    PrecheckEngine precheckEngine(TenantProperties tenantProperties, PrecheckProperties properties,
            DossierFieldRules fieldRules, DocumentStoragePort storage, PieceTextExtractorPort extractor,
            PrecheckModelPort model, ModelOutputReaderPort outputReader, PrecheckBudget budget) {
        return new PrecheckEngine(tenantProperties.id(), fieldRules, storage, extractor, model, outputReader, budget,
                properties.limits());
    }

    @Bean
    RunDossierPrecheckUseCase runDossierPrecheckUseCase(TenantProperties tenantProperties,
            DossierRepository dossierRepository, DossierTypeRepository typeRepository,
            DossierElementRepository elementRepository, PrecheckEngine engine, PrecheckRunRepository runRepository,
            PrecheckVariablesPort variablesPort) {
        return new RunDossierPrecheckUseCaseImpl(tenantProperties.id(), dossierRepository, typeRepository,
                elementRepository, engine, runRepository, variablesPort, Clock.systemUTC());
    }

    @Bean
    ManageDossierPrechecksUseCase manageDossierPrechecksUseCase(TenantProperties tenantProperties,
            DossierRepository dossierRepository, DossierAccessPolicy accessPolicy, PrecheckRunRepository runRepository,
            PrecheckAvailabilityPort availability, PrecheckBudget budget,
            @Value("${app.precheck.max-reruns-per-dossier-per-day:5}") int maxRerunsPerDossierPerDay) {
        return new ManageDossierPrechecksUseCaseImpl(tenantProperties.id(), dossierRepository, accessPolicy,
                runRepository, availability, budget, maxRerunsPerDossierPerDay, Clock.systemUTC());
    }
}
