package be.myrmacta.infrastructure.signature.eid;

import be.myrmacta.domain.signature.model.SignedUploadEvidence;
import be.myrmacta.domain.signature.model.SignedUploadRejection;
import be.myrmacta.domain.signature.model.SignedUploadVerdict;
import eu.europa.esig.dss.diagnostic.CertificateRevocationWrapper;
import eu.europa.esig.dss.diagnostic.CertificateWrapper;
import eu.europa.esig.dss.diagnostic.DiagnosticData;
import eu.europa.esig.dss.diagnostic.PDFRevisionWrapper;
import eu.europa.esig.dss.diagnostic.SignatureWrapper;
import eu.europa.esig.dss.diagnostic.jaxb.XmlDigestMatcher;
import eu.europa.esig.dss.enumerations.CertificationPermission;
import eu.europa.esig.dss.enumerations.DigestAlgorithm;
import eu.europa.esig.dss.enumerations.EncryptionAlgorithm;
import eu.europa.esig.dss.enumerations.Indication;
import eu.europa.esig.dss.enumerations.KeyUsageBit;
import eu.europa.esig.dss.enumerations.RevocationOrigin;
import eu.europa.esig.dss.enumerations.RevocationType;
import eu.europa.esig.dss.enumerations.SubIndication;
import eu.europa.esig.dss.model.InMemoryDocument;
import eu.europa.esig.dss.model.policy.ValidationPolicy;
import eu.europa.esig.dss.pades.validation.PDFDocumentValidator;
import eu.europa.esig.dss.pdf.AnnotationBox;
import eu.europa.esig.dss.pdf.PdfAnnotation;
import eu.europa.esig.dss.pdf.PdfDocumentReader;
import eu.europa.esig.dss.pdf.modifications.DefaultPdfDifferencesFinder;
import eu.europa.esig.dss.pdf.modifications.DefaultPdfObjectModificationsFinder;
import eu.europa.esig.dss.pdf.modifications.PdfDifferencesFinder;
import eu.europa.esig.dss.pdf.modifications.PdfModification;
import eu.europa.esig.dss.pdf.pdfbox.PdfBoxDefaultObjectFactory;
import eu.europa.esig.dss.pdf.pdfbox.PdfBoxDocumentReader;
import eu.europa.esig.dss.spi.validation.CommonCertificateVerifier;
import eu.europa.esig.dss.spi.validation.RevocationDataVerifier;
import eu.europa.esig.dss.validation.policy.ValidationPolicyLoader;
import eu.europa.esig.dss.validation.reports.Reports;
import java.io.IOException;
import java.math.BigInteger;
import java.security.cert.X509Certificate;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.Date;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Vérification d'un PDF signé hors de Myrmacta avec une carte eID, par DSS 6.5.1 (TODO §138, D-EID-VALIDATION).
 *
 * <p>Les contrôles vont du moins cher au plus cher et le premier qui échoue donne le motif :
 * <ol>
 *   <li>{@code NOT_A_PDF}, {@code DOCUMENT_MISMATCH}, {@code NO_NEW_SIGNATURE}, {@code SEVERAL_SIGNATURES} et la
 *       couverture du {@code /ByteRange} : {@link UploadStructure} (octets et PDFBox) ;
 *   <li>{@code CONTENT_MODIFIED} : différences d'objets de DSS du document remis à la révision signée, puis de la
 *       révision signée au fichier final, filtrées par {@link SignatureModificationPolicy} ; pages ajoutées ou
 *       retirées, chevauchement d'annotations nouveau, différence visuelle (rendu des pages, document remis contre
 *       fichier final, nouvelles annotations de signature masquées) ;
 *   <li>{@code SIGNATURE_INVALID} à {@code CERTIFICATE_REVOKED} / {@code REVOCATION_UNAVAILABLE} : une validation
 *       DSS (politique par défaut), lue dans les données de diagnostic plutôt que dans la seule indication, puis
 *       recoupée avec l'indication : un verdict accepté exige que tout soit établi et que DSS dise {@code
 *       TOTAL_PASSED}.
 * </ol>
 *
 * <p>Réseau : seule la révocation sort, par {@link RevocationTransport}, et seulement pour une chaîne vérifiée par
 * {@link TrustedChainGate} (aucune source AIA, {@code checkRevocationForUntrustedChains} faux, et garde propre en plus).
 * Les journaux ne portent aucune donnée du certificat ni du document.
 *
 * <p>Sans état, sûr entre fils : chaque appel bâtit ses propres sources DSS.
 */
