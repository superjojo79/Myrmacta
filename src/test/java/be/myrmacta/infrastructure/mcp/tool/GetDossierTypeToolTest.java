package be.myrmacta.infrastructure.mcp.tool;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import be.myrmacta.domain.dossier.model.DossierNotFoundException;
import be.myrmacta.domain.dossier.model.DossierType;
import be.myrmacta.domain.dossier.port.in.GetDossierTypeUseCase;
import be.myrmacta.infrastructure.config.TenantProperties;
import be.myrmacta.infrastructure.mcp.McpAccessContextFactory;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

class GetDossierTypeToolTest {

    private static final String TENANT = "tenant_dev";

    private GetDossierTypeUseCase getDossierTypeUseCase;
    private GetDossierTypeTool tool;

    @BeforeEach
    void setUp() {
        getDossierTypeUseCase = mock(GetDossierTypeUseCase.class);
        tool = new GetDossierTypeTool(getDossierTypeUseCase, new TenantProperties(TENANT), new McpAccessContextFactory());
        Jwt jwt = Jwt.withTokenValue("t")
                .header("alg", "none")
                .claim("sub", "agent-1")
                .issuedAt(Instant.EPOCH)
                .expiresAt(Instant.EPOCH.plusSeconds(3600))
                .build();
        SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(jwt, List.of()));
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void relaysToGetDossierTypeUseCase() {
        DossierType type = DossierType.inherit(UUID.randomUUID(), TENANT, "CLAIM", "Claim", "wf-claim", true, false, List.of());
        when(getDossierTypeUseCase.getByCode(eq(TENANT), eq("CLAIM"), any())).thenReturn(type);

        DossierType result = tool.getDossierType("CLAIM");

        assertThat(result).isSameAs(type);
    }

    @Test
    void unknownCode_propagatesNotFound() {
        when(getDossierTypeUseCase.getByCode(eq(TENANT), eq("MISSING"), any()))
                .thenThrow(new DossierNotFoundException("nope"));

        assertThatThrownBy(() -> tool.getDossierType("MISSING")).isInstanceOf(DossierNotFoundException.class);
    }
}
