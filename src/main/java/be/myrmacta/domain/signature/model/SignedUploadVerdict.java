package be.myrmacta.domain.signature.model;

import java.util.Objects;

/**
 * Verdict de la vérification d'un PDF signé téléversé (TODO §138) : accepté avec ses constats, ou
 * refusé avec un motif. Jamais les deux.
 */
public record SignedUploadVerdict(SignedUploadRejection rejection, SignedUploadEvidence evidence) {

    public SignedUploadVerdict {
        if ((rejection == null) == (evidence == null)) {
            throw new IllegalArgumentException("a verdict carries either a rejection or an evidence");
        }
    }

    public static SignedUploadVerdict accepted(SignedUploadEvidence evidence) {
        return new SignedUploadVerdict(null, Objects.requireNonNull(evidence, "evidence"));
    }

    public static SignedUploadVerdict rejected(SignedUploadRejection rejection) {
        return new SignedUploadVerdict(Objects.requireNonNull(rejection, "rejection"), null);
    }

    public boolean isAccepted() {
        return rejection == null;
    }
}
