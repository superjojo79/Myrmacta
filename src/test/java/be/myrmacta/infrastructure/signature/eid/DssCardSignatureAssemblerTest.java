package be.myrmacta.infrastructure.signature.eid;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import be.myrmacta.domain.signature.model.SignedUploadRejection;
import be.myrmacta.domain.signature.model.SignedUploadVerdict;
import be.myrmacta.domain.signature.port.out.SignaturePort.CardSignatureDigest;
import be.myrmacta.domain.signature.port.out.SignaturePort.CardSignatureInput;
import be.myrmacta.infrastructure.signature.SignatureAnchors;
import be.myrmacta.infrastructure.signature.eid.EidTestPdfs.Anchor;
import be.myrmacta.infrastructure.signature.eid.EidTestPki.KeyType;
import be.myrmacta.infrastructure.signature.eid.EidTestPki.Signer;
import eu.europa.esig.dss.spi.DSSASN1Utils;
import java.security.Signature;
import java.security.cert.X509Certificate;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.ArrayList;
import java.util.Set;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.interactive.annotation.PDAnnotation;
import org.apache.pdfbox.pdmodel.interactive.annotation.PDAnnotationWidget;
import org.apache.pdfbox.text.PDFTextStripper;
import org.bouncycastle.asn1.DERNull;
import org.bouncycastle.asn1.nist.NISTObjectIdentifiers;
import org.bouncycastle.asn1.x509.AlgorithmIdentifier;
import org.bouncycastle.asn1.x509.DigestInfo;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Signature par carte en deux temps (TODO §138, D-EID-BRIDGE) : préparation et assemblage par deux instances
 * distinctes (comme deux requêtes HTTP), signature « comme une carte » d'un condensé déjà calculé, puis le résultat
 * passe par le vérificateur.
 */
class DssCardSignatureAssemblerTest {

    private static final Instant SIGNING_TIME = EidTestPki.NOW.minusSeconds(30);

    private static EidTestPki pki;
    private static byte[] base;

    @BeforeAll
    static void setUp() {
        pki = EidTestPki.create("Card Test");
        base = EidTestPdfs.basePdf();
    }

    private static DssCardSignatureAssembler assembler() {
        return new DssCardSignatureAssembler(pki.trustedFile());
    }

    private static SignedUploadVerdict verify(byte[] baseDocument, byte[] result, Set<String> used) {
        return new DssSignedPdfVerifier(pki.trustedFile(), EidTestPki.CLOCK, pki.transport()).verify(baseDocument, result, used);
    }

    private static CardSignatureInput input(byte[] document, Signer signer, List<byte[]> chain, Instant time) {
        return input(document, signer, chain, time, 1);
    }

    private static CardSignatureInput input(byte[] document, Signer signer, List<byte[]> chain, Instant time, int rank) {
        return new CardSignatureInput(document, encoded(signer.certificate()), chain, time, rank);
    }

    /** Ce que fait la carte : signer un condensé reçu, sans le recalculer. */
    private static byte[] cardSign(Signer signer, CardSignatureDigest digest, boolean ecdsaRaw) throws Exception {
        if (signer.keyType() == KeyType.RSA_2048) {
            assertThat(digest.hashFunction()).isEqualTo("SHA-256");
            byte[] digestInfo = new DigestInfo(new AlgorithmIdentifier(NISTObjectIdentifiers.id_sha256, DERNull.INSTANCE),
                    digest.digest()).getEncoded();
            Signature rsa = Signature.getInstance("NONEwithRSA");
            rsa.initSign(signer.key());
            rsa.update(digestInfo);
            return rsa.sign();
        }
        assertThat(digest.hashFunction()).isEqualTo("SHA-384");
        Signature ecdsa = Signature.getInstance("NONEwithECDSA");
        ecdsa.initSign(signer.key());
        ecdsa.update(digest.digest());
        byte[] der = ecdsa.sign();
        return ecdsaRaw ? DSSASN1Utils.toPlainDSASignatureValue(der) : der;
    }

