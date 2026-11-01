package be.myrmacta.infrastructure.web.connector;

import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.oidcLogin;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import be.myrmacta.support.OidcClientTestConfiguration;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import tools.jackson.databind.json.JsonMapper;

@SpringBootTest(
        properties = {
            "app.connector.crypto.master-key=MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY=",
            "app.connector.kafka.max-instances-per-tenant=1"
        })
@AutoConfigureMockMvc
@Import(OidcClientTestConfiguration.class)
class ConnectorAdminApiTest {

    private static final String TENANT_ID = "tenant_dev";

    @Autowired
    private MockMvc mockMvc;

    @Test
    void listDefinitions_requiresTenantAdminAndReturnsHttpRestDefinition() throws Exception {
        mockMvc.perform(get("/api/connector-definitions")
                        .with(user("connector-admin", "TENANT_ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[*].type", hasItem("http-rest")))
                .andExpect(jsonPath("$.content[*].type", hasItem("flowable-signal")))
                .andExpect(jsonPath("$.content", isSortedByTypeAscending()));
    }

    @Test
    void listDefinitions_sizeRespectedAndSortRejectsUnsupportedValue() throws Exception {
        mockMvc.perform(get("/api/connector-definitions")
                        .param("page", "0")
                        .param("size", "1")
                        .with(user("connector-admin", "TENANT_ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.size").value(1));

        mockMvc.perform(get("/api/connector-definitions")
                        .param("sort", "label,asc")
                        .with(user("connector-admin", "TENANT_ADMIN")))
                .andExpect(status().isBadRequest());
    }

    private static org.hamcrest.Matcher<Iterable<? extends java.util.Map<String, Object>>> isSortedByTypeAscending() {
        return new org.hamcrest.TypeSafeMatcher<>() {
            @Override
            protected boolean matchesSafely(Iterable<? extends java.util.Map<String, Object>> items) {
                String previous = null;
                for (var item : items) {
                    String type = String.valueOf(item.get("type"));
                    if (previous != null && previous.compareTo(type) > 0) {
                        return false;
                    }
                    previous = type;
                }
                return true;
            }

            @Override
            public void describeTo(org.hamcrest.Description description) {
                description.appendText("content sorted by type ascending");
            }
        };
    }

    @Test
    void listDefinitions_registersExactlyThirteenTypesWithoutCollision_connecteursCibleLotB1_D3cD3d_etMailRoom() throws Exception {
        // T-REG-REGISTRY-COUNT : les 6 connecteurs déjà livrés (http-rest, flowable-signal,
        // flowable-callback, scheduler, smtp, document-storage) + batch-import + kafka + (TODO §87, D3c/D3d) imap-inbound,
        // graph-mail-inbound (D3c-bis), azure-service-bus et (TODO §113, D-MAILROOM-PDF-SFTP) sftp-pdf-inbound et mail-deposit (D-MAILROOM-DEPOSIT), tous
        // enregistrés sans collision de type au démarrage du contexte Spring réel (un doublon
        // ferait échouer ConnectorRegistry.init() et empêcherait ce test de démarrer).
        mockMvc.perform(get("/api/connector-definitions")
                        .param("size", "50")
                        .with(user("connector-admin", "TENANT_ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(13))
                .andExpect(jsonPath("$.content[*].type", hasItem("http-rest")))
                .andExpect(jsonPath("$.content[*].type", hasItem("flowable-signal")))
                .andExpect(jsonPath("$.content[*].type", hasItem("flowable-callback")))
                .andExpect(jsonPath("$.content[*].type", hasItem("scheduler")))
                .andExpect(jsonPath("$.content[*].type", hasItem("smtp")))
                .andExpect(jsonPath("$.content[*].type", hasItem("document-storage")))
                .andExpect(jsonPath("$.content[*].type", hasItem("batch-import")))
                .andExpect(jsonPath("$.content[*].type", hasItem("kafka")))
                .andExpect(jsonPath("$.content[*].type", hasItem("imap-inbound")))
                .andExpect(jsonPath("$.content[*].type", hasItem("graph-mail-inbound")))
                .andExpect(jsonPath("$.content[*].type", hasItem("azure-service-bus")))
                .andExpect(jsonPath("$.content[*].type", hasItem("sftp-pdf-inbound")))
                .andExpect(jsonPath("$.content[*].type", hasItem("mail-deposit")));
    }

    @Test
    void listDefinitions_nonAdmin_returns403() throws Exception {
        mockMvc.perform(get("/api/connector-definitions")
                        .with(user("viewer", "DOSSIER_VIEWER")))
                .andExpect(status().isForbidden());
    }

    @Test
    void createConnector_kafkaInstanceExceedingQuota_returns409_wiredThroughRealConfig() throws Exception {
        // Revue adversariale (connecteurs-cible, lot B1) : preuve bout en bout que
        // app.connector.kafka.max-instances-per-tenant (surchargé à 1 pour cette classe) atteint
        // bien ManageConnectorInstanceUseCaseImpl via le vrai câblage Spring (ConnectorConfig),
        // pas seulement au niveau unitaire mocké.
        String firstId = createKafka("Kafka quota 1 " + UUID.randomUUID());
        try {
            mockMvc.perform(post("/api/connectors")
                            .with(user("connector-admin", "TENANT_ADMIN"))
                            .with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(kafkaPayload("Kafka quota 2 " + UUID.randomUUID())))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.status").value(409));
        } finally {
            // Ne laisse pas une instance kafka activée dans la base après ce test : le
            // réconciliateur (poll @Scheduled réel dans ce contexte Spring) la reprendrait sinon
            // pour le reste de la suite.
            mockMvc.perform(delete("/api/connectors/{id}", firstId)
                    .with(user("connector-admin", "TENANT_ADMIN"))
                    .with(csrf()));
        }
    }

    @Test
    void createConnector_masksWebhookSecretInResponse() throws Exception {
        String name = "Claims webhook " + UUID.randomUUID();

        mockMvc.perform(post("/api/connectors")
                        .with(user("connector-admin", "TENANT_ADMIN"))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(httpRestPayload(name, "SHARED_SECRET_TOKEN")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value(name))
                .andExpect(jsonPath("$.config.webhookSecret").value("********"))
                .andExpect(jsonPath("$.secretConfigured.webhookSecret").value(true));
    }

    @Test
    void createConnector_masksAuthHeaderValueInResponse() throws Exception {
        // authHeaderValue matches no name heuristic (webhookSecret does) — only the connector
        // type's declared PASSWORD schema field catches it (adversarial review follow-up).
        String name = "Claims webhook " + UUID.randomUUID();

        mockMvc.perform(post("/api/connectors")
                        .with(user("connector-admin", "TENANT_ADMIN"))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "%s",
                                  "connectorType": "http-rest",
                                  "enabled": true,
                                  "config": {
                                    "webhookAuthType": "SHARED_SECRET_TOKEN",
                                    "webhookSecret": "plain-secret-0123456789",
                                    "processKey": "smokeProcess",
                                    "dossierTypeCode": "CLAIM",
                                    "authHeaderValue": "Bearer plain-credential-0123456789"
                                  }
                                }
                                """.formatted(name)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.config.authHeaderValue").value("********"))
                .andExpect(jsonPath("$.secretConfigured.authHeaderValue").value(true));
    }

    private static String oauthPayload(String name, String extra) {
        return """
                {
                  "name": "%s",
                  "connectorType": "http-rest",
                  "enabled": true,
                  "config": {
                    "webhookAuthType": "SHARED_SECRET_TOKEN",
                    "webhookSecret": "plain-secret-0123456789",
                    "processKey": "smokeProcess",
                    "dossierTypeCode": "CLAIM",
                    "authMode": "OAUTH2_CLIENT_CREDENTIALS",
                    "oauthTokenUrl": "https://93.184.216.34/tenant/oauth2/v2.0/token",
                    "oauthClientId": "app-1",
                    "oauthClientSecret": "client-secret-0123456789",
                    "oauthAllowedHosts": "erp.example.com"%s
                  }
                }
                """.formatted(name, extra);
    }

    /** D3a : la vraie chaîne HTTP/sécurité/chiffrement — secrets OAuth chiffrés puis masqués, validation appliquée. */
    @Test
    void createConnector_oauth2ClientCredentials_masksBothSecretsAndKeepsPublicFields() throws Exception {
        String name = "ERP oauth " + UUID.randomUUID();
        MvcResult created = mockMvc.perform(post("/api/connectors")
                        .with(user("connector-admin", "TENANT_ADMIN"))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(oauthPayload(name, ",\n\"oauthPrivateKey\": \"-----BEGIN PRIVATE KEY-----\\nQUJD\\n-----END PRIVATE KEY-----\"")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.config.oauthClientSecret").value("********"))
                .andExpect(jsonPath("$.config.oauthPrivateKey").value("********"))
                .andExpect(jsonPath("$.secretConfigured.oauthClientSecret").value(true))
                .andExpect(jsonPath("$.secretConfigured.oauthPrivateKey").value(true))
                .andExpect(jsonPath("$.config.oauthClientId").value("app-1"))
                .andExpect(jsonPath("$.config.authMode").value("OAUTH2_CLIENT_CREDENTIALS"))
                .andReturn();
        String body = created.getResponse().getContentAsString();
        org.assertj.core.api.Assertions.assertThat(body).doesNotContain("client-secret-0123456789", "QUJD");
        String id = JsonMapper.builder().build().readTree(body).get("id").asString();

        String fetched = mockMvc.perform(get("/api/connectors/{id}", id)
                        .with(user("connector-admin", "TENANT_ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.config.oauthClientSecret").value("********"))
                .andReturn().getResponse().getContentAsString();
        org.assertj.core.api.Assertions.assertThat(fetched).doesNotContain("client-secret-0123456789");
    }

    /** Migration STATIC_HEADER → OAuth2 sans recréer l'instance : l'ancien en-tête (masqué, conservé) est simplement ignoré. */
    @Test
    void updateConnector_fromStaticHeaderToOauth2_isPossibleWithoutRecreating() throws Exception {
        String name = "Migrating " + UUID.randomUUID();
        MvcResult created = mockMvc.perform(post("/api/connectors")
                        .with(user("connector-admin", "TENANT_ADMIN"))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(httpRestPayload(name, "SHARED_SECRET_TOKEN").replace("\"config\": {",
                                "\"config\": {\"authHeaderValue\": \"Bearer legacy-credential-0123\",")))
                .andExpect(status().isCreated()).andReturn();
        String id = JsonMapper.builder().build().readTree(created.getResponse().getContentAsString()).get("id").asString();

        mockMvc.perform(patch("/api/connectors/{id}", id)
                        .with(user("connector-admin", "TENANT_ADMIN"))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"config": {"authMode": "OAUTH2_CLIENT_CREDENTIALS",
                                  "oauthTokenUrl": "https://93.184.216.34/token", "oauthClientId": "app-1",
                                  "oauthClientSecret": "client-secret-0123456789", "oauthAllowedHosts": "erp.example.com"}}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.config.authMode").value("OAUTH2_CLIENT_CREDENTIALS"))
                .andExpect(jsonPath("$.config.oauthClientSecret").value("********"));
    }

    @Test
    void createConnector_withAMaskedPlaceholderAsASecret_returns400() throws Exception {
        mockMvc.perform(post("/api/connectors")
                        .with(user("connector-admin", "TENANT_ADMIN"))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(oauthPayload("Masked " + UUID.randomUUID(), "")
                                .replace("client-secret-0123456789", "********")))
                .andExpect(status().isBadRequest());
    }

    @Test
    void createConnector_oauth2WithALoopbackTokenUrl_returns400() throws Exception {
        mockMvc.perform(post("/api/connectors")
                        .with(user("connector-admin", "TENANT_ADMIN"))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(oauthPayload("Loopback " + UUID.randomUUID(), "")
                                .replace("93.184.216.34", "127.0.0.1")))
                .andExpect(status().isBadRequest());
    }

    @Test
    void createConnector_ipAllowlist_returns400() throws Exception {
        mockMvc.perform(post("/api/connectors")
                        .with(user("connector-admin", "TENANT_ADMIN"))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "Blocked %s",
                                  "connectorType": "http-rest",
                                  "enabled": true,
                                  "config": {
                                    "webhookAuthType": "IP_ALLOWLIST",
                                    "webhookSecret": "x",
                                    "processKey": "smokeProcess",
                                    "dossierTypeCode": "CLAIM"
                                  }
                                }
                                """.formatted(UUID.randomUUID())))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));
    }

    @Test
    void createConnector_duplicateActiveName_returns409() throws Exception {
        String name = "Dup name " + UUID.randomUUID();
        createHttpRest(name);

        mockMvc.perform(post("/api/connectors")
                        .with(user("connector-admin", "TENANT_ADMIN"))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(httpRestPayload(name, "SHARED_SECRET_TOKEN")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409));
    }

    @Test
    void softDelete_hidesFromListAndGetReturns404_restoreMakesVisibleAgain() throws Exception {
        String name = "Soft del " + UUID.randomUUID();
        String id = createHttpRest(name);

        mockMvc.perform(delete("/api/connectors/{id}", id)
                        .with(user("connector-admin", "TENANT_ADMIN"))
                        .with(csrf()))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/connectors/{id}", id)
                        .with(user("connector-admin", "TENANT_ADMIN")))
                .andExpect(status().isNotFound());

        mockMvc.perform(get("/api/connectors")
                        .with(user("connector-admin", "TENANT_ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[*].name", not(hasItem(name))));

        mockMvc.perform(get("/api/connectors")
                        .param("sort", "name,desc")
                        .with(user("connector-admin", "TENANT_ADMIN")))
                .andExpect(status().isBadRequest());

        mockMvc.perform(patch("/api/connectors/{id}", id)
                        .with(user("connector-admin", "TENANT_ADMIN"))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"restore\": true}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value(name))
                .andExpect(jsonPath("$.enabled").value(true));

        mockMvc.perform(get("/api/connectors/{id}", id)
                        .with(user("connector-admin", "TENANT_ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value(name));
    }

    @Test
    void softDelete_thenReuseSameName_succeeds() throws Exception {
        String name = "Reuse name " + UUID.randomUUID();
        String id = createHttpRest(name);

        mockMvc.perform(delete("/api/connectors/{id}", id)
                        .with(user("connector-admin", "TENANT_ADMIN"))
                        .with(csrf()))
                .andExpect(status().isNoContent());

        mockMvc.perform(post("/api/connectors")
                        .with(user("connector-admin", "TENANT_ADMIN"))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(httpRestPayload(name, "SHARED_SECRET_TOKEN")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value(name));
    }

    @Test
    void listConnectors_returnsEnvelopeDefaultsAndRejectsOversizedPage() throws Exception {
        mockMvc.perform(get("/api/connectors")
                        .with(user("connector-admin", "TENANT_ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray())
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(20))
                .andExpect(jsonPath("$.totalElements").value(org.hamcrest.Matchers.greaterThanOrEqualTo(0)))
                .andExpect(jsonPath("$.totalPages").value(org.hamcrest.Matchers.greaterThanOrEqualTo(0)));

        mockMvc.perform(get("/api/connectors")
                        .param("size", "101")
                        .with(user("connector-admin", "TENANT_ADMIN")))
                .andExpect(status().isBadRequest());
    }

    @Test
    void patchRestore_nonAdmin_returns403() throws Exception {
        String id = createHttpRest("Restore denied " + UUID.randomUUID());
        mockMvc.perform(delete("/api/connectors/{id}", id)
                        .with(user("connector-admin", "TENANT_ADMIN"))
                        .with(csrf()))
                .andExpect(status().isNoContent());

        mockMvc.perform(patch("/api/connectors/{id}", id)
                        .with(user("viewer", "DOSSIER_VIEWER"))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"restore\": true}"))
                .andExpect(status().isForbidden());
    }

    private String createHttpRest(String name) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/connectors")
                        .with(user("connector-admin", "TENANT_ADMIN"))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(httpRestPayload(name, "SHARED_SECRET_TOKEN")))
                .andExpect(status().isCreated())
                .andReturn();
        return JsonMapper.builder().build()
                .readTree(result.getResponse().getContentAsString())
                .get("id")
                .asText();
    }

    private String createKafka(String name) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/connectors")
                        .with(user("connector-admin", "TENANT_ADMIN"))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(kafkaPayload(name)))
                .andExpect(status().isCreated())
                .andReturn();
        return JsonMapper.builder().build()
                .readTree(result.getResponse().getContentAsString())
                .get("id")
                .asText();
    }

    private static String kafkaPayload(String name) {
        return """
                {
                  "name": "%s",
                  "connectorType": "kafka",
                  "enabled": true,
                  "config": {
                    "bootstrapServers": "localhost:9092",
                    "topic": "claims.events",
                    "groupId": "myrmacta-quota-test",
                    "securityProtocol": "PLAINTEXT",
                    "processKey": "smokeProcess",
                    "dossierTypeCode": "CLAIM"
                  }
                }
                """.formatted(name);
    }

    private static String httpRestPayload(String name, String authType) {
        return """
                {
                  "name": "%s",
                  "connectorType": "http-rest",
                  "enabled": true,
                  "config": {
                    "webhookAuthType": "%s",
                    "webhookSecret": "plain-secret-0123456789",
                    "processKey": "smokeProcess",
                    "dossierTypeCode": "CLAIM"
                  }
                }
                """.formatted(name, authType);
    }

    private static RequestPostProcessor user(String subject, String... roles) {
        return oidcLogin().idToken(token -> token
                        .subject(subject)
                        .claim("tenant_id", TENANT_ID))
                .authorities(java.util.Arrays.stream(roles)
                        .map(role -> role.startsWith("ROLE_") ? role : "ROLE_" + role)
                        .map(SimpleGrantedAuthority::new)
                        .toArray(SimpleGrantedAuthority[]::new));
    }
}
