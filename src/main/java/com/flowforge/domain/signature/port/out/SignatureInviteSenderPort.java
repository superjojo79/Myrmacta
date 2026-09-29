package com.flowforge.domain.signature.port.out;

import com.flowforge.domain.signature.model.Signer;
import java.time.Instant;

/**
 * Envoi de l'invitation à un signataire externe (TODO §72) : e-mail direct, <strong>pas</strong>
 * l'outbox de notification, qui stockerait le lien en clair en base (comme
 * {@code PortalInviteSenderPort}).
 */
public interface SignatureInviteSenderPort {

    /** Lève une exception si l'envoi échoue (le job retentera avec un nouveau jeton). */
    void send(Signer signer, String token, InviteContext context);

    /** {@code simulated} : la signature n'aura aucune valeur juridique (fournisseur stub) — à dire au signataire. */
    record InviteContext(String documentName, String dossierReference, Instant deadlineAt, boolean simulated) {
        public InviteContext(String documentName, String dossierReference, Instant deadlineAt) {
            this(documentName, dossierReference, deadlineAt, false);
        }
    }
}
