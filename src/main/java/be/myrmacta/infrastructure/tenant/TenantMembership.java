package be.myrmacta.infrastructure.tenant;

import be.myrmacta.infrastructure.config.TenantProperties;
import java.util.Objects;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;

/**
 * Règle unique « l'utilisateur appartient au tenant de cette instance » : claim {@code tenant_id} présent et égal à
 * {@link TenantProperties#id()}. Partagée par {@link TenantResolutionFilter} (chaque requête) et par l'enregistrement
 * dans l'annuaire au retour de connexion (TODO §104, A6), qui s'exécute avant que le filtre ne voie l'utilisateur.
 */
public final class TenantMembership {

    private final String tenantId;

    public TenantMembership(TenantProperties tenantProperties) {
        this.tenantId = Objects.requireNonNull(tenantProperties, "tenantProperties").id();
    }

    public boolean belongs(OidcUser user) {
        return tenantId.equals(claimedTenantId(user));
    }

    public static String claimedTenantId(OidcUser user) {
        return user.getClaimAsString("tenant_id");
    }
}
