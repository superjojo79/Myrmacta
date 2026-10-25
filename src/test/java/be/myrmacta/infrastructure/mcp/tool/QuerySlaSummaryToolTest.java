package be.myrmacta.infrastructure.mcp.tool;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import be.myrmacta.domain.reporting.model.SlaCohortTooLargeException;
import be.myrmacta.domain.reporting.model.SlaSummary;
import be.myrmacta.domain.reporting.port.in.GetSlaSummaryUseCase;
import be.myrmacta.infrastructure.config.TenantProperties;
import be.myrmacta.infrastructure.mcp.McpAccessContextFactory;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

class QuerySlaSummaryToolTest {

    private static final String TENANT = "tenant_dev";

    private GetSlaSummaryUseCase getSlaSummaryUseCase;
    private QuerySlaSummaryTool tool;

    @BeforeEach
    void setUp() {
        getSlaSummaryUseCase = mock(GetSlaSummaryUseCase.class);
        tool = new QuerySlaSummaryTool(getSlaSummaryUseCase, new TenantProperties(TENANT), new McpAccessContextFactory());
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
    void relaysToGetSlaSummaryQuery() {
        SlaSummary summary = new SlaSummary(Instant.EPOCH, Instant.EPOCH.plusSeconds(1), "dossierTypeCode", List.of());
        when(getSlaSummaryUseCase.get(any(), any())).thenReturn(summary);

        SlaSummary result = tool.querySlaSummary("2026-01-01T00:00:00Z", "2026-02-01T00:00:00Z", "SINISTRE", "status");

        assertThat(result).isSameAs(summary);
        var captor = org.mockito.ArgumentCaptor.forClass(GetSlaSummaryUseCase.Query.class);
        verify(getSlaSummaryUseCase).get(captor.capture(), any());
        assertThat(captor.getValue().tenantId()).isEqualTo(TENANT);
        assertThat(captor.getValue().from()).isEqualTo("2026-01-01T00:00:00Z");
        assertThat(captor.getValue().to()).isEqualTo("2026-02-01T00:00:00Z");
        assertThat(captor.getValue().dossierTypeCode()).isEqualTo("SINISTRE");
        assertThat(captor.getValue().groupBy()).isEqualTo("status");
    }

    @Test
    void cohortTooLarge_propagatesAsToolError() {
        when(getSlaSummaryUseCase.get(any(), any())).thenThrow(new SlaCohortTooLargeException(10_001L));

        assertThatThrownBy(() -> tool.querySlaSummary(null, null, null, null))
                .isInstanceOf(SlaCohortTooLargeException.class);
    }
}
