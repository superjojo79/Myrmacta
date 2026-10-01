package com.flowforge.infrastructure.mcp.tool;

import com.flowforge.domain.dossier.model.AccessContext;
import com.flowforge.domain.workflow.model.ConfigPublication;
import com.flowforge.domain.workflow.model.DraftArtifactType;
import com.flowforge.domain.workflow.model.PublicationMode;
import com.flowforge.domain.workflow.port.in.ManageDraftsUseCase;
import com.flowforge.domain.workflow.port.in.ProposeDraftUseCase;
import com.flowforge.domain.workflow.port.out.DmnParserPort;
import com.flowforge.domain.workflow.port.out.PublicationPolicy;
import com.flowforge.infrastructure.mcp.McpAccessContextFactory;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Objects;
import org.springframework.ai.mcp.annotation.McpTool;
import org.springframework.ai.mcp.annotation.McpToolParam;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.unit.DataSize;

/**
 * {@code deploy_decision_table} : validates a DMN 1.3 table (schema, JUEL allowlist) then (TODO §82, D6) saves it as a
 * <b>draft</b> that a human publishes; direct publication only when the instance allows it (never in production).
 */
@Component
public class DeployDecisionTableTool {

    private final DmnParserPort dmnParserPort;
    private final ProposeDraftUseCase proposeDraftUseCase;
    private final ManageDraftsUseCase manageDraftsUseCase;
    private final PublicationPolicy publicationPolicy;
    private final McpAccessContextFactory accessContextFactory;
    private final DataSize maxXmlSize;

    public DeployDecisionTableTool(
            DmnParserPort dmnParserPort,
            ProposeDraftUseCase proposeDraftUseCase,
            ManageDraftsUseCase manageDraftsUseCase,
            PublicationPolicy publicationPolicy,
            McpAccessContextFactory accessContextFactory,
            @Value("${spring.servlet.multipart.max-file-size}") DataSize maxXmlSize) {
        this.dmnParserPort = Objects.requireNonNull(dmnParserPort, "dmnParserPort");
        this.proposeDraftUseCase = Objects.requireNonNull(proposeDraftUseCase, "proposeDraftUseCase");
        this.manageDraftsUseCase = Objects.requireNonNull(manageDraftsUseCase, "manageDraftsUseCase");
        this.publicationPolicy = Objects.requireNonNull(publicationPolicy, "publicationPolicy");
        this.accessContextFactory = Objects.requireNonNull(accessContextFactory, "accessContextFactory");
        this.maxXmlSize = Objects.requireNonNull(maxXmlSize, "maxXmlSize");
    }

    @McpTool(
            name = "deploy_decision_table",
            description = "Propose a DMN 1.3 decision table (UTF-8 XML text, not base64). decisionKey must match the decision "
                    + "id. Input/output cell expressions are validated against a JUEL allowlist (literals and simple "
                    + "comparisons only, never a bare identifier). By default the table is saved as a DRAFT that a human "
                    + "publishes from the 'Brouillons à publier' screen; the instance may allow direct publication (never in "
                    + "production). dryRun=true validates without writing. A draft written by someone else is not "
                    + "overwritten unless replaceExistingDraft=true.")
    public Map<String, Object> deployDecisionTable(
            @McpToolParam(required = true, description = "Decision key — must match the DMN decision id")
                    String decisionKey,
            @McpToolParam(required = true, description = "DMN 1.3 XML document, UTF-8 text (not base64)") String xml,
            @McpToolParam(required = false, description = "Validate without writing anything; default false")
                    Boolean dryRun,
            @McpToolParam(required = false, description = "Overwrite a draft written by another author; default false")
                    Boolean replaceExistingDraft) {
        byte[] xmlBytes = xml.getBytes(StandardCharsets.UTF_8);
        if (xmlBytes.length > maxXmlSize.toBytes()) {
            throw new IllegalArgumentException("xml exceeds max size of " + maxXmlSize);
        }
        String extractedKey = dmnParserPort.extractDecisionKey(xmlBytes);
        if (!extractedKey.equals(decisionKey)) {
            throw new IllegalArgumentException(
                    "decisionKey (" + decisionKey + ") does not match the DMN decision id (" + extractedKey + ")");
        }
        AccessContext.User actor = accessContextFactory.currentActor();
        if (Boolean.TRUE.equals(dryRun)) {
            proposeDraftUseCase.validateDmn(actor, decisionKey, xml);
            return DraftToolResults.of("decisionKey", decisionKey, true, DraftToolResults.VALID, null, null);
        }
        ProposeDraftUseCase.Proposal proposal = proposeDraftUseCase.proposeDmn(
                actor, accessContextFactory.provenance(), decisionKey, xml, null, Boolean.TRUE.equals(replaceExistingDraft));
        if (publicationPolicy.directPublicationAllowed()) {
            ConfigPublication publication = manageDraftsUseCase.publish(
                    actor, DraftArtifactType.DMN, decisionKey, proposal.updatedAt(), PublicationMode.DIRECT);
            return DraftToolResults.of("decisionKey", decisionKey, false, DraftToolResults.PUBLISHED, proposal, publication);
        }
        return DraftToolResults.of("decisionKey", decisionKey, false, DraftToolResults.DRAFT_PENDING, proposal, null);
    }
}
