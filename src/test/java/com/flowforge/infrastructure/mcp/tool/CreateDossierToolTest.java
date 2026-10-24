package com.flowforge.infrastructure.mcp.tool;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.flowforge.domain.dossier.model.Dossier;
import com.flowforge.domain.dossier.model.DossierFieldValidationException;
import com.flowforge.domain.dossier.model.DossierStatus;
import com.flowforge.domain.dossier.model.DossierType;
import com.flowforge.domain.dossier.model.FieldDefinition;
import com.flowforge.domain.dossier.model.FieldType;
import com.flowforge.domain.dossier.model.InactiveDossierTypeException;
import com.flowforge.domain.dossier.port.in.CreateDossierUseCase;
import com.flowforge.domain.dossier.port.in.GetDossierTypeUseCase;
import com.flowforge.infrastructure.config.TenantProperties;
import com.flowforge.infrastructure.mcp.McpAccessContextFactory;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

class CreateDossierToolTest {

    private static final String TENANT = "tenant_dev";

    private CreateDossierUseCase createDossierUseCase;
    private GetDossierTypeUseCase getDossierTypeUseCase;
    private CreateDossierTool tool;

    @BeforeEach
    void setUp() {
        createDossierUseCase = mock(CreateDossierUseCase.class);
        getDossierTypeUseCase = mock(GetDossierTypeUseCase.class);
        tool = new CreateDossierTool(
                createDossierUseCase,
                getDossierTypeUseCase,
                new TenantProperties(TENANT),
                new McpAccessContextFactory());
        bindJwt();
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void create_delegatesCommandWithActorSubject() {
        UUID id = UUID.randomUUID();
        when(createDossierUseCase.create(any(), any())).thenReturn(dossier(id));

        Object result = tool.createDossier("SINISTRE", "Acme", Map.of("amount", 10), false);

        ArgumentCaptor<CreateDossierUseCase.CreateDossierCommand> captor =
                ArgumentCaptor.forClass(CreateDossierUseCase.CreateDossierCommand.class);
        verify(createDossierUseCase).create(captor.capture(), any());
        assertThat(captor.getValue().tenantId()).isEqualTo(TENANT);
        assertThat(captor.getValue().dossierTypeCode()).isEqualTo("SINISTRE");
        assertThat(captor.getValue().label()).isEqualTo("Acme");
        assertThat(captor.getValue().fields()).containsEntry("amount", 10);
        assertThat(captor.getValue().createdBy()).isEqualTo("agent-1");
        assertThat(result).isInstanceOf(DossierMcpView.class);
        assertThat(((DossierMcpView) result).id()).isEqualTo(id);
    }

    @Test
    void create_nullFields_sendsEmptyMap() {
        when(createDossierUseCase.create(any(), any())).thenReturn(dossier(UUID.randomUUID()));

        tool.createDossier("SINISTRE", "Acme", null, false);

        ArgumentCaptor<CreateDossierUseCase.CreateDossierCommand> captor =
                ArgumentCaptor.forClass(CreateDossierUseCase.CreateDossierCommand.class);
        verify(createDossierUseCase).create(captor.capture(), any());
        assertThat(captor.getValue().fields()).isEmpty();
    }

    @Test
    void create_blankType_isRejectedWithoutCallingUseCase() {
        assertThatThrownBy(() -> tool.createDossier("  ", "Acme", Map.of(), false))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("dossierTypeCode is required");
        verify(createDossierUseCase, never()).create(any(), any());
    }

    @Test
    void create_blankLabel_isRejectedWithoutCallingUseCase() {
        assertThatThrownBy(() -> tool.createDossier("SINISTRE", "  ", Map.of(), false))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("label is required");
        verify(createDossierUseCase, never()).create(any(), any());
    }

    @Test
    void dryRun_doesNotPersist() {
        when(getDossierTypeUseCase.getByCode(eq(TENANT), eq("SINISTRE"), any()))
                .thenReturn(type(true, List.of()));

        @SuppressWarnings("unchecked")
        Map<String, Object> preview = (Map<String, Object>) tool.createDossier("SINISTRE", "Acme", Map.of(), true);

        assertThat(preview).containsEntry("dryRun", true).containsEntry("valid", true);
        verify(createDossierUseCase, never()).create(any(), any());
    }

    @Test
    void dryRun_appliesFieldRules_reviewD1M2() {
        var engine = new com.flowforge.infrastructure.expression.JsonataExpressionEngine(
                com.flowforge.domain.expression.model.ExpressionLimits.DEFAULTS, java.time.Clock.systemUTC());
        var ruledTool = new CreateDossierTool(createDossierUseCase, getDossierTypeUseCase, new TenantProperties(TENANT),
                new McpAccessContextFactory(), new com.flowforge.domain.dossier.DossierFieldRules(engine));
        var a = new com.flowforge.domain.dossier.model.FieldDefinition("a", "a", com.flowforge.domain.dossier.model.FieldType.STRING, false, null, null, null, false);
        var hiddenRequired = new com.flowforge.domain.dossier.model.FieldDefinition("h", "h", com.flowforge.domain.dossier.model.FieldType.STRING, true,
                null, null, null, false, null, null, null, null, "dossier.fields.a = 'Y'", null);
        var computed = new com.flowforge.domain.dossier.model.FieldDefinition("c", "c", com.flowforge.domain.dossier.model.FieldType.STRING, false,
                null, null, null, false, null, null, null, "'x'", null, null);
        when(getDossierTypeUseCase.getByCode(eq(TENANT), eq("SINISTRE"), any()))
                .thenReturn(type(true, List.of(a, hiddenRequired, computed)));

        // champ masqué obligatoire : l'aperçu n'échoue pas (la création réelle passe)
        @SuppressWarnings("unchecked")
        Map<String, Object> ok = (Map<String, Object>) ruledTool.createDossier("SINISTRE", "Acme", Map.of("a", "N"), true);
        assertThat(ok).containsEntry("valid", true);
        // valeur fournie pour un champ calculé : l'aperçu la refuse comme la création
        assertThatThrownBy(() -> ruledTool.createDossier("SINISTRE", "Acme", Map.of("a", "N", "c", "y"), true))
                .isInstanceOf(com.flowforge.domain.dossier.model.DossierFieldValidationException.class);
    }

    @Test
    void dryRun_inactiveType_isRejected() {
        when(getDossierTypeUseCase.getByCode(eq(TENANT), eq("SINISTRE"), any()))
                .thenReturn(type(false, List.of()));

        assertThatThrownBy(() -> tool.createDossier("SINISTRE", "Acme", Map.of(), true))
                .isInstanceOf(InactiveDossierTypeException.class);
        verify(createDossierUseCase, never()).create(any(), any());
    }

    @Test
    void dryRun_invalidFields_isRejected() {
        when(getDossierTypeUseCase.getByCode(eq(TENANT), eq("SINISTRE"), any()))
                .thenReturn(type(true, List.of(new FieldDefinition(
                        "amount", "Amount", FieldType.NUMBER, true, null, List.of(), null))));

        assertThatThrownBy(() -> tool.createDossier("SINISTRE", "Acme", Map.of(), true))
                .isInstanceOf(DossierFieldValidationException.class);
        verify(createDossierUseCase, never()).create(any(), any());
    }

    /** TODO §102 — l'essai à blanc annonce le doublon que la création refuserait, avec la référence du détenteur. */
    @Test
    void dryRun_reportsADuplicateOfAUniqueField() {
        var plaque = new FieldDefinition("plaque", "Plaque", FieldType.STRING, false, null, null, null, false, null, null,
                null, null, null, null, false, null, null, true);
        when(getDossierTypeUseCase.getByCode(eq(TENANT), eq("SINISTRE"), any())).thenReturn(type(true, List.of(plaque)));
        var port = org.mockito.Mockito.mock(com.flowforge.domain.dossier.port.out.DossierUniquenessPort.class);
        var key = new com.flowforge.domain.dossier.model.UniqueKey("plaque", "BE-1");
        when(port.findHolders(TENANT, "SINISTRE", java.util.Set.of(key), null)).thenReturn(Map.of(key, "DOS-1"));
        var checked = new CreateDossierTool(createDossierUseCase, getDossierTypeUseCase, new TenantProperties(TENANT),
                new McpAccessContextFactory(), com.flowforge.domain.dossier.DossierFieldRules.disabled(), port);

        assertThatThrownBy(() -> checked.createDossier("SINISTRE", "Acme", Map.of("plaque", "BE-1"), true))
                .isInstanceOf(DossierFieldValidationException.class)
                .hasMessageContaining("plaque UNIQUE")
                .hasMessageContaining("DOS-1");
        verify(createDossierUseCase, never()).create(any(), any());
    }

    private static void bindJwt() {
        Jwt jwt = Jwt.withTokenValue("t")
                .header("alg", "none")
                .claim("sub", "agent-1")
                .issuedAt(Instant.EPOCH)
                .expiresAt(Instant.EPOCH.plusSeconds(3600))
                .build();
        SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(jwt, List.of()));
    }

    private static Dossier dossier(UUID id) {
        Instant now = Instant.parse("2026-09-08T00:00:00Z");
        return new Dossier(
                id, TENANT, "REF-1", "SINISTRE", "Acme", DossierStatus.DRAFT,
                Map.of("amount", 10), null, 0L, "agent-1", now, "agent-1", now, null, null, null);
    }

    private static DossierType type(boolean active, List<FieldDefinition> fields) {
        return new DossierType(
                UUID.randomUUID(), TENANT, "SINISTRE", "Sinistre", "wf-sinistre", active, false, fields,
                null, false, null, "system", "system");
    }
}
