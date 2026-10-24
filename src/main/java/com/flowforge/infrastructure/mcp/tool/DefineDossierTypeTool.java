package com.flowforge.infrastructure.mcp.tool;

import com.flowforge.domain.dossier.DossierFieldRules;
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
    private final DossierFieldRules fieldRules;

    public DefineDossierTypeTool(
            GetDossierTypeUseCase getDossierTypeUseCase,
            CreateDossierTypeUseCase createDossierTypeUseCase,
            UpdateDossierTypeUseCase updateDossierTypeUseCase,
            CheckDossierTypeSchemaCompatibilityUseCase checkCompatibilityUseCase,
            TenantProperties tenantProperties,
            McpAccessContextFactory accessContextFactory,
            ObjectMapper objectMapper) {
        this(getDossierTypeUseCase, createDossierTypeUseCase, updateDossierTypeUseCase, checkCompatibilityUseCase,
                tenantProperties, accessContextFactory, objectMapper, DossierFieldRules.disabled());
    }

    @org.springframework.beans.factory.annotation.Autowired
    public DefineDossierTypeTool(
            GetDossierTypeUseCase getDossierTypeUseCase,
            CreateDossierTypeUseCase createDossierTypeUseCase,
            UpdateDossierTypeUseCase updateDossierTypeUseCase,
            CheckDossierTypeSchemaCompatibilityUseCase checkCompatibilityUseCase,
            TenantProperties tenantProperties,
            McpAccessContextFactory accessContextFactory,
            ObjectMapper objectMapper,
            DossierFieldRules fieldRules) {
        this.fieldRules = Objects.requireNonNull(fieldRules, "fieldRules");
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
                    + "documentTemplateKeys is optional: absent/null preserves the existing association on "
                    + "update (defaults to empty on create); provide the full list to replace it. "
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
                                    + "item: {name, label, type (STRING|NUMBER|BOOLEAN|DATE|SELECT|FILE|MONEY|"
                                    + "REPEATABLE_GROUP|EXTERNAL_REF), required, defaultValue, options, group, sensitive, "
                                    + "fields, minRows, maxRows, formula, visibleWhen, requiredWhen, searchable, unique}. MONEY value {amount:\"1234.50\",currency:\"EUR\"} "
                                    + "(options = allowed ISO 4217 currencies, amount checked against the currency "
                                    + "decimals, never rounded); EXTERNAL_REF value {system,id,label?} (options = allowed "
                                    + "systems); REPEATABLE_GROUP value = list of rows typed by `fields` (one level, at "
                                    + "most 20 flat non-sensitive sub-fields, minRows/maxRows up to 500 — mark the group "
                                    + "itself sensitive, not its sub-fields). RULES (JSONata, bounded, read the plain "
                                    + "non-sensitive fields as dossier.fields.x): formula = computed read-only field "
                                    + "recomputed on every save; visibleWhen = field applicable only when true; requiredWhen "
                                    + "= field required when true. Try them with evaluate_dossier_rules and "
                                    + "evaluate_expression. searchable=true (STRING, NUMBER, DATE, BOOLEAN, SELECT; never "
                                    + "sensitive) makes the field usable as a search_dossiers filter. unique=true (STRING, NUMBER, "
                                    + "DATE; never sensitive; computed allowed) = a value may appear in only one non-closed "
                                    + "dossier of the type: a duplicate is refused with error code UNIQUE, and enabling it "
                                    + "while open duplicates exist is refused. Layout (form designer): group = "
                                    + "section title (fields sharing a group are shown together, in order); width = HALF "
                                    + "(two fields per row, default full width); helpText = plain-text hint under the field "
                                    + "(500 chars max)")
                    List<Map<String, Object>> fieldDefinitions,
            @McpToolParam(
                            required = false,
                            description = "Document template keys usable by document-generation task forms on "
                                    + "this type — the full list every call, not a diff. Absent/null preserves "
                                    + "the existing association on update (empty on create).")
                    List<String> documentTemplateKeys,
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
        List<String> rawDocumentTemplateKeys = parseDocumentTemplateKeys(args.get("documentTemplateKeys"));
        boolean rawDryRun = Boolean.TRUE.equals(args.get("dryRun"));

        AccessContext.User actor = accessContextFactory.currentActor();
        String tenantId = tenantProperties.id();
        DossierType existing = findExisting(tenantId, rawCode, actor);

        if (existing == null) {
            return defineCreate(
                    tenantId, rawCode, rawLabel, rawWorkflowKey, rawActive, rawAllowUnknownFields,
                    rawFieldDefinitions, rawDocumentTemplateKeys, rawDryRun, actor);
        }
        return defineUpdate(
                existing, rawLabel, rawWorkflowKey, rawActive, rawAllowUnknownFields, rawFieldDefinitions,
                rawDocumentTemplateKeys, rawDryRun, actor);
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
            List<String> documentTemplateKeys,
            boolean dryRun,
            AccessContext.User actor) {
        if (dryRun) {
            // TODO §80 : le dry-run valide aussi les règles (syntaxe, constructions permises, champs lus) — sinon il mentirait.
            fieldRules.validateDefinitions(fieldDefinitions);
            return result("create", true, code, null);
        }
        DossierType created = createDossierTypeUseCase.create(
                new CreateDossierTypeUseCase.CreateDossierTypeCommand(
                        tenantId, code, label, workflowKey, active, allowUnknownFields, fieldDefinitions,
                        null, false, null, documentTemplateKeys == null ? List.of() : documentTemplateKeys),
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
            List<String> documentTemplateKeys,
            boolean dryRun,
            AccessContext.User actor) {
        // D-DESIGN-CHECK-PLACEMENT: the real (non-dryRun) write path no longer checks
        // compatibility here — UpdateDossierTypeUseCaseImpl does it itself (single source of
        // truth shared with REST) and throws DossierTypeSchemaIncompatibleException, which
        // propagates unchanged. dryRun must never call update() (nothing may persist), so it still
        // needs its own explicit check call to preview compatibility.
        if (dryRun) {
            fieldRules.validateDefinitions(fieldDefinitions);
            var compatibility = checkCompatibilityUseCase.check(new CheckDossierTypeSchemaCompatibilityUseCase.Query(
                    existing.tenantId(), existing.code(), fieldDefinitions, allowUnknownFields));
            if (!compatibility.compatible()) {
                throw new DossierTypeSchemaIncompatibleException(
                        compatibility.incompatibleCount(),
                        compatibility.sampleReferences(),
                        compatibility.duplicates(),
                        compatibility.duplicateCount());
            }
            return result("update", true, existing.code(), null);
        }
        // Absent/null documentTemplateKeys preserves the existing association (extension-mcp-flux-metier,
        // D-MCP-EXT-DOCTPL) — same "absent = unchanged" contract already used elsewhere in this tool.
        List<String> resolvedDocumentTemplateKeys =
                documentTemplateKeys == null ? existing.documentTemplateKeys() : documentTemplateKeys;
        DossierType updated = updateDossierTypeUseCase.update(
                new UpdateDossierTypeUseCase.UpdateDossierTypeCommand(
                        existing.tenantId(), existing.code(), label, workflowKey, active, allowUnknownFields,
                        fieldDefinitions, existing.retentionDays(), existing.retentionNever(),
                        existing.slaTargetHours(), resolvedDocumentTemplateKeys),
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

    private static List<String> parseDocumentTemplateKeys(Object raw) {
        if (raw == null) {
            return null;
        }
        if (!(raw instanceof List<?> rawList)) {
            throw new IllegalArgumentException("documentTemplateKeys must be an array");
        }
        return rawList.stream().map(item -> {
            if (!(item instanceof String key)) {
                throw new IllegalArgumentException("documentTemplateKeys items must be strings");
            }
            return key;
        }).toList();
    }
}
