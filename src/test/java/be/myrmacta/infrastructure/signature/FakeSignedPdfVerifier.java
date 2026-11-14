package be.myrmacta.infrastructure.signature;

import be.myrmacta.domain.signature.SignatureWorkingDocuments;
import be.myrmacta.domain.signature.model.SignedUploadEvidence;
import be.myrmacta.domain.signature.model.SignedUploadRejection;
import be.myrmacta.domain.signature.model.SignedUploadVerdict;
import be.myrmacta.infrastructure.signature.eid.SignedPdfVerifier;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Arrays;
import java.util.Set;

/**
 * Faux vérificateur programmable des tests du fournisseur {@code eid} (TODO §138) : aucune cryptographie, la
 * vraie validation a ses propres tests. Un « PDF signé » est le document remis suivi de {@code \n%SIG:<nom>} ;
 * l'empreinte du certificat est le SHA-256 du nom. {@link #forced} impose un refus à la prochaine vérification.
 */
public final class FakeSignedPdfVerifier implements SignedPdfVerifier {

    private static final String MARK = "\n%SIG:";
    private static volatile SignedUploadRejection forced;

    /** Refus imposé à toute vérification, jusqu'à {@link #reset}. */
    public static void force(SignedUploadRejection rejection) {
        forced = rejection;
    }

    public static void reset() {
        forced = null;
    }

    /** Le PDF que « signe » {@code name} à partir de {@code base}. */
    public static byte[] signed(byte[] base, String name) {
        byte[] mark = (MARK + name).getBytes(StandardCharsets.UTF_8);
        byte[] out = Arrays.copyOf(base, base.length + mark.length);
        System.arraycopy(mark, 0, out, base.length, mark.length);
        return out;
    }

    public static String fingerprintOf(String name) {
        return SignatureWorkingDocuments.digestOf(name.getBytes(StandardCharsets.UTF_8));
    }

    @Override
    public SignedUploadVerdict verify(byte[] baseDocument, byte[] signedDocument, Set<String> usedFingerprints) {
        if (forced != null) {
            return SignedUploadVerdict.rejected(forced);
        }
        if (signedDocument.length <= baseDocument.length
                || !Arrays.equals(baseDocument, Arrays.copyOf(signedDocument, baseDocument.length))) {
            return SignedUploadVerdict.rejected(SignedUploadRejection.DOCUMENT_MISMATCH);
        }
        String tail = new String(signedDocument, baseDocument.length, signedDocument.length - baseDocument.length,
                StandardCharsets.UTF_8);
        if (!tail.startsWith(MARK)) {
            return SignedUploadVerdict.rejected(SignedUploadRejection.NO_NEW_SIGNATURE);
        }
        String name = tail.substring(MARK.length());
        String fingerprint = fingerprintOf(name);
        if (usedFingerprints.contains(fingerprint)) {
            return SignedUploadVerdict.rejected(SignedUploadRejection.DUPLICATE_SIGNER);
        }
        Instant now = Instant.now();
        return SignedUploadVerdict.accepted(new SignedUploadEvidence(name, "CN=Test Citizen CA", "0A1B", fingerprint,
                now.minusSeconds(10), now, "PAdES-BASELINE-B", "RSA_SHA256", true, true, "OCSP", now));
    }
}
