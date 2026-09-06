package com.flowforge.infrastructure.mcp.tool;

import com.flowforge.domain.dossier.model.AccessContext;
import com.flowforge.domain.dossier.model.DossierStatus;
import com.flowforge.domain.workflow.model.BpmnDeploymentResult;
import com.flowforge.domain.workflow.model.WorkflowStatusDefinition;
import com.flowforge.domain.workflow.model.WorkflowTypeConfig;
import com.flowforge.domain.workflow.port.in.DeployBpmnProcessUseCase;
import com.flowforge.domain.workflow.port.in.GetWorkflowTypeConfigUseCase;
import com.flowforge.domain.workflow.port.in.SaveWorkflowTypeConfigUseCase;
import com.flowforge.domain.workflow.port.out.BpmnParserPort;
import com.flowforge.infrastructure.config.TenantProperties;
import com.flowforge.infrastructure.mcp.McpAccessContextFactory;
import io.modelcontextprotocol.spec.McpSchema;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.springframework.ai.mcp.annotation.McpTool;
import org.springframework.ai.mcp.annotation.McpToolParam;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.unit.DataSize;
import tools.jackson.databind.ObjectMapper;

/**
 * {@code deploy_workflow} : parses + validates the BPMN XML, upserts the {@link WorkflowTypeConfig}
 * for its process id, then deploys (DD-DEPLOY-ORDER). Unlike the admin REST BPMN-deployment
 * endpoint (which requires the config to already exist), this MCP tool covers both steps in one
 * call — that is the point of exposing it as a single tool (see analysis doc 15-serveur-mcp.md).
 * Takes the raw {@link McpSchema.CallToolRequest} like {@link DefineDossierTypeTool}, for the same
 * reason: full control over parsing nested {@code statusDefinitions}/enum lists without relying on
 * untested nested-type {@code @McpToolParam} binding.
 */
@Component
public class DeployWorkflowTool {

    private final BpmnParserPort bpmnParserPort;
    private final GetWorkflowTypeConfigUseCase getWorkflowTypeConfigUseCase;
    private final SaveWorkflowTypeConfigUseCase saveWorkflowTypeConfigUseCase;
    private final DeployBpmnProcessUseCase deployBpmnProcessUseCase;
    private final TenantProperties tenantProperties;
    private final McpAccessContextFactory accessContextFactory;
    private final ObjectMapper objectMapper;
    private final DataSize maxXmlSize;

    public DeployWorkflowTool(
            BpmnParserPort bpmnParserPort,
            GetWorkflowTypeConfigUseCase getWorkflowTypeConfigUseCase,
            SaveWorkflowTypeConfigUseCase saveWorkflowTypeConfigUseCase,
            DeployBpmnProcessUseCase deployBpmnProcessUseCase,
            TenantProperties tenantProperties,
            McpAccessContextFactory accessContextFactory,
            ObjectMapper objectMapper,
            @Value("${spring.servlet.multipart.max-file-size}") DataSize maxXmlSize) {
        this.bpmnParserPort = Objects.requireNonNull(bpmnParserPort, "bpmnParserPort");
        this.getWorkflowTypeConfigUseCase =
                Objects.requireNonNull(getWorkflowTypeConfigUseCase, "getWorkflowTypeConfigUseCase");
        this.saveWorkflowTypeConfigUseCase =
                Objects.requireNonNull(saveWorkflowTypeConfigUseCase, "saveWorkflowTypeConfigUseCase");
        this.deployBpmnProcessUseCase =
                Objects.requireNonNull(deployBpmnProcessUseCase, "deployBpmnProcessUseCase");
        this.tenantProperties = Objects.requireNonNull(tenantProperties, "tenantProperties");
        this.accessContextFactory = Objects.requireNonNull(accessContextFactory, "accessContextFactory");
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper");
        this.maxXmlSize = Objects.requireNonNull(maxXmlSize, "maxXmlSize");
    }

