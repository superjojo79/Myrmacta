package be.myrmacta.infrastructure.signature.eid;

import be.myrmacta.infrastructure.signature.eid.EidTestPki.Issued;
import be.myrmacta.infrastructure.signature.eid.EidTestPki.KeyType;
import be.myrmacta.infrastructure.signature.eid.EidTestPki.Signer;
import eu.europa.esig.dss.enumerations.DigestAlgorithm;
import eu.europa.esig.dss.enumerations.SignatureAlgorithm;
import eu.europa.esig.dss.enumerations.SignatureLevel;
import eu.europa.esig.dss.model.DSSDocument;
import eu.europa.esig.dss.model.InMemoryDocument;
import eu.europa.esig.dss.model.SignatureValue;
import eu.europa.esig.dss.model.TimestampBinary;
import eu.europa.esig.dss.model.ToBeSigned;
import eu.europa.esig.dss.model.x509.CertificateToken;
import eu.europa.esig.dss.pades.PAdESSignatureParameters;
import eu.europa.esig.dss.pades.PAdESTimestampParameters;
import eu.europa.esig.dss.pades.SignatureFieldParameters;
import eu.europa.esig.dss.pades.SignatureImageParameters;
import eu.europa.esig.dss.pades.SignatureImageTextParameters;
import eu.europa.esig.dss.pades.signature.PAdESService;
import eu.europa.esig.dss.alert.SilentOnStatusAlert;
import eu.europa.esig.dss.spi.validation.CommonCertificateVerifier;
import eu.europa.esig.dss.spi.x509.CommonTrustedCertificateSource;
import eu.europa.esig.dss.spi.x509.tsp.TSPSource;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigInteger;
import java.security.MessageDigest;
import java.security.Signature;
import java.security.cert.X509Certificate;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.cos.COSDictionary;
import org.apache.pdfbox.cos.COSName;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.encryption.AccessPermission;
import org.apache.pdfbox.pdmodel.encryption.StandardProtectionPolicy;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.apache.pdfbox.pdmodel.interactive.annotation.PDAnnotationText;
import org.apache.pdfbox.pdmodel.interactive.digitalsignature.PDSignature;
import org.apache.pdfbox.pdmodel.interactive.digitalsignature.SignatureInterface;
import org.bouncycastle.asn1.ASN1ObjectIdentifier;
import org.bouncycastle.cert.jcajce.JcaCertStore;
import org.bouncycastle.cms.CMSProcessableByteArray;
import org.bouncycastle.cms.CMSSignedData;
import org.bouncycastle.cms.CMSSignedDataGenerator;
import org.bouncycastle.cms.jcajce.JcaSignerInfoGeneratorBuilder;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;
import org.bouncycastle.tsp.TSPAlgorithms;
import org.bouncycastle.tsp.TimeStampRequest;
import org.bouncycastle.tsp.TimeStampRequestGenerator;
import org.bouncycastle.tsp.TimeStampResponse;
import org.bouncycastle.tsp.TimeStampResponseGenerator;
import org.bouncycastle.tsp.TimeStampTokenGenerator;

/**
 * PDF de test pour la signature apportée par carte eID (TODO §138) : document de base, signatures PAdES
 * réelles faites avec DSS (comme Acrobat ou le service fédéral) ou à la main avec PDFBox et BouncyCastle (CMS
 * {@code adbe.pkcs7.detached} sans attribut ESS, comme Acrobat), et altérations incrémentales.
 */
public final class EidTestPdfs {

    private static final AtomicLong TSA_SERIALS = new AtomicLong(1);

    private EidTestPdfs() {}

    /** Options d'une signature faite avec DSS. */
    public record SignOptions(String subFilter, SignatureLevel level, boolean visible, Instant signingTime,
            EidTestPki pkiForExtension) {
        public static SignOptions defaults() {
            return new SignOptions("ETSI.CAdES.detached", SignatureLevel.PAdES_BASELINE_B, false, EidTestPki.NOW.minusSeconds(120), null);
        }

        public SignOptions subFilter(String value) {
            return new SignOptions(value, level, visible, signingTime, pkiForExtension);
        }

        public SignOptions withVisibleField() {
            return new SignOptions(subFilter, level, true, signingTime, pkiForExtension);
        }

        public SignOptions signingTime(Instant value) {
            return new SignOptions(subFilter, level, visible, value, pkiForExtension);
        }

        /** PAdES-BASELINE-LT ou -LTA : horodatage par une TSA de test, révocation par la hiérarchie donnée. */
        public SignOptions extended(SignatureLevel value, EidTestPki pki) {
            return new SignOptions(subFilter, value, visible, signingTime, pki);
        }
    }

