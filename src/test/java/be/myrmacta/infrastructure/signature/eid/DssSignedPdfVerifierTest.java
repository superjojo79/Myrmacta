package be.myrmacta.infrastructure.signature.eid;

import static org.assertj.core.api.Assertions.assertThat;

import be.myrmacta.domain.signature.model.SignedUploadEvidence;
import be.myrmacta.domain.signature.model.SignedUploadRejection;
import be.myrmacta.domain.signature.model.SignedUploadVerdict;
import be.myrmacta.infrastructure.signature.eid.EidTestPdfs.SignOptions;
import be.myrmacta.infrastructure.signature.eid.EidTestPki.FakeTransport;
import be.myrmacta.infrastructure.signature.eid.EidTestPki.KeyType;
import be.myrmacta.infrastructure.signature.eid.EidTestPki.Signer;
import eu.europa.esig.dss.enumerations.SignatureLevel;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import org.bouncycastle.asn1.x509.qualified.ETSIQCObjectIdentifiers;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Vérificateur de la signature apportée par carte eID (TODO §138) : de vrais PDF signés en PAdES par une hiérarchie
 * de test, la révocation servie en mémoire. Un test par motif de refus.
 */
class DssSignedPdfVerifierTest {

    private static EidTestPki pki;
    private static Signer rsaSigner;
    private static Signer ecSigner;
    private static byte[] base;
    private static byte[] signedRsa;

    @BeforeAll
    static void setUp() {
        pki = EidTestPki.create("Belgium Test");
        rsaSigner = pki.qualifiedSigner(KeyType.RSA_2048);
        ecSigner = pki.qualifiedSigner(KeyType.EC_P384);
        base = EidTestPdfs.basePdf();
        signedRsa = EidTestPdfs.sign(base, rsaSigner);
    }

    private static DssSignedPdfVerifier verifier(FakeTransport transport) {
        return new DssSignedPdfVerifier(pki.trustedFile(), EidTestPki.CLOCK, transport);
    }

    private static SignedUploadVerdict verify(byte[] baseDocument, byte[] uploaded) {
        return verifier(pki.transport()).verify(baseDocument, uploaded, Set.of());
    }

    private static void assertRejected(SignedUploadVerdict verdict, SignedUploadRejection expected) {
        assertThat(verdict.isAccepted()).as("verdict accepted").isFalse();
        assertThat(verdict.rejection()).isEqualTo(expected);
    }

    @Nested
    @DisplayName("Signatures acceptées")
    class Accepted {

        @Test
        @DisplayName("RSA 2048 / SHA-256, ETSI.CAdES.detached : accepté avec ses constats")
        void rsaSha256() {
            FakeTransport transport = pki.transport();
            SignedUploadVerdict verdict = verifier(transport).verify(base, signedRsa, Set.of());

            assertThat(verdict.isAccepted()).as("rejection %s", verdict.rejection()).isTrue();
            SignedUploadEvidence evidence = verdict.evidence();
            assertThat(evidence.signerName()).isEqualTo("Alice Anne Martin");
            assertThat(evidence.certificateIssuer()).contains("Belgium Test Citizen CA");
            assertThat(evidence.certificateSerial())
                    .isEqualTo(rsaSigner.certificate().getSerialNumber().toString(16).toUpperCase());
            assertThat(evidence.certificateFingerprint())
                    .isEqualTo(SigningCertificateFacts.fingerprint(encoded(rsaSigner)))
                    .matches("[0-9a-f]{64}");
            assertThat(evidence.signingTime()).isEqualTo(EidTestPki.NOW.minusSeconds(120));
            assertThat(evidence.verifiedAt()).isEqualTo(EidTestPki.NOW);
            assertThat(evidence.signatureFormat()).isEqualTo("PAdES-BASELINE-B");
            assertThat(evidence.signatureAlgorithm()).isEqualTo("SHA256withRSA");
            assertThat(evidence.qualifiedCertificate()).isTrue();
            assertThat(evidence.qualifiedDevice()).isTrue();
            assertThat(evidence.revocationSource()).isEqualTo("OCSP");
            assertThat(evidence.revocationProducedAt()).isEqualTo(EidTestPki.NOW);
            // D-EID-NRN : le numéro de registre national n'apparaît dans aucun constat.
            assertThat(evidence.toString()).doesNotContain(EidTestPki.FAKE_NATIONAL_NUMBER);
            assertThat(transport.callCount()).isPositive();
        }

