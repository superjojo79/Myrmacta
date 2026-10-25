package be.myrmacta.infrastructure.mcp.tool;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import be.myrmacta.domain.connector.model.ConfigField;
import be.myrmacta.domain.connector.model.ConfigFieldType;
import be.myrmacta.domain.connector.model.ConnectorDefinition;
import be.myrmacta.domain.connector.model.ConnectorInstanceData;
import be.myrmacta.domain.connector.model.ConnectorInstanceNotFoundException;
import be.myrmacta.domain.connector.port.in.ManageConnectorInstanceUseCase;
import be.myrmacta.domain.connector.port.out.ConnectorCatalog;
import be.myrmacta.infrastructure.config.TenantProperties;
import be.myrmacta.infrastructure.mcp.McpAccessContextFactory;
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
    private ConnectorCatalog connectorCatalog;
    private GetConnectorInstanceTool tool;

    @BeforeEach
    void setUp() {
        manageConnectorInstanceUseCase = mock(ManageConnectorInstanceUseCase.class);
        connectorCatalog = mock(ConnectorCatalog.class);
        tool = new GetConnectorInstanceTool(
                manageConnectorInstanceUseCase,
                connectorCatalog,
                new TenantProperties(TENANT),
                new McpAccessContextFactory());
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
    void masksSchemaDeclaredPasswordFieldRegardlessOfName() {
        // authHeaderValue matches no name heuristic (adversarial review follow-up) — only the
        // connector type's declared PASSWORD schema field catches it.
        UUID id = UUID.randomUUID();
        ConnectorInstanceData instance = new ConnectorInstanceData(
                id, TENANT, "Webhook", "http-rest", true, Map.of("authHeaderValue", "Bearer plain-token"));
        when(manageConnectorInstanceUseCase.get(eq(id), eq(TENANT), any())).thenReturn(instance);
        ConnectorDefinition definition = mock(ConnectorDefinition.class);
        when(definition.getConfigSchema()).thenReturn(List.of(new ConfigField(
                "authHeaderValue", "Auth header", ConfigFieldType.PASSWORD, false, null, List.of(), null)));
        when(connectorCatalog.get("http-rest")).thenReturn(definition);
        when(connectorCatalog.isPasswordField("http-rest", "authHeaderValue")).thenCallRealMethod();

        var result = tool.getConnectorInstance(id.toString());

        assertThat(result.config()).containsEntry("authHeaderValue", "********");
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