    /** PDF d'une ou plusieurs pages de texte (PDFBox). */
    public static byte[] basePdf(String text, int pages) {
        try (PDDocument document = new PDDocument(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            for (int i = 1; i <= pages; i++) {
                PDPage page = new PDPage(PDRectangle.A4);
                document.addPage(page);
                try (PDPageContentStream content = new PDPageContentStream(document, page)) {
                    content.beginText();
                    content.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 12);
                    content.newLineAtOffset(72, 760);
                    content.showText(text + " - page " + i);
                    content.endText();
                }
            }
            document.save(out);
            return out.toByteArray();
        } catch (IOException ex) {
            throw new IllegalStateException(ex);
        }
    }

    public static byte[] basePdf() {
        return basePdf("Contrat de test Myrmacta", 1);
    }

    /** Signature PAdES faite avec DSS, mise à jour incrémentale du document donné. */
    public static byte[] sign(byte[] pdf, Signer signer, SignOptions options) {
        return sign(pdf, signer, options, signer.key());
    }

    /** Comme {@link #sign(byte[], Signer, SignOptions)}, la valeur calculée avec {@code key} (autre clé : signature fausse). */
    public static byte[] sign(byte[] pdf, Signer signer, SignOptions options, java.security.PrivateKey key) {
        CommonCertificateVerifier verifier = new CommonCertificateVerifier();
        verifier.setAlertOnExpiredCertificate(new SilentOnStatusAlert());
        verifier.setAlertOnNotYetValidCertificate(new SilentOnStatusAlert());
        verifier.setAlertOnMissingRevocationData(new SilentOnStatusAlert());
        verifier.setAlertOnRevokedCertificate(new SilentOnStatusAlert());
        verifier.setAlertOnInvalidTimestamp(new SilentOnStatusAlert());
        verifier.setAlertOnNoRevocationAfterBestSignatureTime(new SilentOnStatusAlert());
        verifier.setAlertOnUncoveredPOE(new SilentOnStatusAlert());
        verifier.setAugmentationAlertOnSelfSignedCertificateChains(new SilentOnStatusAlert());
        verifier.setAIASource(null);
        if (options.pkiForExtension() != null) {
            EidTestPki pki = options.pkiForExtension();
            CommonTrustedCertificateSource trusted = new CommonTrustedCertificateSource();
            trusted.addCertificate(new CertificateToken(pki.root()));
            verifier.setTrustedCertSources(trusted);
            GatedRevocationSources sources = new GatedRevocationSources(new TrustedChainGate(pki.trustedFile()), pki.transport(), 16);
            verifier.setOcspSource(sources.ocsp());
            verifier.setCrlSource(sources.crl());
        }
        PAdESService service = new PAdESService(verifier);
        if (options.level() != SignatureLevel.PAdES_BASELINE_B) {
            service.setTspSource(testTsp());
        }
        PAdESSignatureParameters parameters = new PAdESSignatureParameters();
        parameters.setSignatureLevel(options.level());
        parameters.setDigestAlgorithm(signer.keyType() == KeyType.RSA_2048 ? DigestAlgorithm.SHA256 : DigestAlgorithm.SHA384);
        parameters.setSigningCertificate(new CertificateToken(signer.certificate()));
        List<CertificateToken> chain = new ArrayList<>();
        for (X509Certificate certificate : signer.chain()) {
            chain.add(new CertificateToken(certificate));
        }
        parameters.setCertificateChain(chain);
        parameters.bLevel().setSigningDate(Date.from(options.signingTime()));
        parameters.setSubFilter(options.subFilter());
        if (options.visible()) {
            SignatureImageParameters image = new SignatureImageParameters();
            SignatureFieldParameters field = new SignatureFieldParameters();
            field.setOriginX(300);
            field.setOriginY(600);
            field.setWidth(200);
            field.setHeight(60);
            image.setFieldParameters(field);
            SignatureImageTextParameters text = new SignatureImageTextParameters();
            text.setText("Signé par carte eID");
            image.setTextParameters(text);
            parameters.setImageParameters(image);
        }
        DSSDocument document = new InMemoryDocument(pdf);
        ToBeSigned toBeSigned = service.getDataToSign(document, parameters);
        Signer withKey = new Signer(signer.certificate(), key, signer.keyType(), signer.chain());
        SignatureValue value = new SignatureValue(algorithmOf(signer.keyType()), sign(withKey, toBeSigned.getBytes()));
        DSSDocument signed = service.signDocument(document, parameters, value);
        return bytes(signed);
    }

