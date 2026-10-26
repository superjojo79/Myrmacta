package be.myrmacta.infrastructure.mcp.tool;

import be.myrmacta.domain.dossier.model.AccessContext;
import be.myrmacta.domain.precheck.model.PrecheckFinding;
import be.myrmacta.domain.precheck.model.PrecheckRun;
import be.myrmacta.domain.precheck.port.in.ManageDossierPrechecksUseCase;
import be.myrmacta.infrastructure.mcp.McpAccessContextFactory;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import org.springframework.ai.mcp.annotation.McpTool;
import org.springframework.ai.mcp.annotation.McpToolParam;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * TODO §106 (issue #211, lot B, P11, H7, HC11) — pré-vérification IA en MCP, mode <b>opération</b> seulement : jamais
 * exposée à l'assistant de conception (D-ASSIST-TOOLS). Lancer exige EDIT et consomme le même budget que le bouton ; la
 * décision sur un constat reste à l'agent, dans l'interface. Présents seulement si la fonction est activée.
 */
@Component
@ConditionalOnProperty(prefix = "app.precheck", name = "enabled", havingValue = "true")
public class DossierPrecheckTools {

    private final ManageDossierPrechecksUseCase useCase;
    private final McpAccessContextFactory accessContextFactory;

    public DossierPrecheckTools(ManageDossierPrechecksUseCase useCase, McpAccessContextFactory accessContextFactory) {
        this.useCase = Objects.requireNonNull(useCase, "useCase");
        this.accessContextFactory = Objects.requireNonNull(accessContextFactory, "accessContextFactory");
    }

    @McpTool(
            name = "run_dossier_precheck",
            description = "Queue a new AI pre-check of a dossier, with the specification of its last pre-check (the "
                    + "workflow's AI pre-check block runs the first one). Requires EDIT on the dossier type; the result "
                    + "is indicative and the agent decides. Read it later with get_dossier_precheck.")
    public Map<String, Object> runDossierPrecheck(
            @McpToolParam(required = true, description = "Dossier id (UUID)") String dossierId) {
        AccessContext.User actor = accessContextFactory.currentActor();
        PrecheckRun run = useCase.request(McpArgs.requireUuid(dossierId, "dossierId"), actor, PrecheckRun.Trigger.MCP);
        return Map.of("runId", run.id().toString(), "status", run.status().name());
    }

    @McpTool(
            name = "get_dossier_precheck",
            description = "Read the latest AI pre-checks of a dossier (most recent first, at most 3): status, summary "
                    + "and findings (kind MISSING|INCONSISTENT|TO_CHECK|OK, origin DETERMINISTIC or MODEL, source, "
                    + "agent decision). Model findings are indicative, never a decision.")
    public Map<String, Object> getDossierPrecheck(
            @McpToolParam(required = true, description = "Dossier id (UUID)") String dossierId) {
        AccessContext.User actor = accessContextFactory.currentActor();
        UUID id = McpArgs.requireUuid(dossierId, "dossierId");
        List<Map<String, Object>> runs = useCase.list(id, actor).stream().limit(3).map(DossierPrecheckTools::view).toList();
        return Map.of("dossierId", id.toString(), "prechecks", runs);
    }

    private static Map<String, Object> view(PrecheckRun run) {
        Map<String, Object> view = new LinkedHashMap<>();
        view.put("runId", run.id().toString());
        view.put("trigger", run.trigger().name());
        view.put("status", run.status().name());
        view.put("failure", run.failure());
        view.put("summary", run.summary());
        view.put("createdAt", run.createdAt().toString());
        view.put("findings", run.findings().stream().map(DossierPrecheckTools::finding).toList());
        return view;
    }

    private static Map<String, Object> finding(PrecheckFinding f) {
        Map<String, Object> view = new LinkedHashMap<>();
        view.put("kind", f.kind().name());
        view.put("origin", f.origin().name());
        view.put("sourceType", f.sourceType().name());
        view.put("sourceRef", f.sourceRef());
        view.put("message", f.message());
        view.put("confidence", f.confidence());
        view.put("decision", f.decision() == null ? null : f.decision().name());
        return view;
    }
}
