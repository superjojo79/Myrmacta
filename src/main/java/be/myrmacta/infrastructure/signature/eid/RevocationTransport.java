package be.myrmacta.infrastructure.signature.eid;

import java.io.IOException;
import java.net.URI;

/**
 * Transport sortant de la révocation (TODO §138, D-EID-REVOCATION) : la seule porte par laquelle la
 * vérification d'une signature eID joint le réseau. Le vérificateur ne l'appelle que pour un certificat
 * dont la chaîne remonte, signature par signature, à une autorité fournie par l'exploitant
 * ({@link TrustedChainGate}) : une adresse venue d'un certificat inconnu n'atteint jamais ce transport.
 *
 * <p>Implémentation de production : {@link PinnedRevocationTransport}. Les tests en donnent une en mémoire.
 */
public interface RevocationTransport {

    /**
     * Envoie une requête OCSP ({@code application/ocsp-request}) et rend le corps de la réponse.
     *
     * @param responder adresse {@code http} ou {@code https} lue dans le certificat (AIA, id-ad-ocsp)
     * @param request requête OCSP encodée en DER
     * @throws IOException adresse refusée, délai dépassé, réponse trop grosse, statut HTTP autre que 200
     */
    byte[] postOcsp(URI responder, byte[] request) throws IOException;

    /**
     * Télécharge une liste de révocation et rend son corps (DER ou PEM).
     *
     * @param distributionPoint adresse {@code http} ou {@code https} lue dans le certificat (CRL DP)
     * @throws IOException adresse refusée, délai dépassé, réponse trop grosse, statut HTTP autre que 200
     */
    byte[] getCrl(URI distributionPoint) throws IOException;
}
