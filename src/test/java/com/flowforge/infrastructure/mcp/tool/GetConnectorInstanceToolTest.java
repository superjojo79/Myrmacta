package com.flowforge.infrastructure.mcp.tool;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.flowforge.domain.connector.model.ConnectorInstanceData;
import com.flowforge.domain.connector.model.ConnectorInstanceNotFoundException;
import com.flowforge.domain.connector.port.in.ManageConnectorInstanceUseCase;
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

class GetConnectorInstanceToolTest {

    private static final String TENANT = "tenant_dev";

    private ManageConnectorInstanceUseCase manageConnectorInstanceUseCase;
    private GetConnectorInstanceTool tool;

    @BeforeEach
    void setUp() {
        manageConnectorInstanceUseCase = mock(ManageConnectorInstanceUseCase.class);
        tool = new GetConnectorInstanceTool(
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
        UUID id = UUID.randomUUID();
        ConnectorInstanceData instance = new ConnectorInstanceData(
                id, TENANT, "Webhook", "http-rest", true, Map.of("webhookSecret", "plain-secret"));
        when(manageConnectorInstanceUseCase.get(eq(id), eq(TENANT), any())).thenReturn(instance);

        var result = tool.getConnectorInstance(id.toString());

        assertThat(result.config()).containsEntry("webhookSecret", "********");
    }

    @Test
    void unknownId_propagatesNotFound() {
        UUID id = UUID.randomUUID();
        when(manageConnectorInstanceUseCase.get(eq(id), eq(TENANT), any()))
                .thenThrow(new ConnectorInstanceNotFoundException(id));

        assertThatThrownBy(() -> tool.getConnectorInstance(id.toString()))
                .isInstanceOf(ConnectorInstanceNotFoundException.class);
    }
}
