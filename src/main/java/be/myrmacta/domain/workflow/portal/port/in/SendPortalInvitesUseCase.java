package be.myrmacta.domain.workflow.portal.port.in;

/** Traite les liens {@code PENDING_SEND} : jeton, e-mail, activation (appelé par un job planifié). */
public interface SendPortalInvitesUseCase {

    /** Nombre de liens activés pendant ce passage. */
    int sendPending();
}
