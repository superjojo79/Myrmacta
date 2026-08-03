package com.flowforge.domain.connector.port.out;

import java.util.Map;

public interface ConnectorSecretCipher {

    Map<String, Object> encryptPasswordFields(String tenantId, Map<String, Object> config);

    Map<String, Object> decryptPasswordFields(String tenantId, Map<String, Object> config);
}
