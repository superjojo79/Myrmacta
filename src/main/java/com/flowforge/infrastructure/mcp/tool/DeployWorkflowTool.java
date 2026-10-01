package com.flowforge.infrastructure.mcp.tool;

import com.flowforge.domain.dossier.model.AccessContext;
import com.flowforge.domain.dossier.model.DossierStatus;
import com.flowforge.domain.workflow.model.ConfigPublication;
import com.flowforge.domain.workflow.model.DraftArtifactType;
import com.flowforge.domain.workflow.model.ProposedWorkflowConfig;
import com.flowforge.domain.workflow.model.PublicationMode;
import com.flowforge.domain.workflow.model.WorkflowStatusDefinition;
import com.flowforge.domain.workflow.port.in.ManageDraftsUseCase;
import com.flowforge.domain.workflow.port.in.ProposeDraftUseCase;
import com.flowforge.domain.workflow.port.out.BpmnParserPort;
import com.flowforge.domain.workflow.port.out.PublicationPolicy;
import com.flowforge.infrastructure.config.TenantProperties;
import com.flowforge.infrastructure.mcp.McpAccessContextFactory;
import io.modelcontextprotocol.spec.McpSchema;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.springframework.ai.mcp.annotation.McpTool;
import org.springframework.ai.mcp.annotation.McpToolParam;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.unit.DataSize;
import tools.jackson.databind.ObjectMapper;

/**
 * {@code deploy_workflow} : validates the BPMN XML and the submitted {@code WorkflowTypeConfig}, then (TODO §82, D6) saves them
 * as a <b>draft</b> — nothing is deployed and no configuration is written until a human publishes it (D-MCP-LC-MIGRATION = A).
 * When the instance allows direct publication (never in production) the draft is published at once through the same use case.
 * Takes the raw {@link McpSchema.CallToolRequest} like {@link DefineDossierTypeTool}, for the same reason: full control over
 * parsing nested {@code statusDefinitions}/enum lists without relying on untested nested-type {@code @McpToolParam} binding.
 */
@Component
public class DeployWorkflowTool {

    private final BpmnParserPort bpmnParserPort;
    private final ProposeDraftUseCase proposeDraftUseCase;
    private final ManageDraftsUseCase manageDraftsUseCase;
    private final PublicationPolicy publicationPolicy;
    private final McpAccessContextFactory accessContextFactory;
    private final ObjectMapper objectMapper;
    private final DataSize maxXmlSize;

    public DeployWorkflowTool(
            BpmnParserPort bpmnParserPort,
            ProposeDraftUseCase proposeDraftUseCase,
            ManageDraftsUseCase manageDraftsUseCase,
            PublicationPolicy publicationPolicy,
            McpAccessContextFactory accessContextFactory,
            ObjectMapper objectMapper,
            @Value("${spring.servlet.multipart.max-file-size}") DataSize maxXmlSize) {
        this.bpmnParserPort = Objects.requireNonNull(bpmnParserPort, "bpmnParserPort");
        this.proposeDraftUseCase = Objects.requireNonNull(proposeDraftUseCase, "proposeDraftUseCase");
        this.manageDraftsUseCase = Objects.requireNonNull(manageDraftsUseCase, "manageDraftsUseCase");
        this.publicationPolicy = Objects.requireNonNull(publicationPolicy, "publicationPolicy");
        this.accessContextFactory = Objects.requireNonNull(accessContextFactory, "accessContextFactory");
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper");
        this.maxXmlSize = Objects.requireNonNull(maxXmlSize, "maxXmlSize");
    }

    @McpTool(
            name = "deploy_workflow",
            description = "Propose a BPMN 2.0 process (UTF-8 XML text, not base64) with its WorkflowTypeConfig "
                    + "(statusDefinitions, allowedDossierStatuses, ...) in one call. By default the proposal is saved as a "
                    + "DRAFT that a human publishes from the 'Brouillons à publier' screen: nothing is deployed and no "
                    + "configuration is written until then. The instance may allow direct publication (never in production). "
                    + "processDefinitionKey must match the BPMN process id. dryRun=true validates without writing anything. "
                    + "A draft written by someone else is not overwritten unless replaceExistingDraft=true.")
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
                    Boolean dryRun,
            @McpToolParam(required = false, description = "Overwrite a draft written by another author; default false")
                    Boolean replaceExistingDraft) {
        // Note: the typed parameters above exist only to shape the generated JSON schema (see
        // class Javadoc) — every value actually used is re-read from the raw request map below.
        Map<String, Object> args = request.arguments();
        String rawProcessDefinitionKey = McpArgs.requireString(args, "processDefinitionKey");
        String rawXml = McpArgs.requireString(args, "xml");
        String rawLabel = McpArgs.requireString(args, "label");
        List<WorkflowStatusDefinition> rawStatusDefinitions = parseStatusDefinitions(args.get("statusDefinitions"));
        List<DossierStatus> rawAllowedDossierStatuses = parseDossierStatuses(args.get("allowedDossierStatuses"));
        DossierStatus rawOnStartDossierStatus = parseDossierStatus(args.get("onStartDossierStatus"));
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
        ProposedWorkflowConfig config = new ProposedWorkflowConfig(
                rawLabel, rawStatusDefinitions, rawAllowedDossierStatuses, rawOnStartDossierStatus, rawObserverUserIds);

        if (rawDryRun) {
            // Mêmes contrôles qu'un déploiement, avec la configuration SOUMISE, avant toute écriture.
            proposeDraftUseCase.validateBpmn(actor, rawProcessDefinitionKey, rawXml, config);
            return DraftToolResults.of("processDefinitionKey", rawProcessDefinitionKey, true, DraftToolResults.VALID, null, null);
        }
        ProposeDraftUseCase.Proposal proposal = proposeDraftUseCase.proposeBpmn(
                actor, accessContextFactory.provenance(), rawProcessDefinitionKey, rawXml, config,
                Boolean.TRUE.equals(args.get("replaceExistingDraft")));
        if (publicationPolicy.directPublicationAllowed()) {
            ConfigPublication publication = manageDraftsUseCase.publish(
                    actor, DraftArtifactType.BPMN, rawProcessDefinitionKey, proposal.updatedAt(), PublicationMode.DIRECT);
            return DraftToolResults.of("processDefinitionKey", rawProcessDefinitionKey, false, DraftToolResults.PUBLISHED, proposal, publication);
        }
        return DraftToolResults.of("processDefinitionKey", rawProcessDefinitionKey, false, DraftToolResults.DRAFT_PENDING, proposal, null);
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
        return list.stream().map(item -> parseDossierStatus(item, "allowedDossierStatuses")).toList();
    }

    private static DossierStatus parseDossierStatus(Object raw) {
        return raw == null ? null : parseDossierStatus(raw, "onStartDossierStatus");
    }

    private static DossierStatus parseDossierStatus(Object raw, String argumentName) {
        if (!(raw instanceof String status)) {
            throw new IllegalArgumentException(argumentName + " items must be strings");
        }
        return DossierStatus.valueOf(status);
    }

    private static List<String> parseObserverUserIds(Object raw) {
        if (raw == null) {
            return List.of();
        }
        if (!(raw instanceof List<?> list)) {
            throw new IllegalArgumentException("observerUserIds must be an array");
        }
        return list.stream().map(item -> {
            if (!(item instanceof String userId)) {
                throw new IllegalArgumentException("observerUserIds items must be strings");
            }
            return userId;
        }).toList();
    }
}
