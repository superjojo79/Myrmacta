package com.flowforge.domain.dossier.model;

/** Lifecycle of a {@link PurgeOutboxRecord} (D-STATUS). */
public enum PurgeOutboxStatus {
    PENDING,
    PROCESSING,
    COMPLETED,
    FAILED
}
