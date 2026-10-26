package be.myrmacta.infrastructure.precheck;

import be.myrmacta.domain.precheck.model.PieceText;
import be.myrmacta.domain.precheck.port.out.PieceTextExtractorPort;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Locale;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.contentstream.operator.Operator;
import org.apache.pdfbox.cos.COSBase;
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
    /**
     * TODO §106 (revue adversariale de #211, F2) — durée de lecture d'une pièce. Les pièces viennent aussi de tiers (portail) :
     * un PDF piégé (flux de contenu énorme, formulaires imbriqués) ne doit pas retenir le thread du job.
     */
    static final Duration MAX_EXTRACTION = Duration.ofSeconds(20);

    private final Duration maxExtraction;

    PdfBoxPieceTextExtractor() {
        this(MAX_EXTRACTION);
    }

    PdfBoxPieceTextExtractor(Duration maxExtraction) {
        this.maxExtraction = maxExtraction;
    }

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

    private PieceText pdf(byte[] content, int maxPages, int maxChars) {
        try (PDDocument document = Loader.loadPDF(new RandomAccessReadBuffer(content), "", null, null,
                MemoryUsageSetting.setupMainMemoryOnly(MAX_PARSE_MEMORY_BYTES).streamCache)) {
            if (document.isEncrypted() && !document.getCurrentAccessPermission().canExtractContent()) {
                return PieceText.unreadable(PieceText.Status.ENCRYPTED);
            }
            int pages = document.getNumberOfPages();
            int read = Math.min(pages, maxPages);
            PDFTextStripper stripper = new DeadlineStripper(System.nanoTime() + maxExtraction.toNanos());
            stripper.setStartPage(1);
            stripper.setEndPage(read);
            return bounded(stripper.getText(document), read, pages > maxPages, maxChars);
        } catch (InvalidPasswordException encrypted) {
            return PieceText.unreadable(PieceText.Status.ENCRYPTED);
        } catch (ExtractionTimeout late) {
            return PieceText.unreadable(PieceText.Status.TOO_COMPLEX);
        } catch (IOException | RuntimeException unreadable) {
            return PieceText.unreadable(PieceText.Status.UNREADABLE);
        }
    }

    private static PieceText plain(byte[] content, int maxChars) {
        // F2 : un caractère fait au plus 4 octets en UTF-8 ; au-delà, rien ne sera gardé : inutile de tout décoder.
        int length = (int) Math.min(content.length, 4L * maxChars + 4);
        boolean cut = length < content.length;
        while (cut && length > 0 && (content[length] & 0xC0) == 0x80) {
            length--; // ne jamais couper une séquence UTF-8
        }
        try {
            String text = StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(content, 0, length))
                    .toString();
            return bounded(text, 1, cut, maxChars);
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

    /** Arrêt au-delà du délai, vérifié à chaque opérateur du flux de contenu (formulaires imbriqués compris). */
    private static final class DeadlineStripper extends PDFTextStripper {
        private final long deadline;

        DeadlineStripper(long deadline) {
            this.deadline = deadline;
        }

        @Override
        protected void processOperator(Operator operator, List<COSBase> operands) throws IOException {
            if (System.nanoTime() - deadline > 0) {
                throw new ExtractionTimeout();
            }
            super.processOperator(operator, operands);
        }

        /** PDFBox ignore les erreurs d'un formulaire ({@code Do}) : le délai dépassé, lui, doit remonter. */
        @Override
        protected void operatorException(Operator operator, List<COSBase> operands, IOException e) throws IOException {
            if (e instanceof ExtractionTimeout) {
                throw e;
            }
            super.operatorException(operator, operands, e);
        }
    }

    private static final class ExtractionTimeout extends IOException {
        ExtractionTimeout() {
            super("piece extraction exceeded its time budget");
        }
    }
}
