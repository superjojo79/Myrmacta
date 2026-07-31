package com.flowforge.domain.dossier.model;

public class InactiveDossierTypeException extends RuntimeException {
    private final String dossierTypeCode;

    public InactiveDossierTypeException(String dossierTypeCode) {
        super("Dossier type is inactive: " + dossierTypeCode);
        this.dossierTypeCode = dossierTypeCode;
    }

    public String dossierTypeCode() {
        return dossierTypeCode;
    }
}
