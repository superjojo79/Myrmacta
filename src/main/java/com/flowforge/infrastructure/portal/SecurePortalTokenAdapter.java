package com.flowforge.infrastructure.portal;

import com.flowforge.domain.workflow.portal.port.out.PortalTokenPort;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;
import org.springframework.stereotype.Component;

/**
 * D-PORTAL-TOKEN-FORMAT — 32 octets {@link SecureRandom} (256 bits) en base64url sans padding
 * (43 caractères). Stockage : SHA-256 hexadécimal non salé — avec 256 bits d'entropie, un sel
 * n'apporte rien contre la force brute, et la recherche par index unique sur le hash évite toute
 * comparaison applicative à temps variable.
 */
@Component
class SecurePortalTokenAdapter implements PortalTokenPort {

    private static final int TOKEN_BYTES = 32;

    private final SecureRandom random = new SecureRandom();

    @Override
    public String newToken() {
        byte[] bytes = new byte[TOKEN_BYTES];
        random.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    @Override
    public String hash(String token) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 unavailable", ex);
        }
    }
}
