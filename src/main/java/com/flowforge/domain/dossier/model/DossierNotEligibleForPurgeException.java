package com.flowforge.domain.dossier.model;

public class DossierNotEligibleForPurgeException extends RuntimeException {
    public DossierNotEligibleForPurgeException(String message) {
        super(message);
    }
}