    @McpTool(
            name = "deploy_workflow",
            description = "Deploy a BPMN 2.0 process (UTF-8 XML text, not base64) and upsert its "
                    + "WorkflowTypeConfig (statusDefinitions, allowedDossierStatuses, ...) in one call. "
                    + "processDefinitionKey must match the BPMN process id. dryRun=true validates without "
                    + "persisting or deploying.")
    public Map<String, Object> deployWorkflow(
            McpSchema.CallToolRequest request,
            @McpToolParam(required = true, description = "Process definition key — must match the BPMN process id")
                    String processDefinitionKey,
            @McpToolParam(required = true, description = "BPMN 2.0 XML document, UTF-8 text (not base64)")
                    String xml,
            @McpToolParam(required = true, description = "Display label") String label,
            @McpToolParam(
                            required = true,
                            description = "Workflow status definitions, exactly one with initial=true. Each item: "
                                    + "{code, label, initial, outcome (NONE|COMPLETED|CANCELLED|FAILED)}")
                    List<Map<String, Object>> statusDefinitions,
            @McpToolParam(
                            required = true,
                            description = "Dossier statuses allowed while this workflow runs, non-empty "
                                    + "(DRAFT|OUVERT|EN_COURS|SUSPENDU|CLOTURE|ARCHIVE)")
                    List<String> allowedDossierStatuses,
            @McpToolParam(required = false, description = "Dossier status to set when the workflow starts")
                    String onStartDossierStatus,
            @McpToolParam(required = false, description = "User ids to notify as observers") List<String> observerUserIds,
            @McpToolParam(required = false, description = "Validate without persisting or deploying; default false")
                    Boolean dryRun) {
        // Note: the typed parameters above exist only to shape the generated JSON schema (see
        // class Javadoc) — every value actually used is re-read from the raw request map below.
        Map<String, Object> args = request.arguments();
        String rawProcessDefinitionKey = McpArgs.requireString(args, "processDefinitionKey");
        String rawXml = McpArgs.requireString(args, "xml");
        String rawLabel = McpArgs.requireString(args, "label");
        List<WorkflowStatusDefinition> rawStatusDefinitions = parseStatusDefinitions(args.get("statusDefinitions"));
        List<DossierStatus> rawAllowedDossierStatuses = parseDossierStatuses(args.get("allowedDossierStatuses"));
        DossierStatus rawOnStartDossierStatus = args.get("onStartDossierStatus") == null
                ? null
                : DossierStatus.valueOf((String) args.get("onStartDossierStatus"));
        List<String> rawObserverUserIds = parseObserverUserIds(args.get("observerUserIds"));
        boolean rawDryRun = Boolean.TRUE.equals(args.get("dryRun"));

        byte[] xmlBytes = rawXml.getBytes(StandardCharsets.UTF_8);
        if (xmlBytes.length > maxXmlSize.toBytes()) {
            throw new IllegalArgumentException("xml exceeds max size of " + maxXmlSize);
        }
        String extractedKey = bpmnParserPort.extractProcessDefinitionKey(xmlBytes);
        if (!extractedKey.equals(rawProcessDefinitionKey)) {
            throw new IllegalArgumentException(
                    "processDefinitionKey (" + rawProcessDefinitionKey + ") does not match the BPMN process id ("
                            + extractedKey + ")");
        }

        AccessContext.User actor = accessContextFactory.currentActor();
        Optional<WorkflowTypeConfig> existing = getWorkflowTypeConfigUseCase.getByKey(rawProcessDefinitionKey);
        WorkflowTypeConfig config = buildConfig(
                existing, rawProcessDefinitionKey, rawLabel, rawStatusDefinitions, rawAllowedDossierStatuses,
                rawOnStartDossierStatus, rawObserverUserIds, actor);

        if (rawDryRun) {
            return result(rawProcessDefinitionKey, true, null, null);
        }

        WorkflowTypeConfig saved = existing.isPresent()
                ? saveWorkflowTypeConfigUseCase.update(config, actor)
                : saveWorkflowTypeConfigUseCase.create(config, actor);

        String filename = rawProcessDefinitionKey + ".bpmn20.xml";
        BpmnDeploymentResult deployment = deployBpmnProcessUseCase.deploy(filename, xmlBytes);

        return result(rawProcessDefinitionKey, false, deployment, saved);
    }

    private WorkflowTypeConfig buildConfig(
            Optional<WorkflowTypeConfig> existing,
            String processDefinitionKey,
            String label,
            List<WorkflowStatusDefinition> statusDefinitions,
            List<DossierStatus> allowedDossierStatuses,
            DossierStatus onStartDossierStatus,
            List<String> observerUserIds,
            AccessContext.User actor) {
        return existing
                .map(current -> new WorkflowTypeConfig(
                        current.id(), current.tenantId(), processDefinitionKey, label, statusDefinitions,
                        allowedDossierStatuses, onStartDossierStatus, observerUserIds, current.createdBy(),
                        actor.userId()))
                .orElseGet(() -> new WorkflowTypeConfig(
                        UUID.randomUUID(), tenantProperties.id(), processDefinitionKey, label, statusDefinitions,
                        allowedDossierStatuses, onStartDossierStatus, observerUserIds, actor.userId(),
                        actor.userId()));
    }

    private static Map<String, Object> result(
            String processDefinitionKey, boolean dryRun, BpmnDeploymentResult deployment, WorkflowTypeConfig config) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("processDefinitionKey", processDefinitionKey);
        map.put("dryRun", dryRun);
        map.put("deployment", deployment);
        map.put("workflowTypeConfig", config);
        return map;
    }

    private List<WorkflowStatusDefinition> parseStatusDefinitions(Object raw) {
        if (!(raw instanceof List<?> list) || list.isEmpty()) {
            throw new IllegalArgumentException("statusDefinitions is required");
        }
        return list.stream().map(item -> objectMapper.convertValue(item, WorkflowStatusDefinition.class)).toList();
    }

    private static List<DossierStatus> parseDossierStatuses(Object raw) {
        if (!(raw instanceof List<?> list)) {
            throw new IllegalArgumentException("allowedDossierStatuses is required");
        }
        return list.stream().map(item -> DossierStatus.valueOf((String) item)).toList();
    }

    private static List<String> parseObserverUserIds(Object raw) {
        if (raw == null) {
            return List.of();
        }
        if (!(raw instanceof List<?> list)) {
            throw new IllegalArgumentException("observerUserIds must be an array");
        }
        return list.stream().map(String.class::cast).toList();
    }
}
