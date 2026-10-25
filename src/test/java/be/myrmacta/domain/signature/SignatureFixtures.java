package be.myrmacta.domain.signature;

import be.myrmacta.domain.signature.model.SignatureMode;
import be.myrmacta.domain.signature.model.SignatureRequest;
import be.myrmacta.domain.signature.model.SignatureStatus;
import be.myrmacta.domain.signature.model.Signer;
import be.myrmacta.domain.signature.model.SignerKind;
import be.myrmacta.domain.signature.model.SignerStatus;
import java.time.Instant;
import java.util.UUID;

/** Fabriques de test partagées du domaine signature (TODO §72). */
public final class SignatureFixtures {

    public static final Instant NOW = Instant.parse("2026-09-30T10:00:00Z");
    public static final String TENANT = "tenant_test";

    private SignatureFixtures() {}

    public static Signer external(UUID requestId, int position, boolean required, String email) {
        return new Signer(UUID.randomUUID(), TENANT, requestId, position, SignerKind.EXTERNAL, required, null,
                "Signataire " + position, email, null, SignerStatus.WAITING, null, 0, null, null, 0L);
    }

    public static Signer internal(UUID requestId, int position, boolean required, String userId) {
        return new Signer(UUID.randomUUID(), TENANT, requestId, position, SignerKind.INTERNAL, required, userId,
                "Interne " + position, null, null, SignerStatus.WAITING, null, 0, null, null, 0L);
    }

    public static SignatureRequest request(UUID id, SignatureMode mode) {
        return request(id, mode, "proc-1");
    }

    public static SignatureRequest request(UUID id, SignatureMode mode, String processInstanceId) {
        return request(id, mode, processInstanceId, "a".repeat(64));
    }

    public static SignatureRequest request(UUID id, SignatureMode mode, String processInstanceId, String digest) {
        return new SignatureRequest(id, TENANT, UUID.randomUUID(), processInstanceId, "signStep", UUID.randomUUID(),
                "storage/doc.pdf", digest, "stub", null, mode, SignatureStatus.PENDING, null, true, null,
                NOW.plusSeconds(86_400), null, 0, NOW, null, "flowable-delegate", 0L);
    }
}
