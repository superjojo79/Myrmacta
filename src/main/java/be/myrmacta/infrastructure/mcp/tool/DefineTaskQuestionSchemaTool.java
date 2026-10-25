package be.myrmacta.infrastructure.mcp.tool;

import be.myrmacta.domain.dossier.model.AccessContext;
import be.myrmacta.domain.dossier.model.FieldDefinition;
import be.myrmacta.domain.workflow.model.TaskQuestionSchema;
import be.myrmacta.domain.workflow.port.in.GetTaskQuestionSchemaUseCase;
import be.myrmacta.domain.workflow.port.in.SaveTaskQuestionSchemaUseCase;
import be.myrmacta.infrastructure.config.TenantProperties;
import be.myrmacta.infrastructure.mcp.McpAccessContextFactory;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import org.springframework.ai.mcp.annotation.McpTool;
import org.springframework.ai.mcp.annotation.McpToolParam;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

/**
 * {@code define_task_question_schema} : create-or-update by {@code schemaKey} — same upsert shape
 * as {@code define_dossier_type}. {@code dryRun=true} still constructs the
 * {@link TaskQuestionSchema} (its compact constructor validates: non-empty fields, unique names,
 * no {@code FILE} type, no collision with reserved/known workflow variable names) without calling
 * {@link SaveTaskQuestionSchemaUseCase#save} — unlike {@code define_dossier_type}'s create dry run,
 * this validation is pure in-memory object construction, not a DB-backed compatibility check, so
 * there is no cost reason to skip it.
 */
@Component
public class DefineTaskQuestionSchemaTool {

    private final GetTaskQuestionSchemaUseCase getTaskQuestionSchemaUseCase;
    private final SaveTaskQuestionSchemaUseCase saveTaskQuestionSchemaUseCase;
    private final TenantProperties tenantProperties;
    private final McpAccessContextFactory accessContextFactory;
    private final ObjectMapper objectMapper;

    public DefineTaskQuestionSchemaTool(
            GetTaskQuestionSchemaUseCase getTaskQuestionSchemaUseCase,
            SaveTaskQuestionSchemaUseCase saveTaskQuestionSchemaUseCase,
            TenantProperties tenantProperties,
            McpAccessContextFactory accessContextFactory,
            ObjectMapper objectMapper) {
        this.getTaskQuestionSchemaUseCase =
                Objects.requireNonNull(getTaskQuestionSchemaUseCase, "getTaskQuestionSchemaUseCase");
        this.saveTaskQuestionSchemaUseCase =
                Objects.requireNonNull(saveTaskQuestionSchemaUseCase, "saveTaskQuestionSchemaUseCase");
        this.tenantProperties = Objects.requireNonNull(tenantProperties, "tenantProperties");
        this.accessContextFactory = Objects.requireNonNull(accessContextFactory, "accessContextFactory");
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper");
    }

    @McpTool(
            name = "define_task_question_schema",
            description = "Create or update a task question schema by schemaKey — a mini-form (one or more "
                    + "fields) a userTask can display via flowable:formKey=\"user-question:<schemaKey>\". "
                    + "Each response becomes a process variable (name = field name); it is never written to "
                    + "a dossier. dryRun=true validates without persisting.")
    public Map<String, Object> defineTaskQuestionSchema(
            @McpToolParam(required = true, description = "Schema key (create if unknown, update if known)")
                    String schemaKey,
            @McpToolParam(required = true, description = "Display label") String label,
            @McpToolParam(
                            required = true,
                            description = "Full field list — the complete schema every call, not a diff. Each "
                                    + "item: {name, label, type (STRING|NUMBER|BOOLEAN|DATE|SELECT), required, "
                                    + "defaultValue, options, group (section title), helpText (plain text, 500 chars max), width (HALF = two per row; default full)}. FILE, MONEY, REPEATABLE_GROUP and EXTERNAL_REF are not supported here. Field names "
                                    + "must not collide with reserved or already-used workflow variable names.")
                    List<Map<String, Object>> fields,
            @McpToolParam(
                            required = false,
                            description = "Validate without persisting (no save, no author stamp); default false")
                    Boolean dryRun) {
        AccessContext.User actor = accessContextFactory.currentActor();
        List<FieldDefinition> parsedFields = parseFields(fields);
        boolean rawDryRun = Boolean.TRUE.equals(dryRun);
        String actorId = actor.userId();

        TaskQuestionSchema existing = getTaskQuestionSchemaUseCase.getByKey(schemaKey, actor).orElse(null);
        if (existing == null) {
            // dryRun on create still constructs the candidate below (validates fields) but does not
            // reach this branch's save() call — see class Javadoc.
            TaskQuestionSchema candidate = new TaskQuestionSchema(
                    UUID.randomUUID(), tenantProperties.id(), schemaKey, label, parsedFields, actorId, actorId);
            if (rawDryRun) {
                return result("create", true, schemaKey, null);
            }
            return result("create", false, schemaKey, saveTaskQuestionSchemaUseCase.save(candidate, actor));
        }
        TaskQuestionSchema candidate = existing.withUpdate(label, parsedFields, actorId);
        if (rawDryRun) {
            return result("update", true, schemaKey, null);
        }
        return result("update", false, schemaKey, saveTaskQuestionSchemaUseCase.save(candidate, actor));
    }

    private List<FieldDefinition> parseFields(Object raw) {
        if (raw == null) {
            throw new IllegalArgumentException("fields is required");
        }
        if (!(raw instanceof List<?> rawList)) {
            throw new IllegalArgumentException("fields must be an array");
        }
        return rawList.stream().map(item -> objectMapper.convertValue(item, FieldDefinition.class)).toList();
    }

    private static Map<String, Object> result(
            String operation, boolean dryRun, String schemaKey, TaskQuestionSchema schema) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("operation", operation);
        map.put("dryRun", dryRun);
        map.put("schemaKey", schemaKey);
        map.put("taskQuestionSchema", schema);
        return map;
    }
}
