package be.myrmacta.infrastructure.web.connector;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import be.myrmacta.domain.connector.model.ConfigField;
import be.myrmacta.domain.connector.model.ConfigFieldType;
import be.myrmacta.domain.connector.model.ConnectorDefinition;
import be.myrmacta.domain.connector.model.ConnectorInstanceData;
import be.myrmacta.domain.connector.port.out.ConnectorCatalog;
import java.util.List;
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

        ConnectorResponse response = ConnectorResponse.from(data, mock(ConnectorCatalog.class));
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

        ConnectorResponse response = ConnectorResponse.from(data, mock(ConnectorCatalog.class));
        String json = new ObjectMapper().writeValueAsString(response);

        assertThat(json).doesNotContain(KNOWN_SECRET);
    }

    @Test
    void from_neverExposesSchemaDeclaredPasswordFieldNotMatchingNameHeuristic() throws Exception {
        // authHeaderValue matches no name heuristic (adversarial review follow-up) — only the
        // connector type's declared PASSWORD schema field catches it.
        ConnectorInstanceData data = new ConnectorInstanceData(
                UUID.randomUUID(), "tenant_dev", "http-rest-trigger", "http-rest", true,
                Map.of("authHeaderValue", KNOWN_SECRET));
        ConnectorDefinition definition = mock(ConnectorDefinition.class);
        when(definition.getConfigSchema()).thenReturn(List.of(new ConfigField(
                "authHeaderValue", "Auth header", ConfigFieldType.PASSWORD, false, null, List.of(), null)));
        ConnectorCatalog catalog = mock(ConnectorCatalog.class);
        when(catalog.get("http-rest")).thenReturn(definition);
        when(catalog.isPasswordField("http-rest", "authHeaderValue")).thenCallRealMethod();

        ConnectorResponse response = ConnectorResponse.from(data, catalog);
        String json = new ObjectMapper().writeValueAsString(response);

        assertThat(json).doesNotContain(KNOWN_SECRET);
        assertThat(response.secretConfigured()).containsEntry("authHeaderValue", true);
    }
}
