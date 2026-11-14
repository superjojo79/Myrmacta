package be.myrmacta.infrastructure.signature.eid;

import eu.europa.esig.dss.model.x509.CertificateToken;
import eu.europa.esig.dss.spi.x509.CommonCertificateSource;
import eu.europa.esig.dss.spi.x509.CommonTrustedCertificateSource;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Autorités fournies par l'exploitant, vues par la vérification d'une signature eID (TODO §138, D-EID-TRUST).
 *
 * <p>Une autorité fournie est une <b>ancre</b> si son émetteur n'est pas lui-même fourni (une racine, ou une
 * intermédiaire livrée sans sa racine). Les autres sont des intermédiaires fournies : DSS s'en sert pour bâtir la
 * chaîne, mais elles ne sont pas des ancres, si bien que leur révocation est contrôlée comme celle du signataire
 * (D-EID-REVOCATION). Aucune autorité n'est téléchargée depuis une adresse d'un certificat (AIA désactivée).
 *
 * <p>{@link #allowsOnlineRevocation} est le garde contre la falsification de requêtes sortantes (SSRF) : un appel
 * de révocation ne part que si le certificat est signé par son émetteur et que cet émetteur est une autorité
 * fournie, ou est signé par l'une d'elles. Les signatures sont vérifiées ici, indépendamment de DSS : une chaîne
 * fabriquée par un attaquant n'atteint jamais le transport, quoi que DSS décide.
 */
final class TrustedChainGate {

    private final List<CertificateToken> provided;
    private final List<CertificateToken> anchors;
    private final List<CertificateToken> intermediates;

    TrustedChainGate(List<X509Certificate> trustedCertificates) {
        Objects.requireNonNull(trustedCertificates, "trustedCertificates");
        List<CertificateToken> tokens = new ArrayList<>();
        for (X509Certificate certificate : trustedCertificates) {
            CertificateToken token = new CertificateToken(Objects.requireNonNull(certificate, "trusted certificate"));
            if (tokens.stream().noneMatch(token::equals)) {
                tokens.add(token);
            }
        }
        if (tokens.isEmpty()) {
            throw new IllegalArgumentException("at least one trusted certificate authority is required (D-EID-TRUST)");
        }
        List<CertificateToken> anchorList = new ArrayList<>();
        List<CertificateToken> intermediateList = new ArrayList<>();
        for (CertificateToken token : tokens) {
            boolean issuedByAnotherProvided = !token.isSelfSigned()
                    && tokens.stream().anyMatch(other -> !other.equals(token) && token.isSignedBy(other));
            (issuedByAnotherProvided ? intermediateList : anchorList).add(token);
        }
        this.provided = List.copyOf(tokens);
        this.anchors = List.copyOf(anchorList);
        this.intermediates = List.copyOf(intermediateList);
    }

    /** Source des ancres, pour un {@code CertificateVerifier} neuf (une par vérification). */
    CommonTrustedCertificateSource trustedSource() {
        CommonTrustedCertificateSource source = new CommonTrustedCertificateSource();
        // Jetons neufs à chaque vérification : DSS enrichit les jetons qu'il traite, ceux du garde restent intacts.
        anchors.forEach(anchor -> source.addCertificate(new CertificateToken(anchor.getCertificate())));
        return source;
    }

    /** Source des intermédiaires fournies : construction de la chaîne, sans valeur de confiance. */
    CommonCertificateSource adjunctSource() {
        CommonCertificateSource source = new CommonCertificateSource();
        intermediates.forEach(intermediate -> source.addCertificate(new CertificateToken(intermediate.getCertificate())));
        return source;
    }

    boolean isAnchor(CertificateToken token) {
        return anchors.contains(token);
    }

    /** Toutes les autorités fournies (ancres et intermédiaires). */
    List<CertificateToken> provided() {
        return provided;
    }

    /**
     * Vrai seulement si {@code certificate} est signé par {@code issuer} et que {@code issuer} est une autorité
     * fournie, ou est signé par une autorité fournie. Les adresses de révocation lues dans {@code certificate}
     * viennent alors d'une autorité choisie par l'exploitant.
     */
    boolean allowsOnlineRevocation(CertificateToken certificate, CertificateToken issuer) {
        if (certificate == null || issuer == null || !certificate.isSignedBy(issuer)) {
            return false;
        }
        if (provided.contains(issuer)) {
            return true;
        }
        for (CertificateToken authority : provided) {
            if (issuer.isSignedBy(authority)) {
                return true;
            }
        }
        return false;
    }
}
