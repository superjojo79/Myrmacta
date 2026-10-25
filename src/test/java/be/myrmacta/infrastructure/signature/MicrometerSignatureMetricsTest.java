package be.myrmacta.infrastructure.signature;

import static org.assertj.core.api.Assertions.assertThat;

import be.myrmacta.domain.signature.model.SignatureOutcome;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

/** S-DISP-5 : compteurs du lot signature, étiquettes bornées et sans donnée personnelle. */
class MicrometerSignatureMetricsTest {

    private final SimpleMeterRegistry registry = new SimpleMeterRegistry();
    private final MicrometerSignatureMetrics metrics = new MicrometerSignatureMetrics(registry);

    @Test
    void countersAreIncrementedWithBoundedTags() {
        metrics.requestCreated("stub", true);
        metrics.outcome(SignatureOutcome.SIGNED);
        metrics.outcome(SignatureOutcome.SIGNED);
        metrics.outcome(SignatureOutcome.DECLINED);
        metrics.dispatchRetry();
        metrics.dispatchGaveUp();
        metrics.callbackRejected("bad hmac: alice@example.org\nX-Evil: 1");

        assertThat(registry.get("myrmacta.signature.requests").tag("provider", "stub").tag("simulated", "true")
                        .counter().count()).isEqualTo(1.0);
        assertThat(registry.get("myrmacta.signature.outcomes").tag("outcome", "SIGNED").counter().count())
                .isEqualTo(2.0);
        assertThat(registry.get("myrmacta.signature.outcomes").tag("outcome", "DECLINED").counter().count())
                .isEqualTo(1.0);
        assertThat(registry.get("myrmacta.signature.dispatch.retries").counter().count()).isEqualTo(1.0);
        assertThat(registry.get("myrmacta.signature.dispatch.gave_up").counter().count()).isEqualTo(1.0);
        String reasonTag = registry.get("myrmacta.signature.callbacks.rejected").counter().getId().getTag("reason");
        assertThat(reasonTag).hasSizeLessThanOrEqualTo(40).matches("[A-Za-z0-9_-]+").doesNotContain("@");
    }
}
