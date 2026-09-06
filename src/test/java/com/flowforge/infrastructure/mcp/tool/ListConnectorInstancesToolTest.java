package com.flowforge.infrastructure.mcp.tool;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.flowforge.domain.connector.model.ConnectorInstanceData;
import com.flowforge.domain.connector.port.in.ManageConnectorInstanceUseCase;
import com.flowforge.domain.shared.PageQuery;
import com.flowforge.domain.shared.PageResult;
import com.flowforge.infrastructure.config.TenantProperties;
import com.flowforge.infrastructure.mcp.McpAccessContextFactory;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

class ListConnectorInstancesToolTest {

    private static final String TENANT = "tenant_dev";

    private ManageConnectorInstanceUseCase manageConnectorInstanceUseCase;
    private ListConnectorInstancesTool tool;

    @BeforeEach
    void setUp() {
        manageConnectorInstanceUseCase = mock(ManageConnectorInstanceUseCase.class);
        tool = new ListConnectorInstancesTool(
                manageConnectorInstanceUseCase, new TenantProperties(TENANT), new McpAccessContextFactory());
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
    void masksSecretConfigValues() {
        ConnectorInstanceData instance = new ConnectorInstanceData(
                UUID.randomUUID(), TENANT, "Webhook", "http-rest", true,
                Map.of("webhookSecret", "plain-secret", "processKey", "CLAIM"));
        when(manageConnectorInstanceUseCase.list(any(), any(), any()))
                .thenReturn(PageResult.of(List.of(instance), 0, 20, 1));

        var result = tool.listConnectorInstances(null, null);

        Map<String, Object> config = result.content().get(0).config();
        assertThat(config).containsEntry("webhookSecret", "********");
        assertThat(config).containsEntry("processKey", "CLAIM");
    }

    @Test
    void relaysPageQueryDefaults() {
        when(manageConnectorInstanceUseCase.list(any(), any(), any())).thenReturn(PageResult.empty(0, 20));

        tool.listConnectorInstances(null, null);

        org.mockito.Mockito.verify(manageConnectorInstanceUseCase)
                .list(org.mockito.ArgumentMatchers.eq(TENANT), org.mockito.ArgumentMatchers.eq(PageQuery.defaults()), any());
    }

    @Test
    void pageAndSizeProvided_relayedAsGiven() {
        when(manageConnectorInstanceUseCase.list(any(), any(), any())).thenReturn(PageResult.empty(2, 5));

        tool.listConnectorInstances(2, 5);

        org.mockito.Mockito.verify(manageConnectorInstanceUseCase)
                .list(org.mockito.ArgumentMatchers.eq(TENANT), org.mockito.ArgumentMatchers.eq(PageQuery.of(2, 5)), any());
    }
}
