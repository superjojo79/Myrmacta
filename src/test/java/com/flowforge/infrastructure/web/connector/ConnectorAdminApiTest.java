package com.flowforge.infrastructure.web.connector;

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

import com.flowforge.support.OidcClientTestConfiguration;
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

@SpringBootTest(properties = "app.connector.crypto.master-key=MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY=")
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
    void listDefinitions_nonAdmin_returns403() throws Exception {
        mockMvc.perform(get("/api/connector-definitions")
                        .with(user("viewer", "DOSSIER_VIEWER")))
                .andExpect(status().isForbidden());
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
