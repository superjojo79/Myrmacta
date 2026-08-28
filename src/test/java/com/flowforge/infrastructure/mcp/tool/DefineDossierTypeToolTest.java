package com.flowforge.infrastructure.mcp.tool;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.flowforge.domain.dossier.model.DossierNotFoundException;
import com.flowforge.domain.dossier.model.DossierType;
import com.flowforge.domain.dossier.model.DossierTypeSchemaIncompatibleException;
import com.flowforge.domain.dossier.port.in.CheckDossierTypeSchemaCompatibilityUseCase;
import com.flowforge.domain.dossier.port.in.CreateDossierTypeUseCase;
import com.flowforge.domain.dossier.port.in.GetDossierTypeUseCase;
import com.flowforge.domain.dossier.port.in.UpdateDossierTypeUseCase;
import com.flowforge.infrastructure.config.TenantProperties;
import com.flowforge.infrastructure.mcp.McpAccessContextFactory;
import io.modelcontextprotocol.spec.McpSchema;
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
import tools.jackson.databind.json.JsonMapper;

class DefineDossierTypeToolTest {

    private static final String TENANT = "tenant_dev";

    private GetDossierTypeUseCase getDossierTypeUseCase;
    private CreateDossierTypeUseCase createDossierTypeUseCase;
    private UpdateDossierTypeUseCase updateDossierTypeUseCase;
    private CheckDossierTypeSchemaCompatibilityUseCase checkCompatibilityUseCase;
    private DefineDossierTypeTool tool;