        @Test
        @DisplayName("ECDSA P-384 / SHA-384 : accepté")
        void ecdsaP384() {
            SignedUploadVerdict verdict = verify(base, EidTestPdfs.sign(base, ecSigner));

            assertThat(verdict.isAccepted()).as("rejection %s", verdict.rejection()).isTrue();
            assertThat(verdict.evidence().signatureAlgorithm()).isEqualTo("SHA384withECDSA");
        }

        @Test
        @DisplayName("Sous-filtre adbe.pkcs7.detached (DSS) : accepté")
        void adbePkcs7() {
            byte[] uploaded = EidTestPdfs.sign(base, rsaSigner, SignOptions.defaults().subFilter("adbe.pkcs7.detached"));

            SignedUploadVerdict verdict = verify(base, uploaded);

            assertThat(verdict.isAccepted()).as("rejection %s", verdict.rejection()).isTrue();
        }

        @Test
        @DisplayName("CMS à la Acrobat (adbe.pkcs7.detached, sans attribut ESS) signé par PDFBox : verdict établi")
        void acrobatLikePkcs7() {
            byte[] uploaded = EidTestPdfs.signWithPdfBox(base, rsaSigner, "adbe.pkcs7.detached", null);

            SignedUploadVerdict verdict = verify(base, uploaded);

            assertThat(verdict.isAccepted()).as("rejection %s", verdict.rejection()).isTrue();
        }

        @Test
        @DisplayName("Signature visible : son apparence n'est pas une modification du contenu")
        void visibleSignature() {
            byte[] uploaded = EidTestPdfs.sign(base, ecSigner, SignOptions.defaults().withVisibleField());

            SignedUploadVerdict verdict = verify(base, uploaded);

            assertThat(verdict.isAccepted()).as("rejection %s", verdict.rejection()).isTrue();
        }

        @Test
        @DisplayName("Second signataire par-dessus un premier (document remis déjà signé)")
        void secondSigner() {
            byte[] uploaded = EidTestPdfs.sign(signedRsa, ecSigner);

            SignedUploadVerdict verdict = verifier(pki.transport())
                    .verify(signedRsa, uploaded, Set.of(SigningCertificateFacts.fingerprint(encoded(rsaSigner))));

            assertThat(verdict.isAccepted()).as("rejection %s", verdict.rejection()).isTrue();
            assertThat(verdict.evidence().certificateFingerprint())
                    .isEqualTo(SigningCertificateFacts.fingerprint(encoded(ecSigner)));
        }

        @Test
        @DisplayName("Fichier étendu en PAdES-BASELINE-LTA (DSS, horodatages) : accepté")
        void longTermArchive() {
            byte[] uploaded = EidTestPdfs.sign(base, rsaSigner, SignOptions.defaults().extended(SignatureLevel.PAdES_BASELINE_LTA, pki));

            SignedUploadVerdict verdict = verify(base, uploaded);

            assertThat(verdict.isAccepted()).as("rejection %s", verdict.rejection()).isTrue();
        }

        @Test
        @DisplayName("Horodatage de document ajouté après la signature : accepté")
        void documentTimestampAfterSignature() {
            byte[] uploaded = EidTestPdfs.documentTimestamp(signedRsa);

            SignedUploadVerdict verdict = verify(base, uploaded);

            assertThat(verdict.isAccepted()).as("rejection %s", verdict.rejection()).isTrue();
        }

