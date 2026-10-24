package com.flowforge.domain.workflow.portal.port.out;

/** Le portail est-il activé sur cette instance (D-PORTAL-ACTIVATION) ? Le domaine ne lit pas la config Spring. */
public interface PortalAvailabilityPort {

    boolean isPortalEnabled();

    /** Plafond tenant de durée de vie d'un lien (D-PORTAL-TTL), en jours. */
    int maxLinkTtlDays();

    /**
     * TODO §103 (E1, D-BEAUTH-ACTIVATION) — authentification forte des tiers activée, avec au moins un fournisseur
     * configuré (vérifié au démarrage). Désactivée par défaut.
     */
    default boolean isStrongAuthenticationEnabled() {
        return false;
    }
}
