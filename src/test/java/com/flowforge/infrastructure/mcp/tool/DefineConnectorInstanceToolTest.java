package com.flowforge.infrastructure.mcp.tool;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.flowforge.domain.connector.model.ConnectorDefinition;
import com.flowforge.domain.connector.model.ConnectorInstanceData;
import com.flowforge.domain.connector.model.ConnectorValidationException;
import com.flowforge.domain.connector.model.UnknownConnectorException;
import com.flowforge.domain.connector.port.in.ManageConnectorInstanceUseCase;
import com.flowforge.domain.connector.port.out.ConnectorCatalog;
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

class DefineConnectorInstanceToolTest {

    private static final String TENANT = "tenant_dev";

    private ManageConnectorInstanceUseCase manageConnectorInstanceUseCase;
    private ConnectorCatalog connectorCatalog;
    private ConnectorDefinition httpRestDefinition;
    private DefineConnectorInstanceTool tool;

    @BeforeEach
    void setUp() {
        manageConnectorInstanceUseCase = mock(ManageConnectorInstanceUseCase.class);
        connectorCatalog = mock(ConnectorCatalog.class);
        httpRestDefinition = mock(ConnectorDefinition.class);
        when(connectorCatalog.get("http-rest")).thenReturn(httpRestDefinition);
        tool = new DefineConnectorInstanceTool(
                manageConnectorInstanceUseCase, connectorCatalog, new TenantProperties(TENANT),
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
    void noId_createsInstance() {
        ConnectorInstanceData created = new ConnectorInstanceData(
                UUID.randomUUID(), TENANT, "Webhook", "http-rest", true, Map.of());
        when(manageConnectorInstanceUseCase.create(any(), any())).thenReturn(created);

        Map<String, Object> result = tool.defineConnectorInstance(
                null, "Webhook", "http-rest", null, Map.of(), null, null);

        assertThat(result.get("operation")).isEqualTo("create");
        verify(manageConnectorInstanceUseCase).create(any(), any());
        verify(manageConnectorInstanceUseCase, never()).update(any(), any());
    }

    @Test
    void create_missingName_isRejected() {
        assertThatThrownBy(() -> tool.defineConnectorInstance(null, null, "http-rest", null, Map.of(), null, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("name");
        verifyNoInteractions(manageConnectorInstanceUseCase);
    }

    @Test
    void create_missingConnectorType_isRejected() {
        assertThatThrownBy(() -> tool.defineConnectorInstance(null, "Webhook", null, null, Map.of(), null, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("connectorType");
        verifyNoInteractions(manageConnectorInstanceUseCase);
    }

    @Test
    void create_dryRun_doesNotPersist() {
        Map<String, Object> result = tool.defineConnectorInstance(
                null, "Webhook", "http-rest", null, Map.of(), null, true);

        assertThat(result.get("dryRun")).isEqualTo(true);
        assertThat(result.get("operation")).isEqualTo("create");
        verify(httpRestDefinition).validate(Map.of());
        verifyNoInteractions(manageConnectorInstanceUseCase);
    }

    @Test
    void create_dryRun_stillValidatesConfigAgainstCatalog() {
        org.mockito.Mockito.doThrow(new ConnectorValidationException("webhookAuthType is required"))
                .when(httpRestDefinition).validate(Map.of());

        assertThatThrownBy(() -> tool.defineConnectorInstance(
                        null, "Webhook", "http-rest", null, Map.of(), null, true))
                .isInstanceOf(ConnectorValidationException.class);
        verifyNoInteractions(manageConnectorInstanceUseCase);
    }

    @Test
    void create_dryRun_unknownConnectorType_isRejected() {
        when(connectorCatalog.get("bogus")).thenThrow(new UnknownConnectorException("bogus"));

        assertThatThrownBy(() -> tool.defineConnectorInstance(
                        null, "Webhook", "bogus", null, Map.of(), null, true))
                .isInstanceOf(UnknownConnectorException.class);
        verifyNoInteractions(manageConnectorInstanceUseCase);
    }

    @Test
    void create_dryRun_missingName_isStillRejected() {
        assertThatThrownBy(() -> tool.defineConnectorInstance(null, null, "http-rest", null, Map.of(), null, true))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("name");
        verifyNoInteractions(manageConnectorInstanceUseCase);
    }

    @Test
    void withId_updatesInstance() {
        UUID id = UUID.randomUUID();
        ConnectorInstanceData updated = new ConnectorInstanceData(id, TENANT, "New", "http-rest", true, Map.of());
        when(manageConnectorInstanceUseCase.update(any(), any())).thenReturn(updated);

        Map<String, Object> result = tool.defineConnectorInstance(
                id.toString(), "New", null, null, null, null, null);

        assertThat(result.get("operation")).isEqualTo("update");
        verify(manageConnectorInstanceUseCase).update(any(), any());
        verify(manageConnectorInstanceUseCase, never()).create(any(), any());
    }

    @Test
    void update_dryRun_checksExistenceWithoutPersisting() {
        UUID id = UUID.randomUUID();
        when(manageConnectorInstanceUseCase.get(eq(id), eq(TENANT), any()))
                .thenReturn(new ConnectorInstanceData(id, TENANT, "New", "http-rest", true, Map.of()));

        Map<String, Object> result = tool.defineConnectorInstance(
                id.toString(), null, null, null, null, null, true);

        assertThat(result.get("dryRun")).isEqualTo(true);
        assertThat(result.get("operation")).isEqualTo("update");
        verify(manageConnectorInstanceUseCase).get(eq(id), eq(TENANT), any());
        verify(manageConnectorInstanceUseCase, never()).update(any(), any());
    }

    @Test
    void responseSecretsAreMasked() {
        ConnectorInstanceData created = new ConnectorInstanceData(
                UUID.randomUUID(), TENANT, "Webhook", "http-rest", true, Map.of("webhookSecret", "plain-secret"));
        when(manageConnectorInstanceUseCase.create(any(), any())).thenReturn(created);

        Map<String, Object> result = tool.defineConnectorInstance(
                null, "Webhook", "http-rest", null, Map.of("webhookSecret", "plain-secret"), null, null);

        ConnectorInstanceMcpView view = (ConnectorInstanceMcpView) result.get("connectorInstance");
        assertThat(view.config()).containsEntry("webhookSecret", "********");
    }
}
