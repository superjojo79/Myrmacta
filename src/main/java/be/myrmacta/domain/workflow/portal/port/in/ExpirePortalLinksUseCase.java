package be.myrmacta.domain.workflow.portal.port.in;

/** Liens {@code ACTIVE} arrivés à expiration → {@code EXPIRED} + notification (D-PORTAL-EXPIRY). */
public interface ExpirePortalLinksUseCase {

    int expireDue();
}
