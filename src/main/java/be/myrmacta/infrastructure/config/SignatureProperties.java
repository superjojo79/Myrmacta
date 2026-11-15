package be.myrmacta.infrastructure.config;

import java.time.Duration;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * TODO §72 (signature-electronique). {@code provider} : {@code none} (défaut, aucune fonctionnalité,
 * un bloc signature est refusé au déploiement), {@code stub} (simulation, D-SIGNATURE-STUB-BEHAVIOR),
 * {@code yousign} ou {@code docusign} (TODO §109, D-SIGNATURE-PROVIDER), ou {@code eid} : signature qualifiée par
 * carte eID, le signataire téléverse le PDF qu'il a signé (TODO §138, D-EID-UPLOAD). Le stub est soumis à la garde
 * de profil (D-SIGNATURE-STUB-GUARD) : hors profil {@code local}/{@code test}, il exige
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
        Dispatch dispatch,
        Eid eid) {

    public static final String PROVIDER_NONE = "none";
    public static final String PROVIDER_STUB = "stub";
    public static final String PROVIDER_YOUSIGN = "yousign";
    public static final String PROVIDER_DOCUSIGN = "docusign";
    public static final String PROVIDER_EID = "eid";
    private static final Set<String> KNOWN_PROVIDERS =
            Set.of(PROVIDER_NONE, PROVIDER_STUB, PROVIDER_YOUSIGN, PROVIDER_DOCUSIGN, PROVIDER_EID);

    /** Marge ajoutée à {@code max-document-bytes} pour un PDF signé : les signatures s'ajoutent à la source. */
    static final long SIGNED_UPLOAD_MARGIN_BYTES = 2L * 1024 * 1024;

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
        if (eid == null) {
            eid = new Eid(null, 0, null, null, null, false);
        }
        if (eid.maxUploadBytes() <= 0) {
            eid = new Eid(eid.trustAnchorsFile(), maxDocumentBytes + SIGNED_UPLOAD_MARGIN_BYTES, eid.revocation(),
                    eid.timestamp(), eid.bridges(), eid.uploadEnabled());
        }
        if (PROVIDER_EID.equals(provider) && !eid.uploadEnabled() && eid.bridges().isEmpty()) {
            throw new IllegalStateException("app.signature.provider=eid offers no way to sign: "
                    + "app.signature.eid.upload-enabled is false and app.signature.eid.bridges is empty "
                    + "(enable at least one bridge, or knowingly enable the upload)");
        }
        if (eid.maxUploadBytes() < maxDocumentBytes) {
            throw new IllegalStateException(
                    "app.signature.eid.max-upload-bytes must not be below app.signature.max-document-bytes: a signed "
                            + "PDF is its source plus signatures");
        }
    }

    public boolean providerActive() {
        return !PROVIDER_NONE.equals(provider);
    }

    public boolean stubSelected() {
        return PROVIDER_STUB.equals(provider);
    }

    public boolean eidSelected() {
        return PROVIDER_EID.equals(provider);
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

    /**
     * Fournisseur {@code eid} (TODO §138). {@code trustAnchorsFile} : fichier PEM des autorités de confiance de
     * l'exploitant, obligatoire avec {@code provider=eid} (D-EID-TRUST). {@code maxUploadBytes} : taille maximale
     * d'un PDF signé téléversé, par défaut {@code max-document-bytes} plus 2 Mio ; la limite multipart globale
     * ({@code spring.servlet.multipart}, 25 Mo par défaut) s'applique avant elle. {@code revocation} : appel
     * sortant vers l'état de révocation d'un certificat (D-EID-REVOCATION). {@code uploadEnabled} : le téléversement
     * d'un PDF signé hors de l'application est offert ; faux par défaut (la vérification d'un fichier apporté par un
     * signataire malveillant n'est pas tenue pour sûre), la signature dans la page par pont restant offerte.
     */
    public record Eid(
            String trustAnchorsFile,
            long maxUploadBytes,
            Revocation revocation,
            Timestamp timestamp,
            List<String> bridges,
            boolean uploadEnabled) {

        /** Ponts navigateur reconnus (D-EID-BRIDGE), dans l'ordre d'essai par défaut. */
        static final List<String> KNOWN_BRIDGES = List.of("web-eid", "beidconnect");

        public Eid {
            trustAnchorsFile = trustAnchorsFile == null || trustAnchorsFile.isBlank() ? null : trustAnchorsFile.trim();
            if (revocation == null) {
                revocation = new Revocation(null, 0);
            }
            if (timestamp == null) {
                timestamp = new Timestamp(null, null, 0);
            }
            bridges = normalizedBridges(bridges);
        }

        /** Absent : les deux ponts ; vide : signature dans la page non offerte ; valeur inconnue : démarrage refusé. */
        private static List<String> normalizedBridges(List<String> raw) {
            if (raw == null) {
                return KNOWN_BRIDGES;
            }
            List<String> out = new java.util.ArrayList<>();
            for (String value : raw) {
                String bridge = value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
                if (bridge.isEmpty()) {
                    continue;
                }
                if (!KNOWN_BRIDGES.contains(bridge)) {
                    throw new IllegalStateException("app.signature.eid.bridges '" + bridge
                            + "' is unknown (expected a list of " + KNOWN_BRIDGES + ")");
                }
                if (!out.contains(bridge)) {
                    out.add(bridge);
                }
            }
            return List.copyOf(out);
        }
    }

    /**
     * TODO §138 (D-EID-TIMESTAMP) — horodatage RFC 3161 du document signé final. {@code url} vide : pas
     * d'horodatage (défaut, aucune TSA par défaut). {@code timeout} : délai total de l'appel, corps compris ;
     * {@code maxResponseBytes} : taille maximale de la réponse.
     */
    public record Timestamp(String url, Duration timeout, long maxResponseBytes) {
        public Timestamp {
            url = url == null || url.isBlank() ? null : url.trim();
            if (timeout == null) {
                timeout = Duration.ofSeconds(10);
            }
            if (timeout.isNegative() || timeout.isZero()) {
                throw new IllegalStateException("app.signature.eid.timestamp.timeout must be positive");
            }
            if (maxResponseBytes <= 0) {
                maxResponseBytes = 64L * 1024;
            }
        }

        public boolean enabled() {
            return url != null;
        }
    }

    /** Délai total de l'appel de révocation (connexion et lecture) et taille maximale de sa réponse. */
    public record Revocation(Duration timeout, long maxResponseBytes) {
        public Revocation {
            if (timeout == null) {
                timeout = Duration.ofSeconds(10);
            }
            if (timeout.isNegative() || timeout.isZero()) {
                throw new IllegalStateException("app.signature.eid.revocation.timeout must be positive");
            }
            if (maxResponseBytes <= 0) {
                maxResponseBytes = 1024L * 1024;
            }
        }
    }
}