public final class DssSignedPdfVerifier implements SignedPdfVerifier {

    private static final Logger log = LoggerFactory.getLogger(DssSignedPdfVerifier.class);

    private static final Set<DigestAlgorithm> ACCEPTED_DIGESTS =
            EnumSet.of(DigestAlgorithm.SHA256, DigestAlgorithm.SHA384, DigestAlgorithm.SHA512);
    private static final Set<SubIndication> CHAIN_FAILURES = EnumSet.of(
            SubIndication.NO_CERTIFICATE_CHAIN_FOUND, SubIndication.NO_CERTIFICATE_CHAIN_FOUND_NO_POE,
            SubIndication.CHAIN_CONSTRAINTS_FAILURE, SubIndication.CERTIFICATE_CHAIN_GENERAL_FAILURE,
            SubIndication.OUT_OF_BOUNDS_NO_POE, SubIndication.OUT_OF_BOUNDS_NOT_REVOKED, SubIndication.EXPIRED,
            SubIndication.NOT_YET_VALID);
    private static final Set<SubIndication> REVOKED = EnumSet.of(
            SubIndication.REVOKED, SubIndication.REVOKED_NO_POE, SubIndication.REVOKED_CA_NO_POE);
    private static final Set<SubIndication> REVOCATION_MISSING = EnumSet.of(
            SubIndication.TRY_LATER, SubIndication.REVOCATION_OUT_OF_BOUNDS_NO_POE);

    /**
     * Réglages. {@code visualComparisonMaxPages} : au-delà, la comparaison visuelle (rendu de chaque page, deux fois)
     * est sautée par DSS ; les contrôles d'objets restent faits. 0 la coupe.
     */
    record Limits(int maxAddedRevisions, int maxSignatures, int maxRevocationCalls, int visualComparisonMaxPages,
            Duration revocationMaxAge, double maxSignatureAreaFraction) {
        /**
         * {@code maxSignatureAreaFraction} : aire maximale du {@code /Rect} d'un widget de signature ajouté, en part de
         * la page (revue de clôture, HAUT 3). 5 % : un cadre de signature Acrobat ordinaire (200 × 60 pt) en fait 2,4 %
         * sur A4 ; une apparence qui masquerait une clause ou la page est refusée.
         */
        static final Limits DEFAULT = new Limits(8, 32, 8, 10, Duration.ofHours(24), 0.05);
    }

    private enum Stage {
        STRUCTURE(SignedUploadRejection.NOT_A_PDF),
        MODIFICATIONS(SignedUploadRejection.CONTENT_MODIFIED),
        VALIDATION(SignedUploadRejection.SIGNATURE_INVALID);

        private final SignedUploadRejection fallback;

        Stage(SignedUploadRejection fallback) {
            this.fallback = fallback;
        }
    }

    private final TrustedChainGate gate;
    private final Clock clock;
    private final RevocationTransport transport;
    private final Limits limits;
    private final ValidationPolicy policy;

    /**
     * @param trustedCertificates autorités fournies par l'exploitant (racines et intermédiaires), au moins une
     * @param clock heure de vérification
     * @param transport transport sortant de la révocation ({@link PinnedRevocationTransport} en production)
     */
    public DssSignedPdfVerifier(List<X509Certificate> trustedCertificates, Clock clock, RevocationTransport transport) {
        this(trustedCertificates, clock, transport, Limits.DEFAULT);
    }

    DssSignedPdfVerifier(List<X509Certificate> trustedCertificates, Clock clock, RevocationTransport transport, Limits limits) {
        this.gate = new TrustedChainGate(trustedCertificates);
        this.clock = Objects.requireNonNull(clock, "clock");
        this.transport = Objects.requireNonNull(transport, "transport");
        this.limits = Objects.requireNonNull(limits, "limits");
        this.policy = ValidationPolicyLoader.fromDefaultValidationPolicy().withDefaultCryptographicSuite().create();
    }

