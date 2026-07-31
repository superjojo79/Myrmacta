package com.flowforge.domain.dossier.model;

public class DossierNotFoundException extends RuntimeException {
    public DossierNotFoundException(String message) {
        super(message);
    }
}
