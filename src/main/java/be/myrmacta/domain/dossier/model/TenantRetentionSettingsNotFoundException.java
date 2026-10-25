package be.myrmacta.domain.dossier.model;

public final class TenantRetentionSettingsNotFoundException extends RuntimeException {

    public TenantRetentionSettingsNotFoundException(String tenantId) {
        super("Retention settings not found for tenant '" + tenantId + "'");
    }
}