    @Override
    public SignedUploadVerdict verify(byte[] baseDocument, byte[] signedDocument, Set<String> usedCertificateFingerprints) {
        Objects.requireNonNull(baseDocument, "baseDocument");
        Set<String> used = usedCertificateFingerprints == null ? Set.of() : usedCertificateFingerprints;
        Stage stage = Stage.STRUCTURE;
        try {
            UploadStructure.Result structure = UploadStructure.inspect(baseDocument, signedDocument,
                    new UploadStructure.Limits(limits.maxAddedRevisions(), limits.maxSignatures()));
            if (structure.refused()) {
                return SignedUploadVerdict.rejected(structure.rejection());
            }
            stage = Stage.MODIFICATIONS;
            if (contentModified(baseDocument, signedDocument, structure.signedRevisionEnd())) {
                return SignedUploadVerdict.rejected(SignedUploadRejection.CONTENT_MODIFIED);
            }
            stage = Stage.VALIDATION;
            return validate(baseDocument, signedDocument, structure, used);
        } catch (RuntimeException | StackOverflowError failure) {
            // Aucun détail du fichier : la classe de l'erreur et l'étape suffisent à l'exploitant.
            log.warn("eID signed upload could not be verified at stage {} ({})", stage, failure.getClass().getSimpleName());
            return SignedUploadVerdict.rejected(stage.fallback);
        }
    }

    // --- CONTENT_MODIFIED ---

    private boolean contentModified(byte[] base, byte[] signed, int signedRevisionEnd) {
        byte[] signedRevision = signedRevisionEnd == signed.length ? signed : Arrays.copyOf(signed, signedRevisionEnd);
        try (PdfBoxDocumentReader baseReader = new PdfBoxDocumentReader(new InMemoryDocument(base));
                PdfBoxDocumentReader revisionReader = new PdfBoxDocumentReader(new InMemoryDocument(signedRevision));
                PdfBoxDocumentReader finalReader = new PdfBoxDocumentReader(new InMemoryDocument(signed))) {
            DefaultPdfObjectModificationsFinder objects = new DefaultPdfObjectModificationsFinder();
            List<String> refused = SignatureModificationPolicy.refused(
                    objects.find(baseReader, revisionReader), SignatureModificationPolicy.Phase.SIGNATURE);
            if (signedRevision != signed) {
                refused.addAll(SignatureModificationPolicy.refused(
                        objects.find(revisionReader, finalReader), SignatureModificationPolicy.Phase.EXTENSION));
            }
            if (!refused.isEmpty()) {
                log.info("eID signed upload refused: {} object change(s) beyond the signature", refused.size());
                return true;
            }
            // Revue de clôture (HAUT 1, HAUT 3) : redéfinitions lues dans les tables de références croisées, et aire des
            // widgets de signature neufs, indépendamment du parcours d'objets de DSS.
            // Contre-revue (HAUT B) : sections de références ajoutées lues strictement par Myrmacta.
            String refusal = AddedXrefSections.refusal(base, signed, baseReader.getPDDocument().getDocument().getXrefTable(),
                    finalReader.getPDDocument().getDocument().getXrefTable());
            if (refusal == null) {
                refusal = IncrementalUpdateCheck.refusal(baseReader.getPDDocument(), finalReader.getPDDocument(),
                        limits.maxSignatureAreaFraction());
            }
            if (refusal == null && signedRevision != signed) {
                refusal = IncrementalUpdateCheck.refusal(revisionReader.getPDDocument(), finalReader.getPDDocument(),
                        limits.maxSignatureAreaFraction());
            }
            // Contre-revue (HAUT A, C, D, E, F) : graphe final parcouru depuis le catalogue.
            if (refusal == null) {
                refusal = FinalGraphWalk.refusal(baseReader.getPDDocument(), finalReader.getPDDocument(), signedRevisionEnd);
            }
            if (refusal == null && signedRevision != signed) {
                refusal = FinalGraphWalk.refusal(revisionReader.getPDDocument(), finalReader.getPDDocument(), -1);
            }
            if (refusal != null) {
                log.info("eID signed upload refused: {}", refusal);
                return true;
            }
            DefaultPdfDifferencesFinder differences = new DefaultPdfDifferencesFinder();
            differences.setMaximalPagesAmountForVisualComparison(limits.visualComparisonMaxPages());
            if (!differences.getPagesDifferences(baseReader, finalReader).isEmpty()) {
                log.info("eID signed upload refused: page count changed");
                return true;
            }
            Set<Integer> overlapsBefore = pages(differences.getAnnotationOverlaps(baseReader));
            Set<Integer> overlapsAfter = pages(differences.getAnnotationOverlaps(finalReader));
            overlapsAfter.removeAll(overlapsBefore);
            if (!overlapsAfter.isEmpty()) {
                log.info("eID signed upload refused: a new annotation overlaps another one");
                return true;
            }
            if (limits.visualComparisonMaxPages() > 0
                    && !differences.getVisualDifferences(baseReader, finalReader).isEmpty()) {
                log.info("eID signed upload refused: visual difference with the delivered document");
                return true;
            }
            return false;
        } catch (IOException unreadable) {
            log.info("eID signed upload refused: a revision cannot be read ({})", unreadable.getClass().getSimpleName());
            return true;
        }
    }