    public static byte[] sign(byte[] pdf, Signer signer) {
        return sign(pdf, signer, SignOptions.defaults());
    }

    /** Horodatage de document (PAdES {@code /DocTimeStamp}) ajouté par-dessus, sans signature. */
    public static byte[] documentTimestamp(byte[] pdf) {
        PAdESService service = new PAdESService(new CommonCertificateVerifier());
        service.setTspSource(testTsp());
        return bytes(service.timestamp(new InMemoryDocument(pdf), new PAdESTimestampParameters()));
    }

    static SignatureAlgorithm algorithmOf(KeyType keyType) {
        return keyType == KeyType.RSA_2048 ? SignatureAlgorithm.RSA_SHA256 : SignatureAlgorithm.ECDSA_SHA384;
    }

    /** Signe des octets avec la clé de test (JCA : DER pour ECDSA). */
    static byte[] sign(Signer signer, byte[] data) {
        try {
            Signature signature = Signature.getInstance(signer.keyType().jcaSignatureAlgorithm());
            signature.initSign(signer.key());
            signature.update(data);
            return signature.sign();
        } catch (Exception ex) {
            throw new IllegalStateException(ex);
        }
    }

    /**
     * Signature faite à la main avec PDFBox et un CMS BouncyCastle sans attribut ESS signing-certificate, à la
     * manière d'Acrobat en {@code adbe.pkcs7.detached}. {@code tamper}, s'il n'est pas nul, modifie le document dans
     * la même révision que la signature (avant qu'elle ne soit calculée).
     */
    public static byte[] signWithPdfBox(byte[] pdf, Signer signer, String subFilter, PdfTamper tamper) {
        try (PDDocument document = Loader.loadPDF(pdf); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Set<COSDictionary> changed = new HashSet<>();
            if (tamper != null) {
                tamper.apply(document, changed);
            }
            PDSignature signature = new PDSignature();
            signature.setFilter(PDSignature.FILTER_ADOBE_PPKLITE);
            signature.setSubFilter(COSName.getPDFName(subFilter));
            Calendar date = Calendar.getInstance();
            date.setTime(Date.from(EidTestPki.NOW.minusSeconds(60)));
            signature.setSignDate(date);
            SignatureInterface cms = content -> cmsSignature(signer, content.readAllBytes());
            document.addSignature(signature, cms);
            for (COSDictionary dictionary : changed) {
                dictionary.setNeedToBeUpdated(true);
            }
            document.saveIncremental(out);
            return out.toByteArray();
        } catch (IOException ex) {
            throw new IllegalStateException(ex);
        }
    }

    /** Altération d'un document ouvert ; les dictionnaires modifiés vont dans {@code changed}. */
    @FunctionalInterface
    public interface PdfTamper {
        void apply(PDDocument document, Set<COSDictionary> changed) throws IOException;
    }