        @Test
        @DisplayName("OCSP injoignable : la CRL prend le relais")
        void crlFallback() {
            SignedUploadVerdict verdict = verifier(pki.transport().ocspUnavailable()).verify(base, signedRsa, Set.of());

            assertThat(verdict.isAccepted()).as("rejection %s", verdict.rejection()).isTrue();
            assertThat(verdict.evidence().revocationSource()).isEqualTo("CRL");
        }
    }

    @Nested
    @DisplayName("Fichiers refusés avant toute cryptographie")
    class Structure {

        @Test
        void notAPdf() {
            assertRejected(verify(base, "bonjour".getBytes(StandardCharsets.UTF_8)), SignedUploadRejection.NOT_A_PDF);
            assertRejected(verify(base, new byte[0]), SignedUploadRejection.NOT_A_PDF);
            assertRejected(verify(base, null), SignedUploadRejection.NOT_A_PDF);
        }

        @Test
        @DisplayName("PDF chiffré : NOT_A_PDF")
        void encrypted() {
            byte[] encrypted = EidTestPdfs.encryptedPdf();

            assertRejected(verify(base, encrypted), SignedUploadRejection.NOT_A_PDF);
        }

        @Test
        @DisplayName("Signature d'un autre document : DOCUMENT_MISMATCH")
        void otherDocument() {
            byte[] other = EidTestPdfs.basePdf("Autre contrat", 1);

            assertRejected(verify(base, EidTestPdfs.sign(other, rsaSigner)), SignedUploadRejection.DOCUMENT_MISMATCH);
        }

        @Test
        @DisplayName("Document remis renvoyé tel quel : NO_NEW_SIGNATURE")
        void baseReturnedUnchanged() {
            assertRejected(verify(base, base.clone()), SignedUploadRejection.NO_NEW_SIGNATURE);
            assertRejected(verify(signedRsa, signedRsa.clone()), SignedUploadRejection.NO_NEW_SIGNATURE);
        }

        @Test
        @DisplayName("Mise à jour incrémentale sans signature : NO_NEW_SIGNATURE")
        void updateWithoutSignature() {
            assertRejected(verify(base, EidTestPdfs.documentTimestamp(base)), SignedUploadRejection.NO_NEW_SIGNATURE);
        }

        @Test
        @DisplayName("Deux signatures ajoutées en un téléversement : SEVERAL_SIGNATURES")
        void twoSignatures() {
            byte[] twice = EidTestPdfs.sign(EidTestPdfs.sign(base, rsaSigner), ecSigner);

            assertRejected(verify(base, twice), SignedUploadRejection.SEVERAL_SIGNATURES);
        }

        @Test
        @DisplayName("Octets ajoutés après la dernière révision : CONTENT_MODIFIED")
        void trailingBytes() {
            byte[] trailing = Arrays.copyOf(signedRsa, signedRsa.length + 12);
            System.arraycopy("% injected\n".getBytes(StandardCharsets.US_ASCII), 0, trailing, signedRsa.length, 11);

            assertRejected(verify(base, trailing), SignedUploadRejection.CONTENT_MODIFIED);
        }
    }

    @Nested
    @DisplayName("Contenu modifié")
    class Modified {

        @Test
        @DisplayName("Texte ajouté à la page dans la même révision que la signature : CONTENT_MODIFIED")
        void pageContentInSignatureRevision() {
            byte[] uploaded = EidTestPdfs.signWithPdfBox(base, rsaSigner, "adbe.pkcs7.detached", EidTestPdfs.ADD_PAGE_TEXT);

            assertRejected(verify(base, uploaded), SignedUploadRejection.CONTENT_MODIFIED);
        }

