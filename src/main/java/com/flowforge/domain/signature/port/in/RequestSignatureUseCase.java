package com.flowforge.domain.signature.port.in;

import com.flowforge.domain.signature.model.SignatureBlockDefinition;
import com.flowforge.domain.signature.model.SignatureStatus;
import java.util.UUID;

/**
 * Émission d'une demande de signature depuis le delegate {@code signatureRequestTask} (TODO §72).
 * Ne lève jamais pour une erreur métier ou fournisseur : la demande est alors créée directement
 * {@code FAILED} avec son motif, pour que le process reprenne (D-SIGNATURE-DISPATCH).
 */
public interface RequestSignatureUseCase {

    Result request(Command command);

    /** @param block bloc lu dans le BPMN déployé (activité courante) */
    record Command(UUID dossierId, String processInstanceId, SignatureBlockDefinition block) {}

    /** @param status {@code PENDING} ou {@code FAILED} ; {@code reason} renseigné si {@code FAILED} */
    record Result(UUID requestId, SignatureStatus status, String reason) {}
}
