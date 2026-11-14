package be.myrmacta.infrastructure.signature.eid;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.math.BigInteger;
import java.net.URI;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PrivateKey;
import java.security.SecureRandom;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.security.spec.ECGenParameterSpec;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicLong;
import org.bouncycastle.asn1.ASN1EncodableVector;
import org.bouncycastle.asn1.ASN1ObjectIdentifier;
import org.bouncycastle.asn1.DERSequence;
import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.asn1.x500.X500NameBuilder;
import org.bouncycastle.asn1.x500.style.BCStyle;
import org.bouncycastle.asn1.x509.AccessDescription;
import org.bouncycastle.asn1.x509.AuthorityInformationAccess;
import org.bouncycastle.asn1.x509.BasicConstraints;
import org.bouncycastle.asn1.x509.CRLDistPoint;
import org.bouncycastle.asn1.x509.CRLReason;
import org.bouncycastle.asn1.x509.DistributionPoint;
import org.bouncycastle.asn1.x509.DistributionPointName;
import org.bouncycastle.asn1.x509.ExtendedKeyUsage;
import org.bouncycastle.asn1.x509.Extension;
import org.bouncycastle.asn1.x509.GeneralName;
import org.bouncycastle.asn1.x509.GeneralNames;
import org.bouncycastle.asn1.x509.KeyPurposeId;
import org.bouncycastle.asn1.x509.KeyUsage;
import org.bouncycastle.asn1.x509.qualified.ETSIQCObjectIdentifiers;
import org.bouncycastle.asn1.x509.qualified.QCStatement;
import org.bouncycastle.cert.X509CRLHolder;
import org.bouncycastle.cert.X509CertificateHolder;
import org.bouncycastle.cert.X509v2CRLBuilder;
import org.bouncycastle.cert.X509v3CertificateBuilder;
import org.bouncycastle.cert.jcajce.JcaX509CertificateHolder;
import org.bouncycastle.cert.jcajce.JcaX509ExtensionUtils;
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder;
import org.bouncycastle.cert.ocsp.BasicOCSPResp;
import org.bouncycastle.cert.ocsp.BasicOCSPRespBuilder;
import org.bouncycastle.cert.ocsp.CertificateID;
import org.bouncycastle.cert.ocsp.CertificateStatus;
import org.bouncycastle.cert.ocsp.OCSPReq;
import org.bouncycastle.cert.ocsp.OCSPResp;
import org.bouncycastle.cert.ocsp.OCSPRespBuilder;
import org.bouncycastle.cert.ocsp.Req;
import org.bouncycastle.cert.ocsp.RespID;
import org.bouncycastle.cert.ocsp.RevokedStatus;
import org.bouncycastle.operator.ContentSigner;
import org.bouncycastle.operator.DigestCalculatorProvider;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;
import org.bouncycastle.operator.jcajce.JcaDigestCalculatorProviderBuilder;

/**
 * Hiérarchie de certificats de test pour la signature apportée par carte eID (TODO §138) : une racine, une
 * autorité intermédiaire « citoyens », des certificats de signature à la carte (usage de clé, déclarations
 * qualifiées, validité, adresses de révocation), un répondeur OCSP et des listes de révocation en mémoire
 * derrière {@link RevocationTransport}. Réutilisable par les tests d'intégration HTTP du lot suivant.
 *
 * <p>Les adresses de révocation et d'autorité ({@code AIA caIssuers}) pointent vers des noms réservés
 * ({@code .test}) : aucun test ne joint le réseau. Les sujets portent un faux numéro de registre national
 * ({@code SERIALNUMBER}), que le vérificateur ne doit jamais rendre (D-EID-NRN).
 */
public final class EidTestPki {

    /** Instant de référence des tests : les certificats sont valides autour de lui. */
    public static final Instant NOW = Instant.parse("2026-11-13T10:00:00Z");