    private static Set<Integer> pages(List<PdfModification> modifications) {
        Set<Integer> pages = new HashSet<>();
        modifications.forEach(modification -> pages.add(modification.getPage()));
        return pages;
    }

    // --- SIGNATURE_INVALID .. REVOCATION_UNAVAILABLE ---

    private SignedUploadVerdict validate(byte[] base, byte[] signed, UploadStructure.Result structure, Set<String> used) {
        Instant now = clock.instant();
        GatedRevocationSources sources = new GatedRevocationSources(gate, transport, limits.maxRevocationCalls());
        CommonCertificateVerifier verifier = new CommonCertificateVerifier();
        verifier.setTrustedCertSources(gate.trustedSource());
        verifier.setAdjunctCertSources(gate.adjunctSource());
        verifier.setAIASource(null);
        verifier.setCheckRevocationForUntrustedChains(false);
        verifier.setOcspSource(sources.ocsp());
        verifier.setCrlSource(sources.crl());
        verifier.setRevocationDataVerifier(revocationDataVerifier());

        PDFDocumentValidator validator = new PDFDocumentValidator(new InMemoryDocument(signed));
        validator.setCertificateVerifier(verifier);
        validator.setValidationTime(Date.from(now));
        validator.setEnableEtsiValidationReport(false);
        validator.setIncludeSemantics(false);
        // La détection des modifications est faite une fois, document remis contre fichier final (plus haut) :
        // pas de second rendu des pages par signature dans la validation.
        PdfBoxDefaultObjectFactory factory = new PdfBoxDefaultObjectFactory();
        factory.setPdfDifferencesFinder(NO_DIFFERENCES);
        validator.setPdfObjFactory(factory);
        Reports reports = validator.validateDocument(policy);
        DiagnosticData diagnostic = reports.getDiagnosticData();

        SignatureWrapper signature = null;
        for (SignatureWrapper candidate : diagnostic.getSignatures()) {
            if (candidate.isCounterSignature()) {
                continue;
            }
            long end = revisionEnd(candidate);
            if (end == structure.signedRevisionEnd()) {
                if (signature != null) {
                    return SignedUploadVerdict.rejected(SignedUploadRejection.SEVERAL_SIGNATURES);
                }
                signature = candidate;
            } else if (end > base.length || end < 0) {
                return SignedUploadVerdict.rejected(SignedUploadRejection.SEVERAL_SIGNATURES);
            } else if (!baseSignatureStillValid(candidate)) {
                return SignedUploadVerdict.rejected(SignedUploadRejection.CONTENT_MODIFIED);
            }
        }
        if (signature == null) {
            return SignedUploadVerdict.rejected(SignedUploadRejection.SIGNATURE_INVALID);
        }
        Indication indication = reports.getSimpleReport().getIndication(signature.getId());
        SubIndication subIndication = reports.getSimpleReport().getSubIndication(signature.getId());

        // 5. Intégrité et cryptographie.
        if (!signatureIntact(signature)) {
            return SignedUploadVerdict.rejected(SignedUploadRejection.SIGNATURE_INVALID);
        }
        // 6. Chaîne de confiance et validité (D-EID-TRUST).
        CertificateWrapper signingCertificate = signature.getSigningCertificate();
        if (signingCertificate == null || !chainTrusted(signature, now)) {
            return SignedUploadVerdict.rejected(SignedUploadRejection.CERTIFICATE_UNTRUSTED);
        }
        // 7. Qualification (D-EID-QUALIFIED).
        // Les données de diagnostic ne portent pas les octets des certificats : on les prend dans la signature DSS.
        eu.europa.esig.dss.spi.signature.AdvancedSignature advanced = validator.getSignatureById(signature.getId());
        if (advanced == null || advanced.getSigningCertificateToken() == null
                || !advanced.getSigningCertificateToken().getDSSIdAsString().equals(signingCertificate.getId())) {
            return SignedUploadVerdict.rejected(SignedUploadRejection.CERTIFICATE_UNTRUSTED);
        }
        SigningCertificateFacts facts = SigningCertificateFacts.of(advanced.getSigningCertificateToken().getEncoded());
        if (!facts.qualified()) {
            return SignedUploadVerdict.rejected(SignedUploadRejection.CERTIFICATE_NOT_QUALIFIED);
        }
        // 8. Un certificat, un signataire.
        if (used.contains(facts.fingerprint())) {
            return SignedUploadVerdict.rejected(SignedUploadRejection.DUPLICATE_SIGNER);
        }
        // 9. Révocation (D-EID-REVOCATION), pour le signataire et chaque intermédiaire qui n'est pas une ancre.
        CertificateRevocationWrapper signerRevocation = null;
        List<CertificateWrapper> revocationChain = signature.getCertificateChain();
        for (int index = 0; index < revocationChain.size(); index++) {
            CertificateWrapper certificate = revocationChain.get(index);
            if (certificate.isTrusted()) {
                break;
            }
            if (index + 1 >= revocationChain.size()) {
                return SignedUploadVerdict.rejected(SignedUploadRejection.REVOCATION_UNAVAILABLE);
            }
            RevocationOutcome outcome = revocationOf(certificate, revocationChain.get(index + 1), now, index == 0,
                    sources.fetchedTokenIds());
            if (outcome.revoked()) {
                return SignedUploadVerdict.rejected(SignedUploadRejection.CERTIFICATE_REVOKED);
            }
            if (outcome.good() == null) {
                return SignedUploadVerdict.rejected(SignedUploadRejection.REVOCATION_UNAVAILABLE);
            }
            if (certificate.getId().equals(signingCertificate.getId())) {
                signerRevocation = outcome.good();
            }
        }
        if (signerRevocation == null) {
            return SignedUploadVerdict.rejected(SignedUploadRejection.REVOCATION_UNAVAILABLE);
        }
        // Tout est établi de notre côté : DSS doit aussi conclure. Dans le doute, refus, avec le motif le plus proche de
        // sa sous-indication (lue en dernier : elle ne doit pas masquer un motif établi plus haut).
        if (indication != Indication.TOTAL_PASSED) {
            log.info("eID signed upload refused: DSS indication {} / {}", indication, subIndication);
            return SignedUploadVerdict.rejected(fromSubIndication(subIndication));
        }
        return SignedUploadVerdict.accepted(new SignedUploadEvidence(
                facts.signerName(),
                facts.issuer(),
                facts.serial(),
                facts.fingerprint(),
                toInstant(signature.getClaimedSigningTime()),
                now,
                signature.getSignatureFormat() == null ? structure.subFilter() : signature.getSignatureFormat().toString(),
                signature.getSignatureAlgorithm() == null ? "?" : signature.getSignatureAlgorithm().getJCEId(),
                facts.qcCompliance(),
                facts.qcSscd(),
                signerRevocation.getRevocationType() == RevocationType.OCSP ? "OCSP" : "CRL",
                toInstant(signerRevocation.getProductionDate())));
    }

