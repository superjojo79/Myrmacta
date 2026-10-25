package be.myrmacta.infrastructure.portal.identity;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.jwk.RSAKey;
import java.util.Base64;

/** Écrit une clé RSA en PEM PKCS#8, le format attendu par {@link PortalIdentityKeys}. */
final class PemKeys {

    private PemKeys() {
    }

    static String toPkcs8Pem(RSAKey key) {
        try {
            byte[] der = key.toRSAPrivateKey().getEncoded();
            return "-----BEGIN PRIVATE KEY-----\n" + Base64.getMimeEncoder(64, "\n".getBytes()).encodeToString(der)
                    + "\n-----END PRIVATE KEY-----\n";
        } catch (JOSEException ex) {
            throw new IllegalStateException(ex);
        }
    }
}
