package com.flowforge.infrastructure.portal.identity;

import com.flowforge.infrastructure.config.PortalStrongAuthProperties.Keys;
import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.jwk.JWK;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.KeyUse;
import com.nimbusds.jose.jwk.RSAKey;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.interfaces.RSAPrivateCrtKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.RSAPublicKeySpec;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Optional;

/**
 * TODO §103 (T11) — clés de l'instance pour le portail : signature de l'assertion {@code private_key_jwt} (clé courante,
 * plus une clé précédente publiée pendant une rotation) et déchiffrement d'un ID token chiffré (itsme). Lues au démarrage
 * depuis des fichiers PEM PKCS#8 (jamais depuis la base) ; RSA de 2048 bits au moins. Seules les parties publiques sont
 * publiées ({@link #publicJwkSetJson()}).
 */
public final class PortalIdentityKeys {

    private static final int MIN_RSA_BITS = 2048;

    private final RSAKey signing;
    private final RSAKey previousSigning;
    private final RSAKey encryption;

    private PortalIdentityKeys(RSAKey signing, RSAKey previousSigning, RSAKey encryption) {
        this.signing = signing;
        this.previousSigning = previousSigning;
        this.encryption = encryption;
    }

    public static PortalIdentityKeys load(Keys keys) {
        return new PortalIdentityKeys(
                read(keys.signingKeyFile(), keys.signingKeyId(), KeyUse.SIGNATURE),
                keys.previousSigningKeyFile() == null || keys.previousSigningKeyFile().isBlank() ? null
                        : read(keys.previousSigningKeyFile(), keys.previousSigningKeyId(), KeyUse.SIGNATURE),
                keys.encryptionKeyFile() == null || keys.encryptionKeyFile().isBlank() ? null
                        : read(keys.encryptionKeyFile(), keys.encryptionKeyId(), KeyUse.ENCRYPTION));
    }

    RSAKey signing() {
        return signing;
    }

    Optional<RSAKey> encryption() {
        return Optional.ofNullable(encryption);
    }

    /** JWKS publique de l'instance, enregistrée chez le fournisseur. */
    public String publicJwkSetJson() {
        List<JWK> published = new ArrayList<>();
        published.add(signing.toPublicJWK());
        if (previousSigning != null) {
            published.add(previousSigning.toPublicJWK());
        }
        if (encryption != null) {
            published.add(encryption.toPublicJWK());
        }
        return new JWKSet(published).toString(true);
    }

    private static RSAKey read(String file, String keyId, KeyUse use) {
        if (file == null || file.isBlank()) {
            throw new IllegalStateException("portal strong-auth key file is not set");
        }
        try {
            String pem = Files.readString(Path.of(file), StandardCharsets.US_ASCII);
            String base64 = pem.replaceAll("-----(BEGIN|END) PRIVATE KEY-----", "").replaceAll("\\s", "");
            KeyFactory factory = KeyFactory.getInstance("RSA");
            RSAPrivateCrtKey privateKey = (RSAPrivateCrtKey) factory.generatePrivate(
                    new PKCS8EncodedKeySpec(Base64.getDecoder().decode(base64)));
            if (privateKey.getModulus().bitLength() < MIN_RSA_BITS) {
                throw new IllegalStateException("portal strong-auth key " + file + " must be RSA of at least 2048 bits");
            }
            var publicKey = (java.security.interfaces.RSAPublicKey) factory.generatePublic(
                    new RSAPublicKeySpec(privateKey.getModulus(), privateKey.getPublicExponent()));
            RSAKey.Builder builder = new RSAKey.Builder(publicKey).privateKey(privateKey).keyUse(use);
            RSAKey key = keyId == null || keyId.isBlank() ? builder.keyIDFromThumbprint().build()
                    : builder.keyID(keyId).build();
            return key;
        } catch (IOException ex) {
            throw new IllegalStateException("portal strong-auth key file unreadable: " + file, ex);
        } catch (GeneralSecurityException | IllegalArgumentException | ClassCastException | JOSEException ex) {
            throw new IllegalStateException("portal strong-auth key file is not a PKCS#8 RSA private key: " + file, ex);
        }
    }
}
