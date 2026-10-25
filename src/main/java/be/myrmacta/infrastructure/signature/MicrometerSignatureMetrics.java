package be.myrmacta.infrastructure.signature;

import be.myrmacta.domain.signature.model.SignatureOutcome;
import be.myrmacta.domain.signature.port.out.SignatureMetricsPort;
import io.micrometer.core.instrument.MeterRegistry;
import java.util.Objects;

/** Compteurs Micrometer du lot signature (TODO §72). Libellés bornés, sans donnée personnelle. */
public final class MicrometerSignatureMetrics implements SignatureMetricsPort {

    static final String REQUESTS = "flowforge.signature.requests";
    static final String OUTCOMES = "flowforge.signature.outcomes";
    static final String DISPATCH_RETRIES = "flowforge.signature.dispatch.retries";
    static final String DISPATCH_GAVE_UP = "flowforge.signature.dispatch.gave_up";
    static final String CALLBACKS_REJECTED = "flowforge.signature.callbacks.rejected";

    private final MeterRegistry registry;

    public MicrometerSignatureMetrics(MeterRegistry registry) {
        this.registry = Objects.requireNonNull(registry, "registry");
    }

    @Override
    public void requestCreated(String providerCode, boolean simulated) {
        registry.counter(REQUESTS, "provider", tag(providerCode), "simulated", String.valueOf(simulated)).increment();
    }

    @Override
    public void outcome(SignatureOutcome outcome) {
        registry.counter(OUTCOMES, "outcome", outcome.name()).increment();
    }

    @Override
    public void dispatchRetry() {
        registry.counter(DISPATCH_RETRIES).increment();
    }

    @Override
    public void dispatchGaveUp() {
        registry.counter(DISPATCH_GAVE_UP).increment();
    }

    @Override
    public void callbackRejected(String reason) {
        registry.counter(CALLBACKS_REJECTED, "reason", tag(reason)).increment();
    }

    /** Étiquette bornée : lettres, chiffres, tiret et souligné, 40 caractères au plus. */
    private static String tag(String value) {
        if (value == null || value.isBlank()) {
            return "unknown";
        }
        String cleaned = value.replaceAll("[^A-Za-z0-9_-]", "_");
        return cleaned.length() > 40 ? cleaned.substring(0, 40) : cleaned;
    }
}
