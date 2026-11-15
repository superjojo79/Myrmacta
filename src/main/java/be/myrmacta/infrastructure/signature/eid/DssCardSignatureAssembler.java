package be.myrmacta.infrastructure.signature.eid;

import be.myrmacta.domain.signature.port.out.SignaturePort.CardSignatureDigest;
import be.myrmacta.domain.signature.port.out.SignaturePort.CardSignatureInput;
import eu.europa.esig.dss.alert.SilentOnStatusAlert;
import eu.europa.esig.dss.enumerations.SignatureAlgorithm;
import eu.europa.esig.dss.enumerations.SignatureLevel;
import eu.europa.esig.dss.model.DSSDocument;
import eu.europa.esig.dss.model.InMemoryDocument;
import eu.europa.esig.dss.model.SignatureValue;
import eu.europa.esig.dss.model.ToBeSigned;
import eu.europa.esig.dss.model.x509.CertificateToken;
import eu.europa.esig.dss.pades.PAdESSignatureParameters;
import eu.europa.esig.dss.pades.signature.PAdESService;
import eu.europa.esig.dss.spi.DSSASN1Utils;
import eu.europa.esig.dss.spi.validation.CommonCertificateVerifier;
import java.io.IOException;
import java.io.InputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.PublicKey;
import java.security.cert.X509Certificate;
import java.security.interfaces.ECPublicKey;
import java.security.interfaces.RSAPublicKey;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Objects;
import java.util.TimeZone;

/**
 * Signature PAdES en deux temps, la clé restant sur la carte (TODO §138, D-EID-BRIDGE), par DSS 6.5.1 : signature
 * PAdES-BASELINE-B invisible (aucune apparence, rien n'est ajouté au contenu des pages), sous-filtre
 * {@code ETSI.CAdES.detached}, par mise à jour incrémentale du document remis. Le résultat n'est pas tenu pour valide :
 * il passe ensuite par {@link DssSignedPdfVerifier}, comme un téléversement.
 *
 * <p><b>Sans état.</b> {@link #prepare} et {@link #assemble} reconstruisent les mêmes paramètres à partir des mêmes
 * entrées et retombent sur les mêmes octets à signer. Ce qui doit être identique entre les deux appels : le document
 * remis, le certificat, la chaîne et l'heure de signature (tronquée à la seconde, fuseau UTC). Le reste est
 * déterministe dans DSS 6.5.1 : l'identifiant du document PDF ({@code /ID}) est dérivé de l'heure de signature, du
 * certificat et de la taille du document ({@code PdfBoxDocumentReader.generateDocumentId}) ; le nom du champ de
 * signature est attribué d'après les champs existants ; les attributs signés CMS ne dépendent que de l'heure, du
 * certificat et du condensé du document ; le cache {@code PdfSignatureCache} vit dans les paramètres, donc il est
 * neuf à chaque appel. Aucun aléa n'intervient hors des documents chiffrés, refusés en amont.
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

    private record Prepared(DSSDocument document, PAdESSignatureParameters parameters, SignatureAlgorithm algorithm) {}

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
        return new Prepared(new InMemoryDocument(input.baseDocument()), parameters, algorithm);
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
        return new PAdESService(verifier);
    }
}
