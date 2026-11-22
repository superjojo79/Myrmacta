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
            EidTestPki pkiForExtension, EidTestPki.FakeTransport revocationForExtension, float[] fieldBox, boolean certify) {
        public SignOptions(String subFilter, SignatureLevel level, boolean visible, Instant signingTime, EidTestPki pkiForExtension) {
            this(subFilter, level, visible, signingTime, pkiForExtension, null, null, false);
        }

        public SignOptions(String subFilter, SignatureLevel level, boolean visible, Instant signingTime, EidTestPki pkiForExtension,
                EidTestPki.FakeTransport revocationForExtension) {
            this(subFilter, level, visible, signingTime, pkiForExtension, revocationForExtension, null, false);
        }

        /** Signature visible au cadre donné (DSS : origine en haut à gauche, en points). */
        public SignOptions visibleAt(float originX, float originY, float width, float height) {
            return new SignOptions(subFilter, level, true, signingTime, pkiForExtension, revocationForExtension,
                    new float[] {originX, originY, width, height}, certify);
        }

        /** Signature de certification {@code /DocMDP P=1} (crée {@code /Perms}). */
        public SignOptions certifying() {
            return new SignOptions(subFilter, level, visible, signingTime, pkiForExtension, revocationForExtension, fieldBox, true);
        }

        /** Révocation embarquée par l'extension LT/LTA, servie par ce transport (par défaut celui de la hiérarchie). */
        public SignOptions embeddingRevocationFrom(EidTestPki.FakeTransport transport) {
            return new SignOptions(subFilter, level, visible, signingTime, pkiForExtension, transport);
        }

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

    /** Repère de signature en texte blanc : page à partir de 0, abscisse et ligne de base en espace utilisateur PDF. */
    public record Anchor(int page, int rank, float x, float baselineY, boolean white) {
        public Anchor(int page, int rank, float x, float baselineY) {
            this(page, rank, x, baselineY, true);
        }

        /** Repère écrit en noir : visible, donc zone non vierge. */
        public Anchor black() {
            return new Anchor(page, rank, x, baselineY, false);
        }
    }

    /**
     * PDF dont les repères {@code {{sN|signature|85|37}}} sont écrits en blanc (comme dans les vrais modèles). Une
     * CropBox décalée de {@code cropOffset} points sur les deux axes (retrait de la MediaBox A4) si elle est non nulle.
     */
    public static byte[] basePdfWithAnchors(int pages, float cropOffset, Anchor... anchors) {
        try (PDDocument document = new PDDocument(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            for (int i = 0; i < pages; i++) {
                PDPage page = new PDPage(PDRectangle.A4);
                if (cropOffset > 0) {
                    page.setCropBox(new PDRectangle(cropOffset, cropOffset, PDRectangle.A4.getWidth() - 2 * cropOffset,
                            PDRectangle.A4.getHeight() - 2 * cropOffset));
                }
                document.addPage(page);
                try (PDPageContentStream content = new PDPageContentStream(document, page)) {
                    content.beginText();
                    content.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 12);
                    content.newLineAtOffset(72, 760);
                    content.showText("Contrat de test Myrmacta - page " + (i + 1));
                    content.endText();
                    for (Anchor anchor : anchors) {
                        if (anchor.page() != i) {
                            continue;
                        }
                        if (anchor.white()) {
                            content.setNonStrokingColor(1f, 1f, 1f);
                        } else {
                            content.setNonStrokingColor(0f, 0f, 0f);
                        }
                        content.beginText();
                        content.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 10);
                        content.newLineAtOffset(anchor.x(), anchor.baselineY());
                        content.showText("{{s" + anchor.rank() + "|signature|85|37}}");
                        content.endText();
                    }
                }
            }
            document.save(out);
            return out.toByteArray();
        } catch (IOException ex) {
            throw new IllegalStateException(ex);
        }
    }

    /** Même PDF avec une annotation de lien de rectangle donné sur la page (à partir de 0). */
    public static byte[] withLink(byte[] pdf, int pageIndex, PDRectangle rect) {
        try (PDDocument document = Loader.loadPDF(pdf); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            org.apache.pdfbox.pdmodel.interactive.annotation.PDAnnotationLink link =
                    new org.apache.pdfbox.pdmodel.interactive.annotation.PDAnnotationLink();
            link.setRectangle(rect);
            document.getPage(pageIndex).getAnnotations().add(link);
            document.save(out);
            return out.toByteArray();
        } catch (IOException ex) {
            throw new IllegalStateException(ex);
        }
    }

    /** Même PDF avec MediaBox et CropBox données à la page 0 (le contenu ne bouge pas en espace utilisateur). */
    public static byte[] withBoxes(byte[] pdf, PDRectangle media, PDRectangle crop) {
        try (PDDocument document = Loader.loadPDF(pdf); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            document.getPage(0).setMediaBox(media);
            document.getPage(0).setCropBox(crop);
            document.save(out);
            return out.toByteArray();
        } catch (IOException ex) {
            throw new IllegalStateException(ex);
        }
    }

    /** Même PDF, la page donnée (à partir de 0) tournée de 90 degrés. */
    public static byte[] withRotatedPage(byte[] pdf, int pageIndex) {
        try (PDDocument document = Loader.loadPDF(pdf); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            document.getPage(pageIndex).setRotation(90);
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
            GatedRevocationSources sources = new GatedRevocationSources(new TrustedChainGate(pki.trustedFile()),
                    options.revocationForExtension() == null ? pki.transport() : options.revocationForExtension(), 16);
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
        if (options.certify()) {
            parameters.setPermission(eu.europa.esig.dss.enumerations.CertificationPermission.NO_CHANGE_PERMITTED);
        }
        if (options.visible()) {
            SignatureImageParameters image = new SignatureImageParameters();
            SignatureFieldParameters field = new SignatureFieldParameters();
            float[] box = options.fieldBox() == null ? new float[] {300, 600, 200, 60} : options.fieldBox();
            field.setOriginX(box[0]);
            field.setOriginY(box[1]);
            field.setWidth(box[2]);
            field.setHeight(box[3]);
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
        return signWithPdfBox(pdf, signer, subFilter, tamper, null);
    }

    /** Comme ci-dessus ; {@code widget}, s'il n'est pas nul, retouche le champ de signature créé avant le calcul. */
    public static byte[] signWithPdfBox(byte[] pdf, Signer signer, String subFilter, PdfTamper tamper, WidgetTamper widget) {
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
            if (widget != null) {
                List<org.apache.pdfbox.pdmodel.interactive.form.PDSignatureField> fields = document.getSignatureFields();
                widget.apply(document, fields.get(fields.size() - 1));
            }
            for (COSDictionary dictionary : changed) {
                dictionary.setNeedToBeUpdated(true);
            }
            document.saveIncremental(out);
            return out.toByteArray();
        } catch (IOException ex) {
            throw new IllegalStateException(ex);
        }
    }

    /** Retouche du champ de signature que PDFBox vient de créer (même révision que la signature). */
    @FunctionalInterface
    public interface WidgetTamper {
        void apply(PDDocument document, org.apache.pdfbox.pdmodel.interactive.form.PDSignatureField field) throws IOException;
    }

    /** Widget pleine page, apparence opaque qui affiche d'autres clauses. */
    public static final WidgetTamper FULL_PAGE_APPEARANCE = (document, field) -> {
        org.apache.pdfbox.pdmodel.interactive.annotation.PDAnnotationWidget widget = field.getWidgets().get(0);
        PDRectangle page = document.getPage(0).getMediaBox();
        widget.setRectangle(new PDRectangle(0, 0, page.getWidth(), page.getHeight()));
        org.apache.pdfbox.pdmodel.interactive.annotation.PDAppearanceStream stream =
                new org.apache.pdfbox.pdmodel.interactive.annotation.PDAppearanceStream(document);
        stream.setBBox(new PDRectangle(0, 0, page.getWidth(), page.getHeight()));
        stream.setResources(new org.apache.pdfbox.pdmodel.PDResources());
        try (PDPageContentStream content = new PDPageContentStream(document, stream)) {
            content.setNonStrokingColor(java.awt.Color.WHITE);
            content.addRect(0, 0, page.getWidth(), page.getHeight());
            content.fill();
            content.beginText();
            content.setNonStrokingColor(java.awt.Color.BLACK);
            content.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 12);
            content.newLineAtOffset(72, 760);
            content.showText("Autres clauses : le signataire cede tous ses biens");
            content.endText();
        }
        org.apache.pdfbox.pdmodel.interactive.annotation.PDAppearanceDictionary appearance =
                new org.apache.pdfbox.pdmodel.interactive.annotation.PDAppearanceDictionary();
        appearance.setNormalAppearance(stream);
        widget.setAppearance(appearance);
    };

    /** Le champ de signature devient une annotation d'un autre sous-type (tampon, texte libre, pièce jointe). */
    public static WidgetTamper subtype(String subtype) {
        return (document, field) -> {
            COSDictionary dictionary = field.getWidgets().get(0).getCOSObject();
            dictionary.setName(COSName.SUBTYPE, subtype);
            dictionary.setItem(COSName.RECT, new PDRectangle(50, 50, 40, 40).getCOSArray());
            if ("FileAttachment".equals(subtype)) {
                COSDictionary fileSpec = new COSDictionary();
                fileSpec.setName(COSName.TYPE, "Filespec");
                fileSpec.setString(COSName.F, "annexe.txt");
                dictionary.setItem(COSName.FS, fileSpec);
            }
        };
    }

    /** Document de {@code pages} pages dont toutes les pages partagent la même police ({@code /F1}, un seul objet). */
    public static byte[] basePdfSharedFont(int pages) {
        try (PDDocument document = new PDDocument(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            PDType1Font font = new PDType1Font(Standard14Fonts.FontName.HELVETICA);
            for (int i = 1; i <= pages; i++) {
                PDPage page = new PDPage(PDRectangle.A4);
                document.addPage(page);
                try (PDPageContentStream content = new PDPageContentStream(document, page)) {
                    content.beginText();
                    content.setFont(font, 12);
                    content.newLineAtOffset(72, 760);
                    content.showText("Clause " + i + " : montant 1 000 EUR");
                    content.endText();
                }
            }
            document.save(out);
            return out.toByteArray();
        } catch (IOException ex) {
            throw new IllegalStateException(ex);
        }
    }

    /** Remplace, sur la page {@code index}, la police partagée {@code /F1} par une autre (nouvel objet). */
    public static PdfTamper replacePageFont(int index) {
        return (document, changed) -> {
            PDPage page = document.getPage(index);
            org.apache.pdfbox.pdmodel.PDResources resources = page.getResources();
            COSName name = resources.getFontNames().iterator().next();
            resources.put(name, new PDType1Font(Standard14Fonts.FontName.COURIER));
            page.getCOSObject().setNeedToBeUpdated(true);
            resources.getCOSObject().setNeedToBeUpdated(true);
            changed.add(page.getCOSObject());
            changed.add(resources.getCOSObject());
        };
    }

    /** Document avec une {@code /AcroForm} dont les ressources {@code /DR} déclarent une police {@code /Helv}. */
    public static byte[] basePdfWithAcroFormResources() {
        try (PDDocument document = Loader.loadPDF(basePdf()); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            org.apache.pdfbox.pdmodel.interactive.form.PDAcroForm form =
                    new org.apache.pdfbox.pdmodel.interactive.form.PDAcroForm(document);
            org.apache.pdfbox.pdmodel.PDResources resources = new org.apache.pdfbox.pdmodel.PDResources();
            resources.put(COSName.getPDFName("Helv"), new PDType1Font(Standard14Fonts.FontName.HELVETICA));
            form.setDefaultResources(resources);
            document.getDocumentCatalog().setAcroForm(form);
            document.save(out);
            return out.toByteArray();
        } catch (IOException ex) {
            throw new IllegalStateException(ex);
        }
    }

    /** Remplace, dans {@code /AcroForm /DR}, la police existante {@code /Helv} (modification, pas création). */
    public static final PdfTamper REPLACE_ACROFORM_FONT = (document, changed) -> {
        org.apache.pdfbox.pdmodel.interactive.form.PDAcroForm form = document.getDocumentCatalog().getAcroForm();
        org.apache.pdfbox.pdmodel.PDResources resources = form.getDefaultResources();
        resources.put(COSName.getPDFName("Helv"), new PDType1Font(Standard14Fonts.FontName.TIMES_ROMAN));
        resources.getCOSObject().setNeedToBeUpdated(true);
        form.getCOSObject().setNeedToBeUpdated(true);
        changed.add(resources.getCOSObject());
        changed.add(form.getCOSObject());
        changed.add(document.getDocumentCatalog().getCOSObject());
    };

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

    // --- révisions forgées à la main (contre-revue : graphes neufs, entrées libres, générations) ---

    /**
     * Ajoute une révision incrémentale écrite à la main : {@code objects} (numéro → texte complet entre
     * {@code N G obj} et {@code endobj}, génération {@code generations} ou 0), {@code extraEntries} des sous-sections
     * de table supplémentaires (texte brut), {@code root} la référence du catalogue du trailer.
     */
    public static byte[] appendRevision(byte[] pdf, java.util.Map<Integer, String> objects, java.util.Map<Integer, Integer> generations,
            String extraEntries, String root) {
        try (PDDocument document = Loader.loadPDF(pdf)) {
            String text = new String(pdf, java.nio.charset.StandardCharsets.ISO_8859_1);
            java.util.regex.Matcher matcher = java.util.regex.Pattern.compile("startxref\\s+(\\d+)").matcher(text);
            long previous = -1;
            while (matcher.find()) {
                previous = Long.parseLong(matcher.group(1));
            }
            long size = Math.max(document.getDocument().getHighestXRefObjectNumber() + 1,
                    objects.keySet().stream().mapToLong(n -> n + 1L).max().orElse(0));
            java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
            out.write(pdf);
            out.write('\n');
            StringBuilder table = new StringBuilder("xref\n");
            for (java.util.Map.Entry<Integer, String> object : new java.util.TreeMap<>(objects).entrySet()) {
                int generation = generations == null ? 0 : generations.getOrDefault(object.getKey(), 0);
                table.append(object.getKey()).append(" 1\n").append(String.format("%010d %05d n \n", out.size(), generation));
                out.write((object.getKey() + " " + generation + " obj\n" + object.getValue() + "\nendobj\n")
                        .getBytes(java.nio.charset.StandardCharsets.ISO_8859_1));
            }
            if (extraEntries != null) {
                table.append(extraEntries);
            }
            long xref = out.size();
            out.write((table + "trailer\n<< /Size " + size + " /Root " + root + " /Prev " + previous + " >>\nstartxref\n" + xref
                    + "\n%%EOF\n").getBytes(java.nio.charset.StandardCharsets.ISO_8859_1));
            return out.toByteArray();
        } catch (IOException ex) {
            throw new IllegalStateException(ex);
        }
    }

    /** Texte PDF d'une valeur COS (références gardées telles quelles). */
    public static String cos(org.apache.pdfbox.cos.COSBase value) {
        if (value instanceof org.apache.pdfbox.cos.COSObject object) {
            return object.getKey().getNumber() + " " + object.getKey().getGeneration() + " R";
        }
        if (value != null && value.getKey() != null && !(value instanceof org.apache.pdfbox.cos.COSStream)) {
            return value.getKey().getNumber() + " " + value.getKey().getGeneration() + " R";
        }
        if (value instanceof COSDictionary dictionary) {
            StringBuilder out = new StringBuilder("<<");
            for (java.util.Map.Entry<COSName, org.apache.pdfbox.cos.COSBase> entry : dictionary.entrySet()) {
                out.append(" /").append(entry.getKey().getName()).append(' ').append(cos(entry.getValue()));
            }
            return out.append(" >>").toString();
        }
        if (value instanceof org.apache.pdfbox.cos.COSArray array) {
            StringBuilder out = new StringBuilder("[");
            for (int i = 0; i < array.size(); i++) {
                out.append(' ').append(cos(array.get(i)));
            }
            return out.append(" ]").toString();
        }
        if (value instanceof COSName name) {
            return "/" + name.getName();
        }
        if (value instanceof org.apache.pdfbox.cos.COSString string) {
            return "<" + java.util.HexFormat.of().formatHex(string.getBytes()) + ">";
        }
        if (value instanceof org.apache.pdfbox.cos.COSInteger integer) {
            return Long.toString(integer.longValue());
        }
        if (value instanceof org.apache.pdfbox.cos.COSFloat number) {
            return Float.toString(number.floatValue());
        }
        if (value instanceof org.apache.pdfbox.cos.COSBoolean bool) {
            return Boolean.toString(bool.getValue());
        }
        return "null";
    }

    /** Clé « N G » d'un objet indirect du catalogue, de la page {@code index}, ou de son premier flux de contenu. */
    public record Keys(int catalog, int pages, int page, int content, int highest) {}

    public static Keys keys(byte[] pdf, int pageIndex) {
        try (PDDocument document = Loader.loadPDF(pdf)) {
            COSDictionary trailer = document.getDocument().getTrailer();
            org.apache.pdfbox.cos.COSObject root = (org.apache.pdfbox.cos.COSObject) trailer.getItem(COSName.ROOT);
            COSDictionary catalog = (COSDictionary) root.getObject();
            org.apache.pdfbox.cos.COSObject pages = (org.apache.pdfbox.cos.COSObject) catalog.getItem(COSName.PAGES);
            org.apache.pdfbox.cos.COSArray kids = (org.apache.pdfbox.cos.COSArray) ((COSDictionary) pages.getObject()).getDictionaryObject(COSName.KIDS);
            org.apache.pdfbox.cos.COSObject page = (org.apache.pdfbox.cos.COSObject) kids.get(pageIndex);
            org.apache.pdfbox.cos.COSBase contents = ((COSDictionary) page.getObject()).getItem(COSName.CONTENTS);
            int content = (int) ((org.apache.pdfbox.cos.COSObject) contents).getKey().getNumber();
            return new Keys((int) root.getKey().getNumber(), (int) pages.getKey().getNumber(), (int) page.getKey().getNumber(),
                    content, (int) document.getDocument().getHighestXRefObjectNumber());
        } catch (IOException ex) {
            throw new IllegalStateException(ex);
        }
    }

    /** Dictionnaire (sans les références de flux) de l'objet {@code number} génération 0. */
    public static COSDictionary dictionary(byte[] pdf, int number) {
        try (PDDocument document = Loader.loadPDF(pdf)) {
            COSDictionary dictionary = (COSDictionary) document.getDocument()
                    .getObjectFromPool(new org.apache.pdfbox.cos.COSObjectKey(number, 0)).getObject();
            COSDictionary copy = new COSDictionary();
            for (java.util.Map.Entry<COSName, org.apache.pdfbox.cos.COSBase> entry : dictionary.entrySet()) {
                copy.setItem(entry.getKey(), entry.getValue());
            }
            return copy;
        } catch (IOException ex) {
            throw new IllegalStateException(ex);
        }
    }

    /** Flux de contenu en texte PDF. */
    public static String contentStream(String operators) {
        return "<< /Length " + operators.length() + " >>\nstream\n" + operators + "\nendstream";
    }

    /** Champs de signature vides en mosaïque opaque (moins de 5 % chacun), sur la page {@code index}. */
    public static PdfTamper emptySignatureMosaic(int count) {
        return (document, changed) -> {
            org.apache.pdfbox.pdmodel.interactive.form.PDAcroForm form = document.getDocumentCatalog().getAcroForm();
            if (form == null) {
                form = new org.apache.pdfbox.pdmodel.interactive.form.PDAcroForm(document);
                document.getDocumentCatalog().setAcroForm(form);
            }
            PDPage page = document.getPage(0);
            for (int i = 0; i < count; i++) {
                org.apache.pdfbox.pdmodel.interactive.form.PDSignatureField field =
                        new org.apache.pdfbox.pdmodel.interactive.form.PDSignatureField(form);
                field.setPartialName("Mosaic" + i);
                org.apache.pdfbox.pdmodel.interactive.annotation.PDAnnotationWidget widget = field.getWidgets().get(0);
                PDRectangle rect = new PDRectangle(60 + (i % 5) * 100, 700 - (i / 5) * 40, 100, 40);
                widget.setRectangle(rect);
                widget.setPage(page);
                org.apache.pdfbox.pdmodel.interactive.annotation.PDAppearanceStream stream =
                        new org.apache.pdfbox.pdmodel.interactive.annotation.PDAppearanceStream(document);
                stream.setBBox(new PDRectangle(0, 0, 100, 40));
                try (PDPageContentStream content = new PDPageContentStream(document, stream)) {
                    content.setNonStrokingColor(java.awt.Color.WHITE);
                    content.addRect(0, 0, 100, 40);
                    content.fill();
                }
                org.apache.pdfbox.pdmodel.interactive.annotation.PDAppearanceDictionary appearance =
                        new org.apache.pdfbox.pdmodel.interactive.annotation.PDAppearanceDictionary();
                appearance.setNormalAppearance(stream);
                widget.setAppearance(appearance);
                form.getFields().add(field);
                page.getAnnotations().add(widget);
            }
            page.getCOSObject().setNeedToBeUpdated(true);
            form.getCOSObject().setNeedToBeUpdated(true);
            changed.add(page.getCOSObject());
            changed.add(form.getCOSObject());
            changed.add(document.getDocumentCatalog().getCOSObject());
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
