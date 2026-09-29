package com.flowforge.domain.signature.port.in;

import com.flowforge.domain.dossier.model.AccessContext;
import com.flowforge.domain.signature.model.SignatureRequest;
import com.flowforge.domain.signature.model.Signer;
import java.util.List;
import java.util.UUID;

/**
 * Gestion des demandes par l'instructeur (TODO §72, US-4, A2) : lecture avec VIEW sur le type du
 * dossier (adresses masquées sans EDIT), annulation et renvoi avec EDIT.
 */
public interface ManageSignatureUseCase {

    Listing list(UUID dossierId, AccessContext.User actor);

    /** @param dryRun vrai : contrôle les droits et l'état, n'écrit rien (tools MCP, D-SIGNATURE-MCP) */
    View cancel(UUID requestId, AccessContext.User actor, boolean dryRun);

    /** Nouvelle invitation à un signataire externe {@code PENDING} (l'ancien jeton est invalidé) ou rappel interne. */
    View resend(UUID requestId, UUID signerId, AccessContext.User actor);

    record Listing(boolean canManage, List<View> requests) {}

    record View(SignatureRequest request, List<Signer> signers, boolean canManage) {}
}
