package be.myrmacta.infrastructure.signature.eid;

import eu.europa.esig.dss.crl.CRLBinary;
import eu.europa.esig.dss.crl.CRLUtils;
import eu.europa.esig.dss.crl.CRLValidity;
import eu.europa.esig.dss.enumerations.DigestAlgorithm;
import eu.europa.esig.dss.enumerations.RevocationOrigin;
import eu.europa.esig.dss.model.x509.CertificateToken;
import eu.europa.esig.dss.spi.CertificateExtensionsUtils;
import eu.europa.esig.dss.spi.DSSRevocationUtils;
import eu.europa.esig.dss.spi.x509.revocation.crl.CRLSource;
import eu.europa.esig.dss.spi.x509.revocation.crl.CRLToken;
import eu.europa.esig.dss.spi.x509.revocation.ocsp.OCSPSource;
import eu.europa.esig.dss.spi.x509.revocation.ocsp.OCSPToken;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicInteger;
import org.bouncycastle.cert.ocsp.BasicOCSPResp;
import org.bouncycastle.cert.ocsp.CertificateID;
import org.bouncycastle.cert.ocsp.OCSPReqBuilder;
import org.bouncycastle.cert.ocsp.OCSPResp;
import org.bouncycastle.cert.ocsp.SingleResp;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Sources de révocation en ligne d'une vérification (TODO §138, D-EID-REVOCATION), à la place de
 * {@code OnlineOCSPSource} et {@code OnlineCRLSource} de {@code dss-service} : même construction de la requête OCSP
 * et même lecture des réponses, mais par {@link RevocationTransport}, derrière {@link TrustedChainGate}, et dans un
 * budget d'appels borné (déni de service). Une instance par vérification : le budget ne se partage pas.
 *
 * <p>Une source ne lève rien : une panne rend {@code null}, et l'absence de révocation se lit ensuite dans le rapport
 * de DSS ({@code REVOCATION_UNAVAILABLE}). Les journaux ne portent ni adresse complète ni donnée du certificat : au
 * plus l'hôte de l'autorité, qui n'est pas une donnée personnelle.
 */
final class GatedRevocationSources {

    private static final Logger log = LoggerFactory.getLogger(GatedRevocationSources.class);

    /** Adresses essayées au plus par certificat et par type (OCSP ou CRL). */
    static final int MAX_URLS_PER_CERTIFICATE = 2;

    private final TrustedChainGate gate;
    private final RevocationTransport transport;
    private final AtomicInteger remainingCalls;
    private final AtomicInteger refusedByGate = new AtomicInteger();

    GatedRevocationSources(TrustedChainGate gate, RevocationTransport transport, int maxCalls) {
        this.gate = Objects.requireNonNull(gate, "gate");
        this.transport = Objects.requireNonNull(transport, "transport");
        this.remainingCalls = new AtomicInteger(maxCalls);
    }

    OCSPSource ocsp() {
        return new Ocsp();
    }

    CRLSource crl() {
        return new Crl();
    }

    /** Nombre de demandes écartées par le garde (chaîne non vérifiée), pour les tests. */
    int refusedByGate() {
        return refusedByGate.get();
    }

    private boolean admit(CertificateToken certificate, CertificateToken issuer) {
        if (certificate == null || issuer == null) {
            return false;
        }
        if (!gate.allowsOnlineRevocation(certificate, issuer)) {
            refusedByGate.incrementAndGet();
            log.debug("online revocation skipped: the certificate chain does not reach a configured authority");
            return false;
        }
        return true;
    }

    private boolean takeCall() {
        if (remainingCalls.getAndDecrement() <= 0) {
            log.warn("eID revocation call budget exhausted for this verification");
            return false;
        }
        return true;
    }

