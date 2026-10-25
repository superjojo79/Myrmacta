package be.myrmacta.domain.signature.port.in;

/** Envoie les invitations des signataires externes {@code PENDING} qui n'en ont pas reçu (TODO §72). */
public interface SendSignatureInvitesUseCase {

    /** @return nombre d'invitations envoyées */
    int sendPending();
}
