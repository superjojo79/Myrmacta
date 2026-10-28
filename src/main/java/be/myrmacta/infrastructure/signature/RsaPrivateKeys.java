package be.myrmacta.infrastructure.signature;

import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.util.Base64;

/** Clé RSA PKCS#8 ou PKCS#1 (PEM). DocuSign remet la clé générée au format PKCS#1. */
final class RsaPrivateKeys {

    private static final byte[] RSA_ALGORITHM_ID = new byte[] {
        0x30, 0x0d,
        0x06, 0x09, 0x2a, (byte) 0x86, 0x48, (byte) 0x86, (byte) 0xf7, 0x0d, 0x01, 0x01, 0x01,
        0x05, 0x00
    };

    private RsaPrivateKeys() {}

    static PrivateKey parse(String pem) throws GeneralSecurityException {
        if (pem == null || pem.isBlank()) {
            throw new GeneralSecurityException("missing key");
        }
        boolean pkcs1 = pem.contains("BEGIN RSA PRIVATE KEY");
        String base64 = pem.replaceAll("-----(BEGIN|END) RSA PRIVATE KEY-----", "")
                .replaceAll("-----(BEGIN|END) PRIVATE KEY-----", "")
                .replaceAll("\\s", "");
        byte[] der;
        try {
            der = Base64.getMimeDecoder().decode(base64);
        } catch (IllegalArgumentException ex) {
            throw new GeneralSecurityException("invalid key encoding");
        }
        if (pkcs1) {
            der = pkcs8FromPkcs1(der);
        }
        try {
            return KeyFactory.getInstance("RSA").generatePrivate(new PKCS8EncodedKeySpec(der));
        } catch (IllegalArgumentException | GeneralSecurityException ex) {
            throw new GeneralSecurityException("invalid key encoding");
        }
    }

    /** Enveloppe PKCS#8 autour d'une clé PKCS#1, sans bibliothèque tierce. */
    private static byte[] pkcs8FromPkcs1(byte[] pkcs1) {
        byte[] version = new byte[] {0x02, 0x01, 0x00};
        byte[] octet = der(0x04, pkcs1);
        return der(0x30, concat(version, RSA_ALGORITHM_ID, octet));
    }

    private static byte[] der(int tag, byte[] content) {
        byte[] length = length(content.length);
        byte[] out = new byte[1 + length.length + content.length];
        out[0] = (byte) tag;
        System.arraycopy(length, 0, out, 1, length.length);
        System.arraycopy(content, 0, out, 1 + length.length, content.length);
        return out;
    }

    private static byte[] length(int n) {
        if (n < 0x80) {
            return new byte[] {(byte) n};
        }
        if (n <= 0xff) {
            return new byte[] {(byte) 0x81, (byte) n};
        }
        if (n <= 0xffff) {
            return new byte[] {(byte) 0x82, (byte) (n >> 8), (byte) n};
        }
        return new byte[] {(byte) 0x83, (byte) (n >> 16), (byte) (n >> 8), (byte) n};
    }

    private static byte[] concat(byte[]... parts) {
        int size = 0;
        for (byte[] part : parts) {
            size += part.length;
        }
        byte[] out = new byte[size];
        int offset = 0;
        for (byte[] part : parts) {
            System.arraycopy(part, 0, out, offset, part.length);
            offset += part.length;
        }
        return out;
    }
}
