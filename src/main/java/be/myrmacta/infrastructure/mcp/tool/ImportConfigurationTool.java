package be.myrmacta.infrastructure.mcp.tool;

import be.myrmacta.domain.promotion.model.ArtifactType;
import be.myrmacta.domain.promotion.model.BundleEntry;
import be.myrmacta.domain.promotion.model.ConfigurationBundle;
import be.myrmacta.domain.promotion.model.InvalidBundleException;
import be.myrmacta.domain.promotion.port.in.ImportConfigurationUseCase;
import be.myrmacta.infrastructure.mcp.McpAccessContextFactory;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.springframework.ai.mcp.annotation.McpTool;
import org.springframework.ai.mcp.annotation.McpToolParam;
import org.springframework.stereotype.Component;

/**
 * TODO §83 (D4, D-PROMO-MCP) — {@code import_configuration} : plan à blanc par défaut ; avec {@code apply=true}, seuls les
 * brouillons BPMN et DMN (avec la configuration de workflow jointe) sont déposés — ils attendent la publication humaine — et,
 * TODO §117 (D-PROMO-MCP-TEMPLATE), les brouillons de templates, qui attendent leur activation. Types de dossier, schémas,
 * connecteurs, configurations autonomes, groupes et réglages de type passent par l'écran d'administration. Aucun secret n'est
 * accepté.
 */
@Component
public class ImportConfigurationTool {

    private final ImportConfigurationUseCase importUseCase;
    private final McpAccessContextFactory accessContextFactory;

    public ImportConfigurationTool(ImportConfigurationUseCase importUseCase, McpAccessContextFactory accessContextFactory) {
        this.importUseCase = Objects.requireNonNull(importUseCase, "importUseCase");
        this.accessContextFactory = Objects.requireNonNull(accessContextFactory, "accessContextFactory");
    }

    @McpTool(
            name = "import_configuration",
            description = "Dry-run (default) or stage a configuration bundle produced by export_configuration. entries: list of "
                    + "{type, key, content} (type: DOSSIER_TYPE, TASK_QUESTION_SCHEMA, CONNECTOR_INSTANCE, DMN, BPMN, "
                    + "WORKFLOW_CONFIG, USER_GROUP, REPLY_WORKFLOW, DEADLINE, DOCUMENT_TEMPLATE, NOTIFICATION_TEMPLATE, PARCOURS; content: the file text). "
                    + "The plan says per artifact CREATE, UPDATE, UNCHANGED, CONFLICT or BLOCKED and why. apply=true only "
                    + "saves BPMN, DMN, document template and notification template DRAFTS (a human publishes or activates them); everything else is "
                    + "applied from the admin import screen. Secrets are never accepted. parameters replace {{name}} "
                    + "placeholders in connector configuration and in the identity-provider group of a user group.")
    public Map<String, Object> importConfiguration(
            @McpToolParam(required = true, description = "Bundle files: [{type, key, content}]") List<Map<String, Object>> entries,
            @McpToolParam(required = false, description = "Label of the source environment") String sourceLabel,
            @McpToolParam(required = false, description = "Environment parameters for {{name}} placeholders")
                    Map<String, Object> parameters,
            @McpToolParam(required = false, description = "false (default): dry-run plan. true: save BPMN, DMN and template drafts") Boolean apply,
            @McpToolParam(required = false, description = "bundleSha256 returned by the plan you reviewed") String expectedBundleSha256) {
        ConfigurationBundle bundle = toBundle(entries, sourceLabel);
        Map<String, String> params = new LinkedHashMap<>();
        if (parameters != null) {
            parameters.forEach((k, v) -> params.put(k, v == null ? "" : String.valueOf(v)));
        }
        var actor = accessContextFactory.currentActor();
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("bundleSha256", bundle.sha256());
        if (Boolean.TRUE.equals(apply)) {
            result.put("report", importUseCase.apply(
                    actor, bundle, Map.of(), params, expectedBundleSha256, ImportConfigurationUseCase.Channel.MCP));
        } else {
            result.put("plan", importUseCase.plan(actor, bundle, params, ImportConfigurationUseCase.Channel.MCP));
        }
        return result;
    }

    private static ConfigurationBundle toBundle(List<Map<String, Object>> entries, String sourceLabel) {
        if (entries == null || entries.isEmpty()) {
            throw new InvalidBundleException("entries is required");
        }
        List<BundleEntry> list = new ArrayList<>();
        for (Map<String, Object> e : entries) {
            Object type = e.get("type");
            Object key = e.get("key");
            Object content = e.get("content");
            if (!(type instanceof String t) || !(key instanceof String k) || !(content instanceof String c)) {
                throw new InvalidBundleException("each entry needs a string type, key and content");
            }
            ArtifactType artifactType;
            try {
                artifactType = ArtifactType.valueOf(t.strip().toUpperCase(java.util.Locale.ROOT));
            } catch (IllegalArgumentException ex) {
                throw new InvalidBundleException("unknown artifact type: " + t);
            }
            list.add(new BundleEntry(artifactType, k, c.getBytes(StandardCharsets.UTF_8)));
        }
        return new ConfigurationBundle(ConfigurationBundle.FORMAT_VERSION, Instant.now(), sourceLabel, "", list);
    }
}
