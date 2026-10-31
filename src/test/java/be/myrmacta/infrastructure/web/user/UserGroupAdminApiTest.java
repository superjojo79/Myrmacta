package be.myrmacta.infrastructure.web.user;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.oidcLogin;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import be.myrmacta.domain.user.model.KnownUser;
import be.myrmacta.domain.user.port.out.KnownUserRepository;
import be.myrmacta.support.OidcClientTestConfiguration;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

/** TODO §114 (issue #190) — administration des groupes par la vraie chaîne HTTP et sécurité. */
@SpringBootTest
@AutoConfigureMockMvc
@Import(OidcClientTestConfiguration.class)
class UserGroupAdminApiTest {

    private static final String TENANT = "tenant_dev";

    @Autowired private MockMvc mockMvc;
    @Autowired private KnownUserRepository knownUsers;

    @Test
    void anAdminCreatesAGroupAddsAKnownMemberAndDeletesIt() throws Exception {
        String code = code();
        String member = "agent-" + UUID.randomUUID();
        knownUsers.upsert(new KnownUser(TENANT, member, "Agent Compta", "compta@example.org", Set.of("DOSSIER_MANAGER"),
                Instant.parse("2026-10-31T08:00:00Z")));

        mockMvc.perform(post("/api/user-groups").with(admin()).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\"" + code + "\",\"label\":\"Comptabilité\",\"idpGroup\":\"/services/compta\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.code").value(code))
                .andExpect(jsonPath("$.idpGroup").value("/services/compta"))
                .andExpect(jsonPath("$.members").isEmpty());
        mockMvc.perform(put("/api/user-groups/{code}/members/{userId}", code, member).with(admin()).with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.members[0].userId").value(member))
                .andExpect(jsonPath("$.members[0].displayName").value("Agent Compta"));
        mockMvc.perform(put("/api/user-groups/{code}", code).with(admin()).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"label\":\"Service comptable\",\"idpGroup\":null}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.label").value("Service comptable"))
                .andExpect(jsonPath("$.idpGroup").doesNotExist())
                .andExpect(jsonPath("$.members[0].userId").value(member));
        mockMvc.perform(get("/api/user-groups").with(admin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.code == '" + code + "')].label").value("Service comptable"));
        mockMvc.perform(get("/api/user-groups/directory").with(admin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.userId == '" + member + "')].displayName").value("Agent Compta"));
        mockMvc.perform(delete("/api/user-groups/{code}/members/{userId}", code, member).with(admin()).with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.members").isEmpty());
        mockMvc.perform(delete("/api/user-groups/{code}", code).with(admin()).with(csrf()))
                .andExpect(status().isNoContent());
        mockMvc.perform(get("/api/user-groups/{code}", code).with(admin()))
                .andExpect(status().isNotFound());
    }

    @Test
    void mistakesAreRefusedWithAReason() throws Exception {
        String code = code();
        mockMvc.perform(post("/api/user-groups").with(admin()).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\"" + code + "\",\"label\":\"RH\"}"))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/user-groups").with(admin()).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\"" + code + "\",\"label\":\"Autre\"}"))
                .andExpect(status().isConflict());
        mockMvc.perform(post("/api/user-groups").with(admin()).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\"rh minuscule\",\"label\":\"RH\"}"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(put("/api/user-groups/{code}/members/{userId}", code, "jamais-connecte").with(admin()).with(csrf()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("Unknown user (never logged in to this instance): jamais-connecte"));
    }

    @Test
    void onlyATenantAdminManagesGroups() throws Exception {
        RequestPostProcessor manager = user("agent", "DOSSIER_MANAGER");

        mockMvc.perform(get("/api/user-groups").with(manager)).andExpect(status().isForbidden());
        mockMvc.perform(get("/api/user-groups/directory").with(manager)).andExpect(status().isForbidden());
        mockMvc.perform(post("/api/user-groups").with(manager).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\"" + code() + "\",\"label\":\"RH\"}"))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/user-groups")).andExpect(status().isUnauthorized());
    }

    private static String code() {
        return "G_" + UUID.randomUUID().toString().replace("-", "").substring(0, 12).toUpperCase();
    }

    private static RequestPostProcessor admin() {
        return user("admin-groupes", "TENANT_ADMIN");
    }

    private static RequestPostProcessor user(String subject, String role) {
        return oidcLogin().idToken(token -> token.subject(subject).claim("tenant_id", TENANT))
                .authorities(new SimpleGrantedAuthority("ROLE_" + role));
    }
}
