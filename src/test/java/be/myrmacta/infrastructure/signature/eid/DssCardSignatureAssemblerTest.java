package be.myrmacta.infrastructure.signature.eid;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import be.myrmacta.domain.signature.model.SignedUploadRejection;
import be.myrmacta.domain.signature.model.SignedUploadVerdict;
import be.myrmacta.domain.signature.port.out.SignaturePort.CardSignatureDigest;
import be.myrmacta.domain.signature.port.out.SignaturePort.CardSignatureInput;
import be.myrmacta.infrastructure.signature.eid.EidTestPki.KeyType;
import be.myrmacta.infrastructure.signature.eid.EidTestPki.Signer;
import eu.europa.esig.dss.spi.DSSASN1Utils;
import java.security.Signature;
import java.security.cert.X509Certificate;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
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
        return new CardSignatureInput(document, encoded(signer.certificate()), chain, time);
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
        CardSignatureInput input = input(document, signer, chain, SIGNING_TIME);
        CardSignatureDigest digest = assembler().prepare(input);
        // Seconde requête : nouvelle instance, entrées reconstruites.
        CardSignatureInput again = input(document.clone(), signer, chain, SIGNING_TIME);
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
        assertThatThrownBy(() -> assembler().prepare(new CardSignatureInput(base, new byte[] {1, 2, 3}, List.of(), SIGNING_TIME)))
                .isInstanceOf(IllegalArgumentException.class).hasMessage("certificate is not readable");
        assertThatThrownBy(() -> assembler().prepare(new CardSignatureInput(base, new byte[64 * 1024], List.of(), SIGNING_TIME)))
                .isInstanceOf(IllegalArgumentException.class);
        Signer signer = pki.qualifiedSigner(KeyType.EC_P384);
        CardSignatureInput input = input(base, signer, List.of(), SIGNING_TIME);
        assertThatThrownBy(() -> assembler().assemble(input, new byte[17])).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> assembler().assemble(input, new byte[4096])).isInstanceOf(IllegalArgumentException.class);
        List<byte[]> tooLong = java.util.Collections.nCopies(6, encoded(pki.intermediate()));
        assertThatThrownBy(() -> assembler().prepare(input(base, signer, tooLong, SIGNING_TIME)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private static byte[] encoded(X509Certificate certificate) {
        try {
            return certificate.getEncoded();
        } catch (Exception ex) {
            throw new IllegalStateException(ex);
        }
    }
}
