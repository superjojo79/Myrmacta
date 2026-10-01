package com.flowforge.infrastructure.mcp.tool;

import com.flowforge.domain.promotion.model.BundleEntry;
import com.flowforge.domain.promotion.model.ConfigurationBundle;
import com.flowforge.domain.promotion.port.in.ExportConfigurationUseCase;
import com.flowforge.infrastructure.mcp.McpAccessContextFactory;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.springframework.ai.mcp.annotation.McpTool;
import org.springframework.ai.mcp.annotation.McpToolParam;
import org.springframework.stereotype.Component;

/**
 * TODO §83 (D4) — {@code export_configuration} : la configuration du tenant (types de dossier, schémas de questions, connecteurs
 * sans secrets, tables DMN et processus BPMN déployés, configurations de workflow) sous forme de fichiers texte. Lecture seule ;
 * jamais de secret. Un export volumineux se télécharge depuis l'écran d'administration (ZIP).
 */
@Component
public class ExportConfigurationTool {

    /** Au-delà, le résultat n'a plus sa place dans un contexte d'agent : l'export ZIP de l'écran prend le relais. */
    static final int MAX_TOTAL_CHARS = 2_000_000;

    private final ExportConfigurationUseCase exportUseCase;
    private final McpAccessContextFactory accessContextFactory;

    public ExportConfigurationTool(ExportConfigurationUseCase exportUseCase, McpAccessContextFactory accessContextFactory) {
        this.exportUseCase = Objects.requireNonNull(exportUseCase, "exportUseCase");
        this.accessContextFactory = Objects.requireNonNull(accessContextFactory, "accessContextFactory");
    }

    @McpTool(
            name = "export_configuration",
            description = "Export this instance's configuration as a versioned bundle of text files: dossier types, task question "
                    + "schemas, connector instances (secret fields removed), deployed DMN tables and BPMN processes, workflow "
                    + "configurations. Read-only, never contains secrets. Feed the entries to import_configuration on another "
                    + "instance (dry-run plan first). Too large an export is refused: use the admin screen's ZIP export.")
    public Map<String, Object> exportConfiguration(
            @McpToolParam(required = false, description = "Free label of this environment (e.g. recette)") String sourceLabel) {
        ConfigurationBundle bundle = exportUseCase.export(accessContextFactory.currentActor(), sourceLabel);
        long chars = 0;
        List<Map<String, Object>> entries = new java.util.ArrayList<>();
        for (BundleEntry e : bundle.entries()) {
            String text = e.text();
            chars += text.length();
            if (chars > MAX_TOTAL_CHARS) {
                throw new IllegalArgumentException("the configuration is too large for MCP (" + bundle.entries().size()
                        + " artifacts): download the ZIP export from the admin screen");
            }
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("type", e.type().name());
            entry.put("key", e.key());
            entry.put("sha256", e.sha256());
            entry.put("content", text);
            entries.add(entry);
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("formatVersion", bundle.formatVersion());
        result.put("generatedAt", bundle.generatedAt().toString());
        result.put("sourceLabel", bundle.sourceLabel());
        result.put("bundleSha256", bundle.sha256());
        result.put("entries", entries);
        return result;
    }
}