    private static SignedUploadRejection fromSubIndication(SubIndication subIndication) {
        if (REVOKED.contains(subIndication)) {
            return SignedUploadRejection.CERTIFICATE_REVOKED;
        }
        if (REVOCATION_MISSING.contains(subIndication)) {
            return SignedUploadRejection.REVOCATION_UNAVAILABLE;
        }
        if (CHAIN_FAILURES.contains(subIndication)) {
            return SignedUploadRejection.CERTIFICATE_UNTRUSTED;
        }
        return SignedUploadRejection.SIGNATURE_INVALID;
    }

    private RevocationDataVerifier revocationDataVerifier() {
        return new OnlineOnlyRevocationDataVerifier();
    }

    /**
     * Réglages de DSS par défaut (algorithmes acceptés), avec trois différences (D-EID-REVOCATION, revue de clôture) :
     * aucune extension ne dispense de révocation sauf {@code id-pkix-ocsp-nocheck} (réservée aux répondeurs OCSP,
     * RFC 6960) ; une donnée de révocation n'est « fraîche » que si elle a été obtenue en ligne pendant cette
     * vérification, si bien que DSS redemande toujours la révocation au transport même quand le PDF en embarque une.
     */
    private static final class OnlineOnlyRevocationDataVerifier extends RevocationDataVerifier {
        OnlineOnlyRevocationDataVerifier() {
            setAcceptableDigestAlgorithms(List.of(DigestAlgorithm.SHA224, DigestAlgorithm.SHA256, DigestAlgorithm.SHA384,
                    DigestAlgorithm.SHA512, DigestAlgorithm.SHA3_256, DigestAlgorithm.SHA3_384, DigestAlgorithm.SHA3_512));
            java.util.Map<EncryptionAlgorithm, Integer> keyLengths = new java.util.EnumMap<>(EncryptionAlgorithm.class);
            keyLengths.put(EncryptionAlgorithm.RSA, 1900);
            keyLengths.put(EncryptionAlgorithm.RSASSA_PSS, 1900);
            keyLengths.put(EncryptionAlgorithm.ECDSA, 256);
            keyLengths.put(EncryptionAlgorithm.PLAIN_ECDSA, 256);
            setAcceptableEncryptionAlgorithmKeyLength(keyLengths);
            setRevocationSkipCertificateExtensions(List.of("1.3.6.1.5.5.7.48.1.5"));
            setRevocationSkipCertificatePolicies(List.of());
            setSignatureMaximumRevocationFreshness(0L);
            setRevocationMaximumRevocationFreshness(0L);
            setTimestampMaximumRevocationFreshness(0L);
        }

