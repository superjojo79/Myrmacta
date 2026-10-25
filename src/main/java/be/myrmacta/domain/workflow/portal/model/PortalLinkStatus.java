package be.myrmacta.domain.workflow.portal.model;

/**
 * Cycle de vie d'un lien portail (TODO §68, design § « Machine à états »). {@link #PENDING_SEND},
 * {@link #ACTIVE} et {@link #NOT_SENT} sont les états « vivants » : un seul lien vivant par tâche
 * (exigence H2, garanti en base par un index unique partiel).
 */
public enum PortalLinkStatus {
    PENDING_SEND,
    ACTIVE,
    NOT_SENT,
    COMPLETED,
    EXPIRED,
    REVOKED;

    public boolean isLive() {
        return this == PENDING_SEND || this == ACTIVE || this == NOT_SENT;
    }
}
