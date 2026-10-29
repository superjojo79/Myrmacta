package be.myrmacta.infrastructure.config;

import java.time.Duration;
import java.util.Locale;
import java.util.Set;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * TODO §72 (signature-electronique). {@code provider} : {@code none} (défaut, aucune fonctionnalité,
 * un bloc signature est refusé au déploiement), {@code stub} (simulation, D-SIGNATURE-STUB-BEHAVIOR),
 * {@code yousign} ou {@code docusign} (TODO §109, D-SIGNATURE-PROVIDER). Le stub est soumis à la garde de profil
 * (D-SIGNATURE-STUB-GUARD) : hors profil {@code local}/{@code test}, il exige
 * {@code stub.allowOutsideLocal=true}.
 */
@ConfigurationProperties(prefix = "app.signature")
public record SignatureProperties(
        String provider,
        Stub stub,
        int maxSigners,
        Duration defaultDeadline,
        Duration maxDeadline,
        long maxDocumentBytes,
        Send send,
        Expiry expiry,
        Dispatch dispatch) {

    public static final String PROVIDER_NONE = "none";
    public static final String PROVIDER_STUB = "stub";
    public static final String PROVIDER_YOUSIGN = "yousign";
    public static final String PROVIDER_DOCUSIGN = "docusign";
    private static final Set<String> KNOWN_PROVIDERS =
            Set.of(PROVIDER_NONE, PROVIDER_STUB, PROVIDER_YOUSIGN, PROVIDER_DOCUSIGN);

    public SignatureProperties {
        provider = provider == null || provider.isBlank() ? PROVIDER_NONE : provider.trim().toLowerCase(Locale.ROOT);
        if (!KNOWN_PROVIDERS.contains(provider)) {
            throw new IllegalStateException(
                    "app.signature.provider '" + provider + "' is unknown (expected one of " + KNOWN_PROVIDERS + ")");
        }
        if (stub == null) {
            stub = new Stub(false);
        }
        if (maxSigners <= 0) {
            maxSigners = 10;
        }
        if (defaultDeadline == null) {
            defaultDeadline = Duration.ofDays(14);
        }
        if (maxDeadline == null) {
            maxDeadline = Duration.ofDays(90);
        }
        if (defaultDeadline.toDays() < 1 || maxDeadline.toDays() < 1) {
            throw new IllegalStateException("app.signature deadlines must be at least one day");
        }
        if (defaultDeadline.compareTo(maxDeadline) > 0) {
            throw new IllegalStateException("app.signature.default-deadline must not exceed app.signature.max-deadline");
        }
        if (maxDocumentBytes <= 0) {
            maxDocumentBytes = 20L * 1024 * 1024;
        }
        if (send == null) {
            send = new Send(null, 0, 0, null);
        }
        if (expiry == null) {
            expiry = new Expiry(null, 0);
        }
        if (dispatch == null) {
            dispatch = new Dispatch(null, 0, 0);
        }
    }

    public boolean providerActive() {
        return !PROVIDER_NONE.equals(provider);
    }

    public boolean stubSelected() {
        return PROVIDER_STUB.equals(provider);
    }

    public int defaultDeadlineDays() {
        return (int) defaultDeadline.toDays();
    }

    public int maxDeadlineDays() {
        return (int) maxDeadline.toDays();
    }

    /** {@code allowOutsideLocal} : second drapeau explicite, jamais dans les gabarits de configuration. */
    public record Stub(boolean allowOutsideLocal) {}

    /** {@code resendMinInterval} : délai minimal entre deux renvois d'invitation au même signataire (anti-inondation). */
    public record Send(Duration pollDelay, int maxAttempts, int batchSize, Duration resendMinInterval) {
        public Send {
            if (resendMinInterval == null) {
                resendMinInterval = Duration.ofMinutes(1);
            }
            if (pollDelay == null) {
                pollDelay = Duration.ofSeconds(10);
            }
            if (maxAttempts <= 0) {
                maxAttempts = 5;
            }
            if (batchSize <= 0) {
                batchSize = 50;
            }
        }
    }

    public record Expiry(Duration pollDelay, int batchSize) {
        public Expiry {
            if (pollDelay == null) {
                pollDelay = Duration.ofMinutes(15);
            }
            if (batchSize <= 0) {
                batchSize = 50;
            }
        }
    }

    /** {@code maxAttempts} : reprises du répartiteur avant alerte (D-SIGNATURE-DISPATCH). */
    public record Dispatch(Duration pollDelay, int maxAttempts, int batchSize) {
        public Dispatch {
            if (pollDelay == null) {
                pollDelay = Duration.ofSeconds(15);
            }
            if (maxAttempts <= 0) {
                maxAttempts = 20;
            }
            if (batchSize <= 0) {
                batchSize = 50;
            }
        }
    }
}
