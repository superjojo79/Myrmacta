package com.flowforge.domain.promotion.model;

import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * TODO §83 (D4) — bundle de configuration versionné. Sans secret (D-PROMO-SECRETS). {@code formatVersion} : seul le format connu
 * de l'instance est accepté (D-PROMO-FORMAT).
 */
public record ConfigurationBundle(
        int formatVersion, Instant generatedAt, String sourceLabel, String appVersion, List<BundleEntry> entries) {

    public static final int FORMAT_VERSION = 1;
    public static final int MAX_ENTRIES = 2000;
    public static final int MAX_ENTRY_BYTES = 5 * 1024 * 1024;
    public static final long MAX_TOTAL_BYTES = 50L * 1024 * 1024;
    public static final int MAX_LABEL_CHARS = 200;

    public ConfigurationBundle {
        Objects.requireNonNull(generatedAt, "generatedAt");
        entries = List.copyOf(Objects.requireNonNull(entries, "entries"));
        sourceLabel = sourceLabel == null ? "" : sourceLabel.strip();
        appVersion = appVersion == null ? "" : appVersion.strip();
        if (formatVersion != FORMAT_VERSION) {
            throw new InvalidBundleException("unsupported bundle formatVersion " + formatVersion + " (this instance reads "
                    + FORMAT_VERSION + ")");
        }
        if (sourceLabel.length() > MAX_LABEL_CHARS || appVersion.length() > MAX_LABEL_CHARS) {
            throw new InvalidBundleException("bundle labels are limited to " + MAX_LABEL_CHARS + " characters");
        }
        if (entries.size() > MAX_ENTRIES) {
            throw new InvalidBundleException("a bundle holds at most " + MAX_ENTRIES + " artifacts");
        }
        Set<String> seen = new HashSet<>();
        long total = 0;
        for (BundleEntry entry : entries) {
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
