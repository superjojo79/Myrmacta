package com.flowforge.infrastructure.mcp.tool;

import com.flowforge.domain.dossier.model.AccessContext;
import com.flowforge.domain.dossier.model.DossierNotFoundException;
import com.flowforge.domain.dossier.model.DossierType;
import com.flowforge.domain.dossier.model.DossierTypeSchemaIncompatibleException;
import com.flowforge.domain.dossier.model.FieldDefinition;
import com.flowforge.domain.dossier.port.in.CheckDossierTypeSchemaCompatibilityUseCase;
import com.flowforge.domain.dossier.port.in.CreateDossierTypeUseCase;
import com.flowforge.domain.dossier.port.in.GetDossierTypeUseCase;
import com.flowforge.domain.dossier.port.in.UpdateDossierTypeUseCase;
import com.flowforge.infrastructure.config.TenantProperties;
import com.flowforge.infrastructure.mcp.McpAccessContextFactory;
import io.modelcontextprotocol.spec.McpSchema;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import org.springframework.ai.mcp.annotation.McpTool;
import org.springframework.ai.mcp.annotation.McpToolParam;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

/**
 * {@code define_dossier_type} : create-or-update by {@code code}. The method takes both the raw
 * {@link McpSchema.CallToolRequest} (so the forbidden retention/SLA keys can be rejected even if
 * present as extra JSON — D-TYPE-FIELDS) <b>and</b> typed {@code @McpToolParam}s: the framework's
 * schema generator excludes {@code CallToolRequest}-typed parameters from the generated JSON
 * schema (verified against {@code McpJsonSchemaGenerator} in spring-ai-mcp-annotations 2.0.0), so
 * without the typed parameters the tool's advertised {@code inputSchema} would be an empty
 * {@code {}} — useless for a calling agent. All argument values are still read from
 * {@code request.arguments()} (the raw map), never from the typed parameters themselves, which
 * exist purely to shape the schema.
 */
@Component
public class DefineDossierTypeTool {

    private static final Set<String> FORBIDDEN_KEYS = Set.of("retentionDays", "retentionNever", "slaTargetHours");

    private final GetDossierTypeUseCase getDossierTypeUseCase;
    private final CreateDossierTypeUseCase createDossierTypeUseCase;
    private final UpdateDossierTypeUseCase updateDossierTypeUseCase;
    private final CheckDossierTypeSchemaCompatibilityUseCase checkCompatibilityUseCase;
    private final TenantProperties tenantProperties;
    private final McpAccessContextFactory accessContextFactory;
    private final ObjectMapper objectMapper;

    public DefineDossierTypeTool(
            GetDossierTypeUseCase getDossierTypeUseCase,
            CreateDossierTypeUseCase createDossierTypeUseCase,
            UpdateDossierTypeUseCase updateDossierTypeUseCase,
            CheckDossierTypeSchemaCompatibilityUseCase checkCompatibilityUseCase,
            TenantProperties tenantProperties,
            McpAccessContextFactory accessContextFactory,
            ObjectMapper objectMapper) {
        this.getDossierTypeUseCase = Objects.requireNonNull(getDossierTypeUseCase, "getDossierTypeUseCase");
        this.createDossierTypeUseCase = Objects.requireNonNull(createDossierTypeUseCase, "createDossierTypeUseCase");
        this.updateDossierTypeUseCase = Objects.requireNonNull(updateDossierTypeUseCase, "updateDossierTypeUseCase");
        this.checkCompatibilityUseCase =
                Objects.requireNonNull(checkCompatibilityUseCase, "checkCompatibilityUseCase");
        this.tenantProperties = Objects.requireNonNull(tenantProperties, "tenantProperties");
        this.accessContextFactory = Objects.requireNonNull(accessContextFactory, "accessContextFactory");
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper");
    }

