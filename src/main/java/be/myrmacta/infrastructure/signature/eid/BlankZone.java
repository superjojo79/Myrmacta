package be.myrmacta.infrastructure.signature.eid;

import java.awt.image.BufferedImage;
import java.io.IOException;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.rendering.ImageType;
import org.apache.pdfbox.rendering.PDFRenderer;

/**
 * Règle « MOYEN E » du vérificateur : la page sous une signature visible doit être vierge. Partagée entre
 * {@link FinalGraphWalk} (qui refuse) et {@link DssCardSignatureAssembler} (qui renonce à l'apparence plutôt que de
 * faire refuser la signature après le PIN).
 */
final class BlankZone {

    private BlankZone() {}

    /**
     * Vrai si le rectangle PDF {@code {gauche, bas, droite, haut}} est vierge sur la page (à partir de 0) du document :
     * rendu à l'échelle 1, couleur du coin haut gauche comme référence, écart de canal supérieur à 24 compté pour
     * différent, tolérance de 0,2 % des pixels (bord anticrénelé d'un trait voisin). Le rectangle est ramené à la CropBox.
     */
    static boolean isBlank(PDDocument document, int pageIndex, double[] rect) throws IOException {
        BufferedImage image = new PDFRenderer(document).renderImage(pageIndex, 1f, ImageType.RGB);
        PDRectangle crop = document.getPage(pageIndex).getCropBox();
        int x0 = clamp((int) Math.floor(rect[0] - crop.getLowerLeftX()), image.getWidth());
        int x1 = clamp((int) Math.ceil(rect[2] - crop.getLowerLeftX()), image.getWidth());
        int y0 = clamp((int) Math.floor(crop.getUpperRightY() - rect[3]), image.getHeight());
        int y1 = clamp((int) Math.ceil(crop.getUpperRightY() - rect[1]), image.getHeight());
        if (x1 <= x0 || y1 <= y0) {
            return true;
        }
        int reference = image.getRGB(x0, y0);
        long total = (long) (x1 - x0) * (y1 - y0);
        long differing = 0;
        for (int y = y0; y < y1; y++) {
            for (int x = x0; x < x1; x++) {
                if (distance(reference, image.getRGB(x, y)) > 24) {
                    differing++;
                }
            }
        }
        return differing * 1000 <= total * 2;
    }

    private static int distance(int a, int b) {
        int dr = Math.abs(((a >> 16) & 0xff) - ((b >> 16) & 0xff));
        int dg = Math.abs(((a >> 8) & 0xff) - ((b >> 8) & 0xff));
        int db = Math.abs((a & 0xff) - (b & 0xff));
        return Math.max(dr, Math.max(dg, db));
    }

    private static int clamp(int value, int max) {
        return Math.max(0, Math.min(value, max));
    }
}
