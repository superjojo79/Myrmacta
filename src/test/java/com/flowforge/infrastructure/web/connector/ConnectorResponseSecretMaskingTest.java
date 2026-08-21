package com.flowforge.infrastructure.web.connector;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.flowforge.domain.connector.model.ConnectorInstanceData;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * D-SECRETS-AUDIT garde-fou : ce test doit rester rouge si une future modification de
 * {@link ConnectorResponse} réintroduit une fuite de secret en clair dans la réponse admin
 * connecteurs. Ne teste pas un scénario métier — sert uniquement de filet anti-régression.
 */
class ConnectorResponseSecretMaskingTest {

    private static final String KNOWN_SECRET = "s3cr3t-connector-value-should-never-leak";

    @Test
    void from_neverExposesSecretValueInSerializedResponse() throws Exception {
        ConnectorInstanceData data = new ConnectorInstanceData(
                UUID.randomUUID(),
                "tenant_dev",
                "http-rest-trigger",
                "http-rest",
                true,
                Map.of("webhookSecret", KNOWN_SECRET, "processKey", "wf-1"));

        ConnectorResponse response = ConnectorResponse.from(data);
        String json = new ObjectMapper().writeValueAsString(response);

        assertThat(json).doesNotContain(KNOWN_SECRET);
        assertThat(response.secretConfigured()).containsEntry("webhookSecret", true);
    }

    @Test
    void from_neverExposesPasswordValueInSerializedResponse() throws Exception {
        ConnectorInstanceData data = new ConnectorInstanceData(
                UUID.randomUUID(),
                "tenant_dev",
                "some-connector",
                "some-type",
                true,
                Map.of("password", KNOWN_SECRET));

        ConnectorResponse response = ConnectorResponse.from(data);
        String json = new ObjectMapper().writeValueAsString(response);

        assertThat(json).doesNotContain(KNOWN_SECRET);
    }
}
