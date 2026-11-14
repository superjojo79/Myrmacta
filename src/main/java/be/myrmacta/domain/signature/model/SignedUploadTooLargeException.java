package be.myrmacta.domain.signature.model;

/** Le PDF signé téléversé dépasse la taille autorisée (TODO §138, D-EID-UPLOAD). */
public final class SignedUploadTooLargeException extends RuntimeException {

    private final long maxBytes;

    public SignedUploadTooLargeException(long maxBytes) {
        super("signed upload exceeds " + maxBytes + " bytes");
        this.maxBytes = maxBytes;
    }

    public long maxBytes() {
        return maxBytes;
    }
}
