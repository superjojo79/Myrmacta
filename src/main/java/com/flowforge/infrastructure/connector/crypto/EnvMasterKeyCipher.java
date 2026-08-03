package com.flowforge.infrastructure.connector.crypto;

import com.flowforge.domain.connector.port.out.ConnectorSecretCipher;
import com.flowforge.infrastructure.config.ConnectorProperties;
import java.nio.ByteBuffer;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.stereotype.Component;

@Component
public class EnvMasterKeyCipher implements ConnectorSecretCipher {

    private static final String ENC_PREFIX = "enc:v1:";
    private static final int GCM_IV_LENGTH = 12;
    private static final int GCM_TAG_BITS = 128;

    private final byte[] masterKeyBytes;
    private final TenantDekStore dekStore;
    private final SecureRandom secureRandom = new SecureRandom();

    public EnvMasterKeyCipher(ConnectorProperties properties, TenantDekStore dekStore) {
        this.dekStore = Objects.requireNonNull(dekStore, "dekStore");
        String masterKey = properties.crypto().masterKey();
        if (masterKey == null || masterKey.isBlank()) {
            this.masterKeyBytes = null;
        } else {
            byte[] decoded = Base64.getDecoder().decode(masterKey);
            if (decoded.length != 32) {
                throw new IllegalStateException(
                        "app.connector.crypto.master-key must decode to 32 bytes");
            }
            this.masterKeyBytes = decoded;
        }
    }

    @Override
    public Map<String, Object> encryptPasswordFields(String tenantId, Map<String, Object> config) {
        if (config == null || config.isEmpty()) {
            return config == null ? Map.of() : config;
        }
        SecretKey dek = resolveDek(tenantId);
        Map<String, Object> out = new LinkedHashMap<>(config);
        for (Map.Entry<String, Object> entry : config.entrySet()) {
            if (!isPasswordKey(entry.getKey()) || !(entry.getValue() instanceof String value)) {
                continue;
            }
            if (value.startsWith(ENC_PREFIX)) {
                continue;
            }
            out.put(entry.getKey(), ENC_PREFIX + encryptToBase64(dek, value.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        }
        return Map.copyOf(out);
    }

    @Override
    public Map<String, Object> decryptPasswordFields(String tenantId, Map<String, Object> config) {
        if (config == null || config.isEmpty()) {
            return config == null ? Map.of() : config;
        }
        SecretKey dek = resolveDek(tenantId);
        Map<String, Object> out = new LinkedHashMap<>(config);
        for (Map.Entry<String, Object> entry : config.entrySet()) {
            if (!isPasswordKey(entry.getKey()) || !(entry.getValue() instanceof String value)) {
                continue;
            }
            if (!value.startsWith(ENC_PREFIX)) {
                continue;
            }
            byte[] plain = decryptFromBase64(dek, value.substring(ENC_PREFIX.length()));
            out.put(entry.getKey(), new String(plain, java.nio.charset.StandardCharsets.UTF_8));
        }
        return Map.copyOf(out);
    }

    static boolean isPasswordKey(String key) {
        String k = key.toLowerCase(Locale.ROOT);
        return k.equals("webhooksecret")
                || k.equals("clientsecret")
                || k.equals("password")
                || k.endsWith("secret")
                || k.endsWith("password");
    }

    private SecretKey resolveDek(String tenantId) {
        requireMasterKey();
        return dekStore
                .findWrappedDek(tenantId)
                .map(wrapped -> unwrapDek(wrapped))
                .orElseGet(() -> createAndStoreDek(tenantId));
    }

    private SecretKey createAndStoreDek(String tenantId) {
        try {
            KeyGenerator keyGen = KeyGenerator.getInstance("AES");
            keyGen.init(256, secureRandom);
            SecretKey dek = keyGen.generateKey();
            String wrapped = encryptToBase64(masterKey(), dek.getEncoded());
            dekStore.saveWrappedDek(tenantId, wrapped, 1);
            return unwrapDek(dekStore.findWrappedDek(tenantId).orElseThrow());
        } catch (GeneralSecurityException ex) {
            throw new IllegalStateException("Failed to create tenant DEK", ex);
        }
    }

    private SecretKey unwrapDek(String wrapped) {
        byte[] raw = decryptFromBase64(masterKey(), wrapped);
        return new SecretKeySpec(raw, "AES");
    }

    private SecretKey masterKey() {
        requireMasterKey();
        return new SecretKeySpec(masterKeyBytes, "AES");
    }

    private void requireMasterKey() {
        if (masterKeyBytes == null) {
            throw new IllegalStateException(
                    "app.connector.crypto.master-key is required to encrypt connector secrets");
        }
    }

    private String encryptToBase64(SecretKey key, byte[] plaintext) {
        try {
            byte[] iv = new byte[GCM_IV_LENGTH];
            secureRandom.nextBytes(iv);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(GCM_TAG_BITS, iv));
            byte[] ciphertext = cipher.doFinal(plaintext);
            ByteBuffer buffer = ByteBuffer.allocate(iv.length + ciphertext.length);
            buffer.put(iv);
            buffer.put(ciphertext);
            return Base64.getEncoder().encodeToString(buffer.array());
        } catch (GeneralSecurityException ex) {
            throw new IllegalStateException("AES-GCM encrypt failed", ex);
        }
    }

    private byte[] decryptFromBase64(SecretKey key, String payload) {
        try {
            byte[] raw = Base64.getDecoder().decode(payload);
            ByteBuffer buffer = ByteBuffer.wrap(raw);
            byte[] iv = new byte[GCM_IV_LENGTH];
            buffer.get(iv);
            byte[] ciphertext = new byte[buffer.remaining()];
            buffer.get(ciphertext);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(GCM_TAG_BITS, iv));
            return cipher.doFinal(ciphertext);
        } catch (GeneralSecurityException | IllegalArgumentException ex) {
            throw new IllegalStateException("AES-GCM decrypt failed", ex);
        }
    }
}
