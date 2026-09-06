package com.flowforge.infrastructure.mcp.tool;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.flowforge.domain.dossier.model.FieldDefinition;
import com.flowforge.domain.dossier.model.FieldType;
import com.flowforge.domain.shared.PageQuery;
import com.flowforge.domain.shared.PageResult;
import com.flowforge.domain.workflow.model.TaskQuestionSchema;
import com.flowforge.domain.workflow.port.in.GetTaskQuestionSchemaUseCase;
import com.flowforge.infrastructure.mcp.McpAccessContextFactory;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

class ListTaskQuestionSchemasToolTest {

    private GetTaskQuestionSchemaUseCase getTaskQuestionSchemaUseCase;
    private ListTaskQuestionSchemasTool tool;

    @BeforeEach
    void setUp() {
        getTaskQuestionSchemaUseCase = mock(GetTaskQuestionSchemaUseCase.class);
        tool = new ListTaskQuestionSchemasTool(getTaskQuestionSchemaUseCase, new McpAccessContextFactory());
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
    void relaysToUseCase() {
        TaskQuestionSchema schema = new TaskQuestionSchema(
                UUID.randomUUID(), "tenant_dev", "orientation", "Orientation",
                List.of(new FieldDefinition("decision", "Decision", FieldType.STRING, true, null, List.of(), null, false)),
                "admin", "admin");
        when(getTaskQuestionSchemaUseCase.list(eq(PageQuery.defaults()), any()))
                .thenReturn(PageResult.of(List.of(schema), 0, 20, 1));

        var result = tool.listTaskQuestionSchemas(null, null);

        assertThat(result.content()).containsExactly(schema);
    }

    @Test
    void pageAndSizeProvided_relayedAsGiven() {
        when(getTaskQuestionSchemaUseCase.list(eq(PageQuery.of(2, 5)), any()))
                .thenReturn(PageResult.empty(2, 5));

        tool.listTaskQuestionSchemas(2, 5);

        org.mockito.Mockito.verify(getTaskQuestionSchemaUseCase).list(eq(PageQuery.of(2, 5)), any());
    }
}
