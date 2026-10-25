package be.myrmacta.infrastructure.tenant;

import java.util.regex.Pattern;

/**
 * Valide un identifiant de tenant au provisioning et au démarrage de l'instance.
 * Pattern aligné sur les contraintes d'identifiant PostgreSQL (décision multi-tenant).
 */
public final class TenantIdValidator {

    private static final Pattern TENANT_ID_PATTERN = Pattern.compile("^[a-z][a-z0-9_]{2,62}$");

    private TenantIdValidator() {}

    public static void validate(String tenantId) {
        if (tenantId == null || !TENANT_ID_PATTERN.matcher(tenantId).matches()) {
            throw new InvalidTenantIdException(tenantId);
        }
    }
}
