package com.flowforge.domain.connector.port.out;

import java.util.Map;

public interface ConnectorSecretCipher {

    /**
     * {@code connectorType} lets the implementation consult the connector's declared config
     * schema (which fields are {@code PASSWORD}) in addition to any name-based heuristic — a
     * field like {@code authHeaderValue} carries a real credential but doesn't match a name
     * pattern (adversarial review follow-up, extension-mcp-flux-metier).
     */
    Map<String, Object> encryptPasswordFields(String tenantId, String connectorType, Map<String, Object> config);

    Map<String, Object> decryptPasswordFields(String tenantId, String connectorType, Map<String, Object> config);

    /** Encrypts a single secret string (AES-GCM, {@code enc:v1:} prefix). */
    String encrypt(String tenantId, String plaintext);

    /** Decrypts a ciphertext that must start with {@code enc:v1:}. */
    String decrypt(String tenantId, String ciphertext);
}
