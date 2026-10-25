package be.myrmacta.domain.promotion.model;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Objects;
import java.util.regex.Pattern;

/** TODO §83 (D4) — un fichier d'un bundle : type, clé (nom stable de l'artefact), contenu. */
public record BundleEntry(ArtifactType type, String key, byte[] content) {

    /** Clé = nom de fichier sûr : jamais de séparateur, ni de point initial, ni de {@code ..}. */
    private static final Pattern KEY = Pattern.compile("[A-Za-z0-9_][A-Za-z0-9_.-]{0,127}");

    public BundleEntry {
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(key, "key");
        Objects.requireNonNull(content, "content");
        if (!KEY.matcher(key).matches() || key.contains("..")) {
            throw new InvalidBundleException("invalid artifact key: '" + key + "'");
        }
        content = content.clone();
    }

    @Override
    public byte[] content() {
        return content.clone();
    }

    public String text() {
        return new String(content, StandardCharsets.UTF_8);
    }

    public String sha256() {
        return sha256(content);
    }

    public static String sha256(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof BundleEntry other
                && type == other.type
                && key.equals(other.key)
                && java.util.Arrays.equals(content, other.content);
    }

    @Override
    public int hashCode() {
        return Objects.hash(type, key) * 31 + java.util.Arrays.hashCode(content);
    }

    @Override
    public String toString() {
        return type + ":" + key + " (" + content.length + " bytes)";
    }
}
