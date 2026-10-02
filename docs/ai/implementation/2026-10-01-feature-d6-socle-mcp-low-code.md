---
phase: implementation
title: Implémentation — D6 (socle MCP low-code)
---

# Implémentation (TODO §82)

- Domaine : `domain/workflow/{model,draft,usecase,port}` — `ProposeDraftUseCaseImpl`, `ManageDraftsUseCaseImpl`, `ProposedConfigs`, `LineDiff`.
- Persistance : `BpmnDraftJpaEntity`/`DmnDraftJpaEntity` (provenance, config JSON), `ConfigPublicationJpaEntity`, requêtes `deleteAutomatedOlderThan`.
- Configuration : `McpGovernanceConfig`, `McpPublicationProperties`, `DraftRetentionProperties`, `McpGovernanceStartupGuard`.
- Web : `DraftsController`, `WorkflowExceptionHandler` (409/404/403).
- MCP : `DeployWorkflowTool`, `DeployDecisionTableTool`, `RedeployDecisionTableTool`, `ListPendingDraftsTool`, `PublishDraftTool`, `McpActorScope`, `McpToolModes`.
- SPA : `api/drafts.ts`, `views/admin/DraftsAdminView.vue`, route `/admin/drafts`, lien « Brouillons ».
- Piège rencontré : le rechargement de la liste après une publication refusée effaçait le message d'erreur (corrigé, test).
