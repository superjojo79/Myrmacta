package com.flowforge.domain.dossier.model;

public class DossierConflictException extends RuntimeException {
    public DossierConflictException(String message) {
        super(message);
    }
}