    private static byte[] signWithCard(byte[] document, Signer signer, List<byte[]> chain, boolean ecdsaRaw) throws Exception {
        return signWithCard(document, signer, chain, ecdsaRaw, 1);
    }

    private static byte[] signWithCard(byte[] document, Signer signer, List<byte[]> chain, boolean ecdsaRaw, int rank)
            throws Exception {
        CardSignatureInput input = input(document, signer, chain, SIGNING_TIME, rank);
        CardSignatureDigest digest = assembler().prepare(input);
        // Seconde requête : nouvelle instance, entrées reconstruites.
        CardSignatureInput again = input(document.clone(), signer, chain, SIGNING_TIME, rank);
        return assembler().assemble(again, cardSign(signer, digest, ecdsaRaw));
    }

    @Test
    @DisplayName("RSA 2048 : prepare, signature du condensé, assemble par une autre instance, verify accepté")
    void rsaEndToEnd() throws Exception {
        Signer signer = pki.qualifiedSigner(KeyType.RSA_2048);

        byte[] result = signWithCard(base, signer, List.of(encoded(pki.intermediate())), false);

        assertThat(Arrays.mismatch(base, Arrays.copyOf(result, base.length))).isEqualTo(-1);
        SignedUploadVerdict verdict = verify(base, result, Set.of());
        assertThat(verdict.isAccepted()).as("rejection %s", verdict.rejection()).isTrue();
        assertThat(verdict.evidence().signatureFormat()).isEqualTo("PAdES-BASELINE-B");
        assertThat(verdict.evidence().signingTime()).isEqualTo(SIGNING_TIME);
    }

    @Test
    @DisplayName("ECDSA P-384, valeur brute r||s, chaîne vide (Web eID) complétée par les autorités : accepté")
    void ecdsaRawWithoutChain() throws Exception {
        Signer signer = pki.qualifiedSigner(KeyType.EC_P384);

        byte[] result = signWithCard(base, signer, List.of(), true);

        SignedUploadVerdict verdict = verify(base, result, Set.of());
        assertThat(verdict.isAccepted()).as("rejection %s", verdict.rejection()).isTrue();
        assertThat(verdict.evidence().signatureAlgorithm()).isEqualTo("SHA384withECDSA");
    }

    @Test
    @DisplayName("ECDSA P-384, valeur DER : acceptée")
    void ecdsaDer() throws Exception {
        Signer signer = pki.qualifiedSigner(KeyType.EC_P384);

        SignedUploadVerdict verdict = verify(base, signWithCard(base, signer, List.of(), false), Set.of());

        assertThat(verdict.isAccepted()).as("rejection %s", verdict.rejection()).isTrue();
    }

    @Test
    @DisplayName("Le condensé ne dépend que des entrées : deux préparations donnent le même")
    void prepareIsDeterministic() {
        Signer signer = pki.qualifiedSigner(KeyType.RSA_2048);
        CardSignatureInput input = input(base, signer, List.of(), SIGNING_TIME);

        assertThat(assembler().prepare(input).digest()).isEqualTo(assembler().prepare(input).digest());
        assertThat(assembler().prepare(input(base, signer, List.of(), SIGNING_TIME.plusSeconds(1))).digest())
                .isNotEqualTo(assembler().prepare(input).digest());
    }

    @Test
    @DisplayName("Second signataire par carte par-dessus un premier : accepté")
    void secondSigner() throws Exception {
        Signer first = pki.qualifiedSigner(KeyType.RSA_2048);
        Signer second = pki.qualifiedSigner(KeyType.EC_P384);
        byte[] once = signWithCard(base, first, List.of(), false);

        byte[] twice = signWithCard(once, second, List.of(), true);

        SignedUploadVerdict verdict = verify(once, twice, Set.of(SigningCertificateFacts.fingerprint(encoded(first.certificate()))));
        assertThat(verdict.isAccepted()).as("rejection %s", verdict.rejection()).isTrue();
    }

