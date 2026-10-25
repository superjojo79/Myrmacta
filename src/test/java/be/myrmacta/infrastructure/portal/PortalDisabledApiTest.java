package be.myrmacta.infrastructure.portal;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import be.myrmacta.support.OidcClientTestConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

/** TODO §68 T-SEC-1 — portail désactivé (défaut) : aucune route publique. */
@SpringBootTest
@AutoConfigureMockMvc
@Import(OidcClientTestConfiguration.class)
class PortalDisabledApiTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void portalApiAndPage_fallIntoTheAuthenticatedChain() throws Exception {
        mockMvc.perform(get("/api/portal/task").header("X-Portal-Token", "x".repeat(43)))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/portal/")).andExpect(status().is3xxRedirection());
    }
}
