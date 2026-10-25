package be.myrmacta.infrastructure.drafts;

import be.myrmacta.domain.workflow.port.out.BpmnDraftRepository;
import be.myrmacta.domain.workflow.port.out.DmnDraftRepository;
import be.myrmacta.infrastructure.config.DraftRetentionProperties;
import be.myrmacta.infrastructure.config.TenantProperties;
import be.myrmacta.infrastructure.observability.MdcScope;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * TODO §82 (D6, D-SOCLE-RETENTION) — purge les brouillons d'origine automatisée (MCP, assistant) non modifiés depuis
 * {@code app.drafts.retention-days}. Les brouillons de l'éditeur (travail humain) ne sont jamais touchés. Un simple
 * {@code DELETE} conditionnel : idempotent, sans verrou, sûr si deux pods l'exécutent (D5).
 */
@Component
class DraftRetentionJob {

    private static final Logger log = LoggerFactory.getLogger(DraftRetentionJob.class);

    private final BpmnDraftRepository bpmnDrafts;
    private final DmnDraftRepository dmnDrafts;
    private final TenantProperties tenantProperties;
    private final DraftRetentionProperties properties;
    private final Clock clock;

    @org.springframework.beans.factory.annotation.Autowired
    DraftRetentionJob(
            BpmnDraftRepository bpmnDrafts,
            DmnDraftRepository dmnDrafts,
            TenantProperties tenantProperties,
            DraftRetentionProperties properties) {
        this(bpmnDrafts, dmnDrafts, tenantProperties, properties, Clock.systemUTC());
    }

    DraftRetentionJob(
            BpmnDraftRepository bpmnDrafts,
            DmnDraftRepository dmnDrafts,
            TenantProperties tenantProperties,
            DraftRetentionProperties properties,
            Clock clock) {
        this.bpmnDrafts = Objects.requireNonNull(bpmnDrafts, "bpmnDrafts");
        this.dmnDrafts = Objects.requireNonNull(dmnDrafts, "dmnDrafts");
        this.tenantProperties = Objects.requireNonNull(tenantProperties, "tenantProperties");
        this.properties = Objects.requireNonNull(properties, "properties");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    @Scheduled(fixedDelayString = "${app.drafts.poll-delay:6h}", initialDelayString = "${app.drafts.initial-delay:10m}")
    void purgeStaleAutomatedDrafts() {
        if (properties.retentionDays() == 0) {
            return;
        }
        Instant cutoff = clock.instant().minus(Duration.ofDays(properties.retentionDays()));
        try (MdcScope ignored = MdcScope.tenant(tenantProperties.id())) {
            int bpmn = bpmnDrafts.deleteAutomatedOlderThan(tenantProperties.id(), cutoff);
            int dmn = dmnDrafts.deleteAutomatedOlderThan(tenantProperties.id(), cutoff);
            if (bpmn + dmn > 0) {
                log.info("drafts.purge removed {} BPMN and {} DMN automated drafts untouched since {}", bpmn, dmn, cutoff);
            }
        }
    }
}
