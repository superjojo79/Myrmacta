package be.myrmacta.domain.signature.model;

/**
 * L'heure de signature fixée à la préparation d'une signature par carte est trop ancienne (plus de quinze minutes)
 * ou dans le futur (TODO §138, D-EID-BRIDGE) : le signataire recommence. Hors de {@link SignedUploadRejection}, qui ne
 * décrit que le fichier téléversé.
 */
public final class SigningSessionExpiredException extends RuntimeException {

    public SigningSessionExpiredException() {
        super("signing session expired");
    }
}
