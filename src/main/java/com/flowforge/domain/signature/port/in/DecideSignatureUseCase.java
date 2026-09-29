package com.flowforge.domain.signature.port.in;

import com.flowforge.domain.signature.model.SignatureStatus;
import com.flowforge.domain.signature.model.SignerStatus;
import java.util.UUID;

/**
 * Décision d'un signataire : signer ou refuser (TODO §72). Externe : identifié par son jeton ;
 * interne : par son identité authentifiée. Tout refus d'accès (demande close, pas son tour, déjà
 * décidé, échéance, décision concurrente) lève la même {@code PortalAccessDeniedException}.
 */
public interface DecideSignatureUseCase {

    Result decideByToken(String token, Choice choice, String reason);

    Result decideInternal(String userId, UUID signerId, Choice choice, String reason);

    enum Choice {
        SIGN,
        DECLINE
    }

    /** @param signerStatus décision du signataire ; {@code requestStatus} : statut de la demande après elle */
    record Result(UUID requestId, SignerStatus signerStatus, SignatureStatus requestStatus) {}
}
