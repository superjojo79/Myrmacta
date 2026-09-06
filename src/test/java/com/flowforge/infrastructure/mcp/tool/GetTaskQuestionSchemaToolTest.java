package com.flowforge.infrastructure.mcp.tool;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.flowforge.domain.dossier.model.FieldDefinition;
import com.flowforge.domain.dossier.model.FieldType;
import com.flowforge.domain.workflow.model.TaskQuestionSchema;
import com.flowforge.domain.workflow.model.UnknownTaskQuestionSchemaException;
import com.flowforge.domain.workflow.port.in.GetTaskQuestionSchemaUseCase;
import com.flowforge.infrastructure.mcp.McpAccessContextFactory;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

class GetTaskQuestionSchemaToolTest {

    private GetTaskQuestionSchemaUseCase getTaskQuestionSchemaUseCase;
    private GetTaskQuestionSchemaTool tool;

    @BeforeEach
    void setUp() {
        getTaskQuestionSchemaUseCase = mock(GetTaskQuestionSchemaUseCase.class);
        tool = new GetTaskQuestionSchemaTool(getTaskQuestionSchemaUseCase, new McpAccessContextFactory());
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
        when(getTaskQuestionSchemaUseCase.getByKey(eq("orientation"), any())).thenReturn(Optional.of(schema));

        assertThat(tool.getTaskQuestionSchema("orientation")).isSameAs(schema);
    }

    @Test
    void unknownSchemaKey_throwsNotFound() {
        when(getTaskQuestionSchemaUseCase.getByKey(eq("missing"), any())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> tool.getTaskQuestionSchema("missing"))
                .isInstanceOf(UnknownTaskQuestionSchemaException.class);
    }
}
