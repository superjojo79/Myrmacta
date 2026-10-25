package be.myrmacta.domain.workflow.port.in;

import be.myrmacta.domain.dossier.model.AccessContext;
import be.myrmacta.domain.workflow.model.WorkflowTypeConfig;

/**
 * {@code actor} required on {@code create}/{@code update} only — mirrors
 * {@code WorkflowTypeConfigController}, whose {@code @PreAuthorize} gates only its write routes;
 * {@link GetWorkflowTypeConfigUseCase} (reads) stays open (extension-mcp-flux-metier, D5), no
 * regression on the already-shipped {@code list_workflow_types} MCP tool.
 */
public interface SaveWorkflowTypeConfigUseCase {

    WorkflowTypeConfig create(WorkflowTypeConfig config, AccessContext.User actor);

    WorkflowTypeConfig update(WorkflowTypeConfig config, AccessContext.User actor);
}
