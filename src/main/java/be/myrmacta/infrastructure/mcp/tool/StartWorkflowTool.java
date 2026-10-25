package be.myrmacta.infrastructure.mcp.tool;

import be.myrmacta.application.workflow.ManualStartOrchestrator;
import be.myrmacta.domain.dossier.DossierAccessPolicy;
import be.myrmacta.domain.dossier.model.AccessContext;
import be.myrmacta.domain.dossier.model.Dossier;
import be.myrmacta.domain.dossier.model.DossierWorkflowLink;
import be.myrmacta.domain.dossier.port.in.GetDossierUseCase;
import be.myrmacta.domain.workflow.WorkflowVariables;
import be.myrmacta.infrastructure.mcp.McpAccessContextFactory;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import org.springframework.ai.mcp.annotation.McpTool;
import org.springframework.ai.mcp.annotation.McpToolParam;
import org.springframework.stereotype.Component;

@Component
public class StartWorkflowTool {

    private final GetDossierUseCase getDossierUseCase;
    private final DossierAccessPolicy accessPolicy;
    private final ManualStartOrchestrator orchestrator;
    private final McpAccessContextFactory accessContextFactory;

    public StartWorkflowTool(
            GetDossierUseCase getDossierUseCase,
            DossierAccessPolicy accessPolicy,
            ManualStartOrchestrator orchestrator,
            McpAccessContextFactory accessContextFactory) {
        this.getDossierUseCase = Objects.requireNonNull(getDossierUseCase, "getDossierUseCase");
        this.accessPolicy = Objects.requireNonNull(accessPolicy, "accessPolicy");
        this.orchestrator = Objects.requireNonNull(orchestrator, "orchestrator");
        this.accessContextFactory = Objects.requireNonNull(accessContextFactory, "accessContextFactory");
    }

    @McpTool(
            name = "start_workflow",
            description = "Start a workflow on a dossier (same rules as POST /api/dossiers/{id}/workflows): "
                    + "one active workflow, required idempotencyKey. dryRun=true validates without starting.")
    public Object startWorkflow(
            @McpToolParam(required = true, description = "Dossier id (UUID)") String dossierId,
            @McpToolParam(required = true, description = "BPMN process definition key") String processDefinitionKey,
            @McpToolParam(required = true, description = "Idempotency key (same as REST Idempotency-Key header)")
                    String idempotencyKey,
            @McpToolParam(required = false, description = "Process variables; reserved keys (dossierId, tenantId, …) rejected")
                    Map<String, Object> variables,
            @McpToolParam(required = false, description = "Validate without starting; default false") Boolean dryRun) {
        AccessContext.User actor = accessContextFactory.currentActor();
        UUID id = McpArgs.requireUuid(dossierId, "dossierId");
        String processKey = McpArgs.requireNonBlank(processDefinitionKey, "processDefinitionKey");
        String idempotency = McpArgs.requireNonBlank(idempotencyKey, "idempotencyKey");
        Map<String, Object> rawVariables = McpArgs.optionalMap(variables, "variables");
        WorkflowVariables.assertNoReservedKeys(rawVariables);

        Dossier dossier = getDossierUseCase.get(id, actor);
        accessPolicy.assertCanEdit(dossier, actor);
        if (Boolean.TRUE.equals(dryRun)) {
            Map<String, Object> preview = new LinkedHashMap<>();
            preview.put("dryRun", true);
            preview.put("valid", true);
            preview.put("dossierId", id.toString());
            preview.put("processDefinitionKey", processKey);
            return preview;
        }

        ManualStartOrchestrator.StartResult started =
                orchestrator.start(id, processKey, rawVariables, idempotency, actor.userId());
        return Result.from(started.link(), started.replay());
    }

    public record Result(
            UUID linkId,
            UUID dossierId,
            String processInstanceId,
            String processDefinitionKey,
            int sequence,
            String status,
            Instant startedAt,
            String startedBy,
            boolean replay) {

        static Result from(DossierWorkflowLink link, boolean replay) {
            return new Result(
                    link.id(),
                    link.dossierId(),
                    link.processInstanceId(),
                    link.processDefinitionKey(),
                    link.sequence(),
                    link.status(),
                    link.startedAt(),
                    link.startedBy(),
                    replay);
        }
    }
}
