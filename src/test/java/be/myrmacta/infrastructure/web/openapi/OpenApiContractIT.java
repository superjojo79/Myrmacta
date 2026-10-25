package be.myrmacta.infrastructure.web.openapi;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.oidcLogin;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import be.myrmacta.support.OidcClientTestConfiguration;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

// TODO §68 : portail activé pour que l'API publique /api/portal/** (conditionnelle) figure au contrat.
@SpringBootTest(properties = {"app.openapi.enabled=true", "springdoc.api-docs.enabled=true", "springdoc.swagger-ui.enabled=true",
        "app.portal.enabled=true", "app.portal.public-base-url=https://flowforge.example.org/tenant",
        "app.signature.provider=stub"})
// TODO §72 : provider stub (profil test) pour que les routes de signature figurent au contrat.
@ActiveProfiles("test")
@AutoConfigureMockMvc
@Import(OidcClientTestConfiguration.class)
class OpenApiContractIT {

    private static final Path GOLDEN = Path.of("openapi/openapi.yaml");

    @Autowired
    private MockMvc mockMvc;

    @Test
    void exportedYamlMatchesGoldenFile() throws Exception {
        String actual = mockMvc.perform(get("/v3/api-docs.yaml").with(admin()))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString(StandardCharsets.UTF_8)
                .replace("\r\n", "\n");
        if (Boolean.getBoolean("openapi.update")) {
            Files.createDirectories(GOLDEN.getParent());
            Files.writeString(GOLDEN, actual);
        }
        String expected = Files.readString(GOLDEN).replace("\r\n", "\n");
        assertThat(actual).isEqualTo(expected);
    }

    private static RequestPostProcessor admin() {
        return oidcLogin()
                .idToken(token -> token
                        .subject("openapi-admin")
                        .claim("tenant_id", "tenant_dev")
                        .claim("email", "openapi-admin@example.com")
                        .claim("name", "openapi-admin"))
                .authorities(List.of(new SimpleGrantedAuthority("ROLE_TENANT_ADMIN")));
    }
}
