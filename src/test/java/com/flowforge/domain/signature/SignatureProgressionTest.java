package com.flowforge.domain.signature;

import static com.flowforge.domain.signature.SignatureFixtures.NOW;
import static com.flowforge.domain.signature.SignatureFixtures.external;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.flowforge.domain.signature.model.SignatureMode;
import com.flowforge.domain.signature.model.SignatureOutcome;
import com.flowforge.domain.signature.model.SignatureProgression;
import com.flowforge.domain.signature.model.SignatureProgression.Decision;
import com.flowforge.domain.signature.model.SignatureProgression.Result;
import com.flowforge.domain.signature.model.Signer;
import com.flowforge.domain.signature.model.SignerStatus;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** S-PROG-1..4, S-PROG-7 (TODO §72) : table de vérité de la progression. */
class SignatureProgressionTest {

    private final UUID requestId = UUID.randomUUID();

    private Signer s(int pos, boolean required) {
        return external(requestId, pos, required, "s" + pos + "@example.org");
    }

    private static List<SignerStatus> statuses(Result r) {
        return r.signers().stream().map(Signer::status).toList();
    }

    @Test
    void parallelStartActivatesEverybody() {
        Result r = SignatureProgression.start(SignatureMode.PARALLEL, List.of(s(0, true), s(1, false), s(2, true)));
        assertThat(statuses(r)).containsOnly(SignerStatus.PENDING);
        assertThat(r.newlyPending()).hasSize(3);
        assertThat(r.closes()).isFalse();
    }

    @Test
    void sequentialStartActivatesOnlyTheFirst() {
        Result r = SignatureProgression.start(SignatureMode.SEQUENTIAL, List.of(s(1, true), s(0, true), s(2, true)));
        assertThat(statuses(r)).containsExactly(SignerStatus.PENDING, SignerStatus.WAITING, SignerStatus.WAITING);
        assertThat(r.newlyPending()).extracting(Signer::position).containsExactly(0);
    }

