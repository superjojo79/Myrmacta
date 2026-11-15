package be.myrmacta.infrastructure.signature.eid;

import java.io.ByteArrayInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.util.HexFormat;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import org.bouncycastle.asn1.ASN1Encodable;
import org.bouncycastle.asn1.ASN1ObjectIdentifier;
import org.bouncycastle.asn1.ASN1OctetString;
import org.bouncycastle.asn1.ASN1Primitive;
import org.bouncycastle.asn1.ASN1Sequence;
import org.bouncycastle.asn1.x500.RDN;
import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.asn1.x500.style.BCStyle;
import org.bouncycastle.asn1.x500.style.IETFUtils;
import org.bouncycastle.asn1.x509.Extension;
import org.bouncycastle.asn1.x509.qualified.ETSIQCObjectIdentifiers;
import org.bouncycastle.asn1.x509.qualified.QCStatement;

/**
 * Ce que Myrmacta lit dans le certificat du signataire (TODO §138, D-EID-QUALIFIED, D-EID-EVIDENCE, D-EID-NRN).
 *
 * <p>Qualification : usage de clé {@code nonRepudiation} ({@code contentCommitment}), déclarations
 * {@code QcCompliance} et {@code QcSSCD} ; si {@code QcType} est présent, il ne vaut que {@code eSign}. Le certificat
 * d'authentification de la carte (usage {@code digitalSignature} seul) n'est donc pas qualifié.
 *
 * <p>Nom : prénoms et nom ({@code givenName}, {@code surname}) s'ils existent, sinon le {@code CN}. L'attribut
 * {@code serialNumber} du <b>sujet</b> (numéro de registre national sur une carte eID) n'est jamais lu (D-EID-NRN) :
 * aucune méthode d'ici ne le rend, ni ne rend le sujet complet.
 */
record SigningCertificateFacts(
        X509Certificate certificate,
        String signerName,
        String issuer,
        String serial,
        String fingerprint,
        boolean nonRepudiation,
        boolean qcCompliance,
        boolean qcSscd,
        boolean qcTypeAcceptable) {

    /** Qualifié au sens de D-EID-QUALIFIED. */
    boolean qualified() {
        return nonRepudiation && qcCompliance && qcSscd && qcTypeAcceptable;
    }

    static SigningCertificateFacts of(byte[] encoded) {
        X509Certificate certificate = parse(encoded);
        boolean[] keyUsage = certificate.getKeyUsage();
        boolean nonRepudiation = keyUsage != null && keyUsage.length > 1 && keyUsage[1];
        Set<ASN1ObjectIdentifier> statements = new HashSet<>();
        Set<ASN1ObjectIdentifier> qcTypes = new HashSet<>();
        boolean qcStatementsReadable = readQcStatements(certificate, statements, qcTypes);
        boolean qcTypePresent = statements.contains(ETSIQCObjectIdentifiers.id_etsi_qcs_QcType);
        boolean qcTypeAcceptable = qcStatementsReadable
                && (!qcTypePresent || qcTypes.equals(Set.of(ETSIQCObjectIdentifiers.id_etsi_qct_esign)));
        return new SigningCertificateFacts(
                certificate,
                signerName(certificate),
                certificate.getIssuerX500Principal().getName(),
                certificate.getSerialNumber().toString(16).toUpperCase(Locale.ROOT),
                fingerprint(encoded),
                nonRepudiation,
                qcStatementsReadable && statements.contains(ETSIQCObjectIdentifiers.id_etsi_qcs_QcCompliance),
                qcStatementsReadable && statements.contains(ETSIQCObjectIdentifiers.id_etsi_qcs_QcSSCD),
                qcTypeAcceptable);
    }

    /** SHA-256 du certificat encodé, en hexadécimal minuscule. */
    static String fingerprint(byte[] encoded) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(encoded));
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException(impossible);
        }
    }

    static X509Certificate parse(byte[] encoded) {
        try {
            return (X509Certificate) CertificateFactory.getInstance("X.509").generateCertificate(new ByteArrayInputStream(encoded));
        } catch (CertificateException | RuntimeException ex) {
            throw new IllegalArgumentException("certificate is not readable");
        }
    }

    /** Prénoms et nom, sinon le CN ; jamais le serialNumber du sujet. */
    private static String signerName(X509Certificate certificate) {
        X500Name subject = X500Name.getInstance(certificate.getSubjectX500Principal().getEncoded());
        String given = firstValue(subject, BCStyle.GIVENNAME);
        String surname = firstValue(subject, BCStyle.SURNAME);
        if (given != null && surname != null) {
            return given + " " + surname;
        }
        String commonName = firstValue(subject, BCStyle.CN);
        if (commonName != null) {
            return commonName;
        }
        return surname != null ? surname : "?";
    }

    private static String firstValue(X500Name name, ASN1ObjectIdentifier attribute) {
        RDN[] rdns = name.getRDNs(attribute);
        if (rdns.length == 0 || rdns[0].getFirst() == null) {
            return null;
        }
        String value = IETFUtils.valueToString(rdns[0].getFirst().getValue()).strip();
        return value.isEmpty() ? null : value;
    }

    /** Faux si l'extension QCStatements est présente mais illisible (dans le doute, non qualifié). */
    private static boolean readQcStatements(X509Certificate certificate, Set<ASN1ObjectIdentifier> statements,
            Set<ASN1ObjectIdentifier> qcTypes) {
        byte[] extension = certificate.getExtensionValue(Extension.qCStatements.getId());
        if (extension == null) {
            return true;
        }
        try {
            ASN1Primitive value = ASN1Primitive.fromByteArray(ASN1OctetString.getInstance(extension).getOctets());
            for (ASN1Encodable element : ASN1Sequence.getInstance(value)) {
                QCStatement statement = QCStatement.getInstance(element);
                statements.add(statement.getStatementId());
                if (ETSIQCObjectIdentifiers.id_etsi_qcs_QcType.equals(statement.getStatementId())) {
                    ASN1Encodable info = statement.getStatementInfo();
                    if (info == null) {
                        return false;
                    }
                    for (ASN1Encodable type : ASN1Sequence.getInstance(info)) {
                        qcTypes.add(ASN1ObjectIdentifier.getInstance(type));
                    }
                }
            }
            return true;
        } catch (Exception malformed) {
            return false;
        }
    }

    /** Jamais le sujet ni le nom : un journal qui afficherait ce record n'y trouve que l'empreinte (D-EID-NRN). */
    @Override
    public String toString() {
        return "SigningCertificateFacts[" + fingerprint + "]";
    }
}
