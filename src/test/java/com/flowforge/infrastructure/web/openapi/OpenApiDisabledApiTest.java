package com.flowforge.infrastructure.web.openapi;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.oidcLogin;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

import com.flowforge.support.OidcClientTestConfiguration;
import java.util.Arrays;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

@SpringBootTest
@AutoConfigureMockMvc
@Import(OidcClientTestConfiguration.class)
class OpenApiDisabledApiTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void docsAreNotServedWhenFlagIsOff() throws Exception {
        mockMvc.perform(get("/v3/api-docs")).andExpect(OpenApiDisabledApiTest::assertNotOpenApiDocument);
        mockMvc.perform(get("/v3/api-docs").with(user("admin", "TENANT_ADMIN")))
                .andExpect(OpenApiDisabledApiTest::assertNotOpenApiDocument);
        mockMvc.perform(get("/v3/api-docs.yaml").with(user("admin", "TENANT_ADMIN")))
                .andExpect(OpenApiDisabledApiTest::assertNotOpenApiDocument);
        mockMvc.perform(get("/swagger-ui.html").with(user("admin", "TENANT_ADMIN")))
                .andExpect(OpenApiDisabledApiTest::assertNotSwaggerUi);
    }

    private static void assertNotOpenApiDocument(MvcResult result) throws Exception {
        String type = result.getResponse().getContentType();
        String body = result.getResponse().getContentAsString();
        boolean openApiJson = result.getResponse().getStatus() == 200
                && type != null
                && type.contains("json")
                && body.contains("\"openapi\"");
        boolean openApiYaml = result.getResponse().getStatus() == 200 && body.startsWith("openapi:");
        if (openApiJson || openApiYaml) {
            throw new AssertionError("OpenAPI docs must not be served when disabled");
        }
    }

    private static void assertNotSwaggerUi(MvcResult result) throws Exception {
        String body = result.getResponse().getContentAsString();
        if (result.getResponse().getStatus() == 200 && body.contains("Swagger UI")) {
            throw new AssertionError("Swagger UI must not return 200 when disabled");
        }
    }

    private static RequestPostProcessor user(String userId, String... roles) {
        return oidcLogin()
                .idToken(token -> token
                        .subject(userId)
                        .claim("tenant_id", "tenant_dev")
                        .claim("email", userId + "@example.com")
                        .claim("name", userId))
                .authorities(Arrays.stream(roles)
                        .map(role -> new SimpleGrantedAuthority("ROLE_" + role))
                        .toArray(SimpleGrantedAuthority[]::new));
    }
}
