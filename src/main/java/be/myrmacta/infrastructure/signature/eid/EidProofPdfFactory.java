package be.myrmacta.infrastructure.signature.eid;

import be.myrmacta.domain.signature.model.SignedUploadEvidence;
import java.io.ByteArrayOutputStream;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.openpdf.text.Document;
import org.openpdf.text.DocumentException;
import org.openpdf.text.Font;
import org.openpdf.text.FontFactory;
import org.openpdf.text.Paragraph;
import org.openpdf.text.pdf.PdfWriter;

/**
 * PDF de preuve du fournisseur {@code eid} (TODO §138, D-EID-EVIDENCE). Modèle : la preuve du stub, sans
 * mention de simulation — ces signatures sont vérifiées. Pour chaque signataire elle liste les constats de
 * la vérification, puis les empreintes du document source et du document final, et dit sur quoi repose la
 * qualification (D-EID-QUALIFIED). Jamais le numéro de registre national (D-EID-NRN) : il n'est pas dans les
 * constats.
 */
final class EidProofPdfFactory {

    /** Sur quoi repose la qualification, dite telle quelle au lecteur de la preuve (D-EID-QUALIFIED). */
    static final String QUALIFICATION_BASIS = "Base de la qualification : chaque certificat remonte à une autorité "
            + "de confiance choisie par l'exploitant de l'instance, est valide, porte l'usage de non-répudiation "
            + "et se déclare qualifié sur dispositif qualifié (QcCompliance, QcSSCD). La liste de confiance "
            + "européenne n'a pas été consultée.";

    /** Un signataire de la demande ; {@code evidence} est nul s'il n'a pas signé. */
    record ProofSigner(
            String label,
            String kind,
            boolean required,
            String status,
            Instant decidedAt,
            Integer sequence,
            SignedUploadEvidence evidence) {}

    /**
     * Horodatage du document final (D-EID-TIMESTAMP). {@code configured} faux : l'exploitant n'en a pas configuré ;
     * {@code configured} vrai et {@code timestamped} faux : la TSA était injoignable, livraison sans horodatage.
     */
    record ProofTimestamp(boolean configured, boolean timestamped, Instant tokenTime, String authority,
            boolean authorityTrusted, String level) {
        static final ProofTimestamp NOT_CONFIGURED = new ProofTimestamp(false, false, null, null, false, null);
        static final ProofTimestamp UNAVAILABLE = new ProofTimestamp(true, false, null, null, false, null);
    }

    record ProofData(
            UUID requestId,
            String providerCode,
            String mode,
            Instant createdAt,
            Instant completedAt,
            String sourceDigest,
            String finalDigest,
            List<ProofSigner> signers,
            ProofTimestamp timestamp) {

        ProofData(UUID requestId, String providerCode, String mode, Instant createdAt, Instant completedAt,
                String sourceDigest, String finalDigest, List<ProofSigner> signers) {
            this(requestId, providerCode, mode, createdAt, completedAt, sourceDigest, finalDigest, signers,
                    ProofTimestamp.NOT_CONFIGURED);
        }

        ProofData {
            timestamp = timestamp == null ? ProofTimestamp.NOT_CONFIGURED : timestamp;
        }
    }

    /** Texte de la livraison sans horodatage, repris tel quel (D-EID-TIMESTAMP : jamais de dégradation silencieuse). */
    static final String TIMESTAMP_UNAVAILABLE = "Horodatage : NON HORODATÉ. L'autorité d'horodatage configurée était "
            + "injoignable ou a donné une réponse invalide à chaque tentative ; le document a été livré sans horodatage "
            + "pour ne pas bloquer le dossier.";