        @Test
        @DisplayName("Annotation ajoutée par une mise à jour incrémentale après la signature : CONTENT_MODIFIED")
        void annotationAfterSignature() {
            byte[] uploaded = EidTestPdfs.incrementalUpdate(signedRsa, EidTestPdfs.ADD_ANNOTATION);

            assertRejected(verify(base, uploaded), SignedUploadRejection.CONTENT_MODIFIED);
        }

        @Test
        @DisplayName("Page modifiée par une mise à jour incrémentale après la signature : CONTENT_MODIFIED")
        void pageContentAfterSignature() {
            byte[] uploaded = EidTestPdfs.incrementalUpdate(signedRsa, EidTestPdfs.ADD_PAGE_TEXT);

            assertRejected(verify(base, uploaded), SignedUploadRejection.CONTENT_MODIFIED);
        }

        @Test
        @DisplayName("Modification glissée entre le document remis et la signature, couverte par elle : CONTENT_MODIFIED")
        void modificationCoveredBySignature() {
            byte[] tampered = EidTestPdfs.incrementalUpdate(base, EidTestPdfs.ADD_PAGE_TEXT);

            assertRejected(verify(base, EidTestPdfs.sign(tampered, rsaSigner)), SignedUploadRejection.CONTENT_MODIFIED);
        }
    }

    @Nested
    @DisplayName("Signature et certificat")
    class Certificates {

        @Test
        @DisplayName("Valeur de signature qui ne correspond pas au certificat : SIGNATURE_INVALID")
        void corruptedSignatureValue() {
            // CMS bien formé, certificat du signataire, mais valeur calculée avec une autre clé.
            byte[] uploaded = EidTestPdfs.sign(base, rsaSigner, SignOptions.defaults(), EidTestPki.rsa(2048).getPrivate());

            assertRejected(verify(base, uploaded), SignedUploadRejection.SIGNATURE_INVALID);
        }

        @Test
        @DisplayName("Chaîne inconnue aux adresses de révocation internes : CERTIFICATE_UNTRUSTED et zéro appel sortant")
        void unknownChainMakesNoOutboundCall() {
            Signer impostor = EidTestPki.selfIssuedImpostor("http://127.0.0.1:8080/ocsp", "http://169.254.169.254/latest/crl");
            FakeTransport transport = pki.transport();

            SignedUploadVerdict verdict = verifier(transport).verify(base, EidTestPdfs.sign(base, impostor), Set.of());

            assertRejected(verdict, SignedUploadRejection.CERTIFICATE_UNTRUSTED);
            assertThat(transport.calls()).isEmpty();
        }

        @Test
        @DisplayName("Signataire d'une autre hiérarchie eID non configurée : refusé, zéro appel sortant")
        void otherHierarchyMakesNoOutboundCall() {
            EidTestPki other = EidTestPki.create("Other Country");
            FakeTransport transport = pki.transport().serve(other);

            SignedUploadVerdict verdict = verifier(transport)
                    .verify(base, EidTestPdfs.sign(base, other.qualifiedSigner(KeyType.RSA_2048)), Set.of());

            assertRejected(verdict, SignedUploadRejection.CERTIFICATE_UNTRUSTED);
            assertThat(transport.calls()).isEmpty();
        }

        @Test
        @DisplayName("Certificat expiré : CERTIFICATE_UNTRUSTED")
        void expired() {
            Signer expired = pki.signer()
                    .validity(EidTestPki.NOW.minus(Duration.ofDays(800)), EidTestPki.NOW.minus(Duration.ofDays(1)))
                    .issue();
            byte[] uploaded = EidTestPdfs.sign(base, expired, SignOptions.defaults().signingTime(EidTestPki.NOW.minus(Duration.ofDays(2))));

            assertRejected(verify(base, uploaded), SignedUploadRejection.CERTIFICATE_UNTRUSTED);
        }

