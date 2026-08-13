package com.flowforge.infrastructure.flowable;

import com.flowforge.domain.dossier.port.out.DossierWorkflowLinkRepository;
import com.flowforge.domain.notification.model.NotificationChannel;
import com.flowforge.domain.notification.model.NotificationEvent;
import com.flowforge.domain.notification.port.in.SendNotificationUseCase;
import com.flowforge.domain.workflow.port.out.WorkflowParticipantPort;
import com.flowforge.infrastructure.config.TenantProperties;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.flowable.common.engine.api.delegate.event.FlowableEvent;
import org.flowable.common.engine.api.delegate.event.FlowableEventListener;
import org.flowable.common.engine.api.delegate.event.FlowableEventType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

@Component
public class WorkflowStateEventListener implements FlowableEventListener {

    static final String WORKFLOW_STATE_CHANGED = "WORKFLOW_STATE_CHANGED";

    private static final Logger LOGGER = LoggerFactory.getLogger(WorkflowStateEventListener.class);

    private final TenantProperties tenantProperties;
    private final DossierWorkflowLinkRepository linkRepository;
    private final WorkflowParticipantPort participantPort;
    private final SendNotificationUseCase sendNotificationUseCase;

    WorkflowStateEventListener(
            TenantProperties tenantProperties,
            DossierWorkflowLinkRepository linkRepository,
            @Lazy WorkflowParticipantPort participantPort,
            SendNotificationUseCase sendNotificationUseCase) {
        this.tenantProperties = Objects.requireNonNull(tenantProperties, "tenantProperties");
        this.linkRepository = Objects.requireNonNull(linkRepository, "linkRepository");
        this.participantPort = Objects.requireNonNull(participantPort, "participantPort");
        this.sendNotificationUseCase = Objects.requireNonNull(sendNotificationUseCase, "sendNotificationUseCase");
    }

    @Override
    public void onEvent(FlowableEvent event) {
        if (!isWorkflowStateEvent(event.getType())) {
            return;
        }
        String processInstanceId = processInstanceId(event);
        if (processInstanceId == null || processInstanceId.isBlank()) {
            return;
        }
        try {
            linkRepository.findByProcessInstanceId(tenantProperties.id(), processInstanceId)
                    .ifPresent(link -> {
                        // Only linkId is exposed — the frontend never sees the raw Flowable
                        // process instance id (see WorkflowLinkVisualizationController, DD-URL).
                        Map<String, Object> payload = Map.of(
                                "type", WORKFLOW_STATE_CHANGED,
                                "linkId", link.id().toString());
                        participantPort.findParticipantUserIds(link).forEach(userId -> sendNotificationUseCase.send(
                                new NotificationEvent(
                                        List.of(NotificationChannel.BROWSER),
                                        null,
                                        null,
                                        null,
                                        userId,
                                        null,
                                        payload),
                                tenantProperties.id()));
                    });
        } catch (RuntimeException ex) {
            LOGGER.warn(
                    "Failed to dispatch workflow state notification for tenantId={} processInstanceId={}",
                    tenantProperties.id(),
                    processInstanceId,
                    ex);
        }
    }

    @Override
    public boolean isFailOnException() {
        return false;
    }

    @Override
    public boolean isFireOnTransactionLifecycleEvent() {
        return false;
    }

    @Override
    public String getOnTransaction() {
        return null;
    }

    private static boolean isWorkflowStateEvent(FlowableEventType type) {
        String name = type.name();
        return "TASK_ASSIGNED".equals(name)
                || "ACTIVITY_COMPLETED".equals(name)
                || "PROCESS_COMPLETED".equals(name);
    }

    private static String processInstanceId(FlowableEvent event) {
        try {
            Method method = event.getClass().getMethod("getProcessInstanceId");
            method.setAccessible(true);
            Object value = method.invoke(event);
            return value == null ? null : value.toString();
        } catch (ReflectiveOperationException ex) {
            return null;
        }
    }
}
