package com.flowforge.infrastructure.mcp.tool;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.flowforge.domain.workflow.model.TaskQuestionSchema;
import com.flowforge.domain.workflow.port.in.GetTaskQuestionSchemaUseCase;
import com.flowforge.domain.workflow.port.in.SaveTaskQuestionSchemaUseCase;
import com.flowforge.infrastructure.config.TenantProperties;
import com.flowforge.infrastructure.mcp.McpAccessContextFactory;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import tools.jackson.databind.json.JsonMapper;

class DefineTaskQuestionSchemaToolTest {

    private static final String TENANT = "tenant_dev";

    private GetTaskQuestionSchemaUseCase getTaskQuestionSchemaUseCase;
    private SaveTaskQuestionSchemaUseCase saveTaskQuestionSchemaUseCase;
    private DefineTaskQuestionSchemaTool tool;

    @BeforeEach
    void setUp() {
        getTaskQuestionSchemaUseCase = mock(GetTaskQuestionSchemaUseCase.class);
        saveTaskQuestionSchemaUseCase = mock(SaveTaskQuestionSchemaUseCase.class);
        tool = new DefineTaskQuestionSchemaTool(
                getTaskQuestionSchemaUseCase,
                saveTaskQuestionSchemaUseCase,
                new TenantProperties(TENANT),
                new McpAccessContextFactory(),
                JsonMapper.builder().build());
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

    private static List<Map<String, Object>> validFields() {
        return List.of(Map.of("name", "decision", "label", "Decision", "type", "STRING", "required", true));
    }

    @Test
    void unknownSchemaKey_creates() {
        when(getTaskQuestionSchemaUseCase.getByKey(any(), any())).thenReturn(Optional.empty());
        when(saveTaskQuestionSchemaUseCase.save(any(), any()))
                .thenAnswer(invocation -> invocation.getArgument(0));

        Map<String, Object> result = tool.defineTaskQuestionSchema("orientation", "Orientation", validFields(), null);

        assertThat(result.get("operation")).isEqualTo("create");
        verify(saveTaskQuestionSchemaUseCase).save(any(), any());
    }

    @Test
    void knownSchemaKey_updates() {
        TaskQuestionSchema existing = new TaskQuestionSchema(
                UUID.randomUUID(), TENANT, "orientation", "Old", validFieldDefs(), "admin", "admin");
        when(getTaskQuestionSchemaUseCase.getByKey(any(), any())).thenReturn(Optional.of(existing));
        when(saveTaskQuestionSchemaUseCase.save(any(), any()))
                .thenAnswer(invocation -> invocation.getArgument(0));

        Map<String, Object> result = tool.defineTaskQuestionSchema("orientation", "New label", validFields(), null);

        assertThat(result.get("operation")).isEqualTo("update");
    }

    @Test
    void create_dryRun_doesNotPersist() {
        when(getTaskQuestionSchemaUseCase.getByKey(any(), any())).thenReturn(Optional.empty());

        Map<String, Object> result = tool.defineTaskQuestionSchema("orientation", "Orientation", validFields(), true);

        assertThat(result.get("dryRun")).isEqualTo(true);
        verifyNoInteractions(saveTaskQuestionSchemaUseCase);
    }

    @Test
    void create_dryRun_stillValidatesFields() {
        when(getTaskQuestionSchemaUseCase.getByKey(any(), any())).thenReturn(Optional.empty());
        List<Map<String, Object>> duplicateNames = List.of(
                Map.of("name", "decision", "label", "A", "type", "STRING", "required", true),
                Map.of("name", "decision", "label", "B", "type", "STRING", "required", true));

        assertThatThrownBy(() -> tool.defineTaskQuestionSchema("orientation", "Orientation", duplicateNames, true))
                .isInstanceOf(IllegalArgumentException.class);
        verifyNoInteractions(saveTaskQuestionSchemaUseCase);
    }

    @Test
    void reservedVariableName_isRejected() {
        when(getTaskQuestionSchemaUseCase.getByKey(any(), any())).thenReturn(Optional.empty());
        List<Map<String, Object>> reserved = List.of(
                Map.of("name", "dossierId", "label", "A", "type", "STRING", "required", true));

        assertThatThrownBy(() -> tool.defineTaskQuestionSchema("orientation", "Orientation", reserved, null))
                .isInstanceOf(IllegalArgumentException.class);
        verify(saveTaskQuestionSchemaUseCase, never()).save(any(), any());
    }

    @Test
    void fieldsNull_isRejected() {
        assertThatThrownBy(() -> tool.defineTaskQuestionSchema("orientation", "Orientation", null, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("fields");
        verifyNoInteractions(getTaskQuestionSchemaUseCase, saveTaskQuestionSchemaUseCase);
    }

    private static List<com.flowforge.domain.dossier.model.FieldDefinition> validFieldDefs() {
        return List.of(new com.flowforge.domain.dossier.model.FieldDefinition(
                "decision", "Decision", com.flowforge.domain.dossier.model.FieldType.STRING, true, null, List.of(),
                null, false));
    }
}
