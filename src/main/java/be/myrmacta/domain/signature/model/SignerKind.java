package be.myrmacta.domain.signature.model;

/** D-SIGNATURE-SIGNER-KIND : un signataire est un tiers (portail) ou un utilisateur Myrmacta. */
public enum SignerKind {
    EXTERNAL,
    INTERNAL;

    public static java.util.Optional<SignerKind> parse(String raw) {
        if (raw == null) {
            return java.util.Optional.empty();
        }
        try {
            return java.util.Optional.of(valueOf(raw.trim().toUpperCase(java.util.Locale.ROOT)));
        } catch (IllegalArgumentException ex) {
            return java.util.Optional.empty();
        }
    }
}
