package be.myrmacta.domain.promotion.model;

import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * TODO §83 (D4) — bundle de configuration versionné. Sans secret (D-PROMO-SECRETS). {@code formatVersion} : seuls les formats
 * connus de l'instance sont acceptés (D-PROMO-FORMAT). TODO §117 (D-PROMO-FORMAT2) — l'export écrit le format 2, l'import lit 1 et 2 ;
 * un bundle de format 1 ne porte que les artefacts de la première tranche.
 */
public record ConfigurationBundle(
        int formatVersion, Instant generatedAt, String sourceLabel, String appVersion, List<BundleEntry> entries) {

    public static final int FORMAT_VERSION = 2;
    public static final int MIN_FORMAT_VERSION = 1;
    public static final int MAX_ENTRIES = 2000;
    /**
     * TODO §117 (D-PROMO-TEMPLATE, revue de clôture #279 M3) — 12 Mio : un template actif peut peser 5 Mio de HTML, que l'encodage
     * JSON (guillemets et sauts de ligne échappés) peut doubler. Le total du bundle reste borné à 50 Mio.
     */
    public static final int MAX_ENTRY_BYTES = 12 * 1024 * 1024;
    public static final long MAX_TOTAL_BYTES = 50L * 1024 * 1024;
    public static final int MAX_LABEL_CHARS = 200;

    public ConfigurationBundle {
        Objects.requireNonNull(generatedAt, "generatedAt");
        entries = List.copyOf(Objects.requireNonNull(entries, "entries"));
        sourceLabel = sourceLabel == null ? "" : sourceLabel.strip();
        appVersion = appVersion == null ? "" : appVersion.strip();
        requireReadable(formatVersion);
        if (sourceLabel.length() > MAX_LABEL_CHARS || appVersion.length() > MAX_LABEL_CHARS) {
            throw new InvalidBundleException("bundle labels are limited to " + MAX_LABEL_CHARS + " characters");
        }
        if (entries.size() > MAX_ENTRIES) {
            throw new InvalidBundleException("a bundle holds at most " + MAX_ENTRIES + " artifacts");
        }
        Set<String> seen = new HashSet<>();
        long total = 0;
        for (BundleEntry entry : entries) {
            if (entry.type().sinceFormatVersion() > formatVersion) {
                throw new InvalidBundleException("a bundle of formatVersion " + formatVersion + " cannot contain " + entry.type()
                        + " artifacts (introduced by formatVersion " + entry.type().sinceFormatVersion() + ")");
            }
            if (!seen.add(entry.type() + "/" + entry.key())) {
                throw new InvalidBundleException("duplicate artifact: " + entry.type() + " " + entry.key());
            }
            int size = entry.content().length;
            if (size > MAX_ENTRY_BYTES) {
                throw new InvalidBundleException("artifact " + entry.type() + " " + entry.key() + " exceeds "
                        + MAX_ENTRY_BYTES + " bytes");
            }
            total += size;
        }
        if (total > MAX_TOTAL_BYTES) {
            throw new InvalidBundleException("bundle exceeds " + MAX_TOTAL_BYTES + " bytes");
        }
    }

    /** Refuse un format que cette instance ne sait pas lire. */
    public static void requireReadable(int formatVersion) {
        if (formatVersion < MIN_FORMAT_VERSION || formatVersion > FORMAT_VERSION) {
            throw new InvalidBundleException("unsupported bundle formatVersion " + formatVersion + " (this instance reads "
                    + MIN_FORMAT_VERSION + " to " + FORMAT_VERSION + ")");
        }
    }

    /** Somme de contrôle du contenu logique (types, clés, octets) : identifie le bundle indépendamment de son archivage. */
    public String sha256() {
        StringBuilder canonical = new StringBuilder();
        entries.stream()
                .sorted(java.util.Comparator.comparing((BundleEntry e) -> e.type().ordinal()).thenComparing(BundleEntry::key))
                .forEach(e -> canonical.append(e.type()).append('/').append(e.key()).append(':').append(e.sha256()).append('\n'));
        return BundleEntry.sha256(canonical.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }

    public java.util.Optional<BundleEntry> find(ArtifactType type, String key) {
        return entries.stream().filter(e -> e.type() == type && e.key().equals(key)).findFirst();
    }

    public List<BundleEntry> of(ArtifactType type) {
        return entries.stream().filter(e -> e.type() == type).sorted(java.util.Comparator.comparing(BundleEntry::key)).toList();
    }
}
