package be.myrmacta.infrastructure.signature.eid;

import eu.europa.esig.dss.enumerations.DigestAlgorithm;
import eu.europa.esig.dss.model.TimestampBinary;
import eu.europa.esig.dss.spi.x509.tsp.TSPSource;
import java.io.IOException;
import java.math.BigInteger;
import java.net.URI;
import java.security.SecureRandom;
import java.security.cert.X509Certificate;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;
import org.bouncycastle.asn1.ASN1ObjectIdentifier;
import org.bouncycastle.cert.X509CertificateHolder;
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter;
import org.bouncycastle.cms.jcajce.JcaSimpleSignerInfoVerifierBuilder;
import org.bouncycastle.tsp.TimeStampRequest;
import org.bouncycastle.tsp.TimeStampRequestGenerator;
import org.bouncycastle.tsp.TimeStampResponse;
import org.bouncycastle.tsp.TimeStampToken;
import org.bouncycastle.util.Selector;

/**
 * Client RFC 3161 de l'autorité d'horodatage choisie par l'exploitant (TODO §138, D-EID-TIMESTAMP), source
 * d'horodatage de DSS. Chaque réponse est vérifiée avant usage : statut accordé, nonce et empreinte du message
 * identiques à la requête ({@code TimeStampResponse.validate}), jeton signé par le certificat qu'il désigne, ce
 * certificat portant l'usage {@code timeStamping} et valide à l'heure du jeton ({@code TimeStampToken.validate}).
 * Une réponse refusée ou une TSA injoignable lève {@link TimestampUnavailableException} : le répartiteur reprendra.
 *
 * <p>Une instance par extension : elle garde les jetons obtenus, pour la preuve.
 */
public final class RfcTimestampSource implements TSPSource {

    private static final long serialVersionUID = 1L;
    private static final SecureRandom NONCES = new SecureRandom();

    /** TSA injoignable, ou réponse refusée. Le message ne porte aucune donnée du document. */
    public static final class TimestampUnavailableException extends RuntimeException {
        private static final long serialVersionUID = 1L;

        TimestampUnavailableException(String message, Throwable cause) {
            super(message, cause);
        }
    }

    /** Un jeton obtenu : son heure et le sujet du certificat de l'autorité. */
    record Token(Instant time, String authority, X509Certificate certificate) {}

    private final transient Transport transport;
    private final URI url;
    private final List<Token> tokens = new CopyOnWriteArrayList<>();

    /** Le POST borné vers la TSA ; remplaçable en test. */
    @FunctionalInterface
    interface Transport {
        byte[] post(URI url, byte[] query) throws IOException;
    }

    RfcTimestampSource(URI url, Transport transport) {
        this.url = Objects.requireNonNull(url, "url");
        this.transport = Objects.requireNonNull(transport, "transport");
    }

    List<Token> tokens() {
        return List.copyOf(tokens);
    }

    @Override
    public TimestampBinary getTimeStampResponse(DigestAlgorithm digestAlgorithm, byte[] digest) {
        TimeStampRequestGenerator generator = new TimeStampRequestGenerator();
        generator.setCertReq(true);
        TimeStampRequest request = generator.generate(new ASN1ObjectIdentifier(digestAlgorithm.getOid()), digest,
                new BigInteger(64, NONCES));
        byte[] answer;
        try {
            answer = transport.post(url, request.getEncoded());
        } catch (IOException ex) {
            throw new TimestampUnavailableException("timestamp authority cannot be reached (" + ex.getMessage() + ")", ex);
        }
        try {
            TimeStampResponse response = new TimeStampResponse(answer);
            response.validate(request);
            TimeStampToken token = response.getTimeStampToken();
            if (token == null) {
                throw new IllegalStateException("no token");
            }
            X509CertificateHolder holder = signerCertificate(token);
            token.validate(new JcaSimpleSignerInfoVerifierBuilder().build(holder));
            X509Certificate certificate = new JcaX509CertificateConverter().getCertificate(holder);
            tokens.add(new Token(token.getTimeStampInfo().getGenTime().toInstant(),
                    certificate.getSubjectX500Principal().getName(), certificate));
            return new TimestampBinary(token.getEncoded());
        } catch (Exception ex) {
            throw new TimestampUnavailableException("timestamp response is not valid (" + ex.getClass().getSimpleName() + ")", ex);
        }
    }

    @SuppressWarnings("unchecked")
    private static X509CertificateHolder signerCertificate(TimeStampToken token) {
        Collection<X509CertificateHolder> matches = token.getCertificates()
                .getMatches((Selector<X509CertificateHolder>) (Selector<?>) token.getSID());
        if (matches.isEmpty()) {
            throw new IllegalStateException("timestamp token carries no signer certificate");
        }
        return matches.iterator().next();
    }
}
