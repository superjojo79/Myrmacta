package be.myrmacta.infrastructure.assistant;

import be.myrmacta.domain.assistant.port.out.ConversationRepository;
import be.myrmacta.infrastructure.config.AssistantProperties;
import be.myrmacta.infrastructure.config.TenantProperties;
import be.myrmacta.infrastructure.observability.MdcScope;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * TODO §86 (LC3, D-ASSIST-HISTORY) — purge les conversations non modifiées depuis {@code app.assistant.retention-days}. Un
 * {@code DELETE} conditionnel : idempotent, sans verrou, sûr si deux pods l'exécutent (D5).
 */
@Component
class ConversationRetentionJob {

    private static final Logger log = LoggerFactory.getLogger(ConversationRetentionJob.class);

    private final ConversationRepository repository;
    private final TenantProperties tenantProperties;
    private final AssistantProperties properties;
    private final Clock clock;

    @Autowired
    ConversationRetentionJob(ConversationRepository repository, TenantProperties tenantProperties, AssistantProperties properties) {
        this(repository, tenantProperties, properties, Clock.systemUTC());
    }

    ConversationRetentionJob(
            ConversationRepository repository, TenantProperties tenantProperties, AssistantProperties properties, Clock clock) {
        this.repository = repository;
        this.tenantProperties = tenantProperties;
        this.properties = properties;
        this.clock = clock;
    }

    @Scheduled(fixedDelayString = "${app.assistant.poll-delay:6h}", initialDelayString = "${app.assistant.initial-delay:15m}")
    void purge() {
        Instant cutoff = clock.instant().minus(Duration.ofDays(properties.retentionDays()));
        try (MdcScope ignored = MdcScope.tenant(tenantProperties.id())) {
            int removed = repository.deleteOlderThan(tenantProperties.id(), cutoff);
            if (removed > 0) {
                log.info("assistant.purge removed {} conversations untouched since {}", removed, cutoff);
            }
        }
    }
}
