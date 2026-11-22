package be.myrmacta.infrastructure.signature;

import java.io.IOException;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.contentstream.operator.Operator;
import org.apache.pdfbox.cos.COSBase;
import org.apache.pdfbox.io.MemoryUsageSetting;
import org.apache.pdfbox.io.RandomAccessReadBuffer;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.pdfbox.text.TextPosition;

/**
 * Repères {@code {{sN|signature|85|37}}} lus dans le PDF (TODO §109, D-SIGNATURE-ANCHOR). Yousign ne
 * renvoie que {@code total_anchors} : deux ancres du même rang satisfont ce compte. Chaque rang doit
 * donc être présent dans le texte. Un PDF illisible ou trop lourd compte comme un repère absent.
 */
public final class SignatureAnchors {

    public static final int WIDTH = 85;
    public static final int HEIGHT = 37;
    private static final long MAX_PARSE_MEMORY_BYTES = 64L * 1024 * 1024;
    private static final Duration MAX_READ = Duration.ofSeconds(20);

    private SignatureAnchors() {}

    /**
     * Cadre de signature d'un repère, comme les ancres intelligentes de Yousign : le coin haut gauche du cadre est le
     * coin haut gauche du texte du repère.
     *
     * @param pageIndex page du repère, à partir de 0
     * @param originX abscisse du coin haut gauche, en points, depuis le bord gauche de la MediaBox
     * @param originY ordonnée du coin haut gauche, en points, depuis le bord haut de la MediaBox : c'est l'origine des
     *     paramètres de champ de signature de DSS 6.5.1 (haut gauche de la MediaBox, ordonnée vers le bas ; la CropBox
     *     est ignorée par DSS, d'où la conversion faite ici)
     */
    public record AnchorBox(int pageIndex, float originX, float originY, float width, float height) {}

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

    /**
     * Résultat de la recherche d'un repère : le cadre, ou la catégorie de repli sans donnée personnelle
     * ({@code absent} : PDF illisible, chiffré, sans repère ; {@code rotated} : page tournée).
     */
    public record Located(AnchorBox box, String fallbackReason) {}

    /**
     * Cadre du repère du rang donné ({@code 1} pour le premier signataire). La première occurrence l'emporte. Vide
     * quand le PDF est illisible, chiffré sans droit d'extraction, trop lourd, quand le repère est absent, ou quand sa
     * page est tournée (un cadre n'y serait pas vérifiable).
     *
     * @throws IllegalStateException si la lecture dépasse son délai : seule entrée non déterministe, donc un échec
     *     franc (et non « absent ») pour que la préparation et l'assemblage ne divergent jamais
     */
    public static Optional<AnchorBox> locate(byte[] pdf, int rank) {
        return Optional.ofNullable(find(pdf, rank).box());
    }

    /** Comme {@link #locate(byte[], int)}, avec la catégorie de repli quand il n'y a pas de cadre. */
    public static Located find(byte[] pdf, int rank) {
        return find(pdf, rank, MAX_READ);
    }

    static Optional<AnchorBox> locate(byte[] pdf, int rank, Duration budget) {
        return Optional.ofNullable(find(pdf, rank, budget).box());
    }

    private static Located find(byte[] pdf, int rank, Duration budget) {
        Located absent = new Located(null, "absent");
        if (pdf == null || pdf.length == 0 || rank < 1) {
            return absent;
        }
        try (PDDocument document = Loader.loadPDF(new RandomAccessReadBuffer(pdf), "", null, null,
                MemoryUsageSetting.setupMainMemoryOnly(MAX_PARSE_MEMORY_BYTES).streamCache)) {
            if (document.isEncrypted() && !document.getCurrentAccessPermission().canExtractContent()) {
                return absent;
            }
            AnchorLocator locator = new AnchorLocator(System.nanoTime() + budget.toNanos(), marker(rank));
            locator.setStartPage(1);
            locator.setEndPage(document.getNumberOfPages());
            locator.getText(document);
            if (locator.found == null) {
                return absent;
            }
            PDPage page = document.getPage(locator.found.pageIndex());
            if (page.getRotation() % 360 != 0) {
                return new Located(null, "rotated");
            }
            return new Located(toMediaBox(locator.found, page), null);
        } catch (ExtractionTimeout timeout) {
            throw new IllegalStateException("signature anchor read exceeded its time budget", timeout);
        } catch (IOException | RuntimeException unreadable) {
            return absent;
        }
    }

    /**
     * Les coordonnées d'affichage de PDFBox partent du coin haut gauche de la CropBox ; DSS place le widget depuis
     * le coin haut gauche de la MediaBox (vérifié sur DSS 6.5.1 avec des boîtes décalées : abscisse PDF =
     * {@code MediaBox.gauche + originX}, ordonnée PDF du haut = {@code MediaBox.haut - originY}).
     */
    private static AnchorBox toMediaBox(AnchorBox display, PDPage page) {
        PDRectangle media = page.getMediaBox();
        PDRectangle crop = page.getCropBox();
        return new AnchorBox(display.pageIndex(),
                display.originX() + crop.getLowerLeftX() - media.getLowerLeftX(),
                display.originY() + media.getUpperRightY() - crop.getUpperRightY(),
                display.width(), display.height());
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
    private static class DeadlineStripper extends PDFTextStripper {
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

    /**
     * Cherche le repère dans chaque mot écrit. Les coordonnées de {@link TextPosition} sont celles de l'espace
     * d'affichage de PDFBox : origine en haut à gauche de la CropBox, ordonnée vers le bas, déjà corrigées de la
     * rotation de page (nulle ici). Le haut du texte est la ligne de base moins la hauteur du glyphe.
     */
    private static final class AnchorLocator extends DeadlineStripper {
        private final String marker;
        private AnchorBox found;

        AnchorLocator(long deadline, String marker) {
            super(deadline);
            this.marker = marker;
        }

        @Override
        protected void writeString(String text, List<TextPosition> positions) throws IOException {
            if (found == null && positions != null && !positions.isEmpty()) {
                StringBuilder joined = new StringBuilder();
                int[] starts = new int[positions.size()];
                for (int i = 0; i < positions.size(); i++) {
                    starts[i] = joined.length();
                    joined.append(positions.get(i).getUnicode());
                }
                int at = joined.indexOf(marker);
                if (at >= 0) {
                    int index = positions.size() - 1;
                    while (index > 0 && starts[index] > at) {
                        index--;
                    }
                    TextPosition first = positions.get(index);
                    found = new AnchorBox(getCurrentPageNo() - 1, first.getXDirAdj(),
                            first.getYDirAdj() - first.getHeightDir(), WIDTH, HEIGHT);
                }
            }
            super.writeString(text, positions);
        }
    }

    private static final class ExtractionTimeout extends IOException {
        ExtractionTimeout() {
            super("signature anchor read exceeded its time budget");
        }
    }
}
