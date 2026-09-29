package com.flowforge.domain.signature.model;

/**
 * {@code WAITING} : pas encore son tour (mode séquentiel). {@code REVOKED} : la demande s'est fermée
 * avant sa décision.
 */
public enum SignerStatus {
    WAITING,
    PENDING,
    SIGNED,
    DECLINED,
    EXPIRED,
    REVOKED;

    public boolean isOpen() {
        return this == WAITING || this == PENDING;
    }
}