    /** Horloge figée sur {@link #NOW}. */
    public static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);

    /** Faux numéro de registre national porté par les sujets des certificats de test. */
    public static final String FAKE_NATIONAL_NUMBER = "85010112345";

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final AtomicLong SERIALS = new AtomicLong(1000);

    /** Type de clé de la carte : anciennes cartes RSA 2048, cartes récentes ECDSA P-384. */
    public enum KeyType {
        RSA_2048("SHA256withRSA"),
        EC_P384("SHA384withECDSA");

        private final String jcaSignatureAlgorithm;

        KeyType(String jcaSignatureAlgorithm) {
            this.jcaSignatureAlgorithm = jcaSignatureAlgorithm;
        }

        public String jcaSignatureAlgorithm() {
            return jcaSignatureAlgorithm;
        }
    }

    /** Un certificat et sa clé privée. */
    public record Issued(X509Certificate certificate, PrivateKey key) {}

    /** Un signataire : son certificat, sa clé, la chaîne au-dessus de lui (intermédiaire puis racine). */
    public record Signer(X509Certificate certificate, PrivateKey key, KeyType keyType, List<X509Certificate> chain) {}

    private final String label;
    private final Issued root;
    private final Issued intermediate;
    private final String ocspUrl;
    private final String crlUrl;
    private final String rootCrlUrl;
    private final Map<BigInteger, Instant> revoked = new ConcurrentHashMap<>();

    private EidTestPki(String label) {
        this.label = label;
        String host = label.toLowerCase(java.util.Locale.ROOT).replaceAll("[^a-z0-9]", "");
        this.ocspUrl = "http://ocsp." + host + ".test/eidc";
        this.crlUrl = "http://crl." + host + ".test/eidc.crl";
        this.rootCrlUrl = "http://crl." + host + ".test/root.crl";
        KeyPair rootKeys = rsa(2048);
        X500Name rootName = new X500NameBuilder(BCStyle.INSTANCE)
                .addRDN(BCStyle.C, "BE").addRDN(BCStyle.CN, label + " Root CA").build();
        this.root = new Issued(caCertificate(rootName, rootKeys, rootName, rootKeys.getPrivate(), null, null), rootKeys.getPrivate());
        KeyPair intermediateKeys = rsa(2048);
        X500Name intermediateName = new X500NameBuilder(BCStyle.INSTANCE)
                .addRDN(BCStyle.C, "BE").addRDN(BCStyle.CN, label + " Citizen CA").addRDN(BCStyle.SERIALNUMBER, "202601").build();
        this.intermediate = new Issued(caCertificate(intermediateName, intermediateKeys, rootName, rootKeys.getPrivate(),
                root.certificate(), rootCrlUrl), intermediateKeys.getPrivate());
    }

    /** Nouvelle hiérarchie, clés fraîches. {@code label} distingue deux hiérarchies dans un même test. */
    public static EidTestPki create(String label) {
        return new EidTestPki(label);
    }

    public X509Certificate root() {
        return root.certificate();
    }

    public X509Certificate intermediate() {
        return intermediate.certificate();
    }

    /** Le fichier de l'exploitant : racine et autorité intermédiaire. */
    public List<X509Certificate> trustedFile() {
        return List.of(root.certificate(), intermediate.certificate());
    }

    public String ocspUrl() {
        return ocspUrl;
    }

    public String crlUrl() {
        return crlUrl;
    }

    public String rootCrlUrl() {
        return rootCrlUrl;
    }

    /** Signataire qualifié par défaut (nonRepudiation, QcCompliance, QcSSCD, QcType eSign, OCSP et CRL). */
    public Signer qualifiedSigner(KeyType keyType) {
        return signer().keyType(keyType).issue();
    }

    public SignerBuilder signer() {
        return new SignerBuilder();
    }

    /** Révoque un certificat émis par l'autorité intermédiaire (ou l'intermédiaire, révoqué par la racine). */
    public void revoke(X509Certificate certificate) {
        revoked.put(certificate.getSerialNumber(), NOW.minus(Duration.ofDays(2)));
    }

    /** Transport en mémoire qui répond pour cette hiérarchie (et d'autres, ajoutées par {@link FakeTransport#serve}). */
    public FakeTransport transport() {
        return new FakeTransport().serve(this);
    }

    /** Réponse OCSP signée par l'autorité émettrice pour la requête donnée. */
    byte[] ocspResponse(byte[] requestBytes, Instant producedAt) throws IOException {
        try {
            OCSPReq request = new OCSPReq(requestBytes);
            X509CertificateHolder issuerHolder = new JcaX509CertificateHolder(intermediate.certificate());
            BasicOCSPRespBuilder builder = new BasicOCSPRespBuilder(new RespID(issuerHolder.getSubject()));
            for (Req single : request.getRequestList()) {
                CertificateID id = single.getCertID();
                Instant revokedAt = revoked.get(id.getSerialNumber());
                CertificateStatus status = revokedAt == null
                        ? CertificateStatus.GOOD
                        : new RevokedStatus(Date.from(revokedAt), CRLReason.keyCompromise);
                builder.addResponse(id, status, Date.from(producedAt.minusSeconds(60)), Date.from(producedAt.plus(Duration.ofHours(12))));
            }
            ContentSigner signer = new JcaContentSignerBuilder("SHA256withRSA").build(intermediate.key());
            BasicOCSPResp basic = builder.build(signer, new X509CertificateHolder[] {issuerHolder}, Date.from(producedAt));
            OCSPResp response = new OCSPRespBuilder().build(OCSPRespBuilder.SUCCESSFUL, basic);
            return response.getEncoded();
        } catch (Exception ex) {
            throw new IOException("test OCSP responder failed", ex);
        }
    }

    /** Liste de révocation de l'autorité intermédiaire ({@code root} faux) ou de la racine ({@code root} vrai). */
    byte[] crl(boolean ofRoot, Instant thisUpdate) throws IOException {
        try {
            Issued issuer = ofRoot ? root : intermediate;
            X509CertificateHolder issuerHolder = new JcaX509CertificateHolder(issuer.certificate());
            X509v2CRLBuilder builder = new X509v2CRLBuilder(issuerHolder.getSubject(), Date.from(thisUpdate.minusSeconds(60)));
            builder.setNextUpdate(Date.from(thisUpdate.plus(Duration.ofDays(1))));
            for (Map.Entry<BigInteger, Instant> entry : revoked.entrySet()) {
                builder.addCRLEntry(entry.getKey(), Date.from(entry.getValue()), CRLReason.keyCompromise);
            }
            JcaX509ExtensionUtils utils = new JcaX509ExtensionUtils();
            builder.addExtension(Extension.authorityKeyIdentifier, false,
                    utils.createAuthorityKeyIdentifier(issuer.certificate()));
            builder.addExtension(Extension.cRLNumber, false, new org.bouncycastle.asn1.x509.CRLNumber(BigInteger.valueOf(SERIALS.incrementAndGet())));
            X509CRLHolder crl = builder.build(new JcaContentSignerBuilder("SHA256withRSA").build(issuer.key()));
            return crl.getEncoded();
        } catch (Exception ex) {
            throw new IOException("test CRL issuer failed", ex);
        }
    }

    /** Fabrique d'un certificat de signataire, émis par l'autorité intermédiaire. */
    public final class SignerBuilder {
        private KeyType keyType = KeyType.RSA_2048;
        private boolean nonRepudiation = true;
        private boolean digitalSignature = false;
        private boolean qcCompliance = true;
        private boolean qcSscd = true;
        private ASN1ObjectIdentifier qcType = ETSIQCObjectIdentifiers.id_etsi_qct_esign;
        private Instant notBefore = NOW.minus(Duration.ofDays(365));
        private Instant notAfter = NOW.plus(Duration.ofDays(3 * 365));
        private boolean ocsp = true;
        private boolean crl = true;
        private String givenName = "Alice Anne";
        private String surname = "Martin";

        public SignerBuilder keyType(KeyType value) {
            this.keyType = value;
            return this;
        }

        /** Certificat d'authentification de la carte : digitalSignature seul, sans non-répudiation. */
        public SignerBuilder authenticationOnly() {
            this.nonRepudiation = false;
            this.digitalSignature = true;
            return this;
        }

        public SignerBuilder withoutQcSscd() {
            this.qcSscd = false;
            return this;
        }

        public SignerBuilder withoutQcCompliance() {
            this.qcCompliance = false;
            return this;
        }

        /** QcType : {@code null} pour l'omettre. */
        public SignerBuilder qcType(ASN1ObjectIdentifier value) {
            this.qcType = value;
            return this;
        }

        public SignerBuilder validity(Instant from, Instant to) {
            this.notBefore = from;
            this.notAfter = to;
            return this;
        }

        public SignerBuilder withoutOcsp() {
            this.ocsp = false;
            return this;
        }

        public SignerBuilder withoutCrl() {
            this.crl = false;
            return this;
        }

        public SignerBuilder name(String given, String family) {
            this.givenName = given;
            this.surname = family;
            return this;
        }

        public Signer issue() {
            try {
                KeyPair keys = keyType == KeyType.RSA_2048 ? rsa(2048) : ecP384();
                X500NameBuilder subject = new X500NameBuilder(BCStyle.INSTANCE)
                        .addRDN(BCStyle.C, "BE")
                        .addRDN(BCStyle.CN, givenName.split(" ")[0] + " " + surname + " (Signature)");
                if (surname != null) {
                    subject.addRDN(BCStyle.SURNAME, surname);
                }
                if (givenName != null) {
                    subject.addRDN(BCStyle.GIVENNAME, givenName);
                }
                subject.addRDN(BCStyle.SERIALNUMBER, FAKE_NATIONAL_NUMBER);
                X509v3CertificateBuilder builder = new JcaX509v3CertificateBuilder(
                        intermediate.certificate(),
                        BigInteger.valueOf(SERIALS.incrementAndGet()),
                        Date.from(notBefore), Date.from(notAfter), subject.build(), keys.getPublic());
                JcaX509ExtensionUtils utils = new JcaX509ExtensionUtils();
                builder.addExtension(Extension.basicConstraints, true, new BasicConstraints(false));
                int usage = (nonRepudiation ? KeyUsage.nonRepudiation : 0) | (digitalSignature ? KeyUsage.digitalSignature : 0);
                builder.addExtension(Extension.keyUsage, true, new KeyUsage(usage));
                builder.addExtension(Extension.subjectKeyIdentifier, false, utils.createSubjectKeyIdentifier(keys.getPublic()));
                builder.addExtension(Extension.authorityKeyIdentifier, false,
                        utils.createAuthorityKeyIdentifier(intermediate.certificate()));
                List<AccessDescription> access = new ArrayList<>();
                // caIssuers vers une adresse de métadonnées : jamais suivie (AIA désactivée, D-EID-TRUST).
                access.add(new AccessDescription(AccessDescription.id_ad_caIssuers,
                        new GeneralName(GeneralName.uniformResourceIdentifier, "http://169.254.169.254/latest/ca.crt")));
                if (ocsp) {
                    access.add(new AccessDescription(AccessDescription.id_ad_ocsp,
                            new GeneralName(GeneralName.uniformResourceIdentifier, ocspUrl)));
                }
                builder.addExtension(Extension.authorityInfoAccess, false,
                        new AuthorityInformationAccess(access.toArray(AccessDescription[]::new)));
                if (crl) {
                    builder.addExtension(Extension.cRLDistributionPoints, false, distributionPoint(crlUrl));
                }
                ASN1EncodableVector statements = new ASN1EncodableVector();
                if (qcCompliance) {
                    statements.add(new QCStatement(ETSIQCObjectIdentifiers.id_etsi_qcs_QcCompliance));
                }
                if (qcSscd) {
                    statements.add(new QCStatement(ETSIQCObjectIdentifiers.id_etsi_qcs_QcSSCD));
                }
                if (qcType != null) {
                    statements.add(new QCStatement(ETSIQCObjectIdentifiers.id_etsi_qcs_QcType, new DERSequence(qcType)));
                }
                if (statements.size() > 0) {
                    builder.addExtension(Extension.qCStatements, false, new DERSequence(statements));
                }
                X509Certificate certificate = toX509(builder.build(
                        new JcaContentSignerBuilder("SHA256withRSA").build(intermediate.key())));
                return new Signer(certificate, keys.getPrivate(), keyType, List.of(intermediate.certificate(), root.certificate()));
            } catch (Exception ex) {
                throw new IllegalStateException("cannot issue test signer certificate", ex);
            }
        }
    }

    /**
     * Transport de révocation en mémoire : répond OCSP et CRL pour les hiérarchies servies, compte chaque appel et
     * garde les adresses demandées. {@link #unavailable()} simule un répondeur injoignable.
     */
    public static final class FakeTransport implements RevocationTransport {
        private final Map<String, EidTestPki> ocsp = new HashMap<>();
        private final Map<String, EidTestPki> intermediateCrl = new HashMap<>();
        private final Map<String, EidTestPki> rootCrl = new HashMap<>();
        private final List<URI> calls = new CopyOnWriteArrayList<>();
        private volatile boolean available = true;
        private volatile boolean ocspAvailable = true;
        private volatile Clock clock = CLOCK;

        public FakeTransport serve(EidTestPki pki) {
            ocsp.put(pki.ocspUrl, pki);
            intermediateCrl.put(pki.crlUrl, pki);
            rootCrl.put(pki.rootCrlUrl, pki);
            return this;
        }

        /** Plus aucune réponse : chaque appel lève une {@link IOException}. */
        public FakeTransport unavailable() {
            this.available = false;
            return this;
        }

        /** OCSP injoignable, CRL disponible (repli). */
        public FakeTransport ocspUnavailable() {
            this.ocspAvailable = false;
            return this;
        }

        /** Horloge des réponses (heure de production). */
        public FakeTransport clock(Clock value) {
            this.clock = Objects.requireNonNull(value);
            return this;
        }

        public int callCount() {
            return calls.size();
        }

        public List<URI> calls() {
            return List.copyOf(calls);
        }

        @Override
        public byte[] postOcsp(URI responder, byte[] request) throws IOException {
            calls.add(responder);
            if (!available || !ocspAvailable) {
                throw new IOException("unreachable");
            }
            EidTestPki pki = ocsp.get(responder.toString());
            if (pki == null) {
                throw new IOException("unknown responder");
            }
            return pki.ocspResponse(request, clock.instant());
        }

        @Override
        public byte[] getCrl(URI distributionPoint) throws IOException {
            calls.add(distributionPoint);
            if (!available) {
                throw new IOException("unreachable");
            }
            EidTestPki pki = intermediateCrl.get(distributionPoint.toString());
            if (pki != null) {
                return pki.crl(false, clock.instant());
            }
            pki = rootCrl.get(distributionPoint.toString());
            if (pki != null) {
                return pki.crl(true, clock.instant());
            }
            throw new IOException("unknown distribution point");
        }
    }

    // --- fabriques bas niveau ---

    private X509Certificate caCertificate(X500Name subject, KeyPair keys, X500Name issuerName, PrivateKey issuerKey,
            X509Certificate issuerCertificate, String crlDistributionPoint) {
        try {
            X509v3CertificateBuilder builder = new JcaX509v3CertificateBuilder(issuerName,
                    BigInteger.valueOf(SERIALS.incrementAndGet()),
                    Date.from(NOW.minus(Duration.ofDays(5 * 365))), Date.from(NOW.plus(Duration.ofDays(10 * 365))),
                    subject, keys.getPublic());
            JcaX509ExtensionUtils utils = new JcaX509ExtensionUtils();
            builder.addExtension(Extension.basicConstraints, true, new BasicConstraints(issuerCertificate == null ? 1 : 0));
            builder.addExtension(Extension.keyUsage, true, new KeyUsage(KeyUsage.keyCertSign | KeyUsage.cRLSign));
            builder.addExtension(Extension.subjectKeyIdentifier, false, utils.createSubjectKeyIdentifier(keys.getPublic()));
            if (issuerCertificate != null) {
                builder.addExtension(Extension.authorityKeyIdentifier, false, utils.createAuthorityKeyIdentifier(issuerCertificate));
            }
            if (crlDistributionPoint != null) {
                builder.addExtension(Extension.cRLDistributionPoints, false, distributionPoint(crlDistributionPoint));
            }
            return toX509(builder.build(new JcaContentSignerBuilder("SHA256withRSA").build(issuerKey)));
        } catch (Exception ex) {
            throw new IllegalStateException("cannot issue test CA certificate", ex);
        }
    }

    /**
     * Autorité d'horodatage de test, sous sa propre racine (absente du fichier de l'exploitant, comme une TSA
     * publique qui n'est pas une autorité eID).
     */
    public static Issued timestampAuthority() {
        try {
            KeyPair rootKeys = rsa(2048);
            X500Name rootName = new X500Name("C=BE, CN=Test TSA Root");
            X509v3CertificateBuilder rootBuilder = new JcaX509v3CertificateBuilder(rootName, BigInteger.valueOf(SERIALS.incrementAndGet()),
                    Date.from(NOW.minus(Duration.ofDays(3650))), Date.from(NOW.plus(Duration.ofDays(3650))), rootName, rootKeys.getPublic());
            rootBuilder.addExtension(Extension.basicConstraints, true, new BasicConstraints(0));
            rootBuilder.addExtension(Extension.keyUsage, true, new KeyUsage(KeyUsage.keyCertSign | KeyUsage.cRLSign));
            X509Certificate tsaRoot = toX509(rootBuilder.build(new JcaContentSignerBuilder("SHA256withRSA").build(rootKeys.getPrivate())));
            KeyPair keys = rsa(2048);
            X509v3CertificateBuilder builder = new JcaX509v3CertificateBuilder(tsaRoot, BigInteger.valueOf(SERIALS.incrementAndGet()),
                    Date.from(NOW.minus(Duration.ofDays(365))), Date.from(NOW.plus(Duration.ofDays(3650))),
                    new X500Name("C=BE, CN=Test TSA"), keys.getPublic());
            builder.addExtension(Extension.basicConstraints, true, new BasicConstraints(false));
            builder.addExtension(Extension.keyUsage, true, new KeyUsage(KeyUsage.digitalSignature | KeyUsage.nonRepudiation));
            builder.addExtension(Extension.extendedKeyUsage, true, new ExtendedKeyUsage(KeyPurposeId.id_kp_timeStamping));
            X509Certificate tsa = toX509(builder.build(new JcaContentSignerBuilder("SHA256withRSA").build(rootKeys.getPrivate())));
            return new Issued(tsa, keys.getPrivate());
        } catch (Exception ex) {
            throw new IllegalStateException("cannot issue test TSA certificate", ex);
        }
    }

    /**
     * Certificat auto-signé qui imite un signataire qualifié (mêmes extensions, adresses de révocation fournies par
     * l'attaquant) : une chaîne inconnue.
     */
    public static Signer selfIssuedImpostor(String ocspUrl, String crlUrl) {
        try {
            KeyPair caKeys = rsa(2048);
            X500Name caName = new X500Name("C=BE, CN=Impostor CA");
            X509v3CertificateBuilder caBuilder = new JcaX509v3CertificateBuilder(caName, BigInteger.valueOf(SERIALS.incrementAndGet()),
                    Date.from(NOW.minus(Duration.ofDays(30))), Date.from(NOW.plus(Duration.ofDays(3650))), caName, caKeys.getPublic());
            caBuilder.addExtension(Extension.basicConstraints, true, new BasicConstraints(0));
            caBuilder.addExtension(Extension.keyUsage, true, new KeyUsage(KeyUsage.keyCertSign | KeyUsage.cRLSign));
            caBuilder.addExtension(Extension.cRLDistributionPoints, false, distributionPoint(crlUrl));
            X509Certificate ca = toX509(caBuilder.build(new JcaContentSignerBuilder("SHA256withRSA").build(caKeys.getPrivate())));
            KeyPair keys = rsa(2048);
            X509v3CertificateBuilder builder = new JcaX509v3CertificateBuilder(ca, BigInteger.valueOf(SERIALS.incrementAndGet()),
                    Date.from(NOW.minus(Duration.ofDays(30))), Date.from(NOW.plus(Duration.ofDays(365))),
                    new X500Name("C=BE, CN=Mallory Doe (Signature), SURNAME=Doe, GIVENNAME=Mallory"), keys.getPublic());
            builder.addExtension(Extension.keyUsage, true, new KeyUsage(KeyUsage.nonRepudiation));
            builder.addExtension(Extension.authorityInfoAccess, false, new AuthorityInformationAccess(new AccessDescription[] {
                new AccessDescription(AccessDescription.id_ad_ocsp, new GeneralName(GeneralName.uniformResourceIdentifier, ocspUrl)),
                new AccessDescription(AccessDescription.id_ad_caIssuers,
                        new GeneralName(GeneralName.uniformResourceIdentifier, "http://10.0.0.1/ca.crt"))}));
            builder.addExtension(Extension.cRLDistributionPoints, false, distributionPoint(crlUrl));
            ASN1EncodableVector statements = new ASN1EncodableVector();
            statements.add(new QCStatement(ETSIQCObjectIdentifiers.id_etsi_qcs_QcCompliance));
            statements.add(new QCStatement(ETSIQCObjectIdentifiers.id_etsi_qcs_QcSSCD));
            builder.addExtension(Extension.qCStatements, false, new DERSequence(statements));
            X509Certificate certificate = toX509(builder.build(new JcaContentSignerBuilder("SHA256withRSA").build(caKeys.getPrivate())));
            return new Signer(certificate, keys.getPrivate(), KeyType.RSA_2048, List.of(ca));
        } catch (Exception ex) {
            throw new IllegalStateException("cannot issue impostor certificate", ex);
        }
    }

    private static CRLDistPoint distributionPoint(String url) {
        DistributionPointName name = new DistributionPointName(
                new GeneralNames(new GeneralName(GeneralName.uniformResourceIdentifier, url)));
        return new CRLDistPoint(new DistributionPoint[] {new DistributionPoint(name, null, null)});
    }

    static KeyPair rsa(int bits) {
        try {
            KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
            generator.initialize(bits, RANDOM);
            return generator.generateKeyPair();
        } catch (Exception ex) {
            throw new IllegalStateException(ex);
        }
    }

    static KeyPair ecP384() {
        try {
            KeyPairGenerator generator = KeyPairGenerator.getInstance("EC");
            generator.initialize(new ECGenParameterSpec("secp384r1"), RANDOM);
            return generator.generateKeyPair();
        } catch (Exception ex) {
            throw new IllegalStateException(ex);
        }
    }

    static X509Certificate toX509(X509CertificateHolder holder) throws CertificateException, IOException {
        return (X509Certificate) CertificateFactory.getInstance("X.509")
                .generateCertificate(new ByteArrayInputStream(holder.getEncoded()));
    }

    static DigestCalculatorProvider digests() {
        try {
            return new JcaDigestCalculatorProviderBuilder().build();
        } catch (Exception ex) {
            throw new IllegalStateException(ex);
        }
    }

    @Override
    public String toString() {
        return "EidTestPki[" + label + "]";
    }
}
