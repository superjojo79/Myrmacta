package com.flowforge.domain.signature.model;

/** D-SIGNATURE-ORDER : tous les signataires notifiés ensemble, ou un à la fois dans l'ordre du rang. */
public enum SignatureMode {
    PARALLEL,
    SEQUENTIAL;

    public static java.util.Optional<SignatureMode> parse(String raw) {
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
