package com.flowforge.domain.signature.port.out;

import java.util.UUID;

/**
 * Notifié quand une demande vient de passer à un statut terminal (TODO §72, D-SIGNATURE-DISPATCH).
 * L'implémentation tente la reprise du process <em>après validation</em> de la transaction courante ;
 * le job du répartiteur reprend ce que cette tentative immédiate n'a pas pu faire.
 */
public interface SignatureOutcomeListener {

    void outcomeRecorded(UUID requestId);
}