        @Override
        public boolean isRevocationDataFresh(eu.europa.esig.dss.spi.x509.revocation.RevocationToken<?> token, Date validationTime,
                eu.europa.esig.dss.enumerations.Context context) {
            return token.getExternalOrigin() == RevocationOrigin.EXTERNAL && super.isRevocationDataFresh(token, validationTime, context);
        }
    }

    private static long revisionEnd(SignatureWrapper signature) {
        PDFRevisionWrapper revision = signature.getPDFRevision();
        if (revision == null) {
            return -1;
        }
        List<BigInteger> range = revision.getSignatureByteRange();
        if (range == null || range.size() != 4) {
            return -1;
        }
        return range.get(2).add(range.get(3)).longValueExact();
    }

    /** Une signature du document remis : intacte, et sans verrou de certification qui interdit tout ajout. */
    private static boolean baseSignatureStillValid(SignatureWrapper signature) {
        if (!signature.isSignatureIntact()) {
            return false;
        }
        PDFRevisionWrapper revision = signature.getPDFRevision();
        return revision == null || revision.getDocMDPPermissions() != CertificationPermission.NO_CHANGE_PERMITTED;
    }

    private static boolean signatureIntact(SignatureWrapper signature) {
        if (!signature.isSignatureIntact() || !signature.isSignatureValid() || !signature.isBLevelTechnicallyValid()) {
            return false;
        }
        PDFRevisionWrapper revision = signature.getPDFRevision();
        if (revision == null || !revision.isSignatureByteRangeValid()) {
            return false;
        }
        List<XmlDigestMatcher> matchers = signature.getDigestMatchers();
        if (matchers == null || matchers.isEmpty()) {
            return false;
        }
        for (XmlDigestMatcher matcher : matchers) {
            if (!matcher.isDataFound() || !matcher.isDataIntact() || !ACCEPTED_DIGESTS.contains(matcher.getDigestMethod())) {
                return false;
            }
        }
        return acceptedAlgorithm(signature);
    }

