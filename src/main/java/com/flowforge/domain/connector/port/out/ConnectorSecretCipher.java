package com.flowforge.domain.connector.port.out;

import java.util.Map;

public interface ConnectorSecretCipher {

    Map<String, Object> encryptPasswordFields(String tenantId, Map<String, Object> config);

    Map<String, Object> decryptPasswordFields(String tenantId, Map<String, Object> config);

    /** Encrypts a single secret string (AES-GCM, {@code enc:v1:} prefix). */
    String encrypt(String tenantId, String plaintext);

    /** Decrypts a ciphertext that must start with {@code enc:v1:}. */
    String decrypt(String tenantId, String ciphertext);
}
