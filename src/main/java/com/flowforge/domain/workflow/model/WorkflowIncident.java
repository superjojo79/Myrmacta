package com.flowforge.domain.workflow.model;

import java.time.Instant;
import java.util.Objects;

/**
 * TODO §94 — job en échec d'une instance de process. {@code since} est la date de l'état courant du job (Flowable ne
 * conserve pas d'horodatage d'échec distinct). {@code message} est le message de l'exception, tronqué ; il peut contenir
 * des données métier selon la brique, d'où {@link #withoutMessage()} (D-INCIDENT-MESSAGE).
 */
public record WorkflowIncident(
        String jobId,
        String processInstanceId,
        String activityId,
        String activityName,
        IncidentState state,
        Instant since,
        int retriesLeft,
        String message) {

    public static final int MAX_MESSAGE_LENGTH = 300;

    public WorkflowIncident {
        Objects.requireNonNull(jobId, "jobId");
        Objects.requireNonNull(processInstanceId, "processInstanceId");
        Objects.requireNonNull(state, "state");
        retriesLeft = Math.max(0, retriesLeft);
        if (message != null && message.codePointCount(0, message.length()) > MAX_MESSAGE_LENGTH) {
            message = message.substring(0, message.offsetByCodePoints(0, MAX_MESSAGE_LENGTH)) + "…";
        }
    }

    public WorkflowIncident withoutMessage() {
        return message == null ? this
                : new WorkflowIncident(jobId, processInstanceId, activityId, activityName, state, since, retriesLeft, null);
    }
}