        @Test
        @DisplayName("Certificat d'authentification de la carte (digitalSignature seul) : CERTIFICATE_NOT_QUALIFIED")
        void authenticationCertificate() {
            Signer authentication = pki.signer().authenticationOnly().issue();

            assertRejected(verify(base, EidTestPdfs.sign(base, authentication)), SignedUploadRejection.CERTIFICATE_NOT_QUALIFIED);
        }

        @Test
        @DisplayName("Certificat sans QcSSCD : CERTIFICATE_NOT_QUALIFIED")
        void withoutQcSscd() {
            Signer signer = pki.signer().withoutQcSscd().issue();

            assertRejected(verify(base, EidTestPdfs.sign(base, signer)), SignedUploadRejection.CERTIFICATE_NOT_QUALIFIED);
        }

        @Test
        @DisplayName("Certificat sans QcCompliance, ou QcType autre que eSign : CERTIFICATE_NOT_QUALIFIED")
        void withoutQcComplianceOrWrongType() {
            Signer noCompliance = pki.signer().withoutQcCompliance().issue();
            Signer seal = pki.signer().qcType(ETSIQCObjectIdentifiers.id_etsi_qct_eseal).issue();

            assertRejected(verify(base, EidTestPdfs.sign(base, noCompliance)), SignedUploadRejection.CERTIFICATE_NOT_QUALIFIED);
            assertRejected(verify(base, EidTestPdfs.sign(base, seal)), SignedUploadRejection.CERTIFICATE_NOT_QUALIFIED);
        }

        @Test
        @DisplayName("QcType absent : accepté (seuls QcCompliance et QcSSCD sont exigés)")
        void withoutQcType() {
            Signer signer = pki.signer().qcType(null).issue();

            SignedUploadVerdict verdict = verify(base, EidTestPdfs.sign(base, signer));

            assertThat(verdict.isAccepted()).as("rejection %s", verdict.rejection()).isTrue();
        }

        @Test
        @DisplayName("Empreinte déjà utilisée dans la demande : DUPLICATE_SIGNER")
        void duplicateSigner() {
            SignedUploadVerdict verdict = verifier(pki.transport())
                    .verify(base, signedRsa, Set.of(SigningCertificateFacts.fingerprint(encoded(rsaSigner))));

            assertRejected(verdict, SignedUploadRejection.DUPLICATE_SIGNER);
        }
    }

    @Nested
    @DisplayName("Révocation")
    class Revocation {

        @Test
        @DisplayName("Certificat révoqué : CERTIFICATE_REVOKED")
        void revoked() {
            EidTestPki local = EidTestPki.create("Revocation Test");
            Signer signer = local.qualifiedSigner(KeyType.RSA_2048);
            local.revoke(signer.certificate());
            DssSignedPdfVerifier verifier = new DssSignedPdfVerifier(local.trustedFile(), EidTestPki.CLOCK, local.transport());

            assertRejected(verifier.verify(base, EidTestPdfs.sign(base, signer), Set.of()), SignedUploadRejection.CERTIFICATE_REVOKED);
        }

        @Test
        @DisplayName("Autorité intermédiaire révoquée : CERTIFICATE_REVOKED")
        void intermediateRevoked() {
            EidTestPki local = EidTestPki.create("Revoked CA Test");
            Signer signer = local.qualifiedSigner(KeyType.RSA_2048);
            local.revoke(local.intermediate());
            DssSignedPdfVerifier verifier = new DssSignedPdfVerifier(local.trustedFile(), EidTestPki.CLOCK, local.transport());

            assertRejected(verifier.verify(base, EidTestPdfs.sign(base, signer), Set.of()), SignedUploadRejection.CERTIFICATE_REVOKED);
        }

        @Test
        @DisplayName("Révocation injoignable : REVOCATION_UNAVAILABLE")
        void unavailable() {
            SignedUploadVerdict verdict = verifier(pki.transport().unavailable()).verify(base, signedRsa, Set.of());

            assertRejected(verdict, SignedUploadRejection.REVOCATION_UNAVAILABLE);
        }