    @Test
    void startRequiresAtLeastOneRequiredSigner() {
        assertThatThrownBy(() -> SignatureProgression.start(SignatureMode.PARALLEL, List.of(s(0, false))))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void parallelAllRequiredSignedClosesSigned() {
        Result start = SignatureProgression.start(SignatureMode.PARALLEL, List.of(s(0, true), s(1, true)));
        Signer first = start.signers().get(0);
        Signer second = start.signers().get(1);
        Result afterFirst = SignatureProgression.decide(
                SignatureMode.PARALLEL, start.signers(), first.id(), Decision.SIGN, NOW, null);
        assertThat(afterFirst.closes()).isFalse();
        Result afterSecond = SignatureProgression.decide(
                SignatureMode.PARALLEL, afterFirst.signers(), second.id(), Decision.SIGN, NOW, null);
        assertThat(afterSecond.outcome()).isEqualTo(SignatureOutcome.SIGNED);
        assertThat(statuses(afterSecond)).containsOnly(SignerStatus.SIGNED);
    }

    @Test
    void requiredDeclineClosesDeclinedAndRevokesOthers() {
        Result start = SignatureProgression.start(SignatureMode.PARALLEL, List.of(s(0, true), s(1, true), s(2, false)));
        Result r = SignatureProgression.decide(
                SignatureMode.PARALLEL, start.signers(), start.signers().get(1).id(), Decision.DECLINE, NOW, "non");
        assertThat(r.outcome()).isEqualTo(SignatureOutcome.DECLINED);
        assertThat(statuses(r))
                .containsExactly(SignerStatus.REVOKED, SignerStatus.DECLINED, SignerStatus.REVOKED);
        assertThat(r.signers().get(1).declineReason()).isEqualTo("non");
    }

    @Test
    void optionalDeclineDoesNotCloseInParallel() {
        Result start = SignatureProgression.start(SignatureMode.PARALLEL, List.of(s(0, true), s(1, false)));
        Result r = SignatureProgression.decide(
                SignatureMode.PARALLEL, start.signers(), start.signers().get(1).id(), Decision.DECLINE, NOW, null);
        assertThat(r.closes()).isFalse();
        assertThat(statuses(r)).containsExactly(SignerStatus.PENDING, SignerStatus.DECLINED);
    }

    @Test
    void lastRequiredSignedRevokesRemainingOptional() {
        Result start = SignatureProgression.start(SignatureMode.PARALLEL, List.of(s(0, true), s(1, false)));
        Result r = SignatureProgression.decide(
                SignatureMode.PARALLEL, start.signers(), start.signers().get(0).id(), Decision.SIGN, NOW, null);
        assertThat(r.outcome()).isEqualTo(SignatureOutcome.SIGNED);
        assertThat(statuses(r)).containsExactly(SignerStatus.SIGNED, SignerStatus.REVOKED);
    }

    @Test
    void sequentialSignatureActivatesNextInRankOrder() {
        Result start = SignatureProgression.start(SignatureMode.SEQUENTIAL, List.of(s(0, true), s(1, true), s(2, true)));
        Result r = SignatureProgression.decide(
                SignatureMode.SEQUENTIAL, start.signers(), start.signers().get(0).id(), Decision.SIGN, NOW, null);
        assertThat(statuses(r)).containsExactly(SignerStatus.SIGNED, SignerStatus.PENDING, SignerStatus.WAITING);
        assertThat(r.newlyPending()).extracting(Signer::position).containsExactly(1);
    }

    @Test
    void sequentialOptionalDeclineMovesToNext() {
        Result start = SignatureProgression.start(SignatureMode.SEQUENTIAL, List.of(s(0, false), s(1, true)));
        Result r = SignatureProgression.decide(
                SignatureMode.SEQUENTIAL, start.signers(), start.signers().get(0).id(), Decision.DECLINE, NOW, null);
        assertThat(r.closes()).isFalse();
        assertThat(statuses(r)).containsExactly(SignerStatus.DECLINED, SignerStatus.PENDING);
        Result end = SignatureProgression.decide(
                SignatureMode.SEQUENTIAL, r.signers(), r.signers().get(1).id(), Decision.SIGN, NOW, null);
        assertThat(end.outcome()).isEqualTo(SignatureOutcome.SIGNED);
    }

    @Test
    void sequentialRequiredDeclineStopsTheChain() {
        Result start = SignatureProgression.start(SignatureMode.SEQUENTIAL, List.of(s(0, true), s(1, true)));
        Result r = SignatureProgression.decide(
                SignatureMode.SEQUENTIAL, start.signers(), start.signers().get(0).id(), Decision.DECLINE, NOW, null);
        assertThat(r.outcome()).isEqualTo(SignatureOutcome.DECLINED);
        assertThat(statuses(r)).containsExactly(SignerStatus.DECLINED, SignerStatus.REVOKED);
    }

    @Test
    void decidingANonPendingSignerIsRejected() {
        Result start = SignatureProgression.start(SignatureMode.SEQUENTIAL, List.of(s(0, true), s(1, true)));
        UUID waiting = start.signers().get(1).id();
        assertThatThrownBy(() -> SignatureProgression.decide(
                        SignatureMode.SEQUENTIAL, start.signers(), waiting, Decision.SIGN, NOW, null))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void decidingAnUnknownSignerIsRejected() {
        Result start = SignatureProgression.start(SignatureMode.PARALLEL, List.of(s(0, true)));
        assertThatThrownBy(() -> SignatureProgression.decide(
                        SignatureMode.PARALLEL, start.signers(), UUID.randomUUID(), Decision.SIGN, NOW, null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void expiryExpiresEverythingStillOpenAndKeepsDecisions() {
        Result start = SignatureProgression.start(SignatureMode.SEQUENTIAL, List.of(s(0, false), s(1, true), s(2, true)));
        Result afterFirst = SignatureProgression.decide(
                SignatureMode.SEQUENTIAL, start.signers(), start.signers().get(0).id(), Decision.SIGN, NOW, null);
        Result expired = SignatureProgression.expire(afterFirst.signers(), NOW);
        assertThat(expired.outcome()).isEqualTo(SignatureOutcome.EXPIRED);
        assertThat(statuses(expired))
                .containsExactly(SignerStatus.SIGNED, SignerStatus.EXPIRED, SignerStatus.EXPIRED);
    }

    @Test
    void cancelRevokesOpenSignersOnly() {
        Result start = SignatureProgression.start(SignatureMode.PARALLEL, List.of(s(0, false), s(1, true)));
        Result afterFirst = SignatureProgression.decide(
                SignatureMode.PARALLEL, start.signers(), start.signers().get(0).id(), Decision.SIGN, NOW, null);
        Result cancelled = SignatureProgression.cancel(afterFirst.signers(), NOW);
        assertThat(cancelled.outcome()).isEqualTo(SignatureOutcome.CANCELLED);
        assertThat(statuses(cancelled)).containsExactly(SignerStatus.SIGNED, SignerStatus.REVOKED);
    }
}