    /** RSA (PKCS#1 v1.5 ou PSS) d'au moins 2048 bits, ou ECDSA d'au moins 256 bits ; SHA-2 seulement. */
    private static boolean acceptedAlgorithm(SignatureWrapper signature) {
        if (!ACCEPTED_DIGESTS.contains(signature.getDigestAlgorithm())) {
            return false;
        }
        EncryptionAlgorithm encryption = signature.getEncryptionAlgorithm();
        int keyLength;
        try {
            keyLength = Integer.parseInt(signature.getKeyLengthUsedToSignThisToken());
        } catch (RuntimeException unknown) {
            return false;
        }
        if (encryption == EncryptionAlgorithm.RSA || encryption == EncryptionAlgorithm.RSASSA_PSS) {
            return keyLength >= 2048;
        }
        if (encryption == EncryptionAlgorithm.ECDSA || encryption == EncryptionAlgorithm.PLAIN_ECDSA) {
            return keyLength >= 256;
        }
        return false;
    }

    /** La chaîne finit sur une ancre fournie, chaque certificat est signé par le suivant et valide maintenant. */
    private static boolean chainTrusted(SignatureWrapper signature, Instant now) {
        // Pas d'exigence de référence ESS (signing-certificate) : un CMS adbe.pkcs7.detached d'Acrobat n'en porte pas ;
        // le certificat est désigné par l'identifiant du signataire et lié par la vérification de la signature.
        if (!signature.isTrustedChain()) {
            return false;
        }
        List<CertificateWrapper> chain = signature.getCertificateChain();
        if (chain == null || chain.isEmpty()) {
            return false;
        }
        Date at = Date.from(now);
        for (int index = 0; index < chain.size(); index++) {
            CertificateWrapper certificate = chain.get(index);
            // Revue de clôture (MOYEN 5) : tout émetteur est une autorité (CA, keyCertSign) dont la longueur de chemin
            // admet les autorités intermédiaires placées sous elle ; sans dépendre du seul verdict de DSS.
            if (index > 0 && !isAuthority(certificate, index - 1)) {
                return false;
            }
            if (certificate.getNotBefore() == null || certificate.getNotAfter() == null
                    || at.before(certificate.getNotBefore()) || at.after(certificate.getNotAfter())) {
                return false;
            }
            if (certificate.isTrusted()) {
                return true;
            }
            if (!certificate.isSignatureIntact() || !certificate.isSignatureValid()) {
                return false;
            }
        }
        return false;
    }

    private record RevocationOutcome(boolean revoked, CertificateRevocationWrapper good) {}

    private static boolean isAuthority(CertificateWrapper certificate, int authoritiesBelow) {
        int pathLength = certificate.getPathLenConstraint();
        return certificate.isCA()
                && certificate.getKeyUsages().contains(KeyUsageBit.KEY_CERT_SIGN)
                && (pathLength < 0 || pathLength >= authoritiesBelow);
    }

