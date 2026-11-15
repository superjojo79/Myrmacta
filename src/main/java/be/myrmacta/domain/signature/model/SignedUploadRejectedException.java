package be.myrmacta.domain.signature.model;

import java.util.Objects;

/**
 * Le PDF signé téléversé est refusé (TODO §138). Le motif ne décrit que le fichier du signataire : il
 * peut lui être restitué tel quel (422, propriété {@code code}).
 */
public final class SignedUploadRejectedException extends RuntimeException {

    private final transient SignedUploadRejection rejection;

    public SignedUploadRejectedException(SignedUploadRejection rejection) {
        super("signed upload rejected: " + Objects.requireNonNull(rejection, "rejection"));
        this.rejection = rejection;
    }

    public SignedUploadRejection rejection() {
        return rejection;
    }
}
