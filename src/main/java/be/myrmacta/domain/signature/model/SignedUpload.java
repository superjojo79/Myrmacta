package be.myrmacta.domain.signature.model;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Téléversement accepté d'un PDF signé hors de Myrmacta (TODO §138, D-EID-UPLOAD). Immuable.
 *
 * <p>Les téléversements d'une demande se suivent : le document de travail d'une demande est le PDF du
 * dernier téléversement accepté, sinon la source (D-EID-CHAIN). {@code sequence} part de 1.
 * {@code digest} est le SHA-256 (hexadécimal minuscule) du PDF stocké sous {@code storageRef} ;
 * {@code evidence} garde les constats de la vérification (D-EID-EVIDENCE).
 */
public record SignedUpload(
        UUID id,
        String tenantId,
        UUID requestId,
        UUID signerId,
        int sequence,
        String storageRef,
        String digest,
        SignedUploadEvidence evidence,
        Instant createdAt) {

    public SignedUpload {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(tenantId, "tenantId");
        Objects.requireNonNull(requestId, "requestId");
        Objects.requireNonNull(signerId, "signerId");
        if (sequence < 1) {
            throw new IllegalArgumentException("sequence starts at 1");
        }
        Objects.requireNonNull(storageRef, "storageRef");
        Objects.requireNonNull(digest, "digest");
        Objects.requireNonNull(evidence, "evidence");
        Objects.requireNonNull(createdAt, "createdAt");
    }
}
