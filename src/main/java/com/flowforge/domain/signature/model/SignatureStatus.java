package com.flowforge.domain.signature.model;

public enum SignatureStatus {
    PENDING,
    SIGNED,
    DECLINED,
    EXPIRED,
    FAILED,
    CANCELLED;

    public boolean isTerminal() {
        return this != PENDING;
    }

    /** Issue correspondant à un statut terminal ; {@code null} tant que la demande est {@code PENDING}. */
    public SignatureOutcome outcome() {
        return this == PENDING ? null : SignatureOutcome.valueOf(name());
    }
}