    /** Adresses {@code http(s)} lues dans le certificat, dans l'ordre, au plus {@link #MAX_URLS_PER_CERTIFICATE}. */
    static List<URI> usableUrls(List<String> urls) {
        List<URI> usable = new ArrayList<>();
        if (urls == null) {
            return usable;
        }
        for (String url : urls) {
            if (usable.size() >= MAX_URLS_PER_CERTIFICATE) {
                break;
            }
            try {
                URI uri = URI.create(url.trim());
                String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(Locale.ROOT);
                if ((scheme.equals("http") || scheme.equals("https")) && uri.getHost() != null) {
                    usable.add(uri);
                }
            } catch (RuntimeException ignored) {
                // adresse malformée : écartée
            }
        }
        return usable;
    }

    private static String hostOf(URI uri) {
        return uri.getHost() == null ? "?" : uri.getHost();
    }

    private final class Ocsp implements OCSPSource {
        private static final long serialVersionUID = 1L;

        @Override
        public OCSPToken getRevocationToken(CertificateToken certificate, CertificateToken issuer) {
            if (!admit(certificate, issuer)) {
                return null;
            }
            List<URI> urls = usableUrls(CertificateExtensionsUtils.getOCSPAccessUrls(certificate));
            if (urls.isEmpty()) {
                return null;
            }
            byte[] request;
            try {
                // CertID en SHA-1, comme OnlineOCSPSource : c'est un identifiant, pas une signature.
                CertificateID certificateId = DSSRevocationUtils.getOCSPCertificateID(certificate, issuer, DigestAlgorithm.SHA1);
                request = new OCSPReqBuilder().addRequest(certificateId).build().getEncoded();
            } catch (Exception ex) {
                log.warn("cannot build an OCSP request ({})", ex.getClass().getSimpleName());
                return null;
            }
            for (URI url : urls) {
                if (!takeCall()) {
                    return null;
                }
                try {
                    byte[] body = transport.postOcsp(url, request);
                    OCSPResp response = new OCSPResp(body);
                    if (response.getStatus() != OCSPResp.SUCCESSFUL || !(response.getResponseObject() instanceof BasicOCSPResp basic)) {
                        log.warn("OCSP responder {} gave no usable answer", hostOf(url));
                        continue;
                    }
                    SingleResp single = DSSRevocationUtils.getLatestSingleResponse(basic, certificate, issuer);
                    if (single == null) {
                        log.warn("OCSP responder {} did not answer for the requested certificate", hostOf(url));
                        continue;
                    }
                    OCSPToken token = new OCSPToken(basic, single, certificate, issuer);
                    token.setSourceURL(url.toString());
                    token.setExternalOrigin(RevocationOrigin.EXTERNAL);
                    return token;
                } catch (Exception ex) {
                    log.warn("OCSP responder {} cannot be used ({})", hostOf(url), ex.getClass().getSimpleName());
                }
            }
            return null;
        }
    }

    private final class Crl implements CRLSource {
        private static final long serialVersionUID = 1L;

        @Override
        public CRLToken getRevocationToken(CertificateToken certificate, CertificateToken issuer) {
            if (!admit(certificate, issuer)) {
                return null;
            }
            for (URI url : usableUrls(CertificateExtensionsUtils.getCRLAccessUrls(certificate))) {
                if (!takeCall()) {
                    return null;
                }
                try {
                    byte[] body = transport.getCrl(url);
                    if (body == null || body.length == 0) {
                        continue;
                    }
                    CRLBinary binary = CRLUtils.buildCRLBinary(body);
                    CRLValidity validity = CRLUtils.buildCRLValidity(binary, issuer);
                    CRLToken token = new CRLToken(certificate, validity);
                    token.setSourceURL(url.toString());
                    token.setExternalOrigin(RevocationOrigin.EXTERNAL);
                    return token;
                } catch (Exception ex) {
                    log.warn("CRL distribution point {} cannot be used ({})", hostOf(url), ex.getClass().getSimpleName());
                }
            }
            return null;
        }
    }
}