    @Test
    @DisplayName("Mauvaise valeur de signature : le vérificateur refuse SIGNATURE_INVALID")
    void wrongSignatureValue() throws Exception {
        Signer signer = pki.qualifiedSigner(KeyType.RSA_2048);
        CardSignatureInput input = input(base, signer, List.of(), SIGNING_TIME);
        byte[] value = cardSign(signer, assembler().prepare(input), false);
        value[10] ^= 0x01;

        assertThat(verify(base, assembler().assemble(input, value), Set.of()).rejection())
                .isEqualTo(SignedUploadRejection.SIGNATURE_INVALID);
    }

    @Test
    @DisplayName("Heure de signature différente entre prepare et assemble : refusé")
    void differentSigningTime() throws Exception {
        Signer signer = pki.qualifiedSigner(KeyType.EC_P384);
        byte[] value = cardSign(signer, assembler().prepare(input(base, signer, List.of(), SIGNING_TIME)), true);

        byte[] result = assembler().assemble(input(base, signer, List.of(), SIGNING_TIME.plusSeconds(5)), value);

        assertThat(verify(base, result, Set.of()).rejection()).isEqualTo(SignedUploadRejection.SIGNATURE_INVALID);
    }

    @Test
    @DisplayName("Certificat différent entre prepare et assemble : refusé")
    void differentCertificate() throws Exception {
        Signer signer = pki.qualifiedSigner(KeyType.RSA_2048);
        Signer other = pki.qualifiedSigner(KeyType.RSA_2048);
        byte[] value = cardSign(signer, assembler().prepare(input(base, signer, List.of(), SIGNING_TIME)), false);

        byte[] result = assembler().assemble(input(base, other, List.of(), SIGNING_TIME), value);

        assertThat(verify(base, result, Set.of()).isAccepted()).isFalse();
        assertThat(verify(base, result, Set.of()).rejection()).isEqualTo(SignedUploadRejection.SIGNATURE_INVALID);
    }

