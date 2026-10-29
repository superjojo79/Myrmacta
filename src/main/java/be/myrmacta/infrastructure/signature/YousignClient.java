package be.myrmacta.infrastructure.signature;

import java.util.Optional;
import java.util.UUID;

/**
 * Opérations Yousign appelées par l'adaptateur (TODO §109, D-SIGNATURE-HTTP). L'hôte est celui du
 * bac à sable ou de la production, jamais une URL venue du BPMN.
 */
interface YousignClient {

    UUID createSignatureRequest(String name, boolean orderedSigners, String externalId, String expirationDate);

    /** {@code totalAnchors} : ancres reconnues dans le PDF ({@code parse_anchors}). */
    Upload uploadSignablePdf(UUID requestId, String filename, byte[] pdf);

    UUID addSigner(UUID requestId, String firstName, String lastName, String email);

    void activate(UUID requestId);

    /**
     * Lien à demander au moment où le signataire ouvre la page. Vide si ce n'est pas encore son tour.
     * L'hôte du lien est contrôlé avant le retour.
     */
    Optional<String> signatureLink(UUID requestId, int signerPosition);

    void cancel(UUID requestId);

    byte[] downloadSignedPdf(UUID requestId);

    byte[] downloadAuditTrail(UUID requestId);

    record Upload(UUID documentId, int totalAnchors) {}
}
