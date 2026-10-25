package be.myrmacta.infrastructure.tenant;

/**
 * Identifiant de tenant non conforme à la regex d'identifiant PostgreSQL / provisioning.
 */
public final class InvalidTenantIdException extends RuntimeException {

    public InvalidTenantIdException(String tenantId) {
        super("Invalid tenant id: " + (tenantId == null ? "null" : tenantId));
    }
}