        @Test
        @DisplayName("Certificat sans adresse de révocation : REVOCATION_UNAVAILABLE")
        void noRevocationPointer() {
            Signer signer = pki.signer().withoutOcsp().withoutCrl().issue();

            assertRejected(verify(base, EidTestPdfs.sign(base, signer)), SignedUploadRejection.REVOCATION_UNAVAILABLE);
        }

        @Test
        @DisplayName("Réponse OCSP trop ancienne (plus de 24 h) : REVOCATION_UNAVAILABLE")
        void staleRevocation() {
            Clock old = Clock.fixed(EidTestPki.NOW.minus(Duration.ofDays(3)), ZoneOffset.UTC);
            SignedUploadVerdict verdict = verifier(pki.transport().clock(old)).verify(base, signedRsa, Set.of());

            assertRejected(verdict, SignedUploadRejection.REVOCATION_UNAVAILABLE);
        }
    }

    @Test
    @DisplayName("Garde des appels de révocation : ni chaîne fabriquée ni émetteur non fourni")
    void gateRefusesUnverifiedChains() {
        TrustedChainGate gate = new TrustedChainGate(pki.trustedFile());
        Signer impostor = EidTestPki.selfIssuedImpostor("http://10.0.0.1/ocsp", "http://10.0.0.1/crl");
        var signerToken = new eu.europa.esig.dss.model.x509.CertificateToken(rsaSigner.certificate());
        var intermediateToken = new eu.europa.esig.dss.model.x509.CertificateToken(pki.intermediate());
        var impostorToken = new eu.europa.esig.dss.model.x509.CertificateToken(impostor.certificate());
        var impostorCa = new eu.europa.esig.dss.model.x509.CertificateToken(impostor.chain().get(0));

        assertThat(gate.allowsOnlineRevocation(signerToken, intermediateToken)).isTrue();
        assertThat(gate.allowsOnlineRevocation(impostorToken, impostorCa)).isFalse();
        assertThat(gate.allowsOnlineRevocation(impostorToken, intermediateToken)).isFalse();
        assertThat(gate.isAnchor(new eu.europa.esig.dss.model.x509.CertificateToken(pki.root()))).isTrue();
        assertThat(gate.isAnchor(intermediateToken)).isFalse();
    }

    @Test
    @DisplayName("Le vérificateur ne lève rien, même sur un document remis illisible")
    void neverThrows() {
        SignedUploadVerdict verdict = verify("pas un pdf".getBytes(StandardCharsets.UTF_8), signedRsa);

        assertThat(verdict.isAccepted()).isFalse();
    }

    @Test
    @DisplayName("Trois signatures successives : chacune acceptée sur la précédente")
    void chainOfThree() {
        Signer third = pki.signer().name("Bob", "Dupont").keyType(KeyType.EC_P384).issue();
        byte[] second = EidTestPdfs.sign(signedRsa, ecSigner);
        byte[] last = EidTestPdfs.sign(second, third);

        SignedUploadVerdict verdict = verify(second, last);

        assertThat(verdict.isAccepted()).as("rejection %s", verdict.rejection()).isTrue();
        assertThat(verdict.evidence().signerName()).isEqualTo("Bob Dupont");
        assertThat(Instant.now()).isNotNull();
        assertThat(List.of(verdict.evidence().certificateIssuer())).isNotEmpty();
    }

    static byte[] encoded(Signer signer) {
        try {
            return signer.certificate().getEncoded();
        } catch (Exception ex) {
            throw new IllegalStateException(ex);
        }
    }

    static int indexOf(byte[] data, byte[] pattern, int from) {
        outer:
        for (int i = from; i <= data.length - pattern.length; i++) {
            for (int j = 0; j < pattern.length; j++) {
                if (data[i + j] != pattern[j]) {
                    continue outer;
                }
            }
            return i;
        }
        return -1;
    }
}
