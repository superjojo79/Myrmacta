package be.myrmacta.domain.workflow.model;

import java.time.Instant;

public record DecisionTableVersionInfo(String decisionKey, int version, String deploymentId, Instant deployedAt) {}