    @McpTool(
            name = "define_dossier_type",
            description = "Create or update a dossier type's schema by code (label, workflowKey, active, "
                    + "allowUnknownFields, fieldDefinitions — the full schema every call). Retention/SLA policy "
                    + "cannot be set here: create defaults to inherit, update preserves the existing policy. "
                    + "dryRun=true validates without persisting.")
    public Map<String, Object> defineDossierType(
            McpSchema.CallToolRequest request,
            @McpToolParam(required = true, description = "Dossier type code (create if unknown, update if known)")
                    String code,
            @McpToolParam(required = true, description = "Display label") String label,
            @McpToolParam(required = true, description = "Workflow key this type is attached to") String workflowKey,
            @McpToolParam(required = true, description = "Whether the type is active") Boolean active,
            @McpToolParam(
                            required = true,
                            description = "Whether dossiers of this type may carry fields not declared below")
                    Boolean allowUnknownFields,
            @McpToolParam(
                            required = true,
                            description = "Full field schema — the complete list every call, not a diff. Each "
                                    + "item: {name, label, type (STRING|NUMBER|BOOLEAN|DATE|SELECT|FILE), "
                                    + "required, defaultValue, options, group, sensitive}")
                    List<Map<String, Object>> fieldDefinitions,
            @McpToolParam(
                            required = false,
                            description = "Validate without persisting (no save, no author stamp); default false")
                    Boolean dryRun) {
        // Note: the typed parameters above exist only to shape the generated JSON schema (see
        // class Javadoc) — every value actually used is re-read from the raw request map below,
        // since that is the one path already proven (unit + IT tested) to reject forbidden keys.
        Map<String, Object> args = request.arguments();
        for (String forbidden : FORBIDDEN_KEYS) {
            if (args.containsKey(forbidden)) {
                throw new IllegalArgumentException(
                        forbidden + " cannot be set via define_dossier_type (D-TYPE-FIELDS)");
            }
        }

        String rawCode = McpArgs.requireString(args, "code");
        String rawLabel = McpArgs.requireString(args, "label");
        String rawWorkflowKey = McpArgs.requireString(args, "workflowKey");
        boolean rawActive = McpArgs.requireBoolean(args, "active");
        boolean rawAllowUnknownFields = McpArgs.requireBoolean(args, "allowUnknownFields");
        List<FieldDefinition> rawFieldDefinitions = parseFieldDefinitions(args.get("fieldDefinitions"));
        boolean rawDryRun = Boolean.TRUE.equals(args.get("dryRun"));

        AccessContext.User actor = accessContextFactory.currentActor();
        String tenantId = tenantProperties.id();
        DossierType existing = findExisting(tenantId, rawCode, actor);

        if (existing == null) {
            return defineCreate(
                    tenantId, rawCode, rawLabel, rawWorkflowKey, rawActive, rawAllowUnknownFields,
                    rawFieldDefinitions, rawDryRun, actor);
        }
        return defineUpdate(
                existing, rawLabel, rawWorkflowKey, rawActive, rawAllowUnknownFields, rawFieldDefinitions, rawDryRun,
                actor);
    }

    private DossierType findExisting(String tenantId, String code, AccessContext.User actor) {
        try {
            return getDossierTypeUseCase.getByCode(tenantId, code, actor);
        } catch (DossierNotFoundException notFound) {
            return null;
        }
    }

    private Map<String, Object> defineCreate(
            String tenantId,
            String code,
            String label,
            String workflowKey,
            boolean active,
            boolean allowUnknownFields,
            List<FieldDefinition> fieldDefinitions,
            boolean dryRun,
            AccessContext.User actor) {
        if (dryRun) {
            return result("create", true, code, null);
        }
        DossierType created = createDossierTypeUseCase.create(
                new CreateDossierTypeUseCase.CreateDossierTypeCommand(
                        tenantId, code, label, workflowKey, active, allowUnknownFields, fieldDefinitions,
                        null, false, null),
                actor);
        return result("create", false, code, created);
    }

    private Map<String, Object> defineUpdate(
            DossierType existing,
            String label,
            String workflowKey,
            boolean active,
            boolean allowUnknownFields,
            List<FieldDefinition> fieldDefinitions,
            boolean dryRun,
            AccessContext.User actor) {
        var compatibility = checkCompatibilityUseCase.check(new CheckDossierTypeSchemaCompatibilityUseCase.Query(
                existing.tenantId(), existing.code(), fieldDefinitions, allowUnknownFields));
        if (!compatibility.compatible()) {
            throw new DossierTypeSchemaIncompatibleException(
                    compatibility.incompatibleCount(), compatibility.sampleReferences());
        }
        if (dryRun) {
            return result("update", true, existing.code(), null);
        }
        DossierType updated = updateDossierTypeUseCase.update(
                new UpdateDossierTypeUseCase.UpdateDossierTypeCommand(
                        existing.tenantId(), existing.code(), label, workflowKey, active, allowUnknownFields,
                        fieldDefinitions, existing.retentionDays(), existing.retentionNever(),
                        existing.slaTargetHours()),
                actor);
        return result("update", false, existing.code(), updated);
    }

    private static Map<String, Object> result(String operation, boolean dryRun, String code, DossierType type) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("operation", operation);
        map.put("dryRun", dryRun);
        map.put("code", code);
        map.put("dossierType", type);
        return map;
    }

    private List<FieldDefinition> parseFieldDefinitions(Object raw) {
        if (raw == null) {
            return List.of();
        }
        if (!(raw instanceof List<?> rawList)) {
            throw new IllegalArgumentException("fieldDefinitions must be an array");
        }
        return rawList.stream().map(item -> objectMapper.convertValue(item, FieldDefinition.class)).toList();
    }
}
