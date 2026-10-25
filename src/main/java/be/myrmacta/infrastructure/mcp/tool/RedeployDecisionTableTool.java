package be.myrmacta.infrastructure.mcp.tool;

import be.myrmacta.domain.dossier.model.AccessContext;
import be.myrmacta.domain.workflow.model.ConfigPublication;
import be.myrmacta.domain.workflow.model.DraftArtifactType;
import be.myrmacta.domain.workflow.model.PublicationMode;
import be.myrmacta.domain.workflow.port.in.ManageDraftsUseCase;
import be.myrmacta.domain.workflow.port.in.ProposeDraftUseCase;
import be.myrmacta.domain.workflow.port.out.PublicationPolicy;
import be.myrmacta.infrastructure.mcp.McpAccessContextFactory;
import java.util.Map;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.mcp.annotation.McpTool;
import org.springframework.ai.mcp.annotation.McpToolParam;
import org.springframework.stereotype.Component;

/**
 * {@code redeploy_decision_table} : loads an earlier published version of a DMN table into a <b>draft</b> (TODO §82, D6,
 * D-MCP-LC-MIGRATION = A) that a human publishes as the new current version; direct publication only when the instance allows.
 */
@Component
public class RedeployDecisionTableTool {

    private static final Logger log = LoggerFactory.getLogger(RedeployDecisionTableTool.class);

    private final ProposeDraftUseCase proposeDraftUseCase;
    private final ManageDraftsUseCase manageDraftsUseCase;
    private final PublicationPolicy publicationPolicy;
    private final McpAccessContextFactory accessContextFactory;

    public RedeployDecisionTableTool(
            ProposeDraftUseCase proposeDraftUseCase,
            ManageDraftsUseCase manageDraftsUseCase,
            PublicationPolicy publicationPolicy,
            McpAccessContextFactory accessContextFactory) {
        this.proposeDraftUseCase = Objects.requireNonNull(proposeDraftUseCase, "proposeDraftUseCase");
        this.manageDraftsUseCase = Objects.requireNonNull(manageDraftsUseCase, "manageDraftsUseCase");
        this.publicationPolicy = Objects.requireNonNull(publicationPolicy, "publicationPolicy");
        this.accessContextFactory = Objects.requireNonNull(accessContextFactory, "accessContextFactory");
    }

    @McpTool(
            name = "redeploy_decision_table",
            description = "Propose redeploying an earlier version of a DMN decision table as the new current version: the "
                    + "version is loaded into a DRAFT that a human publishes (the instance may allow direct publication, never "
                    + "in production). Does not delete history. Refuses when that version is already current.")
    public Map<String, Object> redeployDecisionTable(
            @McpToolParam(required = true, description = "Decision key") String decisionKey,
            @McpToolParam(required = true, description = "Version number to redeploy") Integer version,
            @McpToolParam(required = false, description = "Overwrite a draft written by another author; default false")
                    Boolean replaceExistingDraft) {
        if (version == null) {
            throw new IllegalArgumentException("version is required");
        }
        log.info("dmn.redeploy decisionKey={} version={}", decisionKey, version);
        AccessContext.User actor = accessContextFactory.currentActor();
        ProposeDraftUseCase.Proposal proposal = proposeDraftUseCase.proposeDmnFromVersion(
                actor, accessContextFactory.provenance(), decisionKey, version, Boolean.TRUE.equals(replaceExistingDraft));
        if (publicationPolicy.directPublicationAllowed()) {
            ConfigPublication publication = manageDraftsUseCase.publish(
                    actor, DraftArtifactType.DMN, decisionKey, proposal.updatedAt(), PublicationMode.DIRECT);
            return DraftToolResults.of("decisionKey", decisionKey, false, DraftToolResults.PUBLISHED, proposal, publication);
        }
        return DraftToolResults.of("decisionKey", decisionKey, false, DraftToolResults.DRAFT_PENDING, proposal, null);
    }
}
