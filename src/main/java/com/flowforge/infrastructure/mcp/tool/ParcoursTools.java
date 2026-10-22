package com.flowforge.infrastructure.mcp.tool;

import com.flowforge.domain.dossier.model.AccessContext;
import com.flowforge.domain.parcours.model.Parcours;
import com.flowforge.domain.parcours.model.ParcoursDefinition;
import com.flowforge.domain.parcours.port.in.CompileParcoursUseCase;
import com.flowforge.domain.parcours.port.in.ManageParcoursUseCase;
import com.flowforge.infrastructure.mcp.McpAccessContextFactory;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.springframework.ai.mcp.annotation.McpTool;
import org.springframework.ai.mcp.annotation.McpToolParam;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

/**
 * TODO §88 (LC5) — concepteur de parcours exposé au MCP (et donc à l'assistant) : {@code list_parcours}, {@code get_parcours},
 * {@code define_parcours} (crée ou met à jour ; la définition est validée comme à la compilation) et {@code compile_parcours}
 * (BPMN déposé en <b>brouillon</b> — jamais publié —, ou à blanc). Un agent n'édite que le parcours ; le BPMN en découle.
 */
@Component
public class ParcoursTools {

    private final ManageParcoursUseCase manage;
    private final CompileParcoursUseCase compile;
    private final McpAccessContextFactory accessContextFactory;
    private final ObjectMapper objectMapper;

    public ParcoursTools(
            ManageParcoursUseCase manage,
            CompileParcoursUseCase compile,
            McpAccessContextFactory accessContextFactory,
            ObjectMapper objectMapper) {
        this.manage = Objects.requireNonNull(manage, "manage");
        this.compile = Objects.requireNonNull(compile, "compile");
        this.accessContextFactory = Objects.requireNonNull(accessContextFactory, "accessContextFactory");
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper");
    }

