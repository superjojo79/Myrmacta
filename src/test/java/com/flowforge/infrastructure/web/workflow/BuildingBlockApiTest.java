package com.flowforge.infrastructure.web.workflow;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.oidcLogin;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.flowforge.domain.connector.model.ConnectorInstanceData;
import com.flowforge.domain.connector.port.out.ConnectorInstanceRepository;
import com.flowforge.support.OidcClientTestConfiguration;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

/** S-CAT-4, S-REF-1..S-REF-4, T-SEC-1 — API du catalogue et des références de la palette (TODO §73, M1). */
@SpringBootTest
@AutoConfigureMockMvc
@Import(OidcClientTestConfiguration.class)
class BuildingBlockApiTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ConnectorInstanceRepository connectors;

    private static RequestPostProcessor as(String role) {
        return oidcLogin()
                .idToken(t -> t.subject("u-" + role).claim("tenant_id", "tenant_dev"))
                .authorities(new SimpleGrantedAuthority("ROLE_" + role));
    }

    @Test
    void catalogIsReadableByBothEditorRoles() throws Exception {
        for (String role : new String[] {"WORKFLOW_ADMIN", "TENANT_ADMIN"}) {
            mockMvc.perform(get("/api/bpmn-building-blocks").with(as(role)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.version").value(1))
                    .andExpect(jsonPath("$.blocks.length()").value(11))
                    .andExpect(jsonPath("$.blocks[0].id").value("generate-document"))
                    .andExpect(jsonPath("$.blocks[0].parameters[0].name").value("documentType"));
        }
    }

    @Test
    void otherRolesAndAnonymousAreRefused() throws Exception {
        mockMvc.perform(get("/api/bpmn-building-blocks").with(as("DOSSIER_MANAGER")))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/bpmn-building-blocks/references/documentTemplates").with(as("DOSSIER_MANAGER")))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/bpmn-building-blocks")).andExpect(status().isUnauthorized());
    }

    @Test
    void workflowAdminGetsTheReferenceListsOfTheAdministrationSources() throws Exception {
        for (String source : new String[] {"documentTemplates", "connectorInstances", "notificationWebhooks"}) {
            mockMvc.perform(get("/api/bpmn-building-blocks/references/" + source).with(as("WORKFLOW_ADMIN")))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$").isArray());
        }
    }

    @Test
    void unknownOrNotServedSourceIs404() throws Exception {
        for (String source : new String[] {"nope", "dossierTypes", "dmnTables"}) {
            mockMvc.perform(get("/api/bpmn-building-blocks/references/" + source).with(as("TENANT_ADMIN")))
                    .andExpect(status().isNotFound());
        }
    }

    @Test
    void connectorReferencesExposeOnlyValueAndLabelNeverTheConfiguration() throws Exception {
        String secret = "s3cr3t-" + System.nanoTime();
        UUID id = UUID.randomUUID();
        connectors.save(new ConnectorInstanceData(id, "tenant_dev", "erp-" + System.nanoTime(), "http-rest", true,
                Map.of("baseUrl", "https://example.org", "token", secret)));

        String body = mockMvc.perform(get("/api/bpmn-building-blocks/references/connectorInstances").with(as("WORKFLOW_ADMIN")))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        org.assertj.core.api.Assertions.assertThat(body).contains(id.toString()).doesNotContain(secret)
                .doesNotContain("baseUrl").doesNotContain("config");
        org.assertj.core.api.Assertions.assertThat(body.replaceAll("\\{\"value\":\"[^\"]*\",\"label\":\"[^\"]*\"}", ""))
                .isIn("[]", "[,]", "[,,]", "[,,,]", "[,,,,]", "[,,,,,]");
    }
}
