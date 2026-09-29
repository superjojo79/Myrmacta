package com.flowforge.domain.signature.port.in;

/**
 * Le process en attente a disparu (annulé, supprimé, terminé sans avoir reçu l'issue) : ses demandes
 * ouvertes sont annulées et leurs liens invalidés (TODO §72, D-SIGNATURE-CANCEL-PROPAGATION).
 */
public interface CancelSignatureRequestsForProcessUseCase {

    /** @return nombre de demandes annulées */
    int cancelForProcess(String processInstanceId);

    /**
     * L'attente du message {@code signatureCompleted} a été interrompue alors que le process continue (frontière,
     * sous-processus terminé…) : cette demande, si elle est encore ouverte, est annulée et ses liens invalidés.
     *
     * @return vrai si la demande a été annulée par cet appel
     */
    boolean cancelInterruptedWait(java.util.UUID requestId);
}