    @McpTool(name = "list_parcours", description = "List the parcours (step-by-step designs compiled into BPMN): key, label, version, "
            + "number of steps, last update and last compilation.")
    public List<Map<String, Object>> listParcours() {
        AccessContext.User actor = accessContextFactory.currentActor();
        return manage.list(actor).stream().map(p -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("key", p.key());
            m.put("label", p.label());
            m.put("version", p.version());
            m.put("steps", p.definition().steps().size());
            m.put("updatedAt", p.updatedAt());
            m.put("lastCompiledAt", p.lastCompiledAt());
            return m;
        }).toList();
    }

    @McpTool(name = "get_parcours", description = "Read one parcours with its full definition (statuses, steps) and its version, "
            + "needed as expectedVersion to update it.")
    public Map<String, Object> getParcours(@McpToolParam(required = true, description = "Parcours key") String key) {
        return view(manage.get(accessContextFactory.currentActor(), McpArgs.requireNonBlank(key, "key")));
    }

    @McpTool(name = "define_parcours", description = "Create or update a parcours: a sequence of steps a business user can read, compiled "
            + "into BPMN by compile_parcours. Steps run in list order unless 'next' says otherwise; a DECISION step chooses its path with "
            + "simple comparisons (variable operator value) and a defaultNext, and may loop back. Step kinds and their params: "
            + "QUESTION {schemaKey}; EXTERNAL {schemaKey, portalRecipient, portalLinkTtlDays?}; DOCUMENT {documentType, documentVariant?, "
            + "documentLocale?, attach?=true|false}; NOTIFY {notificationChannels, notificationTo?, notificationSubject?, notificationBody?, "
            + "notificationUserId?, notificationWebhookConfigId?}; CONNECTOR {connectorInstanceId}; DMN {decisionTableReferenceKey}; "
            + "EXPRESSION {formula, formulaResult}; MAP_FIELDS {fieldMapping}; SET_WORKFLOW_STATUS {targetWorkflowStatus}; "
            + "SET_DOSSIER_STATUS {targetDossierStatus}; WAIT {duration e.g. PT24H, P2D}; DECISION {branches:[{variable, operator "
            + "EQ|NE|GT|GE|LT|LE, value, next}], defaultNext}. The definition is validated (unreachable steps, loops with no exit, "
            + "undeclared statuses are refused). No secret goes here. dryRun=true validates without saving.")
    public Map<String, Object> defineParcours(
            @McpToolParam(required = true, description = "Parcours key = BPMN process key (camelCase)") String key,
            @McpToolParam(required = true, description = "Display label") String label,
            @McpToolParam(required = true, description = "Workflow statuses, exactly one initial: [{code, label, initial, outcome "
                    + "(NONE|COMPLETED|CANCELLED|FAILED)}]") List<Map<String, Object>> statuses,
            @McpToolParam(required = true, description = "Dossier statuses allowed while it runs, non-empty "
                    + "(DRAFT|OUVERT|EN_COURS|SUSPENDU|CLOTURE|ARCHIVE)") List<String> allowedDossierStatuses,
            @McpToolParam(required = false, description = "Dossier status set when the workflow starts") String onStartDossierStatus,
            @McpToolParam(required = true, description = "Steps: [{id (camelCase), label, kind, params:{…}, next?, branches?:[…], "
                    + "defaultNext?}]") List<Map<String, Object>> steps,
            @McpToolParam(required = false, description = "Code of the dossier type the parcours works on (helps the designer screen "
                    + "suggest its fields; not compiled into the BPMN)") String dossierTypeCode,
            @McpToolParam(required = false, description = "Version read with get_parcours; absent = create") Integer expectedVersion,
            @McpToolParam(required = false, description = "Validate without saving; default false") Boolean dryRun) {
        AccessContext.User actor = accessContextFactory.currentActor();
        String parcoursKey = McpArgs.requireNonBlank(key, "key");
        Map<String, Object> raw = new LinkedHashMap<>();
        raw.put("statuses", statuses == null ? List.of() : statuses);
        raw.put("allowedDossierStatuses", allowedDossierStatuses == null ? List.of() : allowedDossierStatuses);
        raw.put("onStartDossierStatus", onStartDossierStatus == null || onStartDossierStatus.isBlank() ? null : onStartDossierStatus.strip());
        raw.put("steps", steps == null ? List.of() : steps);
        raw.put("dossierTypeCode", dossierTypeCode);
        ParcoursDefinition definition = objectMapper.convertValue(raw, ParcoursDefinition.class);
        if (Boolean.TRUE.equals(dryRun)) {
            var preview = compile.preview(actor, parcoursKey, label, definition);
            Map<String, Object> out = new LinkedHashMap<>();
            out.put("dryRun", true);
            out.put("key", parcoursKey);
            // « valide » = le déploiement accepterait le BPMN (parseur, liste blanche, références), pas seulement le compilateur
            out.put("valid", preview.problem() == null);
            if (preview.problem() != null) {
                out.put("problem", preview.problem());
            }
            out.put("warnings", preview.compiled().warnings());
            return out;
        }
        Parcours saved = manage.save(actor, new ManageParcoursUseCase.SaveCommand(parcoursKey, label, definition, expectedVersion));
        Map<String, Object> out = view(saved);
        out.put("dryRun", false);
        return out;
    }

    @McpTool(name = "compile_parcours", description = "Compile a saved parcours into BPMN and save it as a DRAFT process (never published: a human "
            + "publishes it from Admin > Drafts; nothing runs until then). dryRun=true validates the BPMN (parser, allowlist, references to "
            + "documents, connectors, schemas, DMN tables) without writing. Returns the BPMN XML and non-blocking warnings. A draft written "
            + "by someone else is not overwritten unless replaceExistingDraft=true.")
    public Map<String, Object> compileParcours(
            @McpToolParam(required = true, description = "Parcours key") String key,
            @McpToolParam(required = false, description = "Validate without writing; default false") Boolean dryRun,
            @McpToolParam(required = false, description = "Overwrite a draft by another author; default false") Boolean replaceExistingDraft) {
        AccessContext.User actor = accessContextFactory.currentActor();
        var result = compile.compile(actor, accessContextFactory.provenance(), McpArgs.requireNonBlank(key, "key"),
                Boolean.TRUE.equals(dryRun), Boolean.TRUE.equals(replaceExistingDraft));
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("key", key.trim());
        out.put("dryRun", result.proposal() == null);
        out.put("status", result.proposal() == null ? "VALID" : "DRAFT_PENDING");
        out.put("warnings", result.compiled().warnings());
        out.put("sha256", result.compiled().sha256());
        out.put("xml", result.compiled().xml());
        return out;
    }

    private Map<String, Object> view(Parcours p) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("key", p.key());
        m.put("label", p.label());
        m.put("version", p.version());
        m.put("definition", objectMapper.convertValue(p.definition(), Map.class));
        m.put("updatedAt", p.updatedAt());
        m.put("lastCompiledAt", p.lastCompiledAt());
        return m;
    }
}
