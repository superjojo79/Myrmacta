package be.myrmacta.infrastructure.signature.eid;

import be.myrmacta.domain.signature.model.SignedUploadRejection;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.cos.COSName;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.interactive.digitalsignature.PDSignature;

/**
 * Contrôles d'octets et de structure d'un téléversement, avant toute cryptographie (TODO §138, D-EID-BINDING) :
 * fichier PDF lisible et non chiffré, prolongement octet pour octet du document remis, exactement une signature
 * nouvelle (les horodatages de document ne comptent pas), {@code /ByteRange} qui couvre tout le fichier jusqu'à la
 * fin d'une révision, aucun octet après la dernière révision. Bornes contre le déni de service : nombre de révisions
 * ajoutées et de signatures examinées.
 *
 * <p>Le contrôle des objets modifiés (D-EID-BINDING, « les objets ajoutés ne servent qu'à la signature ») se fait
 * ensuite, avec les révisions repérées ici ({@link DssSignedPdfVerifier}).
 */
final class UploadStructure {

    private static final byte[] EOF_MARKER = "%%EOF".getBytes(StandardCharsets.US_ASCII);
    private static final byte[] PDF_HEADER = "%PDF-".getBytes(StandardCharsets.US_ASCII);
    /** Octets blancs tolérés après le dernier {@code %%EOF} (fins de ligne, espaces). */
    private static final int MAX_TRAILING_WHITESPACE = 64;

    /** Résultat : un refus, ou la fin de la révision couverte par la nouvelle signature. */
    record Result(SignedUploadRejection rejection, int signedRevisionEnd, String subFilter) {
        static Result refuse(SignedUploadRejection rejection) {
            return new Result(rejection, -1, null);
        }

        boolean refused() {
            return rejection != null;
        }
    }

    record Limits(int maxAddedRevisions, int maxSignatures) {}

    private UploadStructure() {}

    static Result inspect(byte[] base, byte[] signed, Limits limits) {
        if (signed == null || signed.length < PDF_HEADER.length || !startsWith(signed, PDF_HEADER)) {
            return Result.refuse(SignedUploadRejection.NOT_A_PDF);
        }
        // D-EID-BINDING : le prolongement se compare sur les octets, avant toute analyse du fichier (moins cher, et un
        // fichier étranger n'est jamais analysé).
        if (Arrays.equals(base, signed)) {
            // Le document remis renvoyé tel quel : le signataire n'a pas signé.
            return Result.refuse(SignedUploadRejection.NO_NEW_SIGNATURE);
        }
        if (!extendsBase(base, signed)) {
            return Result.refuse(SignedUploadRejection.DOCUMENT_MISMATCH);
        }
        List<int[]> newSignatureRanges = new ArrayList<>();
        List<String> newSubFilters = new ArrayList<>();
        try (PDDocument document = Loader.loadPDF(signed)) {
            if (document.isEncrypted()) {
                return Result.refuse(SignedUploadRejection.NOT_A_PDF);
            }
            if (countMarkers(signed, base.length) > limits.maxAddedRevisions()) {
                return Result.refuse(SignedUploadRejection.CONTENT_MODIFIED);
            }
            List<PDSignature> dictionaries = document.getSignatureDictionaries();
            if (dictionaries.size() > limits.maxSignatures()) {
                return Result.refuse(SignedUploadRejection.SEVERAL_SIGNATURES);
            }
            for (PDSignature dictionary : dictionaries) {
                int[] range = byteRange(dictionary);
                boolean newOne = range == null || end(range) > base.length;
                if (!newOne || isDocumentTimestamp(dictionary)) {
                    continue;
                }
                if (range == null) {
                    // Une signature nouvelle sans /ByteRange lisible ne couvre rien.
                    return Result.refuse(SignedUploadRejection.CONTENT_MODIFIED);
                }
                newSignatureRanges.add(range);
                newSubFilters.add(dictionary.getSubFilter());
            }
        } catch (IOException | RuntimeException unreadable) {
            return Result.refuse(SignedUploadRejection.NOT_A_PDF);
        }
        if (newSignatureRanges.isEmpty()) {
            return Result.refuse(SignedUploadRejection.NO_NEW_SIGNATURE);
        }
        if (newSignatureRanges.size() > 1) {
            return Result.refuse(SignedUploadRejection.SEVERAL_SIGNATURES);
        }
        int[] range = newSignatureRanges.get(0);
        long revisionEnd = end(range);
        // [0, a] et [b, b+c] : le trou (/Contents) est dans la partie ajoutée, la couverture va jusqu'à la fin d'une
        // révision, et rien n'est ajouté après la dernière révision.
        if (range[0] != 0 || range[1] < base.length || range[2] <= range[1] || revisionEnd > signed.length
                || !endsWithEofMarker(signed, (int) revisionEnd) || !onlyWhitespaceAfterLastMarker(signed)) {
            return Result.refuse(SignedUploadRejection.CONTENT_MODIFIED);
        }
        return new Result(null, (int) revisionEnd, newSubFilters.get(0));
    }

