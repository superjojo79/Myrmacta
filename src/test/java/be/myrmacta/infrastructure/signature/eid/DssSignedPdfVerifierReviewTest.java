package be.myrmacta.infrastructure.signature.eid;

import static org.assertj.core.api.Assertions.assertThat;

import be.myrmacta.domain.signature.model.SignedUploadRejection;
import be.myrmacta.domain.signature.model.SignedUploadVerdict;
import be.myrmacta.infrastructure.signature.eid.EidTestPdfs.SignOptions;
import be.myrmacta.infrastructure.signature.eid.EidTestPki.FakeTransport;
import be.myrmacta.infrastructure.signature.eid.EidTestPki.KeyType;
import be.myrmacta.infrastructure.signature.eid.EidTestPki.Signer;
import eu.europa.esig.dss.enumerations.SignatureLevel;
import eu.europa.esig.dss.model.x509.CertificateToken;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Set;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * Scénarios de la revue indépendante de clôture (issue #10) : chacun reproduit un cas promis comme refusé.
 */
class DssSignedPdfVerifierReviewTest {

    private static EidTestPki pki;
    private static Signer signer;
    private static byte[] base;

    @BeforeAll
    static void setUp() {
        pki = EidTestPki.create("Review Test");
        signer = pki.qualifiedSigner(KeyType.RSA_2048);
        base = EidTestPdfs.basePdf();
    }

    private static SignedUploadVerdict verify(byte[] previous, byte[] uploaded, FakeTransport transport) {
        return new DssSignedPdfVerifier(pki.trustedFile(), EidTestPki.CLOCK, transport).verify(previous, uploaded, Set.of());
    }

    private static SignedUploadVerdict verify(byte[] previous, byte[] uploaded) {
        return verify(previous, uploaded, pki.transport());
    }

    private static void assertRejected(SignedUploadVerdict verdict, SignedUploadRejection expected) {
        assertThat(verdict.isAccepted()).as("accepted").isFalse();
        assertThat(verdict.rejection()).isEqualTo(expected);
    }

    private static void assertAccepted(SignedUploadVerdict verdict) {
        assertThat(verdict.isAccepted()).as("rejection %s", verdict.rejection()).isTrue();
    }

    @Nested
    @DisplayName("HAUT 1 — redéfinition d'objets du document remis")
    class Redefinition {

        private final byte[] twelvePages = EidTestPdfs.basePdfSharedFont(12);

        @Test
        @DisplayName("12 pages, police partagée remplacée sur la page 5 dans la révision de la signature : refusé")
        void sharedFontReplacedInSignatureRevision() {
            byte[] uploaded = EidTestPdfs.signWithPdfBox(twelvePages, signer, "adbe.pkcs7.detached", EidTestPdfs.replacePageFont(4));

            assertRejected(verify(twelvePages, uploaded), SignedUploadRejection.CONTENT_MODIFIED);
        }

        @Test
        @DisplayName("12 pages, police partagée remplacée par une révision ajoutée après la signature : refusé")
        void sharedFontReplacedAfterSignature() {
            byte[] signed = EidTestPdfs.sign(twelvePages, signer);
            byte[] uploaded = EidTestPdfs.incrementalUpdate(signed, EidTestPdfs.replacePageFont(4));

            assertRejected(verify(twelvePages, uploaded), SignedUploadRejection.CONTENT_MODIFIED);
        }

        @Test
        @DisplayName("12 pages, signature honnête : acceptée (au-delà de la borne de comparaison visuelle)")
        void honestSignatureOnTwelvePages() {
            assertAccepted(verify(twelvePages, EidTestPdfs.sign(twelvePages, signer)));
        }
    }

    @Nested
    @DisplayName("HAUT 2 — signataire des données de révocation")
    class RevocationSigner {

        @Test
        @DisplayName("Carte révoquée, OCSP « good » embarqué signé par une carte sœur : CERTIFICATE_REVOKED")
        void embeddedOcspSignedBySiblingCard() {
            EidTestPki local = EidTestPki.create("Sibling Test");
            Signer revokedCard = local.qualifiedSigner(KeyType.RSA_2048);
            Signer sibling = local.qualifiedSigner(KeyType.RSA_2048);
            local.revoke(revokedCard.certificate());
            FakeTransport forged = local.transport().ocspSignedBy(EidTestPki.asIssued(sibling));
            byte[] uploaded = EidTestPdfs.sign(base, revokedCard, SignOptions.defaults()
                    .extended(SignatureLevel.PAdES_BASELINE_LT, local).embeddingRevocationFrom(forged));

            SignedUploadVerdict verdict = new DssSignedPdfVerifier(local.trustedFile(), EidTestPki.CLOCK, local.transport())
                    .verify(base, uploaded, Set.of());

            assertRejected(verdict, SignedUploadRejection.CERTIFICATE_REVOKED);
        }

        @Test
        @DisplayName("Répondeur en ligne qui signe par une carte sœur : sa réponse n'est pas crue")
        void onlineOcspSignedBySiblingCardIsIgnored() {
            Signer sibling = pki.qualifiedSigner(KeyType.RSA_2048);
            FakeTransport forged = pki.transport().ocspSignedBy(EidTestPki.asIssued(sibling));
            Signer withoutCrl = pki.signer().withoutCrl().issue();

            assertRejected(verify(base, EidTestPdfs.sign(base, withoutCrl), forged), SignedUploadRejection.REVOCATION_UNAVAILABLE);
        }

        @Test
        @DisplayName("OCSP embarqué légitime (PAdES-LT) : accepté")
        void legitimateEmbeddedOcsp() {
            byte[] uploaded = EidTestPdfs.sign(base, signer, SignOptions.defaults().extended(SignatureLevel.PAdES_BASELINE_LT, pki));

            assertAccepted(verify(base, uploaded));
        }

        @Test
        @DisplayName("CRL embarquée (OCSP injoignable au moment de l'extension) : acceptée")
        void embeddedCrl() {
            byte[] uploaded = EidTestPdfs.sign(base, signer, SignOptions.defaults()
                    .extended(SignatureLevel.PAdES_BASELINE_LT, pki).embeddingRevocationFrom(pki.transport().ocspUnavailable()));

            assertAccepted(verify(base, uploaded));
        }

        @Test
        @DisplayName("Choix retenu : la révocation embarquée ne suffit pas, révocation en ligne injoignable : REVOCATION_UNAVAILABLE")
        void embeddedRevocationAloneIsNotEnough() {
            byte[] uploaded = EidTestPdfs.sign(base, signer, SignOptions.defaults().extended(SignatureLevel.PAdES_BASELINE_LT, pki));

            assertRejected(verify(base, uploaded, pki.transport().unavailable()), SignedUploadRejection.REVOCATION_UNAVAILABLE);
        }
    }

    @Nested
    @DisplayName("HAUT 3 — apparence de la signature ajoutée")
    class Appearance {

        @Test
        @DisplayName("Widget de signature pleine page avec apparence opaque : CONTENT_MODIFIED")
        void fullPageWidget() {
            byte[] uploaded = EidTestPdfs.signWithPdfBox(base, signer, "adbe.pkcs7.detached", null, EidTestPdfs.FULL_PAGE_APPEARANCE);

            assertRejected(verify(base, uploaded), SignedUploadRejection.CONTENT_MODIFIED);
        }

        @ParameterizedTest
        @ValueSource(strings = {"Stamp", "FreeText", "FileAttachment"})
        @DisplayName("Annotation d'un autre sous-type portant /FT /Sig : CONTENT_MODIFIED")
        void otherSubtypeCarryingSignatureField(String subtype) {
            byte[] uploaded = EidTestPdfs.signWithPdfBox(base, signer, "adbe.pkcs7.detached", null, EidTestPdfs.subtype(subtype));

            assertRejected(verify(base, uploaded), SignedUploadRejection.CONTENT_MODIFIED);
        }

        @Test
        @DisplayName("Petite signature visible (DSS) : acceptée")
        void smallVisibleSignature() {
            assertAccepted(verify(base, EidTestPdfs.sign(base, signer, SignOptions.defaults().withVisibleField())));
        }
    }

    @Nested
    @DisplayName("MOYEN 4 — fraîcheur des CRL d'autorité")
    class CrlFreshness {

        @Test
        @DisplayName("CRL de la racine publiée il y a deux mois et encore valable : acceptée")
        void oldButValidAuthorityCrl() {
            FakeTransport transport = pki.transport()
                    .rootCrl(EidTestPki.NOW.minus(Duration.ofDays(60)), EidTestPki.NOW.plus(Duration.ofDays(120)));

            assertAccepted(verify(base, EidTestPdfs.sign(base, signer), transport));
        }

        @Test
        @DisplayName("CRL de la racine dont nextUpdate est passé : REVOCATION_UNAVAILABLE")
        void expiredAuthorityCrl() {
            FakeTransport transport = pki.transport()
                    .rootCrl(EidTestPki.NOW.minus(Duration.ofDays(200)), EidTestPki.NOW.minus(Duration.ofDays(1)));

            assertRejected(verify(base, EidTestPdfs.sign(base, signer), transport), SignedUploadRejection.REVOCATION_UNAVAILABLE);
        }
    }

    @Nested
    @DisplayName("MOYEN 5 — l'émetteur doit être une autorité")
    class IssuerMustBeAuthority {

        @Test
        @DisplayName("Certificat fabriqué avec la clé d'une carte : CERTIFICATE_UNTRUSTED, aucun appel vers l'adresse choisie")
        void certificateForgedWithACardKey() {
            Signer forged = EidTestPki.forgedBelow(signer, "http://ocsp.attacker.test/x");
            FakeTransport transport = pki.transport();

            SignedUploadVerdict verdict = verify(base, EidTestPdfs.sign(base, forged), transport);

            assertRejected(verdict, SignedUploadRejection.CERTIFICATE_UNTRUSTED);
            assertThat(transport.calls()).noneMatch(uri -> "ocsp.attacker.test".equals(uri.getHost()));
        }

        @Test
        @DisplayName("Garde : un certificat de signataire n'est pas un émetteur admis")
        void gateRefusesNonAuthorityIssuer() {
            Signer forged = EidTestPki.forgedBelow(signer, "http://ocsp.attacker.test/x");
            TrustedChainGate gate = new TrustedChainGate(pki.trustedFile());

            assertThat(gate.allowsOnlineRevocation(new CertificateToken(forged.certificate()),
                    new CertificateToken(signer.certificate()))).isFalse();
            assertThat(gate.allowsOnlineRevocation(new CertificateToken(signer.certificate()),
                    new CertificateToken(pki.intermediate()))).isTrue();
        }
    }

    @Nested
    @DisplayName("BAS")
    class Low {

        @Test
        @DisplayName("En-tête PDF mais fichier qui ne prolonge pas le document remis : DOCUMENT_MISMATCH avant toute analyse")
        void prefixComparedBeforeParsing() {
            assertRejected(verify(base, "%PDF-1.7 rien d'autre".getBytes(StandardCharsets.US_ASCII)),
                    SignedUploadRejection.DOCUMENT_MISMATCH);
        }

        @Test
        @DisplayName("Prolongement d'un document remis chiffré : NOT_A_PDF")
        void encryptedExtension() {
            byte[] encrypted = EidTestPdfs.encryptedPdf();
            byte[] uploaded = (new String(encrypted, StandardCharsets.ISO_8859_1) + "\n% suite\n%%EOF\n")
                    .getBytes(StandardCharsets.ISO_8859_1);

            assertRejected(verify(encrypted, uploaded), SignedUploadRejection.NOT_A_PDF);
        }

        @Test
        @DisplayName("Police existante de /AcroForm /DR remplacée dans la révision de la signature : CONTENT_MODIFIED")
        void acroFormResourceModified() {
            byte[] withForm = EidTestPdfs.basePdfWithAcroFormResources();
            byte[] uploaded = EidTestPdfs.signWithPdfBox(withForm, signer, "adbe.pkcs7.detached", EidTestPdfs.REPLACE_ACROFORM_FONT);

            assertRejected(verify(withForm, uploaded), SignedUploadRejection.CONTENT_MODIFIED);
        }

        @Test
        @DisplayName("Signature honnête sur un document à /AcroForm /DR : acceptée")
        void honestSignatureWithAcroFormResources() {
            byte[] withForm = EidTestPdfs.basePdfWithAcroFormResources();

            assertAccepted(verify(withForm, EidTestPdfs.signWithPdfBox(withForm, signer, "adbe.pkcs7.detached", null)));
        }
    }
}
