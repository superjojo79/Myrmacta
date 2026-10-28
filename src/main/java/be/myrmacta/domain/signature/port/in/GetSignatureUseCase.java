package be.myrmacta.domain.signature.port.in;

import java.time.Instant;

/**
 * Lecture, par un signataire externe, de ce qui le concerne (TODO §72) : le contexte de sa demande
 * et le document à signer — rien d'autre du dossier (anti-IDOR).
 */
public interface GetSignatureUseCase {

    Context context(String token);

    DocumentContent document(String token);

    /**
     * {@code signingUrl} est l'adresse chez le fournisseur au moment de l'ouverture, nulle tant que
     * le signataire n'est pas notifié ou si la signature se fait dans Myrmacta (D-SIGNATURE-LINK).
     */
    record Context(
            String dossierReference,
            String documentLabel,
            String signerName,
            boolean required,
            Instant deadlineAt,
            boolean simulated,
            boolean hosted,
            String signingUrl) {}

    record DocumentContent(byte[] content, String contentType, String filename) {}
}