    /**
     * Révocation d'un certificat (D-EID-REVOCATION, revue de clôture HAUT 2 et MOYEN 4).
     *
     * <ul>
     *   <li>Seules les données obtenues <b>en ligne</b>, par le transport et à la vérification, comptent : une donnée
     *       embarquée dans le PDF (PAdES-LT, LTA) n'est pas crue, quoi qu'en pense DSS. Un PDF d'outil tiers reste
     *       accepté, puisque la révocation est redemandée (fraîcheur maximale nulle).
     *   <li>Le signataire de la donnée doit avoir autorité sur le certificat : pour une CRL, l'émetteur du certificat
     *       (usage {@code cRLSign}) ; pour une réponse OCSP, l'émetteur lui-même ou un répondeur délégué émis
     *       directement par lui avec l'usage étendu {@code id-kp-OCSPSigning}. Une autre carte de la même hiérarchie ne
     *       l'a pas.
     *   <li>Fraîcheur : une CRL vaut tant que son {@code nextUpdate} n'est pas passé (une CRL d'autorité est publiée à
     *       plusieurs mois d'intervalle) ; une réponse OCSP pour le certificat du signataire a au plus
     *       {@code revocationMaxAge}. Aucune date n'est dans le futur au-delà de cinq minutes.
     * </ul>
     */
    private RevocationOutcome revocationOf(CertificateWrapper certificate, CertificateWrapper issuer, Instant now,
            boolean signerCertificate, Set<String> fetchedOnline) {
        CertificateRevocationWrapper good = null;
        Instant oldest = now.minus(limits.revocationMaxAge());
        Instant skew = now.plus(Duration.ofMinutes(5));
        for (CertificateRevocationWrapper revocation : certificate.getCertificateRevocationData()) {
            // Obtenue en ligne pendant cette vérification (une copie embarquée identique a le même identifiant).
            if (!fetchedOnline.contains(revocation.getId())) {
                continue;
            }
            if (!revocation.isSignatureIntact() || !revocation.isSignatureValid() || !revocation.isTrustedChain()
                    || !signedWithAuthority(revocation, issuer)) {
                continue;
            }
            if (revocation.getStatus() != null && revocation.getStatus().isRevoked()) {
                return new RevocationOutcome(true, null);
            }
            Date thisUpdate = revocation.getThisUpdate();
            Date nextUpdate = revocation.getNextUpdate();
            if (thisUpdate == null || thisUpdate.toInstant().isAfter(skew)) {
                continue;
            }
            boolean fresh;
            if (revocation.getRevocationType() == RevocationType.CRL) {
                fresh = nextUpdate != null && nextUpdate.toInstant().isAfter(now);
            } else {
                fresh = (!signerCertificate || !thisUpdate.toInstant().isBefore(oldest))
                        && (nextUpdate == null || nextUpdate.toInstant().isAfter(now));
            }
            if (revocation.getStatus() != null && revocation.getStatus().isGood() && fresh) {
                if (good == null || revocation.getProductionDate().after(good.getProductionDate())) {
                    good = revocation;
                }
            }
        }
        return new RevocationOutcome(false, good);
    }

    /** Le signataire de la donnée de révocation a autorité sur le certificat contrôlé (voir {@link #revocationOf}). */
    private static boolean signedWithAuthority(CertificateRevocationWrapper revocation, CertificateWrapper issuer) {
        CertificateWrapper signer = revocation.getSigningCertificate();
        if (signer == null || issuer == null) {
            return false;
        }
        if (signer.getId().equals(issuer.getId())) {
            return revocation.getRevocationType() != RevocationType.CRL
                    || issuer.getKeyUsages().contains(KeyUsageBit.CRL_SIGN);
        }
        if (revocation.getRevocationType() != RevocationType.OCSP) {
            return false;
        }
        CertificateWrapper signerIssuer = signer.getSigningCertificate();
        return signerIssuer != null && signerIssuer.getId().equals(issuer.getId())
                && signer.getExtendedKeyUsages().stream().anyMatch(oid -> OCSP_SIGNING.equals(oid.getValue()));
    }

    private static final String OCSP_SIGNING = "1.3.6.1.5.5.7.3.9";

    private static Instant toInstant(Date date) {
        return date == null ? null : date.toInstant();
    }

    /** Détection des différences coupée dans la validation DSS : elle est faite une fois, plus haut. */
    private static final PdfDifferencesFinder NO_DIFFERENCES = new PdfDifferencesFinder() {
        @Override
        public List<PdfModification> getAnnotationOverlaps(PdfDocumentReader reader) {
            return List.of();
        }

        @Override
        public boolean isAnnotationBoxOverlapping(AnnotationBox annotationBox, List<PdfAnnotation> pdfAnnotations) {
            return false;
        }

        @Override
        public List<PdfModification> getPagesDifferences(PdfDocumentReader signed, PdfDocumentReader last) {
            return List.of();
        }

        @Override
        public List<PdfModification> getVisualDifferences(PdfDocumentReader signed, PdfDocumentReader last) {
            return List.of();
        }
    };
}
