package be.myrmacta.infrastructure.connector;

import be.myrmacta.domain.connector.model.ConnectorInstanceData;
import be.myrmacta.domain.connector.port.in.ConnectorTriggerRuntime;
import be.myrmacta.domain.connector.port.out.ConnectorInstanceRepository;
import be.myrmacta.domain.connector.port.out.ConnectorScheduleStateRepository;
import be.myrmacta.infrastructure.config.InboundConnectorProperties;
import be.myrmacta.infrastructure.config.TenantProperties;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * TODO §87 (D3c, D3d) — interroge les instances activées des connecteurs d'entrée par interrogation ({@code imap-inbound},
 * {@code azure-service-bus}) à leur propre cadence ({@code pollSeconds}). Même mécanique que {@link BatchImportConnectorTriggerJob} :
 * créneau pris par comparaison-échange avant l'interrogation (deux exécutants ne lisent pas la même boîte ni la même file en même
 * temps, D5), conservé si l'interrogation échoue (réessai à l'intervalle de l'instance) ; chaque instance est isolée (une boîte en panne n'empêche pas les autres).
 */
@Component
class InboundPollingConnectorTriggerJob {

    private static final Logger log = LoggerFactory.getLogger(InboundPollingConnectorTriggerJob.class);
    static final Set<String> TYPES = Set.of("imap-inbound", "graph-mail-inbound", "azure-service-bus", "sftp-pdf-inbound");
    private static final int DEFAULT_POLL_SECONDS = 60;

    private final ConnectorInstanceRepository connectorInstanceRepository;
    private final ConnectorScheduleStateRepository scheduleStateRepository;
    private final ConnectorTriggerRuntime connectorTriggerRuntime;
    private final TenantProperties tenantProperties;
    private final InboundConnectorProperties inbound;

    InboundPollingConnectorTriggerJob(
            ConnectorInstanceRepository connectorInstanceRepository,
            ConnectorScheduleStateRepository scheduleStateRepository,
            ConnectorTriggerRuntime connectorTriggerRuntime,
            TenantProperties tenantProperties,
            InboundConnectorProperties inbound) {
        this.connectorInstanceRepository = Objects.requireNonNull(connectorInstanceRepository, "connectorInstanceRepository");
        this.scheduleStateRepository = Objects.requireNonNull(scheduleStateRepository, "scheduleStateRepository");
        this.connectorTriggerRuntime = Objects.requireNonNull(connectorTriggerRuntime, "connectorTriggerRuntime");
        this.tenantProperties = Objects.requireNonNull(tenantProperties, "tenantProperties");
        this.inbound = Objects.requireNonNull(inbound, "inbound");
    }

    @Scheduled(fixedDelayString = "${app.inbound.poll-delay:15s}", initialDelayString = "${app.inbound.initial-delay:30s}")
    void poll() {
        process(Instant.now());
    }

    void process(Instant tickTime) {
        Instant now = tickTime.truncatedTo(ChronoUnit.MICROS);
        for (ConnectorInstanceData instance : connectorInstanceRepository.listByTenantId(tenantProperties.id())) {
            if (!TYPES.contains(instance.connectorType()) || !instance.enabled()) {
                continue;
            }
            Optional<Instant> last = scheduleStateRepository.findLastTriggeredAt(instance.id());
            if (!isDue(instance, last, now)) {
                continue;
            }
            if (!scheduleStateRepository.claimTrigger(instance.id(), last.orElse(null), now)) {
                continue;
            }
            try {
                connectorTriggerRuntime.onIncomingEvent(instance, Map.of());
            } catch (RuntimeException | StackOverflowError failure) {
                // Le créneau n'est PAS rendu (revue D3c/D3d, M4) : une instance en échec permanent (mot de passe expiré, hôte
                // injoignable) n'est réessayée qu'au bout de son propre intervalle, jamais à chaque tick (pas de martèlement,
                // pas de verrouillage de compte). Message neutre : le détail peut citer l'hôte, jamais un secret.
                log.warn("{}: instance {} failed this poll, next attempt in {} s: {}", instance.connectorType(), instance.id(),
                        Math.max(pollSeconds(instance.config()), inbound.minPollSeconds()), failure.getMessage());
            }
        }
    }

    private boolean isDue(ConnectorInstanceData instance, Optional<Instant> last, Instant now) {
        if (last.isEmpty()) {
            return true;
        }
        int seconds = pollSeconds(instance.config());
        return !last.get().plus(Duration.ofSeconds(Math.max(seconds, inbound.minPollSeconds()))).isAfter(now);
    }

    private static int pollSeconds(Map<String, Object> config) {
        Object value = config.get("pollSeconds");
        if (value == null) {
            return DEFAULT_POLL_SECONDS;
        }
        try {
            return Integer.parseInt(String.valueOf(value).trim());
        } catch (NumberFormatException ex) {
            return DEFAULT_POLL_SECONDS;
        }
    }
}