    @BeforeEach
    void setUp() {
        getDossierTypeUseCase = mock(GetDossierTypeUseCase.class);
        createDossierTypeUseCase = mock(CreateDossierTypeUseCase.class);
        updateDossierTypeUseCase = mock(UpdateDossierTypeUseCase.class);
        checkCompatibilityUseCase = mock(CheckDossierTypeSchemaCompatibilityUseCase.class);
        tool = new DefineDossierTypeTool(
                getDossierTypeUseCase,
                createDossierTypeUseCase,
                updateDossierTypeUseCase,
                checkCompatibilityUseCase,
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

    @Test
    void unknownCode_create() {
        when(getDossierTypeUseCase.getByCode(any(), any(), any()))
                .thenThrow(new DossierNotFoundException("nope"));
        when(createDossierTypeUseCase.create(any(), any())).thenReturn(type("CLAIM"));

        Map<String, Object> result = invoke(baseArgs("CLAIM"));

        assertThat(result.get("operation")).isEqualTo("create");
        verify(createDossierTypeUseCase).create(any(), any());
        verify(updateDossierTypeUseCase, never()).update(any(), any());
    }

    @Test
    void knownCode_update() {
        DossierType existing = type("CLAIM");
        when(getDossierTypeUseCase.getByCode(any(), any(), any())).thenReturn(existing);
        when(updateDossierTypeUseCase.update(any(), any())).thenReturn(existing);

        Map<String, Object> result = invoke(baseArgs("CLAIM"));

        assertThat(result.get("operation")).isEqualTo("update");
        verify(createDossierTypeUseCase, never()).create(any(), any());
    }

    @Test
    void dryRun_doesNotSave() {
        when(getDossierTypeUseCase.getByCode(any(), any(), any()))
                .thenThrow(new DossierNotFoundException("nope"));

        Map<String, Object> args = baseArgs("CLAIM");
        args.put("dryRun", true);
        Map<String, Object> result = invoke(args);

        assertThat(result.get("dryRun")).isEqualTo(true);
        verifyNoInteractions(createDossierTypeUseCase);
    }

    @Test
    void forbiddenRetentionDaysKey_isRejectedWithoutAnyUseCaseCall() {
        Map<String, Object> args = baseArgs("CLAIM");
        args.put("retentionDays", 30);

        assertThatThrownBy(() -> invoke(args))
                .isInstanceOf(IllegalArgumentException.class);
        verifyNoInteractions(getDossierTypeUseCase, createDossierTypeUseCase, updateDossierTypeUseCase);
    }

    @Test
    void forbiddenSlaTargetHoursKey_isRejected() {
        Map<String, Object> args = baseArgs("CLAIM");
        args.put("slaTargetHours", 48);

        assertThatThrownBy(() -> invoke(args))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void update_preservesExistingRetentionAndSla() {
        DossierType existing = new DossierType(
                UUID.randomUUID(), TENANT, "CLAIM", "Old", "wf-old", true, false, List.of(),
                30, false, 48, "system", "system");
        when(getDossierTypeUseCase.getByCode(any(), any(), any())).thenReturn(existing);
        when(updateDossierTypeUseCase.update(any(), any())).thenReturn(existing);

        invoke(baseArgs("CLAIM"));

        var captor = org.mockito.ArgumentCaptor.forClass(UpdateDossierTypeUseCase.UpdateDossierTypeCommand.class);
        verify(updateDossierTypeUseCase).update(captor.capture(), any());
        assertThat(captor.getValue().retentionDays()).isEqualTo(30);
        assertThat(captor.getValue().retentionNever()).isFalse();
        assertThat(captor.getValue().slaTargetHours()).isEqualTo(48);
    }

    @Test
    void update_incompatibleSchema_isRejectedWithoutSaving() {
        // D-DESIGN-CHECK-PLACEMENT: the compatibility check now runs inside
        // UpdateDossierTypeUseCaseImpl itself (single source of truth for REST and MCP alike), so
        // the tool no longer calls checkCompatibilityUseCase directly on the real (non-dryRun)
        // path — it relies on updateDossierTypeUseCase.update() to throw.
        DossierType existing = type("CLAIM");
        when(getDossierTypeUseCase.getByCode(any(), any(), any())).thenReturn(existing);
        when(updateDossierTypeUseCase.update(any(), any()))
                .thenThrow(new DossierTypeSchemaIncompatibleException(3, List.of("REF-1")));

        assertThatThrownBy(() -> invoke(baseArgs("CLAIM")))
                .isInstanceOf(DossierTypeSchemaIncompatibleException.class);
        verify(updateDossierTypeUseCase).update(any(), any());
        verifyNoInteractions(checkCompatibilityUseCase);
    }

    @Test
    void dryRun_onUpdatePath_doesNotSave() {
        DossierType existing = type("CLAIM");
        when(getDossierTypeUseCase.getByCode(any(), any(), any())).thenReturn(existing);
        when(checkCompatibilityUseCase.check(any()))
                .thenReturn(new CheckDossierTypeSchemaCompatibilityUseCase.Result(0, List.of()));

        Map<String, Object> args = baseArgs("CLAIM");
        args.put("dryRun", true);
        Map<String, Object> result = invoke(args);

        assertThat(result.get("operation")).isEqualTo("update");
        assertThat(result.get("dryRun")).isEqualTo(true);
        verifyNoInteractions(updateDossierTypeUseCase);
    }

    @Test
    void fieldDefinitionsOmitted_defaultsToEmptyList() {
        when(getDossierTypeUseCase.getByCode(any(), any(), any()))
                .thenThrow(new DossierNotFoundException("nope"));
        when(createDossierTypeUseCase.create(any(), any())).thenReturn(type("CLAIM"));

        Map<String, Object> args = baseArgs("CLAIM");
        args.remove("fieldDefinitions");
        invoke(args);

        var captor = org.mockito.ArgumentCaptor.forClass(CreateDossierTypeUseCase.CreateDossierTypeCommand.class);
        verify(createDossierTypeUseCase).create(captor.capture(), any());
        assertThat(captor.getValue().fieldDefinitions()).isEmpty();
    }

    @Test
    void fieldDefinitionsNonEmpty_areParsedAndRelayed() {
        when(getDossierTypeUseCase.getByCode(any(), any(), any()))
                .thenThrow(new DossierNotFoundException("nope"));
        when(createDossierTypeUseCase.create(any(), any())).thenReturn(type("CLAIM"));

        Map<String, Object> args = baseArgs("CLAIM");
        args.put("fieldDefinitions", List.of(Map.of(
                "name", "amount", "label", "Amount", "type", "NUMBER", "required", true)));
        invoke(args);

        var captor = org.mockito.ArgumentCaptor.forClass(CreateDossierTypeUseCase.CreateDossierTypeCommand.class);
        verify(createDossierTypeUseCase).create(captor.capture(), any());
        assertThat(captor.getValue().fieldDefinitions()).hasSize(1);
        assertThat(captor.getValue().fieldDefinitions().get(0).name()).isEqualTo("amount");
    }

    @Test
    void fieldDefinitionsWrongType_isRejected() {
        Map<String, Object> args = baseArgs("CLAIM");
        args.put("fieldDefinitions", "not-a-list");

        assertThatThrownBy(() -> invoke(args))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("fieldDefinitions");
        verifyNoInteractions(getDossierTypeUseCase, createDossierTypeUseCase, updateDossierTypeUseCase);
    }

    private static Map<String, Object> baseArgs(String code) {
        var args = new java.util.HashMap<String, Object>();
        args.put("code", code);
        args.put("label", "Claim");
        args.put("workflowKey", "wf-claim");
        args.put("active", true);
        args.put("allowUnknownFields", false);
        args.put("fieldDefinitions", List.of());
        return args;
    }

    private static McpSchema.CallToolRequest request(Map<String, Object> args) {
        return new McpSchema.CallToolRequest("define_dossier_type", args);
    }

    /**
     * The typed parameters after {@code request} exist only to shape the generated JSON schema
     * (see class Javadoc) — the tool always reads from {@code request.arguments()}, so tests can
     * pass null for all of them.
     */
    private Map<String, Object> invoke(Map<String, Object> args) {
        return tool.defineDossierType(request(args), null, null, null, null, null, null, null);
    }

    private static DossierType type(String code) {
        return DossierType.inherit(UUID.randomUUID(), TENANT, code, "Claim", "wf-claim", true, false, List.of());
    }
}
