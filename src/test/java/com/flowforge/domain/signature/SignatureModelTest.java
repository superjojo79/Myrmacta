package com.flowforge.domain.signature;

import static com.flowforge.domain.signature.SignatureFixtures.external;
import static com.flowforge.domain.signature.SignatureFixtures.internal;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.flowforge.domain.signature.model.SignatureBlockDefinition;
import com.flowforge.domain.signature.model.SignatureBlockDefinition.SignerDefinition;
import com.flowforge.domain.signature.model.SignatureMode;
import com.flowforge.domain.signature.model.SignatureOutcome;
import com.flowforge.domain.signature.model.SignatureStatus;
import com.flowforge.domain.signature.model.Signer;
import com.flowforge.domain.signature.model.SignerKind;
import com.flowforge.domain.signature.model.SignerStatus;
import com.flowforge.domain.workflow.portal.model.PortalRecipientSource;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** T1.1 : invariants des records de domaine (TODO §72). */
class SignatureModelTest {

    private static PortalRecipientSource src(String s) {
        return PortalRecipientSource.parse(s).orElseThrow();
    }

    @Test
    void externalSignerNeedsAnEmailAndInternalNeedsAUser() {
        UUID req = UUID.randomUUID();
        assertThatThrownBy(() -> external(req, 0, true, " ")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> internal(req, 0, true, null)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void identityKeyIsNormalizedAndKindSpecific() {
        UUID req = UUID.randomUUID();
        Signer a = external(req, 0, true, "Alice@Example.ORG ");
        Signer b = external(req, 1, true, "alice@example.org");
        Signer u = internal(req, 2, true, "Alice");
        assertThat(a.identityKey()).isEqualTo(b.identityKey());
        assertThat(u.identityKey()).isNotEqualTo(a.identityKey());
        assertThat(a.actorId()).startsWith("signature:");
        assertThat(u.actorId()).isEqualTo("Alice");
        assertThat(a.maskedEmail()).doesNotContain("Alice@Example").contains("@");
    }

    @Test
    void declineReasonIsBounded() {
        Signer s = external(UUID.randomUUID(), 0, true, "a@example.org");
        assertThatThrownBy(() -> s.decided(SignerStatus.DECLINED, SignatureFixtures.NOW, "x".repeat(501)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void statusHelpers() {
        assertThat(SignatureStatus.PENDING.isTerminal()).isFalse();
        assertThat(SignatureStatus.EXPIRED.outcome()).isEqualTo(SignatureOutcome.EXPIRED);
        assertThat(SignatureStatus.PENDING.outcome()).isNull();
        assertThat(SignatureOutcome.CANCELLED.status()).isEqualTo(SignatureStatus.CANCELLED);
        assertThat(SignerStatus.WAITING.isOpen()).isTrue();
        assertThat(SignerStatus.SIGNED.isOpen()).isFalse();
        assertThat(SignatureMode.parse(" sequential ")).contains(SignatureMode.SEQUENTIAL);
        assertThat(SignatureMode.parse("x")).isEmpty();
        assertThat(SignerKind.parse("internal")).contains(SignerKind.INTERNAL);
    }

    @Test
    void blockNeedsSignersRequiredOnesAndDistinctSources() {
        SignerDefinition a = new SignerDefinition(src("dossierField:a"), SignerKind.EXTERNAL, true, "A");
        SignerDefinition b = new SignerDefinition(src("dossierField:b"), SignerKind.INTERNAL, false, "B");
        SignerDefinition aDup = new SignerDefinition(src("dossierField:a"), SignerKind.INTERNAL, true, "A2");
        PortalRecipientSource doc = src("variable:generatedDocumentId");

        assertThat(new SignatureBlockDefinition("t", doc, SignatureMode.PARALLEL, 14, List.of(a, b)).hasExternalSigner())
                .isTrue();
        assertThatThrownBy(() -> new SignatureBlockDefinition("t", doc, SignatureMode.PARALLEL, null, List.of()))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new SignatureBlockDefinition("t", doc, SignatureMode.PARALLEL, null, List.of(b)))
                .hasMessageContaining("required");
        assertThatThrownBy(() -> new SignatureBlockDefinition("t", doc, SignatureMode.PARALLEL, null, List.of(a, aDup)))
                .hasMessageContaining("distinct");
        assertThatThrownBy(() -> new SignatureBlockDefinition("t", doc, SignatureMode.PARALLEL, 0, List.of(a)))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