    static boolean extendsBase(byte[] base, byte[] signed) {
        return signed.length > base.length && Arrays.mismatch(base, 0, base.length, signed, 0, base.length) == -1;
    }

    private static boolean isDocumentTimestamp(PDSignature dictionary) {
        String type = dictionary.getCOSObject().getNameAsString(COSName.TYPE);
        return "DocTimeStamp".equals(type) || "ETSI.RFC3161".equals(dictionary.getSubFilter());
    }

    /** {@code /ByteRange} à quatre entiers positifs, sinon {@code null}. */
    private static int[] byteRange(PDSignature dictionary) {
        try {
            int[] range = dictionary.getByteRange();
            if (range == null || range.length != 4) {
                return null;
            }
            for (int value : range) {
                if (value < 0) {
                    return null;
                }
            }
            return range;
        } catch (RuntimeException malformed) {
            return null;
        }
    }

    private static long end(int[] range) {
        return (long) range[2] + range[3];
    }

    private static boolean startsWith(byte[] data, byte[] prefix) {
        return Arrays.mismatch(data, 0, prefix.length, prefix, 0, prefix.length) == -1;
    }

    /** Nombre de {@code %%EOF} après {@code from} : une révision ajoutée par marqueur. */
    static int countMarkers(byte[] data, int from) {
        int count = 0;
        for (int i = Math.max(0, from); i <= data.length - EOF_MARKER.length; i++) {
            if (data[i] == '%' && matchesAt(data, i, EOF_MARKER)) {
                count++;
                i += EOF_MARKER.length - 1;
            }
        }
        return count;
    }

    /** Les octets qui précèdent {@code end} se terminent par {@code %%EOF}, suivi au plus d'une fin de ligne. */
    private static boolean endsWithEofMarker(byte[] data, int end) {
        int i = end;
        int eol = 0;
        while (i > 0 && (data[i - 1] == '\n' || data[i - 1] == '\r') && eol < 2) {
            i--;
            eol++;
        }
        return i >= EOF_MARKER.length && matchesAt(data, i - EOF_MARKER.length, EOF_MARKER);
    }

    private static boolean onlyWhitespaceAfterLastMarker(byte[] data) {
        int i = data.length;
        int blank = 0;
        while (i > 0 && isWhitespace(data[i - 1])) {
            i--;
            if (++blank > MAX_TRAILING_WHITESPACE) {
                return false;
            }
        }
        return i >= EOF_MARKER.length && matchesAt(data, i - EOF_MARKER.length, EOF_MARKER);
    }

    private static boolean isWhitespace(byte b) {
        return b == '\n' || b == '\r' || b == ' ' || b == '\t' || b == 0x0C || b == 0;
    }

    private static boolean matchesAt(byte[] data, int offset, byte[] pattern) {
        if (offset < 0 || offset + pattern.length > data.length) {
            return false;
        }
        for (int j = 0; j < pattern.length; j++) {
            if (data[offset + j] != pattern[j]) {
                return false;
            }
        }
        return true;
    }
}
