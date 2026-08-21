package com.flowforge.domain.dossier.model;

public record ExportDossiersQuery(String dossierTypeCode, DossierStatus status, String referencePrefix) {

    public ExportDossiersQuery {
        if (dossierTypeCode == null || dossierTypeCode.isBlank()) {
            throw new IllegalArgumentException("dossierTypeCode is required");
        }
        if (referencePrefix != null) {
            referencePrefix = referencePrefix.isBlank() ? null : referencePrefix.trim();
        }
    }
}
