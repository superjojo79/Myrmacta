package be.myrmacta.infrastructure.signature;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import be.myrmacta.infrastructure.signature.SignatureAnchors.AnchorBox;
import be.myrmacta.infrastructure.signature.eid.EidTestPdfs;
import be.myrmacta.infrastructure.signature.eid.EidTestPdfs.Anchor;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Position des repères {@code {{sN|signature|85|37}}} (D-SIGNATURE-ANCHOR) : cadre posé comme une ancre Yousign. */
class SignatureAnchorsTest {

    private static final float PAGE_HEIGHT = 842f;

    @Test
    @DisplayName("Rang 1 et 2 : page et coin haut gauche du texte du repère, cadre de 85 x 37")
    void locatesEachRankOnItsPage() {
        byte[] pdf = EidTestPdfs.basePdfWithAnchors(2, 0, new Anchor(0, 1, 100, 300), new Anchor(1, 2, 150, 500));

        AnchorBox first = SignatureAnchors.locate(pdf, 1).orElseThrow();
        AnchorBox second = SignatureAnchors.locate(pdf, 2).orElseThrow();

        assertThat(first.pageIndex()).isZero();
        assertThat(first.originX()).isCloseTo(100f, within(0.5f));
        // Haut du texte : entre la ligne de base et un corps de texte (10 pt) au-dessus de elle, en ordonnée vers le bas.
        assertThat(first.originY()).isBetween(PAGE_HEIGHT - 300 - 12f, PAGE_HEIGHT - 300);
        assertThat(first.width()).isEqualTo(85f);
        assertThat(first.height()).isEqualTo(37f);
        assertThat(second.pageIndex()).isEqualTo(1);
        assertThat(second.originX()).isCloseTo(150f, within(0.5f));
        assertThat(second.originY()).isBetween(PAGE_HEIGHT - 500 - 12f, PAGE_HEIGHT - 500);
    }

    @Test
    @DisplayName("CropBox décalée : le cadre garde la même position sur la page, exprimée depuis la MediaBox (origine DSS)")
    void coordinatesAreConvertedToTheMediaBox() {
        byte[] pdf = EidTestPdfs.basePdfWithAnchors(1, 20, new Anchor(0, 1, 100, 300));

        AnchorBox box = SignatureAnchors.locate(pdf, 1).orElseThrow();

        assertThat(box.originX()).isCloseTo(100f, within(0.5f));
        assertThat(box.originY()).isBetween(PAGE_HEIGHT - 300 - 12f, PAGE_HEIGHT - 300);
    }

    @Test
    @DisplayName("Rang absent, PDF sans repère, octets illisibles ou vides : vide")
    void absentOrUnreadableGivesNothing() {
        byte[] pdf = EidTestPdfs.basePdfWithAnchors(1, 0, new Anchor(0, 1, 100, 300));

        assertThat(SignatureAnchors.locate(pdf, 2)).isEmpty();
        assertThat(SignatureAnchors.locate(EidTestPdfs.basePdf(), 1)).isEmpty();
        assertThat(SignatureAnchors.locate("pas un pdf".getBytes(), 1)).isEmpty();
        assertThat(SignatureAnchors.locate(new byte[0], 1)).isEmpty();
        assertThat(SignatureAnchors.locate(null, 1)).isEmpty();
        assertThat(SignatureAnchors.locate(pdf, 0)).isEmpty();
    }

    @Test
    @DisplayName("Repère sur une page tournée : vide (le vérificateur refuse une signature visible sur une page tournée)")
    void rotatedPageGivesNothing() {
        byte[] pdf = EidTestPdfs.withRotatedPage(EidTestPdfs.basePdfWithAnchors(2, 0, new Anchor(1, 1, 100, 300)), 1);

        assertThat(SignatureAnchors.locate(pdf, 1)).isEmpty();
    }

    @Test
    @DisplayName("Délai de lecture dépassé : échec (IllegalStateException), jamais « repère absent »")
    void timeoutFailsClosed() {
        byte[] pdf = EidTestPdfs.basePdfWithAnchors(1, 0, new Anchor(0, 1, 100, 300));

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> SignatureAnchors.locate(pdf, 1, java.time.Duration.ofNanos(-1)))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("Repère en double : la première occurrence l'emporte")
    void firstOccurrenceWins() {
        byte[] pdf = EidTestPdfs.basePdfWithAnchors(2, 0, new Anchor(0, 1, 100, 300), new Anchor(1, 1, 200, 400));

        Optional<AnchorBox> box = SignatureAnchors.locate(pdf, 1);

        assertThat(box).get().extracting(AnchorBox::pageIndex).isEqualTo(0);
    }
}
