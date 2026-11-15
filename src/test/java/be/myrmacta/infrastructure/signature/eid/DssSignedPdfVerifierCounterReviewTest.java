package be.myrmacta.infrastructure.signature.eid;

import static org.assertj.core.api.Assertions.assertThat;

import be.myrmacta.domain.signature.model.SignedUploadRejection;
import be.myrmacta.domain.signature.model.SignedUploadVerdict;
import be.myrmacta.infrastructure.signature.eid.EidTestPdfs.Keys;
import be.myrmacta.infrastructure.signature.eid.EidTestPdfs.SignOptions;
import be.myrmacta.infrastructure.signature.eid.EidTestPki.KeyType;
import be.myrmacta.infrastructure.signature.eid.EidTestPki.Signer;
import java.util.Map;
import java.util.Set;
import org.apache.pdfbox.cos.COSArray;
import org.apache.pdfbox.cos.COSDictionary;
import org.apache.pdfbox.cos.COSName;
import org.apache.pdfbox.cos.COSObject;
import org.apache.pdfbox.cos.COSObjectKey;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Scénarios de la contre-revue (issue #10) : PDF forgés, chacun promis comme refusé. */
class DssSignedPdfVerifierCounterReviewTest {

    private static EidTestPki pki;
    private static Signer signer;
    private static Signer second;
    private static byte[] base;
    private static byte[] base12;
    private static byte[] signed12;

    @BeforeAll
    static void setUp() {
        pki = EidTestPki.create("Counter Review");
        signer = pki.qualifiedSigner(KeyType.RSA_2048);
        second = pki.qualifiedSigner(KeyType.EC_P384);
        base = EidTestPdfs.basePdf();
        base12 = EidTestPdfs.basePdfSharedFont(12);
        signed12 = EidTestPdfs.sign(base12, signer);
    }

    private static SignedUploadVerdict verify(byte[] previous, byte[] uploaded) {
        return new DssSignedPdfVerifier(pki.trustedFile(), EidTestPki.CLOCK, pki.transport()).verify(previous, uploaded, Set.of());
    }

    private static void assertRejected(SignedUploadVerdict verdict, SignedUploadRejection expected) {
        assertThat(verdict.isAccepted()).as("accepted").isFalse();
        assertThat(verdict.rejection()).isEqualTo(expected);
    }

    private static String ref(int number) {
        return number + " 0 R";
    }

    @Test
    @DisplayName("A — /Root neuf, /Pages neuf, copie neuve de la page 5 avec une police neuve : refusé")
    void newRootGraph() {
        Keys keys = EidTestPdfs.keys(signed12, 4);
        int catalog = keys.highest() + 1;
        int pages = catalog + 1;
        int page = catalog + 2;
        int font = catalog + 3;
        COSDictionary catalogCopy = EidTestPdfs.dictionary(signed12, keys.catalog());
        catalogCopy.setItem(COSName.PAGES, new COSObject(null, new COSObjectKey(pages, 0)));
        COSDictionary pagesCopy = EidTestPdfs.dictionary(signed12, keys.pages());
        COSArray kids = (COSArray) pagesCopy.getDictionaryObject(COSName.KIDS);
        COSArray newKids = new COSArray();
        for (int i = 0; i < kids.size(); i++) {
            newKids.add(i == 4 ? new COSObject(null, new COSObjectKey(page, 0)) : kids.get(i));
        }
        pagesCopy.setItem(COSName.KIDS, newKids);
        String newPage = "<< /Type /Page /Parent " + ref(pages) + " /MediaBox [0 0 595.27563 841.8898] /Contents "
                + ref(keys.content()) + " /Resources << /Font << /F1 " + ref(font) + " >> >> >>";
        byte[] uploaded = EidTestPdfs.appendRevision(signed12, Map.of(
                catalog, EidTestPdfs.cos(catalogCopy),
                pages, EidTestPdfs.cos(pagesCopy),
                page, newPage,
                font, "<< /Type /Font /Subtype /Type1 /BaseFont /Courier >>"), null, null, ref(catalog));

        assertRejected(verify(base12, uploaded), SignedUploadRejection.CONTENT_MODIFIED);
    }

    @Test
    @DisplayName("B — entrée libre ajoutée pour le flux de contenu d'une page : refusé")
    void freeEntryForAContentStream() {
        Keys keys = EidTestPdfs.keys(signed12, 4);
        byte[] uploaded = EidTestPdfs.appendRevision(signed12, Map.of(), null,
                keys.content() + " 1\n0000000000 00001 f \n", ref(keys.catalog()));

        assertRejected(verify(base12, uploaded), SignedUploadRejection.CONTENT_MODIFIED);
    }

    @Test
    @DisplayName("B — même numéro, autre génération, pour le flux de contenu d'une page : refusé")
    void sameNumberOtherGeneration() {
        Keys keys = EidTestPdfs.keys(signed12, 4);
        byte[] uploaded = EidTestPdfs.appendRevision(signed12,
                Map.of(keys.content(), EidTestPdfs.contentStream("BT /F1 12 Tf 72 760 Td (Clause 5 : montant 1 EUR) Tj ET")),
                Map.of(keys.content(), 1), null, ref(keys.catalog()));

        assertRejected(verify(base12, uploaded), SignedUploadRejection.CONTENT_MODIFIED);
    }

    @Test
    @DisplayName("C — une vraie signature et vingt champs de signature vides en mosaïque opaque : refusé")
    void emptySignatureFieldMosaic() {
        byte[] uploaded = EidTestPdfs.signWithPdfBox(base, signer, "adbe.pkcs7.detached", EidTestPdfs.emptySignatureMosaic(20));

        assertRejected(verify(base, uploaded), SignedUploadRejection.CONTENT_MODIFIED);
    }

    @Test
    @DisplayName("D — /DSS planté avec une clé hors liste (/Contents vers le flux d'une page) : refusé dès le premier signataire")
    void plantedDssKey() {
        assertRejected(verify(base12, plantedDss()), SignedUploadRejection.CONTENT_MODIFIED);
    }

    @Test
    @DisplayName("D — second signataire complice qui redéfinit l'objet planté sous /DSS (flux d'une page) : refusé")
    void accompliceRedefinesObjectPlantedUnderDss() {
        byte[] planted = plantedDss();
        byte[] signedBySecond = EidTestPdfs.sign(planted, second);
        Keys keys = EidTestPdfs.keys(planted, 4);
        byte[] uploaded = EidTestPdfs.appendRevision(signedBySecond,
                Map.of(keys.content(), EidTestPdfs.contentStream("BT /F1 12 Tf 72 760 Td (Clause 5 : montant 1 EUR) Tj ET")),
                null, null, ref(keys.catalog()));

        assertRejected(verify(planted, uploaded), SignedUploadRejection.CONTENT_MODIFIED);
    }

    /** Premier signataire, puis catalogue redéfini avec {@code /DSS << /Contents flux de la page 5 >>}. */
    private static byte[] plantedDss() {
        Keys keys = EidTestPdfs.keys(signed12, 4);
        int dss = keys.highest() + 1;
        COSDictionary catalog = EidTestPdfs.dictionary(signed12, keys.catalog());
        catalog.setItem(COSName.getPDFName("DSS"), new COSObject(null, new COSObjectKey(dss, 0)));
        return EidTestPdfs.appendRevision(signed12, Map.of(
                keys.catalog(), EidTestPdfs.cos(catalog),
                dss, "<< /Contents " + ref(keys.content()) + " >>"), null, null, ref(keys.catalog()));
    }

    @Test
    @DisplayName("E — signature visible posée sur le texte du document remis : refusé")
    void visibleSignatureOverText() {
        byte[] uploaded = EidTestPdfs.sign(base, signer, SignOptions.defaults().visibleAt(60, 60, 200, 40));

        assertRejected(verify(base, uploaded), SignedUploadRejection.CONTENT_MODIFIED);
    }

    @Test
    @DisplayName("E — signature visible ordinaire dans une zone vide : acceptée")
    void visibleSignatureInBlankArea() {
        SignedUploadVerdict verdict = verify(base, EidTestPdfs.sign(base, signer, SignOptions.defaults().visibleAt(300, 600, 200, 60)));

        assertThat(verdict.isAccepted()).as("rejection %s", verdict.rejection()).isTrue();
    }

    @Test
    @DisplayName("F — signature de certification (/Perms /DocMDP P=1) : refusée")
    void certificationSignature() {
        assertRejected(verify(base, EidTestPdfs.sign(base, signer, SignOptions.defaults().certifying())),
                SignedUploadRejection.CONTENT_MODIFIED);
    }

    @Test
    @DisplayName("Widget pleine page sur 12 pages (sans comparaison visuelle) : la borne d'aire tient seule")
    void fullPageWidgetBeyondVisualComparison() {
        byte[] uploaded = EidTestPdfs.signWithPdfBox(base12, signer, "adbe.pkcs7.detached", null, EidTestPdfs.FULL_PAGE_APPEARANCE);

        assertRejected(verify(base12, uploaded), SignedUploadRejection.CONTENT_MODIFIED);
    }

    @Test
    @DisplayName("Second signataire sur 12 pages : accepté")
    void secondSignerOnTwelvePages() {
        SignedUploadVerdict verdict = verify(signed12, EidTestPdfs.sign(signed12, second));

        assertThat(verdict.isAccepted()).as("rejection %s", verdict.rejection()).isTrue();
    }

    @Test
    @DisplayName("B — document remis à flux de références (objets compressés) : signature, second signataire et horodatage acceptés")
    void crossReferenceStreams() throws Exception {
        byte[] compressed;
        try (org.apache.pdfbox.pdmodel.PDDocument document = org.apache.pdfbox.Loader.loadPDF(EidTestPdfs.basePdfSharedFont(3));
                java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream()) {
            document.save(out, org.apache.pdfbox.pdfwriter.compress.CompressParameters.DEFAULT_COMPRESSION);
            compressed = out.toByteArray();
        }
        assertThat(new String(compressed, java.nio.charset.StandardCharsets.ISO_8859_1)).contains("/XRef").contains("/ObjStm");
        byte[] once = EidTestPdfs.sign(compressed, signer);
        assertThat(new String(once, compressed.length, once.length - compressed.length, java.nio.charset.StandardCharsets.ISO_8859_1))
                .contains("/XRef");

        SignedUploadVerdict first = verify(compressed, once);
        SignedUploadVerdict secondOne = verify(once, EidTestPdfs.sign(once, second));
        SignedUploadVerdict stamped = verify(compressed, EidTestPdfs.documentTimestamp(once));

        assertThat(first.isAccepted()).as("rejection %s", first.rejection()).isTrue();
        assertThat(secondOne.isAccepted()).as("rejection %s", secondOne.rejection()).isTrue();
        assertThat(stamped.isAccepted()).as("rejection %s", stamped.rejection()).isTrue();
    }

    @Test
    @DisplayName("B — section de références ajoutée illisible : refusé")
    void unreadableAddedSection() {
        Keys keys = EidTestPdfs.keys(signed12, 4);
        byte[] uploaded = EidTestPdfs.appendRevision(signed12, Map.of(), null, "abc def\n", ref(keys.catalog()));

        assertRejected(verify(base12, uploaded), SignedUploadRejection.CONTENT_MODIFIED);
    }
}
