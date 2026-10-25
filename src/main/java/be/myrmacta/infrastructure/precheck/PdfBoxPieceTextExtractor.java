package be.myrmacta.infrastructure.precheck;

import be.myrmacta.domain.precheck.model.PieceText;
import be.myrmacta.domain.precheck.port.out.PieceTextExtractorPort;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.io.MemoryUsageSetting;
import org.apache.pdfbox.io.RandomAccessReadBuffer;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.encryption.InvalidPasswordException;
import org.apache.pdfbox.text.PDFTextStripper;

/**
 * TODO §106 (lot A, P6, H3, D-PRECHECK-EXTRACT) — texte des pièces : PDF à couche texte (PDFBox) et texte brut ; ni OCR ni lecture d'image.
 * Bornes : pages lues ({@code maxPages}), caractères gardés ({@code maxChars}) et mémoire de lecture du PDF
 * ({@link #MAX_PARSE_MEMORY_BYTES}, jamais de fichier temporaire). Toute pièce illisible donne un statut, jamais une
 * exception : la pré-vérification en fait un constat « À VÉRIFIER ».
 */
final class PdfBoxPieceTextExtractor implements PieceTextExtractorPort {

    static final long MAX_PARSE_MEMORY_BYTES = 64L * 1024 * 1024;

    @Override
    public PieceText extract(byte[] content, String contentType, int maxPages, int maxChars) {
        if (content == null || content.length == 0) {
            return PieceText.unreadable(PieceText.Status.UNREADABLE);
        }
        String type = contentType == null ? "" : contentType.toLowerCase(Locale.ROOT).split(";")[0].strip();
        if (type.equals("application/pdf")) {
            return pdf(content, maxPages, maxChars);
        }
        if (type.equals("text/plain")) {
            return plain(content, maxChars);
        }
        if (type.startsWith("image/")) {
            return PieceText.unreadable(PieceText.Status.NO_TEXT);
        }
        return PieceText.unreadable(PieceText.Status.UNSUPPORTED);
    }

    private static PieceText pdf(byte[] content, int maxPages, int maxChars) {
        try (PDDocument document = Loader.loadPDF(new RandomAccessReadBuffer(content), "", null, null,
                MemoryUsageSetting.setupMainMemoryOnly(MAX_PARSE_MEMORY_BYTES).streamCache)) {
            if (document.isEncrypted() && !document.getCurrentAccessPermission().canExtractContent()) {
                return PieceText.unreadable(PieceText.Status.ENCRYPTED);
            }
            int pages = document.getNumberOfPages();
            int read = Math.min(pages, maxPages);
            PDFTextStripper stripper = new PDFTextStripper();
            stripper.setStartPage(1);
            stripper.setEndPage(read);
            return bounded(stripper.getText(document), read, pages > maxPages, maxChars);
        } catch (InvalidPasswordException encrypted) {
            return PieceText.unreadable(PieceText.Status.ENCRYPTED);
        } catch (IOException | RuntimeException unreadable) {
            return PieceText.unreadable(PieceText.Status.UNREADABLE);
        }
    }

    private static PieceText plain(byte[] content, int maxChars) {
        try {
            String text = StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(content))
                    .toString();
            return bounded(text, 1, false, maxChars);
        } catch (CharacterCodingException notUtf8) {
            return PieceText.unreadable(PieceText.Status.UNREADABLE);
        }
    }

    /** Caractères de contrôle retirés (sauf saut de ligne et tabulation), blancs répétés réduits, longueur bornée. */
    static PieceText bounded(String raw, int pagesRead, boolean pagesTruncated, int maxChars) {
        String text = raw == null ? "" : raw
                .replaceAll("[\\p{Cc}&&[^\\n\\t]]", " ")
                .replaceAll("[ \\t]+", " ")
                .replaceAll(" ?\n ?", "\n")
                .replaceAll("\\n\\s*\\n+", "\n\n")
                .strip();
        if (text.isEmpty()) {
            return PieceText.unreadable(PieceText.Status.NO_TEXT);
        }
        boolean truncated = pagesTruncated;
        if (text.length() > maxChars) {
            int end = maxChars;
            if (Character.isHighSurrogate(text.charAt(end - 1))) {
                end--; // ne jamais couper une paire de substitution
            }
            text = text.substring(0, end);
            truncated = true;
        }
        return new PieceText(PieceText.Status.READABLE, text, pagesRead, truncated);
    }
}