    /** Ajoute du texte au contenu de la première page. */
    public static final PdfTamper ADD_PAGE_TEXT = (document, changed) -> {
        PDPage page = document.getPage(0);
        try (PDPageContentStream content = new PDPageContentStream(document, page, PDPageContentStream.AppendMode.APPEND, true)) {
            content.beginText();
            content.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD), 14);
            content.newLineAtOffset(72, 700);
            content.showText("Montant : 1 000 000 EUR");
            content.endText();
        }
        page.getCOSObject().setNeedToBeUpdated(true);
        changed.add(page.getCOSObject());
        changed.add(document.getDocumentCatalog().getCOSObject());
    };

    /** Ajoute une note (annotation texte) sur la première page. */
    public static final PdfTamper ADD_ANNOTATION = (document, changed) -> {
        PDPage page = document.getPage(0);
        PDAnnotationText note = new PDAnnotationText();
        note.setContents("Annulé");
        note.setRectangle(new PDRectangle(50, 50, 20, 20));
        page.getAnnotations().add(note);
        page.getCOSObject().setNeedToBeUpdated(true);
        note.getCOSObject().setNeedToBeUpdated(true);
        changed.add(page.getCOSObject());
        changed.add(note.getCOSObject());
    };

    /** Mise à jour incrémentale qui applique {@code tamper}, sans signature. */
    public static byte[] incrementalUpdate(byte[] pdf, PdfTamper tamper) {
        try (PDDocument document = Loader.loadPDF(pdf); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Set<COSDictionary> changed = new HashSet<>();
            tamper.apply(document, changed);
            document.saveIncremental(out, changed);
            return out.toByteArray();
        } catch (IOException ex) {
            throw new IllegalStateException(ex);
        }
    }

    /** PDF chiffré (mot de passe d'ouverture vide, mot de passe propriétaire). */
    public static byte[] encryptedPdf() {
        try (PDDocument document = Loader.loadPDF(basePdf()); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            StandardProtectionPolicy policy = new StandardProtectionPolicy("owner-secret", "", new AccessPermission());
            policy.setEncryptionKeyLength(128);
            document.protect(policy);
            document.save(out);
            return out.toByteArray();
        } catch (IOException ex) {
            throw new IllegalStateException(ex);
        }
    }

    static byte[] cmsSignature(Signer signer, byte[] content) throws IOException {
        try {
            CMSSignedDataGenerator generator = new CMSSignedDataGenerator();
            generator.addSignerInfoGenerator(new JcaSignerInfoGeneratorBuilder(EidTestPki.digests())
                    .build(new JcaContentSignerBuilder(signer.keyType().jcaSignatureAlgorithm()).build(signer.key()), signer.certificate()));
            List<X509Certificate> certificates = new ArrayList<>();
            certificates.add(signer.certificate());
            certificates.add(signer.chain().get(0));
            generator.addCertificates(new JcaCertStore(certificates));
            CMSSignedData data = generator.generate(new CMSProcessableByteArray(content), false);
            return data.getEncoded();
        } catch (Exception ex) {
            throw new IOException("test CMS signature failed", ex);
        }
    }

    /** Source d'horodatage de test (TSA sous sa propre racine, inconnue du vérificateur). */
    public static TSPSource testTsp() {
        Issued tsa = EidTestPki.timestampAuthority();
        return (digestAlgorithm, digest) -> {
            try {
                TimeStampTokenGenerator generator = new TimeStampTokenGenerator(
                        new org.bouncycastle.cms.jcajce.JcaSimpleSignerInfoGeneratorBuilder().build("SHA256withRSA", tsa.key(), tsa.certificate()),
                        EidTestPki.digests().get(new org.bouncycastle.asn1.x509.AlgorithmIdentifier(
                                org.bouncycastle.asn1.nist.NISTObjectIdentifiers.id_sha256)),
                        new ASN1ObjectIdentifier("1.2.3.4.1"));
                generator.addCertificates(new JcaCertStore(List.of(tsa.certificate())));
                TimeStampRequestGenerator requests = new TimeStampRequestGenerator();
                requests.setCertReq(true);
                TimeStampRequest request = requests.generate(new ASN1ObjectIdentifier(digestAlgorithm.getOid()), digest);
                TimeStampResponse response = new TimeStampResponseGenerator(generator, TSPAlgorithms.ALLOWED)
                        .generate(request, BigInteger.valueOf(TSA_SERIALS.incrementAndGet()), Date.from(EidTestPki.NOW.minusSeconds(30)));
                return new TimestampBinary(response.getTimeStampToken().getEncoded());
            } catch (Exception ex) {
                throw new IllegalStateException("test TSA failed", ex);
            }
        };
    }

    static byte[] bytes(DSSDocument document) {
        try (InputStream in = document.openStream()) {
            return in.readAllBytes();
        } catch (IOException ex) {
            throw new IllegalStateException(ex);
        }
    }

    static byte[] sha256(byte[] data) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(data);
        } catch (Exception ex) {
            throw new IllegalStateException(ex);
        }
    }
}
