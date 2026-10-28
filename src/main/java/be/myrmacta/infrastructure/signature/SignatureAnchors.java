package be.myrmacta.infrastructure.signature;

import java.io.IOException;
import java.time.Duration;
import java.util.List;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.contentstream.operator.Operator;
import org.apache.pdfbox.cos.COSBase;
import org.apache.pdfbox.io.MemoryUsageSetting;
import org.apache.pdfbox.io.RandomAccessReadBuffer;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;

/**
 * Repères {@code {{sN|signature|85|37}}} lus dans le PDF (TODO §109, D-SIGNATURE-ANCHOR). Yousign ne
 * renvoie que {@code total_anchors} : deux ancres du même rang satisfont ce compte. Chaque rang doit
 * donc être présent dans le texte. Un PDF illisible ou trop lourd compte comme un repère absent.
 */
final class SignatureAnchors {

    static final int WIDTH = 85;
    static final int HEIGHT = 37;
    private static final long MAX_PARSE_MEMORY_BYTES = 64L * 1024 * 1024;
    private static final Duration MAX_READ = Duration.ofSeconds(20);

    private SignatureAnchors() {}

    static String marker(int rank) {
        return "{{s" + rank + "|signature|" + WIDTH + "|" + HEIGHT + "}}";
    }

    /** Vrai seulement si les rangs {@code 1..signerCount} ont chacun leur repère. */
    static boolean everyRankPresent(byte[] pdf, int signerCount) {
        if (pdf == null || pdf.length == 0 || signerCount < 1) {
            return false;
        }
        String text = text(pdf);
        for (int rank = 1; rank <= signerCount; rank++) {
            if (!text.contains(marker(rank))) {
                return false;
            }
        }
        return true;
    }

    private static String text(byte[] pdf) {
        try (PDDocument document = Loader.loadPDF(new RandomAccessReadBuffer(pdf), "", null, null,
                MemoryUsageSetting.setupMainMemoryOnly(MAX_PARSE_MEMORY_BYTES).streamCache)) {
            if (document.isEncrypted() && !document.getCurrentAccessPermission().canExtractContent()) {
                return "";
            }
            PDFTextStripper stripper = new DeadlineStripper(System.nanoTime() + MAX_READ.toNanos());
            stripper.setStartPage(1);
            stripper.setEndPage(document.getNumberOfPages());
            String extracted = stripper.getText(document);
            return extracted == null ? "" : extracted;
        } catch (IOException | RuntimeException unreadable) {
            return "";
        }
    }

    /** Arrêt au-delà du délai, vérifié à chaque opérateur du flux de contenu. */
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

        @Override
        protected void operatorException(Operator operator, List<COSBase> operands, IOException exception)
                throws IOException {
            if (exception instanceof ExtractionTimeout) {
                throw exception;
            }
            super.operatorException(operator, operands, exception);
        }
    }

    private static final class ExtractionTimeout extends IOException {
        ExtractionTimeout() {
            super("signature anchor read exceeded its time budget");
        }
    }
}
