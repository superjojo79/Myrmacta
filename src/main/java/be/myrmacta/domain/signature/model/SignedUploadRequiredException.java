package be.myrmacta.domain.signature.model;

/**
 * Le signataire a cliqué « Signer » alors que le fournisseur attend un PDF signé hors de Myrmacta
 * (TODO §138, D-EID-UPLOAD). Le refus (DECLINE) reste possible.
 */
public final class SignedUploadRequiredException extends RuntimeException {

    public SignedUploadRequiredException() {
        super("signature requires a signed document upload");
    }
}
