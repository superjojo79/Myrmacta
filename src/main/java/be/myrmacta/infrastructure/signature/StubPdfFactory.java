package be.myrmacta.infrastructure.signature;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.openpdf.text.Document;
import org.openpdf.text.DocumentException;
import org.openpdf.text.Element;
import org.openpdf.text.Font;
import org.openpdf.text.FontFactory;
import org.openpdf.text.Paragraph;
import org.openpdf.text.Rectangle;
import org.openpdf.text.pdf.BaseFont;
import org.openpdf.text.pdf.PdfContentByte;
import org.openpdf.text.pdf.PdfGState;
import org.openpdf.text.pdf.PdfReader;
import org.openpdf.text.pdf.PdfStamper;
import org.openpdf.text.pdf.PdfWriter;

/**
 * PDF du stub (TODO §72, D-SIGNATURE-PDF, D-SIGNATURE-PROOF) : filigrane du document « signé » et
 * PDF de preuve. Tout artefact porte {@link #MARK} : un document simulé ne doit jamais pouvoir être
 * pris pour une vraie signature (R-SIM-EN-PROD). Pile OpenPDF, déjà présente via Flying Saucer (F7).
 */
final class StubPdfFactory {

    static final String MARK = "SIMULATION — sans valeur juridique";

    /** Une ligne de la preuve : un signataire et sa décision (identité déjà masquée). */
    record ProofSigner(String label, String kind, boolean required, String status, Instant decidedAt) {}

    record ProofData(
            UUID requestId,
            String documentName,
            String documentDigest,
            String providerCode,
            String mode,
            Instant createdAt,
            Instant completedAt,
            List<ProofSigner> signers) {}

    /** Refuse un contenu que la signature (filigrane, preuve) ne saurait relire : échec à l'émission, pas à la fin. */
    void requireReadable(byte[] pdf) {
        try {
            PdfReader reader = new PdfReader(pdf);
            try {
                if (reader.getNumberOfPages() < 1) {
                    throw new IllegalArgumentException("PDF without page");
                }
            } finally {
                reader.close();
            }
        } catch (IOException | RuntimeException ex) {
            throw new IllegalArgumentException("unreadable PDF: " + ex.getMessage(), ex);
        }
    }

    byte[] watermark(byte[] sourcePdf) {
        try {
            PdfReader reader = new PdfReader(sourcePdf);
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            PdfStamper stamper = new PdfStamper(reader, out);
            // Le marquage ne repose pas que sur l'incrustation (retirable) : métadonnées du document, et
            // modification interdite (mot de passe propriétaire aléatoire, jamais conservé). La vraie
            // protection reste la métadonnée « simulated » de l'élément du dossier.
            java.util.Map<String, String> info = new java.util.HashMap<>();
            if (stamper.getInfoDictionary() != null) {
                info.putAll(stamper.getInfoDictionary());
            }
            info.put("Subject", MARK);
            info.put("Keywords", "SIMULATION");
            stamper.setInfoDictionary(info);
            byte[] ownerPassword = new byte[16];
            new java.security.SecureRandom().nextBytes(ownerPassword);
            stamper.setEncryption(null, ownerPassword, PdfWriter.ALLOW_PRINTING | PdfWriter.ALLOW_COPY,
                    PdfWriter.ENCRYPTION_AES_128);
            BaseFont bold = BaseFont.createFont(BaseFont.HELVETICA_BOLD, BaseFont.CP1252, BaseFont.NOT_EMBEDDED);
            BaseFont plain = BaseFont.createFont(BaseFont.HELVETICA, BaseFont.CP1252, BaseFont.NOT_EMBEDDED);
            PdfGState translucent = new PdfGState();
            translucent.setFillOpacity(0.22f);
            for (int page = 1; page <= reader.getNumberOfPages(); page++) {
                Rectangle size = reader.getPageSizeWithRotation(page);
                PdfContentByte over = stamper.getOverContent(page);
                over.saveState();
                over.setGState(translucent);
                over.beginText();
                over.setFontAndSize(bold, 30);
                over.setColorFill(Color.RED);
                over.showTextAligned(Element.ALIGN_CENTER, MARK, size.getWidth() / 2, size.getHeight() / 2, 40);
                over.endText();
                over.restoreState();
                over.saveState();
                over.beginText();
                over.setFontAndSize(plain, 8);
                over.setColorFill(Color.RED);
                over.showTextAligned(Element.ALIGN_CENTER, MARK, size.getWidth() / 2, 12, 0);
                over.endText();
                over.restoreState();
            }
            stamper.close();
            reader.close();
            return out.toByteArray();
        } catch (IOException | DocumentException ex) {
            throw new IllegalStateException("cannot watermark the source document (is it a PDF?)", ex);
        }
    }

    byte[] proof(ProofData data) {
        try {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            Document document = new Document();
            PdfWriter.getInstance(document, out);
            document.open();
            Font red = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 14, Color.RED);
            Font title = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 12);
            Font body = FontFactory.getFont(FontFactory.HELVETICA, 10);
            document.add(new Paragraph(MARK.toUpperCase(java.util.Locale.ROOT), red));
            document.add(new Paragraph("Certificat de signature simulé", title));
            document.add(new Paragraph(" ", body));
            document.add(new Paragraph("Demande : " + data.requestId(), body));
            document.add(new Paragraph("Document : " + data.documentName(), body));
            document.add(new Paragraph("Empreinte SHA-256 : " + data.documentDigest(), body));
            document.add(new Paragraph("Fournisseur : " + data.providerCode() + " (simulation)", body));
            document.add(new Paragraph("Mode : " + data.mode(), body));
            document.add(new Paragraph("Émise le : " + data.createdAt(), body));
            document.add(new Paragraph("Terminée le : " + data.completedAt(), body));
            document.add(new Paragraph(" ", body));
            document.add(new Paragraph("Signataires", title));
            for (ProofSigner signer : data.signers()) {
                document.add(new Paragraph(
                        "- " + signer.label() + " [" + signer.kind() + ", "
                                + (signer.required() ? "requis" : "facultatif") + "] : " + signer.status()
                                + (signer.decidedAt() == null ? "" : " le " + signer.decidedAt()),
                        body));
            }
            document.add(new Paragraph(" ", body));
            document.add(new Paragraph(MARK + " — aucune valeur probante.", red));
            document.close();
            return out.toByteArray();
        } catch (DocumentException ex) {
            throw new IllegalStateException("cannot build the simulation proof", ex);
        }
    }
}
