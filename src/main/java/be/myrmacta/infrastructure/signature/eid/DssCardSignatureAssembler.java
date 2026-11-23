package be.myrmacta.infrastructure.signature.eid;

import be.myrmacta.domain.signature.port.out.SignaturePort.CardSignatureDigest;
import be.myrmacta.domain.signature.port.out.SignaturePort.CardSignatureInput;
import be.myrmacta.infrastructure.signature.SignatureAnchors;
import be.myrmacta.infrastructure.signature.SignatureAnchors.AnchorBox;
import eu.europa.esig.dss.alert.SilentOnStatusAlert;
import eu.europa.esig.dss.enumerations.SignatureAlgorithm;
import eu.europa.esig.dss.enumerations.SignatureLevel;
import eu.europa.esig.dss.enumerations.TextWrapping;
import eu.europa.esig.dss.model.DSSDocument;
import eu.europa.esig.dss.model.InMemoryDocument;
import eu.europa.esig.dss.model.SignatureValue;
import eu.europa.esig.dss.model.ToBeSigned;
import eu.europa.esig.dss.model.x509.CertificateToken;
import eu.europa.esig.dss.pades.DSSFileFont;
import eu.europa.esig.dss.pades.PAdESSignatureParameters;
import eu.europa.esig.dss.pades.SignatureFieldParameters;
import eu.europa.esig.dss.pades.SignatureImageParameters;
import eu.europa.esig.dss.pades.SignatureImageTextParameters;
import eu.europa.esig.dss.pades.signature.PAdESService;
import eu.europa.esig.dss.spi.DSSASN1Utils;
import eu.europa.esig.dss.pdf.pdfbox.PdfBoxNativeObjectFactory;
import eu.europa.esig.dss.spi.validation.CommonCertificateVerifier;
import java.awt.Color;
import java.io.IOException;
import java.io.InputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.PublicKey;
import java.security.cert.X509Certificate;
import java.security.interfaces.ECPublicKey;
import java.security.interfaces.RSAPublicKey;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.TimeZone;
import org.apache.fontbox.ttf.TTFParser;
import org.apache.fontbox.ttf.TrueTypeFont;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.io.MemoryUsageSetting;
import org.apache.pdfbox.io.RandomAccessReadBuffer;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.interactive.annotation.PDAnnotation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Signature PAdES en deux temps, la clé restant sur la carte (TODO §138, D-EID-BRIDGE), par DSS 6.5.1 : signature
 * PAdES-BASELINE-B, sous-filtre {@code ETSI.CAdES.detached}, par mise à jour incrémentale du document remis.
 * L'apparence est posée au repère {@code {{sN|signature|85|37}}} du signataire quand le document le porte
 * (TODO §156, D-EID-VISIBLE ; cadre de
 * 85 x 37 pt dont le coin haut gauche est celui du texte du repère, comme une ancre Yousign) ; sans repère, la
 * signature reste invisible et rien n'est ajouté au contenu des pages. Le résultat n'est pas tenu pour valide :
 * il passe ensuite par {@link DssSignedPdfVerifier}, comme un téléversement.
 *
 * <p><b>Sans état.</b> {@link #prepare} et {@link #assemble} reconstruisent les mêmes paramètres à partir des mêmes
 * entrées et retombent sur les mêmes octets à signer. Ce qui doit être identique entre les deux appels : le document
 * remis, le certificat, la chaîne et l'heure de signature (tronquée à la seconde, fuseau UTC). Le reste est
 * déterministe dans DSS 6.5.1 : l'identifiant du document PDF ({@code /ID}) est dérivé de l'heure de signature, du
 * certificat et de la taille du document ({@code PdfBoxDocumentReader.generateDocumentId}) ; le nom du champ de
 * signature est attribué d'après les champs existants ; les attributs signés CMS ne dépendent que de l'heure, du
 * certificat et du condensé du document ; le cache {@code PdfSignatureCache} vit dans les paramètres, donc il est
 * neuf à chaque appel. Aucun aléa n'intervient hors des documents chiffrés, refusés en amont. L'apparence est une
 * fonction pure des mêmes entrées plus le rang du signataire : repère lu dans le document remis, nom lu dans le
 * certificat, date de l'heure de signature, police {@code LiberationSans-Regular} lue dans les ressources et incluse
 * en sous-ensemble (étiquette de sous-ensemble dérivée des glyphes par PDFBox, sans aléa).
 *
 * <p>Algorithme déduit de la clé publique : RSA (2048 bits au moins) → PKCS#1 v1.5 avec SHA-256 ; EC P-384 → ECDSA
 * avec SHA-384 ; EC P-256 → ECDSA avec SHA-256. Le condensé rendu est celui des octets à signer (les attributs signés
 * CMS) : c'est ce que le pont transmet à la carte.
 */
public final class DssCardSignatureAssembler implements CardSignatureAssembler {

    static final int MAX_CERTIFICATE_BYTES = 16 * 1024;
    static final int MAX_CHAIN_LENGTH = 5;
    static final int MAX_SIGNATURE_VALUE_BYTES = 1024;
    private static final int MAX_CHAIN_COMPLETION = 5;
    private static final Logger log = LoggerFactory.getLogger(DssCardSignatureAssembler.class);
    private static final String FONT_RESOURCE = "/fonts/LiberationSans-Regular.ttf";
    private static final float FONT_SIZE = 6f;
    private static final float PADDING = 2f;
    private static final int MAX_NAME_LENGTH = 60;
    private static final String SIGNATURE_SUFFIX = " (Signature)";
    private static final ZoneId DISPLAY_ZONE = ZoneId.of("Europe/Brussels");
    private static final DateTimeFormatter DISPLAY_DATE = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm", Locale.ROOT);
    private static final long MAX_PARSE_MEMORY_BYTES = 64L * 1024 * 1024;
    private static final double EPSILON = 0.01;

    private final TrustedChainGate authorities;

    /**
     * @param trustedCertificates autorités fournies par l'exploitant (les mêmes que celles du vérificateur), qui
     *     complètent la chaîne quand le pont ne rend que le certificat du signataire
     */
    public DssCardSignatureAssembler(List<X509Certificate> trustedCertificates) {
        this.authorities = new TrustedChainGate(trustedCertificates);
    }

    @Override
    public CardSignatureDigest prepare(CardSignatureInput input) {
        Prepared prepared = prepared(input);
        if (prepared.fallback() != null) {
            // Une seule fois par signature (ici, pas à l'assemblage) ; ni nom ni donnée du document.
            log.warn("signature anchor of rank {} not usable ({}), signature stays invisible", input.signerRank(),
                    prepared.fallback());
        }
        ToBeSigned toBeSigned = service().getDataToSign(prepared.document(), prepared.parameters());
        String hashFunction = prepared.algorithm().getDigestAlgorithm().getJavaName();
        try {
            return new CardSignatureDigest(MessageDigest.getInstance(hashFunction).digest(toBeSigned.getBytes()), hashFunction);
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException(impossible);
        }
    }

    @Override
    public byte[] assemble(CardSignatureInput input, byte[] signatureValue) {
        Prepared prepared = prepared(input);
        byte[] value = cmsSignatureValue(prepared.algorithm(), signatureValue);
        DSSDocument signed = service().signDocument(prepared.document(), prepared.parameters(),
                new SignatureValue(prepared.algorithm(), value));
        try (InputStream in = signed.openStream()) {
            return in.readAllBytes();
        } catch (IOException ex) {
            throw new IllegalStateException("signed document cannot be read", ex);
        }
    }

    private record Prepared(DSSDocument document, PAdESSignatureParameters parameters, SignatureAlgorithm algorithm,
            String fallback) {}

    private Prepared prepared(CardSignatureInput input) {
        Objects.requireNonNull(input, "input");
        if (input.baseDocument() == null || input.baseDocument().length == 0) {
            throw new IllegalArgumentException("base document is missing");
        }
        if (input.signingTime() == null) {
            throw new IllegalArgumentException("signing time is missing");
        }
        X509Certificate certificate = certificate(input.certificate());
        SignatureAlgorithm algorithm = algorithmFor(certificate.getPublicKey());
        if (input.certificateChain().size() > MAX_CHAIN_LENGTH) {
            throw new IllegalArgumentException("certificate chain is too long");
        }
        List<CertificateToken> chain = new ArrayList<>();
        for (byte[] encoded : input.certificateChain()) {
            chain.add(new CertificateToken(certificate(encoded)));
        }
        CertificateToken signing = new CertificateToken(certificate);
        completeChain(signing, chain);

        PAdESSignatureParameters parameters = new PAdESSignatureParameters();
        parameters.setSignatureLevel(SignatureLevel.PAdES_BASELINE_B);
        parameters.setSigningCertificate(signing);
        parameters.setCertificateChain(chain);
        parameters.setDigestAlgorithm(algorithm.getDigestAlgorithm());
        parameters.setEncryptionAlgorithm(algorithm.getEncryptionAlgorithm());
        parameters.setSubFilter("ETSI.CAdES.detached");
        parameters.setSigningTimeZone(TimeZone.getTimeZone("UTC"));
        Instant signingTime = input.signingTime().truncatedTo(ChronoUnit.SECONDS);
        parameters.bLevel().setSigningDate(Date.from(signingTime));
        SignatureAnchors.Located located = SignatureAnchors.find(input.baseDocument(), input.signerRank());
        String fallback = located.fallbackReason();
        if (located.box() != null) {
            fallback = unusableFrame(input.baseDocument(), located.box());
            if (fallback == null) {
                parameters.setImageParameters(appearance(located.box(), input.certificate(), signingTime));
            }
        }
        return new Prepared(new InMemoryDocument(input.baseDocument()), parameters, algorithm, fallback);
    }

    /**
     * Catégorie de repli si le cadre ne peut pas recevoir l'apparence, sinon {@code null}. DSS lève une exception
     * (hors de la page, sur une annotation existante) et le vérificateur refuse après le PIN une zone non vierge :
     * on les écarte avant, de façon déterministe, pour garder la signature invisible plutôt qu'échouer.
     * Catégories : {@code outside page}, {@code overlap}, {@code not blank}, {@code unreadable}.
     */
    private static String unusableFrame(byte[] pdf, AnchorBox anchor) {
        try (PDDocument document = Loader.loadPDF(new RandomAccessReadBuffer(pdf), "", null, null,
                MemoryUsageSetting.setupMainMemoryOnly(MAX_PARSE_MEMORY_BYTES).streamCache)) {
            PDPage page = document.getPage(anchor.pageIndex());
            PDRectangle media = page.getMediaBox();
            double left = media.getLowerLeftX() + anchor.originX();
            double right = left + anchor.width();
            double top = media.getUpperRightY() - anchor.originY();
            double bottom = top - anchor.height();
            if (left < media.getLowerLeftX() - EPSILON || right > media.getUpperRightX() + EPSILON
                    || bottom < media.getLowerLeftY() - EPSILON || top > media.getUpperRightY() + EPSILON) {
                return "outside page";
            }
            for (PDAnnotation annotation : page.getAnnotations()) {
                PDRectangle r = annotation.getRectangle();
                if (r == null) {
                    continue;
                }
                double aLeft = Math.min(r.getLowerLeftX(), r.getUpperRightX());
                double aRight = Math.max(r.getLowerLeftX(), r.getUpperRightX());
                double aBottom = Math.min(r.getLowerLeftY(), r.getUpperRightY());
                double aTop = Math.max(r.getLowerLeftY(), r.getUpperRightY());
                if (aRight > aLeft && aTop > aBottom
                        && aLeft <= right + EPSILON && aRight >= left - EPSILON
                        && aBottom <= top + EPSILON && aTop >= bottom - EPSILON) {
                    return "overlap";
                }
            }
            return BlankZone.isBlank(document, anchor.pageIndex(), new double[] {left, bottom, right, top})
                    ? null : "not blank";
        } catch (IOException | RuntimeException unreadable) {
            return "unreadable";
        }
    }

    /**
     * Apparence texte au cadre du repère : « Signé avec la carte eID », le nom du signataire, la date en heure de
     * Bruxelles. Jamais le numéro de registre national (D-EID-NRN) : le nom vient de {@link SigningCertificateFacts}.
     */
    private static SignatureImageParameters appearance(AnchorBox anchor, byte[] certificate, Instant signingTime) {
        SignatureFieldParameters field = new SignatureFieldParameters();
        field.setPage(anchor.pageIndex() + 1);
        field.setOriginX(anchor.originX());
        field.setOriginY(anchor.originY());
        field.setWidth(anchor.width());
        field.setHeight(anchor.height());
        SignatureImageTextParameters text = new SignatureImageTextParameters();
        text.setText("Signé avec la carte eID\n" + displayName(certificate) + "\n"
                + DISPLAY_DATE.format(signingTime.atZone(DISPLAY_ZONE)));
        text.setFont(font());
        text.setTextWrapping(TextWrapping.FILL_BOX_AND_LINEBREAK);
        text.setPadding(PADDING);
        text.setTextColor(Color.BLACK);
        SignatureImageParameters image = new SignatureImageParameters();
        image.setFieldParameters(field);
        image.setTextParameters(text);
        return image;
    }

    /**
     * Prénoms et nom, sinon le CN sans son suffixe « (Signature) » ; borné à {@value #MAX_NAME_LENGTH} points de code,
     * chaque caractère sans glyphe dans LiberationSans (émoji, idéogramme, contrôle) devenant « ? » : PDFBox ne doit
     * jamais échouer sur un nom.
     */
    private static String displayName(byte[] certificate) {
        String name = SigningCertificateFacts.of(certificate).signerName().strip();
        if (name.endsWith(SIGNATURE_SUFFIX)) {
            name = name.substring(0, name.length() - SIGNATURE_SUFFIX.length()).strip();
        }
        BitSet glyphs = LiberationSans.GLYPHS;
        StringBuilder safe = new StringBuilder();
        name.codePoints().limit(MAX_NAME_LENGTH)
                .forEach(cp -> safe.appendCodePoint(cp >= 0 && cp < glyphs.size() && glyphs.get(cp) ? cp : '?'));
        return safe.toString();
    }

    /** Police des ressources : octets et jeu des points de code couverts, lus une fois (immuables ensuite). */
    private static final class LiberationSans {
        static final byte[] BYTES;
        static final BitSet GLYPHS = new BitSet(0x10000);

        static {
            try (InputStream in = DssCardSignatureAssembler.class.getResourceAsStream(FONT_RESOURCE)) {
                if (in == null) {
                    throw new IllegalStateException("font resource is missing");
                }
                BYTES = in.readAllBytes();
                try (TrueTypeFont ttf = new TTFParser().parse(new RandomAccessReadBuffer(BYTES))) {
                    var cmap = ttf.getUnicodeCmapLookup();
                    for (int cp = 0x20; cp < 0x10000; cp++) {
                        if (cp != 0x7f && !(cp >= 0x80 && cp < 0xa0) && cmap.getGlyphId(cp) != 0) {
                            GLYPHS.set(cp);
                        }
                    }
                }
            } catch (IOException ex) {
                throw new IllegalStateException("font resource cannot be read", ex);
            }
        }
    }

    private static DSSFileFont font() {
        DSSFileFont font = new DSSFileFont(new InMemoryDocument(LiberationSans.BYTES), FONT_SIZE);
        font.setEmbedFontSubset(true);
        return font;
    }

    /**
     * Ajoute les autorités de l'exploitant qui chaînent au-dessus du dernier certificat connu (Web eID ne rend que le
     * certificat du signataire). Sans autorité qui chaîne, le certificat seul est embarqué : le vérificateur décide.
     */
    private void completeChain(CertificateToken signing, List<CertificateToken> chain) {
        CertificateToken current = chain.isEmpty() ? signing : chain.get(chain.size() - 1);
        for (int depth = 0; depth < MAX_CHAIN_COMPLETION && !current.isSelfSigned(); depth++) {
            CertificateToken issuer = null;
            for (CertificateToken authority : authorities.provided()) {
                if (!authority.equals(current) && !chain.contains(authority) && current.isSignedBy(authority)) {
                    issuer = authority;
                    break;
                }
            }
            if (issuer == null) {
                return;
            }
            chain.add(new CertificateToken(issuer.getCertificate()));
            current = issuer;
        }
    }

    static X509Certificate certificate(byte[] encoded) {
        if (encoded == null || encoded.length == 0 || encoded.length > MAX_CERTIFICATE_BYTES) {
            throw new IllegalArgumentException("certificate is missing or too large");
        }
        return SigningCertificateFacts.parse(encoded);
    }

    static SignatureAlgorithm algorithmFor(PublicKey key) {
        if (key instanceof RSAPublicKey rsa) {
            if (rsa.getModulus().bitLength() < 2048) {
                throw new IllegalArgumentException("RSA key is too short");
            }
            return SignatureAlgorithm.RSA_SHA256;
        }
        if (key instanceof ECPublicKey ec) {
            int fieldSize = ec.getParams().getCurve().getField().getFieldSize();
            if (fieldSize == 384) {
                return SignatureAlgorithm.ECDSA_SHA384;
            }
            if (fieldSize == 256) {
                return SignatureAlgorithm.ECDSA_SHA256;
            }
        }
        throw new IllegalArgumentException("certificate key type is not supported");
    }

    /**
     * Valeur pour le CMS : RSA telle quelle ; ECDSA en DER, converti depuis la forme brute r||s (IEEE P1363) que
     * rendent la plupart des cartes et des ponts, par {@link DSSASN1Utils#toStandardDSASignatureValue}.
     */
    static byte[] cmsSignatureValue(SignatureAlgorithm algorithm, byte[] value) {
        if (value == null || value.length == 0 || value.length > MAX_SIGNATURE_VALUE_BYTES) {
            throw new IllegalArgumentException("signature value is missing or too large");
        }
        if (algorithm.getEncryptionAlgorithm() != eu.europa.esig.dss.enumerations.EncryptionAlgorithm.ECDSA) {
            return value.clone();
        }
        if (DSSASN1Utils.isAsn1EncodedSignatureValue(value)) {
            return value.clone();
        }
        int expected = algorithm == SignatureAlgorithm.ECDSA_SHA384 ? 96 : 64;
        if (value.length != expected) {
            throw new IllegalArgumentException("ECDSA signature value is not readable");
        }
        try {
            return DSSASN1Utils.toStandardDSASignatureValue(value);
        } catch (RuntimeException unreadable) {
            throw new IllegalArgumentException("ECDSA signature value is not readable");
        }
    }

    /** Service sans source en ligne : l'assemblage ne joint jamais le réseau, le vérificateur juge le certificat. */
    private static PAdESService service() {
        CommonCertificateVerifier verifier = new CommonCertificateVerifier();
        verifier.setAIASource(null);
        verifier.setAlertOnExpiredCertificate(new SilentOnStatusAlert());
        verifier.setAlertOnNotYetValidCertificate(new SilentOnStatusAlert());
        verifier.setAlertOnMissingRevocationData(new SilentOnStatusAlert());
        verifier.setAlertOnRevokedCertificate(new SilentOnStatusAlert());
        PAdESService service = new PAdESService(verifier);
        // Dessinateur natif : le texte de l'apparence est du vrai texte PDF, avec la police incluse.
        service.setPdfObjFactory(new PdfBoxNativeObjectFactory());
        return service;
    }
}
