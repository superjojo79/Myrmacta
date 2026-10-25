package be.myrmacta.infrastructure.mcp;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import be.myrmacta.domain.dossier.model.AccessContext;
import be.myrmacta.infrastructure.config.M2mSecurityProperties;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.oauth2.jwt.Jwt;

class McpAccessContextFactoryTest {

    private final McpAccessContextFactory factory = new McpAccessContextFactory();

    @Test
    void usesSubjectWhenPresent() {
        AccessContext.User actor = factory.from(jwt(Map.of("sub", "user-42", "client_id", "agent-1")));

        assertThat(actor.userId()).isEqualTo("user-42");
        assertThat(actor.roles()).containsExactly("API_SERVICE");
    }

    @Test
    void fallsBackToClientIdWhenSubjectBlank() {
        AccessContext.User actor = factory.from(jwt(Map.of("sub", "", "client_id", "agent-1")));

        assertThat(actor.userId()).isEqualTo("agent-1");
    }

    @Test
    void fallsBackToClientIdWhenSubjectAbsent() {
        AccessContext.User actor = factory.from(jwt(Map.of("client_id", "agent-1")));

        assertThat(actor.userId()).isEqualTo("agent-1");
    }

    @Test
    void fallsBackToDefaultActorWhenNeitherPresent() {
        AccessContext.User actor = factory.from(jwt(Map.of()));

        assertThat(actor.userId()).isEqualTo("api-service");
    }

    @Test
    void neverUsesSystemOrServiceRoles() {
        AccessContext.User actor = factory.from(jwt(Map.of("sub", "user-1")));

        assertThat(actor.roles()).isEqualTo(Set.of("API_SERVICE"));
    }

    @Test
    void trustedClientIdsEmpty_acceptsAnyM2mClient() {
        McpAccessContextFactory unrestricted =
                new McpAccessContextFactory(new M2mSecurityProperties(null, null, List.of()));

        AccessContext.User actor = unrestricted.from(jwt(Map.of("client_id", "any-client")));

        assertThat(actor.userId()).isEqualTo("any-client");
        assertThat(actor.roles()).containsExactly("API_SERVICE");
    }

    @Test
    void trustedClientIdsConfigured_allowsListedClientId() {
        McpAccessContextFactory restricted = new McpAccessContextFactory(
                new M2mSecurityProperties(null, null, List.of("agent-1")));

        AccessContext.User actor = restricted.from(jwt(Map.of("client_id", "agent-1")));

        assertThat(actor.roles()).containsExactly("API_SERVICE");
    }

    @Test
    void trustedClientIdsConfigured_rejectsUnlistedClientId() {
        McpAccessContextFactory restricted = new McpAccessContextFactory(
                new M2mSecurityProperties(null, null, List.of("agent-1")));

        assertThatThrownBy(() -> restricted.from(jwt(Map.of("client_id", "rogue-client"))))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void trustedClientIdsConfigured_fallsBackToSubjectWhenClientIdAbsent() {
        McpAccessContextFactory restricted = new McpAccessContextFactory(
                new M2mSecurityProperties(null, null, List.of("user-42")));

        AccessContext.User actor = restricted.from(jwt(Map.of("sub", "user-42")));

        assertThat(actor.userId()).isEqualTo("user-42");
    }

    private static Jwt jwt(Map<String, Object> claims) {
        var builder = Jwt.withTokenValue("token")
                .header("alg", "none")
                .issuedAt(Instant.EPOCH)
                .expiresAt(Instant.EPOCH.plusSeconds(3600));
        if (claims.isEmpty()) {
            // Jwt requires at least one claim.
            builder.claim("tenant_id", "tenant_dev");
        } else {
            claims.forEach(builder::claim);
        }
        return builder.build();
    }
}
