package be.myrmacta.domain.dossier.model;

import java.util.Objects;

public final class InvalidRetentionPolicyException extends RuntimeException {

    public InvalidRetentionPolicyException(String message) {
        super(Objects.requireNonNull(message, "message"));
    }
}