    byte[] proof(ProofData data) {
        try {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            Document document = new Document();
            PdfWriter.getInstance(document, out);
            document.open();
            Font title = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 14);
            Font heading = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 12);
            Font body = FontFactory.getFont(FontFactory.HELVETICA, 10);
            Font small = FontFactory.getFont(FontFactory.HELVETICA, 9);
            document.add(new Paragraph("Preuve de signature électronique", title));
            document.add(new Paragraph(" ", body));
            document.add(new Paragraph("Demande : " + data.requestId(), body));
            document.add(new Paragraph("Fournisseur : " + data.providerCode()
                    + " (signature apportée par le signataire, vérifiée par l'application)", body));
            document.add(new Paragraph("Mode : " + data.mode(), body));
            document.add(new Paragraph("Émise le : " + data.createdAt(), body));
            document.add(new Paragraph("Terminée le : " + data.completedAt(), body));
            document.add(new Paragraph("Empreinte SHA-256 du document source : " + data.sourceDigest(), small));
            document.add(new Paragraph("Empreinte SHA-256 du document final : " + data.finalDigest(), small));
            timestamp(document, heading, small, data.timestamp());
            document.add(new Paragraph(" ", body));
            document.add(new Paragraph("Signataires", heading));
            for (ProofSigner signer : data.signers()) {
                document.add(new Paragraph(
                        "- " + clean(signer.label()) + " [" + signer.kind() + ", "
                                + (signer.required() ? "requis" : "facultatif") + "] : " + signer.status()
                                + (signer.decidedAt() == null ? "" : " le " + signer.decidedAt()),
                        body));
                SignedUploadEvidence evidence = signer.evidence();
                if (evidence == null) {
                    continue;
                }
                line(document, small, "Rang de signature", String.valueOf(signer.sequence()));
                line(document, small, "Nom lu dans le certificat", evidence.signerName());
                line(document, small, "Émetteur du certificat", evidence.certificateIssuer());
                line(document, small, "Numéro de série du certificat", evidence.certificateSerial());
                line(document, small, "Empreinte SHA-256 du certificat", evidence.certificateFingerprint());
                line(document, small, "Heure déclarée par le signataire (non prouvée)",
                        evidence.signingTime() == null ? "non déclarée" : evidence.signingTime().toString());
                line(document, small, "Heure de la vérification", evidence.verifiedAt().toString());
                line(document, small, "Format de signature", evidence.signatureFormat());
                line(document, small, "Algorithme de signature", evidence.signatureAlgorithm());
                line(document, small, "Certificat qualifié (QcCompliance)", yesNo(evidence.qualifiedCertificate()));
                line(document, small, "Dispositif qualifié (QcSSCD)", yesNo(evidence.qualifiedDevice()));
                line(document, small, "Source de révocation", evidence.revocationSource());
                line(document, small, "Date de la réponse de révocation",
                        evidence.revocationProducedAt() == null ? null : evidence.revocationProducedAt().toString());
            }
            document.add(new Paragraph(" ", body));
            document.add(new Paragraph(QUALIFICATION_BASIS, small));
            document.close();
            return out.toByteArray();
        } catch (DocumentException ex) {
            throw new IllegalStateException("cannot build the signature proof", ex);
        }
    }

    private static void timestamp(Document document, Font heading, Font small, ProofTimestamp timestamp)
            throws DocumentException {
        document.add(new Paragraph(" ", small));
        document.add(new Paragraph("Horodatage du document final", heading));
        if (!timestamp.configured()) {
            document.add(new Paragraph("Horodatage : non configuré par l'exploitant (document non horodaté).", small));
            return;
        }
        if (!timestamp.timestamped()) {
            document.add(new Paragraph(TIMESTAMP_UNAVAILABLE, small));
            return;
        }
        line(document, small, "Horodaté", "oui");
        line(document, small, "Heure du jeton d'horodatage", timestamp.tokenTime() == null ? null : timestamp.tokenTime().toString());
        line(document, small, "Autorité d'horodatage", timestamp.authority());
        line(document, small, "Niveau atteint", timestamp.level());
        line(document, small, "Autorité rattachée aux autorités de confiance de l'exploitant",
                timestamp.authorityTrusted() ? "oui" : "non (horodatage non qualifié, date attestée par la TSA choisie "
                        + "par l'exploitant, sans chaîne de confiance vérifiée)");
    }

    private static void line(Document document, Font font, String label, String value) throws DocumentException {
        Paragraph paragraph = new Paragraph("    " + label + " : " + (value == null ? "-" : clean(value)), font);
        document.add(paragraph);
    }

    private static String yesNo(boolean value) {
        return value ? "oui" : "non";
    }

    /** Texte venu d'un certificat : sans caractère de contrôle. */
    private static String clean(String value) {
        return value == null ? "" : value.replaceAll("\\p{Cntrl}", " ");
    }
}
