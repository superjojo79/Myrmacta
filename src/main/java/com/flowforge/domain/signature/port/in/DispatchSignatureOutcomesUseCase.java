package com.flowforge.domain.signature.port.in;

import java.util.UUID;

/**
 * Reprise du process en attente quand une demande est terminée (TODO §72, D-SIGNATURE-DISPATCH) :
 * corrélation du message {@code signatureCompleted} par {@code signatureRequestId}, avec reprise tant
 * que le process n'est pas (encore) abonné.
 */
public interface DispatchSignatureOutcomesUseCase {

    static final String MESSAGE_NAME = "signatureCompleted";
    static final String CORRELATION_VARIABLE = "signatureRequestId";
    static final String OUTCOME_VARIABLE = "signatureOutcome";

    /** @return vrai si le process a été repris (ou n'a plus à l'être) */
    boolean dispatch(UUID requestId);

    /** @return nombre de demandes reprises */
    int dispatchPending();
}
