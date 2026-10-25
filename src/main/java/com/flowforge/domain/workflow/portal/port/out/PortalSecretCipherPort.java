package com.flowforge.domain.workflow.portal.port.out;

/** TODO §104 — chiffrement du NRN et du vérificateur PKCE au repos (même clé maîtresse que les secrets de connecteur). */
public interface PortalSecretCipherPort {

    String encrypt(String plaintext);

    String decrypt(String ciphertext);
}
