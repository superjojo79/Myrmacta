package com.flowforge.infrastructure.mcp.tool;

import com.flowforge.domain.workflow.model.PendingDraft;
import com.flowforge.domain.workflow.port.in.ManageDraftsUseCase;
import com.flowforge.infrastructure.mcp.McpAccessContextFactory;
import java.util.List;
import java.util.Objects;
import org.springframework.ai.mcp.annotation.McpTool;
import org.springframework.stereotype.Component;

/** TODO §82 (D6) — brouillons de configuration en attente de publication (BPMN, DMN), avec provenance et écart indicatif. */
@Component
public class ListPendingDraftsTool {

    private final ManageDraftsUseCase manageDraftsUseCase;
    private final McpAccessContextFactory accessContextFactory;

    public ListPendingDraftsTool(ManageDraftsUseCase manageDraftsUseCase, McpAccessContextFactory accessContextFactory) {
        this.manageDraftsUseCase = Objects.requireNonNull(manageDraftsUseCase, "manageDraftsUseCase");
        this.accessContextFactory = Objects.requireNonNull(accessContextFactory, "accessContextFactory");
    }

    @McpTool(
            name = "list_pending_drafts",
            description = "List the BPMN and DMN drafts awaiting publication: type, key, provenance (UI, MCP, ASSISTANT), "
                    + "author, last change, published version and an indicative line diff against it. Most recent first.")
    public List<PendingDraft> listPendingDrafts() {
        return manageDraftsUseCase.listPending(accessContextFactory.currentActor());
    }
}
