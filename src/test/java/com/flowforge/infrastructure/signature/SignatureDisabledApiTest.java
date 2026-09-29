package com.flowforge.infrastructure.signature;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.flowforge.domain.signature.port.out.SignaturePort;
import com.flowforge.support.OidcClientTestConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

/**
 * S-ACCESS-8, S-CONN-4, S-STUB-7 — fournisseur {@code none} (défaut), portail activé : aucune route
 * signature, aucun bean d'exécution. Le portail (tâches externes) reste, lui, opérationnel.
 */
@SpringBootTest(properties = {
        "app.portal.enabled=true",
        "app.portal.public-base-url=https://ff.example.org/tenant-a"
})
@AutoConfigureMockMvc
@Import(OidcClientTestConfiguration.class)
class SignatureDisabledApiTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ApplicationContext context;

    @Test
    void signatureRoutesAndBeansAreAbsentWithoutProvider() throws Exception {
        mockMvc.perform(get("/api/portal/signature").header("X-Portal-Token", "x".repeat(43)))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/api/portal/signature/document").header("X-Portal-Token", "x".repeat(43)))
                .andExpect(status().isNotFound());
        assertThat(context.getBeanNamesForType(SignaturePort.class)).isEmpty();
        assertThat(context.containsBean("signatureRequestTask")).isFalse();
        assertThat(context.containsBean("signatureInviteSenderJob")).isFalse();
    }
}
