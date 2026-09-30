package com.flowforge.domain.workflow.palette;

/**
 * Motif XML qui reconnaît une brique (D-PALETTE-RECOGNITION) et sert à la créer. Champs non utilisés à {@code null}
 * ou {@code false}.
 */
public record BlockMarker(String delegate, String serviceTaskType, String formKeyPrefix, boolean portalRecipient) {

    public static BlockMarker delegate(String delegate) {
        return new BlockMarker(delegate, null, null, false);
    }

    public static BlockMarker dmn() {
        return new BlockMarker(null, "dmn", null, false);
    }

    public static BlockMarker userQuestion() {
        return new BlockMarker(null, null, "user-question:", false);
    }

    public static BlockMarker externalTask() {
        return new BlockMarker(null, null, "user-question:", true);
    }
}
