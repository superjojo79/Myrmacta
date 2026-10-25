package be.myrmacta.domain.signature.port.in;

/** Expire les demandes dont l'échéance est dépassée (TODO §72, D-SIGNATURE-DEADLINE). */
public interface ExpireSignatureRequestsUseCase {

    /** @return nombre de demandes expirées */
    int expireDue();
}
