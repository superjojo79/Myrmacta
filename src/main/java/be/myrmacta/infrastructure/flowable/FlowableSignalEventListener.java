package be.myrmacta.infrastructure.flowable;

import be.myrmacta.domain.connector.model.ConnectorInstanceData;
import be.myrmacta.domain.connector.port.in.ConnectorTriggerRuntime;
import be.myrmacta.domain.connector.port.out.ConnectorInstanceRepository;
import be.myrmacta.infrastructure.config.TenantProperties;
import be.myrmacta.infrastructure.connector.builtin.FlowableSignalConnector;
import jakarta.annotation.PostConstruct;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import org.flowable.common.engine.api.delegate.event.FlowableEngineEventType;
import org.flowable.engine.RuntimeService;
import org.flowable.engine.delegate.event.AbstractFlowableEngineEventListener;
import org.flowable.engine.delegate.event.FlowableSignalEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Global Flowable listener for {@link FlowableEngineEventType#ACTIVITY_SIGNALED} (DD-SIGNAL-HOOK).
 * Matches enabled {@code flowable-signal} instances by {@code config.signalName} and forwards
 * to {@link ConnectorTriggerRuntime} (outbox only). Soft-deleted / disabled instances are ignored.
 */
@Component
public class FlowableSignalEventListener extends AbstractFlowableEngineEventListener {

    private static final Logger log = LoggerFactory.getLogger(FlowableSignalEventListener.class);

    private final RuntimeService runtimeService;
    private final ConnectorInstanceRepository repository;
    private final ConnectorTriggerRuntime triggerRuntime;
    private final TenantProperties tenantProperties;

    public FlowableSignalEventListener(
            RuntimeService runtimeService,
            ConnectorInstanceRepository repository,
            ConnectorTriggerRuntime triggerRuntime,
            TenantProperties tenantProperties) {
        super(Set.of(FlowableEngineEventType.ACTIVITY_SIGNALED));
        this.runtimeService = Objects.requireNonNull(runtimeService, "runtimeService");
        this.repository = Objects.requireNonNull(repository, "repository");
        this.triggerRuntime = Objects.requireNonNull(triggerRuntime, "triggerRuntime");
        this.tenantProperties = Objects.requireNonNull(tenantProperties, "tenantProperties");
    }

    @PostConstruct
    void register() {
        runtimeService.addEventListener(this, FlowableEngineEventType.ACTIVITY_SIGNALED);
    }

    @Override
    protected void activitySignaled(FlowableSignalEvent event) {
        String signalName = event.getSignalName();
        if (signalName == null || signalName.isBlank()) {
            return;
        }
        String tenantId = tenantProperties.id();
        Map<String, Object> payload = buildPayload(event);
        for (ConnectorInstanceData instance : repository.listByTenantId(tenantId)) {
            if (!instance.enabled()) {
                continue;
            }
            if (!FlowableSignalConnector.TYPE.equals(instance.connectorType())) {
                continue;
            }
            Object configured = instance.config() == null ? null : instance.config().get("signalName");
            if (!signalName.equals(String.valueOf(configured))) {
                continue;
            }
            try {
                triggerRuntime.onIncomingEvent(instance, payload);
            } catch (RuntimeException ex) {
                log.warn(
                        "flowable-signal TRIGGER failed for instance {} signal {}: {}",
                        instance.id(),
                        signalName,
                        ex.getMessage());
            }
        }
    }

    private static Map<String, Object> buildPayload(FlowableSignalEvent event) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("signalName", event.getSignalName());
        payload.put("executionId", event.getExecutionId());
        payload.put("processInstanceId", event.getProcessInstanceId());
        payload.put("activityId", event.getActivityId());
        Object signalData = event.getSignalData();
        if (signalData instanceof Map<?, ?> map) {
            Map<String, Object> variables = new LinkedHashMap<>();
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                variables.put(String.valueOf(entry.getKey()), entry.getValue());
            }
            payload.put("variables", variables);
            payload.put("signalData", variables);
        } else if (signalData != null) {
            payload.put("signalData", signalData);
            payload.put("variables", Map.of());
        } else {
            payload.put("variables", Map.of());
        }
        return payload;
    }

    @Override
    public boolean isFailOnException() {
        // Connector TRIGGER failures must not abort the BPMN signal delivery path.
        return false;
    }
}
