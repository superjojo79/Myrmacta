package be.myrmacta.infrastructure.signature.eid;

import eu.europa.esig.dss.enumerations.SignatureLevel;
import eu.europa.esig.dss.model.DSSDocument;
import eu.europa.esig.dss.model.InMemoryDocument;
import eu.europa.esig.dss.model.x509.CertificateToken;
import eu.europa.esig.dss.pades.PAdESSignatureParameters;
import eu.europa.esig.dss.pades.PAdESTimestampParameters;
import eu.europa.esig.dss.pades.signature.PAdESService;
import eu.europa.esig.dss.spi.validation.CommonCertificateVerifier;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.security.cert.X509Certificate;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Horodatage RFC 3161 du document signé final (TODO §138, D-EID-TIMESTAMP), configuré par l'exploitant.
 *
 * <p>Premier essai : extension PAdES-BASELINE-LTA par DSS (horodatage de document, dictionnaire {@code /DSS} des
 * données de validation, horodatage d'archive). DSS refuse l'extension si une donnée de validation manque (chaîne de la
 * TSA hors des autorités de l'exploitant, révocation introuvable) : on se replie alors sur un seul horodatage de
 * document. Les révisions ajoutées sont des extensions, que {@link DssSignedPdfVerifier} admet après une signature.
 *
 * <p>Une TSA injoignable ou une réponse refusée lève {@link RfcTimestampSource.TimestampUnavailableException} : aucun
 * repli silencieux, c'est l'appelant qui décide (reprise du répartiteur, puis livraison sans horodatage dite dans la
 * preuve).
 *
 * <p>Certificat de la TSA hors des autorités de l'exploitant : le jeton est gardé (l'adresse de la TSA est un choix de
 * l'exploitant et le jeton est vérifié cryptographiquement), mais il n'est ni dit qualifié ni rattaché à une chaîne de
 * confiance, et la preuve le dit ({@link Result#authorityTrusted()}). Le niveau atteint est alors l'horodatage de
 * document : DSS ne peut pas réunir les données de validation d'une chaîne qu'il ne connaît pas.
 */
public final class DssDocumentTimestamper implements DocumentTimestamper {

    private static final Logger log = LoggerFactory.getLogger(DssDocumentTimestamper.class);

    /** Niveau atteint, dit tel quel dans la preuve. */
    static final String LEVEL_LTA = "PAdES-BASELINE-LTA";
    static final String LEVEL_DOCUMENT_TIMESTAMP = "horodatage de document (PAdES-BASELINE-T)";

    private final URI url;
    private final TrustedChainGate gate;
    private final RevocationTransport revocation;
    private final RfcTimestampSource.Transport tsa;

    /**
     * @param url adresse de la TSA, {@code http} ou {@code https}
     * @param trustedCertificates autorités de l'exploitant (celles du vérificateur)
     * @param revocation transport de la révocation (données de validation de la chaîne de la TSA)
     * @param timeout délai total de l'appel à la TSA, corps compris
     * @param maxResponseBytes taille maximale de la réponse de la TSA
     */
    public DssDocumentTimestamper(String url, List<X509Certificate> trustedCertificates, RevocationTransport revocation,
            java.time.Duration timeout, long maxResponseBytes) {
        this(checkedUrl(url), trustedCertificates, revocation,
                bounded(PinnedRevocationTransport.forOperatorUrl(timeout, maxResponseBytes)));
    }

    DssDocumentTimestamper(URI url, List<X509Certificate> trustedCertificates, RevocationTransport revocation,
            RfcTimestampSource.Transport tsa) {
        this.url = Objects.requireNonNull(url, "url");
        this.gate = new TrustedChainGate(trustedCertificates);
        this.revocation = Objects.requireNonNull(revocation, "revocation");
        this.tsa = Objects.requireNonNull(tsa, "tsa");
    }

    private static RfcTimestampSource.Transport bounded(PinnedRevocationTransport transport) {
        return (target, query) -> transport.post(target, query, "application/timestamp-query", "application/timestamp-reply");
    }

    static URI checkedUrl(String url) {
        if (url == null || url.isBlank()) {
            throw new IllegalArgumentException("timestamp authority URL is missing");
        }
        URI uri;
        try {
            uri = URI.create(url.trim());
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException("timestamp authority URL is malformed");
        }
        String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(Locale.ROOT);
        if (!(scheme.equals("http") || scheme.equals("https")) || uri.getHost() == null || uri.getRawUserInfo() != null) {
            throw new IllegalArgumentException("timestamp authority URL must be http or https, without credentials");
        }
        return uri;
    }

    /**
     * @throws RfcTimestampSource.TimestampUnavailableException TSA injoignable ou réponse refusée
     */
    @Override
    public Result timestamp(byte[] signedDocument) {
        Objects.requireNonNull(signedDocument, "signedDocument");
        DSSDocument document = new InMemoryDocument(signedDocument);
        RfcTimestampSource source = new RfcTimestampSource(url, tsa);
        try {
            DSSDocument extended = service(source).extendDocument(document, ltaParameters());
            return result(extended, source, LEVEL_LTA);
        } catch (RuntimeException failure) {
            rethrowIfUnavailable(failure);
            // Données de validation incomplètes (souvent : chaîne de la TSA inconnue) : un seul horodatage de document.
            log.info("eID document timestamp: LTA augmentation not possible ({}), document timestamp only",
                    failure.getClass().getSimpleName());
        }
        RfcTimestampSource fallback = new RfcTimestampSource(url, tsa);
        try {
            DSSDocument timestamped = service(fallback).timestamp(document, new PAdESTimestampParameters());
            return result(timestamped, fallback, LEVEL_DOCUMENT_TIMESTAMP);
        } catch (RuntimeException failure) {
            rethrowIfUnavailable(failure);
            throw new RfcTimestampSource.TimestampUnavailableException(
                    "document timestamp failed (" + failure.getClass().getSimpleName() + ")", failure);
        }
    }

    /** DSS enveloppe parfois l'exception de la source d'horodatage : on la cherche dans les causes. */
    private static void rethrowIfUnavailable(Throwable failure) {
        for (Throwable cause = failure; cause != null; cause = cause.getCause()) {
            if (cause instanceof RfcTimestampSource.TimestampUnavailableException unavailable) {
                throw unavailable;
            }
        }
    }

    private Result result(DSSDocument document, RfcTimestampSource source, String level) {
        List<RfcTimestampSource.Token> tokens = source.tokens();
        if (tokens.isEmpty()) {
            throw new RfcTimestampSource.TimestampUnavailableException("no timestamp token was obtained", null);
        }
        RfcTimestampSource.Token last = tokens.get(tokens.size() - 1);
        boolean trusted = tokens.stream().allMatch(token -> chainsToOperatorAuthorities(token.certificate()));
        try (InputStream in = document.openStream()) {
            return new Result(in.readAllBytes(), last.time(), last.authority(), trusted, level);
        } catch (IOException ex) {
            throw new IllegalStateException("timestamped document cannot be read", ex);
        }
    }

    private boolean chainsToOperatorAuthorities(X509Certificate certificate) {
        CertificateToken token = new CertificateToken(certificate);
        for (CertificateToken authority : gate.provided()) {
            if (authority.equals(token) || token.isSignedBy(authority)) {
                return true;
            }
        }
        return false;
    }

    private static PAdESSignatureParameters ltaParameters() {
        PAdESSignatureParameters parameters = new PAdESSignatureParameters();
        parameters.setSignatureLevel(SignatureLevel.PAdES_BASELINE_LTA);
        return parameters;
    }

    /** Défauts de DSS : une donnée de validation manquante interrompt l'extension (pas de LTA incomplet). */
    private PAdESService service(RfcTimestampSource source) {
        CommonCertificateVerifier verifier = new CommonCertificateVerifier();
        verifier.setTrustedCertSources(gate.trustedSource());
        verifier.setAdjunctCertSources(gate.adjunctSource());
        verifier.setAIASource(null);
        verifier.setCheckRevocationForUntrustedChains(false);
        GatedRevocationSources sources = new GatedRevocationSources(gate, revocation, 16);
        verifier.setOcspSource(sources.ocsp());
        verifier.setCrlSource(sources.crl());
        PAdESService service = new PAdESService(verifier);
        service.setTspSource(source);
        return service;
    }
}
