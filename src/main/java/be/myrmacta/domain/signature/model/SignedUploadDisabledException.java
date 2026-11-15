package be.myrmacta.domain.signature.model;

/**
 * Le téléversement d'un PDF signé hors de l'application est désactivé (TODO §138,
 * {@code app.signature.eid.upload-enabled}). Levée après la résolution d'accès : le refus uniforme garde la priorité.
 */
public final class SignedUploadDisabledException extends RuntimeException {

    public SignedUploadDisabledException() {
        super("signed upload is disabled");
    }
}