    @Test
    @DisplayName("Certificat illisible, trop gros, valeur ECDSA illisible : IllegalArgumentException")
    void unreadableInputs() throws Exception {
        assertThatThrownBy(() -> assembler().prepare(new CardSignatureInput(base, new byte[] {1, 2, 3}, List.of(), SIGNING_TIME, 1)))
                .isInstanceOf(IllegalArgumentException.class).hasMessage("certificate is not readable");
        assertThatThrownBy(() -> assembler().prepare(new CardSignatureInput(base, new byte[64 * 1024], List.of(), SIGNING_TIME, 1)))
                .isInstanceOf(IllegalArgumentException.class);
        Signer signer = pki.qualifiedSigner(KeyType.EC_P384);
        CardSignatureInput input = input(base, signer, List.of(), SIGNING_TIME);
        assertThatThrownBy(() -> assembler().assemble(input, new byte[17])).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> assembler().assemble(input, new byte[4096])).isInstanceOf(IllegalArgumentException.class);
        List<byte[]> tooLong = java.util.Collections.nCopies(6, encoded(pki.intermediate()));
        assertThatThrownBy(() -> assembler().prepare(input(base, signer, tooLong, SIGNING_TIME)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    // --- Apparence visible au repère {{sN|signature|85|37}} (D-SIGNATURE-ANCHOR) ---

    /** Repères : s1 page 2 en (100, 300), s2 page 1 en (300, 200), en blanc. */
    private static byte[] anchored() {
        return EidTestPdfs.basePdfWithAnchors(2, 0, new Anchor(1, 1, 100, 300), new Anchor(0, 2, 300, 200));
    }

    /** Rectangles non vides des widgets de signature : {page, llx, lly, urx, ury}. */
    private static List<float[]> visibleWidgets(byte[] pdf) throws Exception {
        List<float[]> found = new ArrayList<>();
        try (PDDocument document = Loader.loadPDF(pdf)) {
            for (int p = 0; p < document.getNumberOfPages(); p++) {
                for (PDAnnotation annotation : document.getPage(p).getAnnotations()) {
                    if (annotation instanceof PDAnnotationWidget widget && widget.getRectangle() != null) {
                        PDRectangle r = widget.getRectangle();
                        if (r.getWidth() * r.getHeight() > 0) {
                            found.add(new float[] {p, r.getLowerLeftX(), r.getLowerLeftY(), r.getUpperRightX(), r.getUpperRightY()});
                        }
                    }
                }
            }
        }
        return found;
    }

    /** Texte réel des apparences de signature (flux d'apparence des widgets), lu par le décodage ToUnicode des polices. */
    private static String appearanceText(byte[] pdf) throws Exception {
        StringBuilder text = new StringBuilder();
        try (PDDocument document = Loader.loadPDF(pdf)) {
            for (PDPage page : document.getPages()) {
                for (PDAnnotation annotation : page.getAnnotations()) {
                    if (annotation instanceof PDAnnotationWidget widget && widget.getAppearance() != null
                            && widget.getAppearance().getNormalAppearance() != null
                            && widget.getAppearance().getNormalAppearance().isStream()) {
                        text.append(new AppearanceStripper().text(document, widget));
                    }
                }
            }
        }
        // DSS ajuste la taille et coupe les lignes pour remplir le cadre : on compare à espacement normalisé.
        return text.toString().replaceAll("\\s+", " ").strip();
    }

    /** Page de travail qui reprend le flux et les ressources de l'apparence, pour que PDFBox en lise le texte. */
    private static final class AppearanceStripper {
        String text(PDDocument source, PDAnnotationWidget widget) throws java.io.IOException {
            var form = widget.getNormalAppearanceStream();
            try (PDDocument scratch = new PDDocument()) {
                PDPage page = new PDPage(PDRectangle.A4);
                scratch.addPage(page);
                page.setResources(form.getResources());
                page.setContents(form.getContentStream());
                return new PDFTextStripper().getText(scratch);
            }
        }
    }

    private static List<String> fontNames(byte[] pdf) throws Exception {
        List<String> names = new ArrayList<>();
        try (PDDocument document = Loader.loadPDF(pdf)) {
            for (PDPage page : document.getPages()) {
                for (PDAnnotation annotation : page.getAnnotations()) {
                    if (annotation instanceof PDAnnotationWidget widget && widget.getNormalAppearanceStream() != null
                            && widget.getNormalAppearanceStream().getResources() != null) {
                        var resources = widget.getNormalAppearanceStream().getResources();
                        for (var name : resources.getFontNames()) {
                            var font = resources.getFont(name);
                            names.add(font.getName() + (font.isEmbedded() ? " embedded" : " not-embedded"));
                        }
                    }
                }
            }
        }
        return names;
    }

    @Test
    @DisplayName("Repère présent : le widget fait 85 x 37 pt au coin haut gauche du repère, et le vérificateur accepte")
    void visibleSignatureAtTheAnchor() throws Exception {
        Signer signer = pki.qualifiedSigner(KeyType.RSA_2048);
        byte[] document = anchored();
        SignatureAnchors.AnchorBox anchor = SignatureAnchors.locate(document, 1).orElseThrow();

        byte[] result = signWithCard(document, signer, List.of(encoded(pki.intermediate())), false, 1);

        List<float[]> widgets = visibleWidgets(result);
        assertThat(widgets).hasSize(1);
        float[] rect = widgets.get(0);
        assertThat(rect[0]).isEqualTo(1f);
        assertThat(rect[3] - rect[1]).isCloseTo(85f, org.assertj.core.api.Assertions.within(0.5f));
        assertThat(rect[4] - rect[2]).isCloseTo(37f, org.assertj.core.api.Assertions.within(0.5f));
        assertThat(rect[1]).isCloseTo(anchor.originX(), org.assertj.core.api.Assertions.within(0.5f));
        assertThat(rect[4]).isCloseTo(842f - anchor.originY(), org.assertj.core.api.Assertions.within(0.5f));
        assertLiteralRect(rect, 100f, 300f);
        SignedUploadVerdict verdict = verify(document, result, Set.of());
        assertThat(verdict.isAccepted()).as("rejection %s", verdict.rejection()).isTrue();
        assertThat(verdict.evidence().signatureFormat()).isEqualTo("PAdES-BASELINE-B");
        assertThat(verdict.evidence().signingTime()).isEqualTo(SIGNING_TIME);
    }

    /** Rectangle attendu en coordonnées PDF littérales : gauche = abscisse du repère, haut entre la ligne de base et +12 pt. */
    private static void assertLiteralRect(float[] rect, float anchorX, float baselineY) {
        var tolerance = org.assertj.core.api.Assertions.within(0.5f);
        assertThat(rect[1]).isCloseTo(anchorX, tolerance);
        assertThat(rect[3]).isCloseTo(anchorX + 85f, tolerance);
        assertThat(rect[4]).isBetween(baselineY, baselineY + 12f);
        assertThat(rect[4] - rect[2]).isCloseTo(37f, tolerance);
    }

    @Test
    @DisplayName("CropBox décalée : le widget est posé par rapport à la zone visible, et le vérificateur accepte")
    void visibleSignatureWithCropBoxOffset() throws Exception {
        Signer signer = pki.qualifiedSigner(KeyType.RSA_2048);
        byte[] document = EidTestPdfs.basePdfWithAnchors(1, 20, new Anchor(0, 1, 100, 300));
        SignatureAnchors.AnchorBox anchor = SignatureAnchors.locate(document, 1).orElseThrow();

        byte[] result = signWithCard(document, signer, List.of(), false, 1);

        float[] rect = visibleWidgets(result).get(0);
        // Même position sur la page que sans CropBox : abscisse du repère, haut du texte juste au-dessus de sa ligne de base.
        assertThat(rect[1]).isCloseTo(100f, org.assertj.core.api.Assertions.within(0.5f));
        assertThat(rect[4]).isBetween(300f, 312f);
        assertThat(rect[4]).isCloseTo(842f - anchor.originY(), org.assertj.core.api.Assertions.within(0.5f));
        SignedUploadVerdict verdict = verify(document, result, Set.of());
        assertThat(verdict.isAccepted()).as("rejection %s", verdict.rejection()).isTrue();
    }

    @Test
    @DisplayName("Préparation déterministe avec apparence visible : deux instances, même condensé ; aller-retour accepté")
    void visiblePrepareIsDeterministic() throws Exception {
        Signer signer = pki.qualifiedSigner(KeyType.EC_P384);
        byte[] document = anchored();
        CardSignatureInput input = input(document, signer, List.of(), SIGNING_TIME, 2);

        assertThat(assembler().prepare(input).digest()).isEqualTo(assembler().prepare(input).digest());
        assertThat(assembler().prepare(input(document, signer, List.of(), SIGNING_TIME, 1)).digest())
                .isNotEqualTo(assembler().prepare(input).digest());
        byte[] result = signWithCard(document, signer, List.of(), true, 2);
        assertThat(verify(document, result, Set.of()).isAccepted()).isTrue();
    }

    @Test
    @DisplayName("Deux signataires : chacun à son repère, les deux widgets visibles, la seconde signature est acceptée")
    void twoSignersEachAtTheirAnchor() throws Exception {
        Signer first = pki.qualifiedSigner(KeyType.RSA_2048);
        Signer second = pki.signer().keyType(KeyType.EC_P384).name("Bruno", "Lambert").issue();
        byte[] document = anchored();
        byte[] once = signWithCard(document, first, List.of(), false, 1);

        byte[] twice = signWithCard(once, second, List.of(), true, 2);

        List<float[]> widgets = visibleWidgets(twice);
        assertThat(widgets).hasSize(2);
        assertThat(widgets).extracting(w -> (int) w[0]).containsExactlyInAnyOrder(1, 0);
        for (float[] widget : widgets) {
            if (widget[0] == 1f) {
                assertLiteralRect(widget, 100f, 300f);
            } else {
                assertLiteralRect(widget, 300f, 200f);
            }
        }
        SignedUploadVerdict verdict = verify(once, twice, Set.of(SigningCertificateFacts.fingerprint(encoded(first.certificate()))));
        assertThat(verdict.isAccepted()).as("rejection %s", verdict.rejection()).isTrue();
    }

    @Test
    @DisplayName("Repère du rang absent : signature invisible comme avant (rectangle d'aire nulle), acceptée")
    void missingAnchorKeepsTheInvisibleSignature() throws Exception {
        Signer signer = pki.qualifiedSigner(KeyType.RSA_2048);
        byte[] document = EidTestPdfs.basePdfWithAnchors(1, 0, new Anchor(0, 1, 100, 300));

        byte[] result = signWithCard(document, signer, List.of(), false, 2);

        assertThat(visibleWidgets(result)).isEmpty();
        SignedUploadVerdict verdict = verify(document, result, Set.of());
        assertThat(verdict.isAccepted()).as("rejection %s", verdict.rejection()).isTrue();
    }

    @Test
    @DisplayName("Page du repère tournée : signature invisible, acceptée")
    void rotatedAnchorPageKeepsTheInvisibleSignature() throws Exception {
        Signer signer = pki.qualifiedSigner(KeyType.RSA_2048);
        byte[] document = EidTestPdfs.withRotatedPage(EidTestPdfs.basePdfWithAnchors(1, 0, new Anchor(0, 1, 100, 300)), 0);

        byte[] result = signWithCard(document, signer, List.of(), false, 1);

        assertThat(visibleWidgets(result)).isEmpty();
        assertThat(verify(document, result, Set.of()).isAccepted()).isTrue();
    }

    @Test
    @DisplayName("L'apparence porte le nom du signataire et jamais le numéro de registre national")
    void appearanceHasTheNameAndNeverTheNationalNumber() throws Exception {
        Signer signer = pki.qualifiedSigner(KeyType.RSA_2048);

        byte[] result = signWithCard(anchored(), signer, List.of(), false, 1);

        String text = appearanceText(result);
        assertThat(text).contains("Signé avec la carte eID").contains("Alice Anne Martin")
                .contains(java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm")
                        .format(SIGNING_TIME.atZone(java.time.ZoneId.of("Europe/Brussels"))));
        assertThat(text).doesNotContain(EidTestPki.FAKE_NATIONAL_NUMBER).doesNotContain("(Signature)");
    }

    @Test
    @DisplayName("Nom accentué : l'apparence le porte et LiberationSans y est embarquée")
    void accentedNameEmbedsLiberationSans() throws Exception {
        Signer signer = pki.signer().keyType(KeyType.RSA_2048).name("Hélène", "Dupré").issue();

        byte[] document = anchored();
        byte[] result = signWithCard(document, signer, List.of(), false, 1);

        assertThat(appearanceText(result)).contains("Hélène Dupré");
        assertThat(fontNames(result)).anyMatch(name -> name.contains("LiberationSans") && name.endsWith(" embedded"));
        assertThat(verify(document, result, Set.of()).isAccepted()).isTrue();
    }

    private static byte[] signedInvisibleAndAccepted(byte[] document, int rank) throws Exception {
        Signer signer = pki.qualifiedSigner(KeyType.RSA_2048);
        byte[] result = signWithCard(document, signer, List.of(), false, rank);
        assertThat(visibleWidgets(result)).isEmpty();
        SignedUploadVerdict verdict = verify(document, result, Set.of());
        assertThat(verdict.isAccepted()).as("rejection %s", verdict.rejection()).isTrue();
        return result;
    }

    @Test
    @DisplayName("Cadre qui déborde de la page (bas, droite) : signature invisible, acceptée, sans exception")
    void frameOutsideThePageFallsBackToInvisible() throws Exception {
        signedInvisibleAndAccepted(EidTestPdfs.basePdfWithAnchors(1, 0, new Anchor(0, 1, 100, 20)), 1);
        signedInvisibleAndAccepted(EidTestPdfs.basePdfWithAnchors(1, 0, new Anchor(0, 1, 540, 300)), 1);
    }

    @Test
    @DisplayName("Annotation existante sous le cadre (lien) : signature invisible, acceptée")
    void frameOverAnExistingAnnotationFallsBackToInvisible() throws Exception {
        byte[] document = EidTestPdfs.withLink(EidTestPdfs.basePdfWithAnchors(1, 0, new Anchor(0, 1, 100, 300)), 0,
                new PDRectangle(150, 290, 100, 40));

        signedInvisibleAndAccepted(document, 1);
    }

    @Test
    @DisplayName("Deux repères à moins de 85 pt : le second cadre chevauche le widget du premier, il reste invisible")
    void secondFrameOverlappingTheFirstWidgetIsInvisible() throws Exception {
        Signer first = pki.qualifiedSigner(KeyType.RSA_2048);
        Signer second = pki.qualifiedSigner(KeyType.EC_P384);
        byte[] document = EidTestPdfs.basePdfWithAnchors(1, 0, new Anchor(0, 1, 100, 300), new Anchor(0, 2, 150, 300));
        byte[] once = signWithCard(document, first, List.of(), false, 1);

        byte[] twice = signWithCard(once, second, List.of(), true, 2);

        assertThat(visibleWidgets(once)).hasSize(1);
        assertThat(visibleWidgets(twice)).hasSize(1);
        SignedUploadVerdict verdict = verify(once, twice, Set.of(SigningCertificateFacts.fingerprint(encoded(first.certificate()))));
        assertThat(verdict.isAccepted()).as("rejection %s", verdict.rejection()).isTrue();
    }

    @Test
    @DisplayName("Repère écrit en noir (zone non vierge) : signature invisible, acceptée")
    void nonBlankAnchorZoneFallsBackToInvisible() throws Exception {
        signedInvisibleAndAccepted(EidTestPdfs.basePdfWithAnchors(1, 0, new Anchor(0, 1, 100, 300).black()), 1);
    }

    @Test
    @DisplayName("MediaBox et CropBox décalées sur les deux axes : rectangle aux coordonnées littérales, accepté")
    void offsetMediaAndCropBoxes() throws Exception {
        Signer signer = pki.qualifiedSigner(KeyType.RSA_2048);
        byte[] document = EidTestPdfs.withBoxes(EidTestPdfs.basePdfWithAnchors(1, 0, new Anchor(0, 1, 100, 400)),
                new PDRectangle(50, 50, 595, 842), new PDRectangle(70, 80, 500, 700));

        byte[] result = signWithCard(document, signer, List.of(), false, 1);

        List<float[]> widgets = visibleWidgets(result);
        assertThat(widgets).hasSize(1);
        assertLiteralRect(widgets.get(0), 100f, 400f);
        SignedUploadVerdict verdict = verify(document, result, Set.of());
        assertThat(verdict.isAccepted()).as("rejection %s", verdict.rejection()).isTrue();
    }

    @Test
    @DisplayName("Nom avec émoji, idéogramme ou très long : remplacé par « ? » / borné par points de code, jamais d'exception")
    void unsupportedCharactersAndLongNamesNeverBreakTheAppearance() throws Exception {
        Signer emoji = pki.signer().keyType(KeyType.RSA_2048).name("Hélène \uD83D\uDE00 \u674E", "Dupré").issue();
        byte[] document = anchored();

        byte[] result = signWithCard(document, emoji, List.of(), false, 1);

        assertThat(appearanceText(result)).contains("Hélène ? ? Dupré");
        assertThat(verify(document, result, Set.of()).isAccepted()).isTrue();
        Signer longName = pki.signer().keyType(KeyType.RSA_2048).name("AAAAAAAAA ".repeat(5) + "B".repeat(9) + "\uD83D\uDE00\uD83D\uDE00", "Z").issue();
        byte[] second = signWithCard(document, longName, List.of(), false, 1);
        assertThat(visibleWidgets(second)).hasSize(1);
    }

    private static byte[] encoded(X509Certificate certificate) {
        try {
            return certificate.getEncoded();
        } catch (Exception ex) {
            throw new IllegalStateException(ex);
        }
    }
}
