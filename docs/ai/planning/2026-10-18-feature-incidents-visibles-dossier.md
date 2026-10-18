---
phase: planning
title: Planning — incidents-visibles-dossier
status: draft
created: 2026-10-18
feature: incidents-visibles-dossier
todo_ref: TODO.md §94
---

# Planning — incidents-visibles-dossier

TDD, un commit par volet.

## Volet A — domaine et adaptateur

- [x] **A1.** Modèle (`WorkflowIncident`, `IncidentState`, `TenantIncident`, exceptions), port `WorkflowIncidentPort`.
- [x] **A2.** `FlowableWorkflowIncidentAdapter` et son IT sur moteur réel (états, message tronqué, rejeu, tenant).
- [x] **A3.** `WorkflowIncidentsUseCaseImpl` (liste du lien, rejeu, liste du tenant, liste système pour MCP) et câblage
  Spring.
- [x] **A4.** Diagramme : `failedIds` enrichi (`WorkflowLinkVisualizationUseCaseImpl`, `WorkflowDiagramView.withFailed`).

## Volet B — API et MCP

- [x] **B1.** Endpoints lien (liste, rejeu), audit `incident_retried`, gestion des exceptions (404, 409).
- [x] **B2.** `AdminIncidentController` (liste paginée du tenant).
- [x] **B3.** MCP `get_dossier` : `incidents` sans message.
- [x] **B4.** Golden OpenAPI régénéré, `OpenApiContractIT` vert.

## Volet C — interface

- [x] **C1.** `api/incidents.ts` et types.
- [x] **C2.** `IncidentBanner.vue` dans la fiche dossier (rejeu sans `confirm`, rechargement du diagramme).
- [x] **C3.** `IncidentsAdminView.vue`, route `/admin/incidents`, entrée de menu, i18n fr/en.
- [x] **C4.** Playwright (fiche et écran de supervision).

## Documentation et clôture

- [x] **D1.** `GUIDE-UTILISATEUR.md`, `GUIDE-CONCEPTION-BPMN.md` §6c, `analysis doc/15-serveur-mcp.md`, `TODO.md` §94,
  `DEFERRED-FEATURES.md` §7.
- [ ] **D2.** Verify complet, `test:all`, recette R6 sur l'instance locale, `CHANGELOG.md` à l'ouverture de la PR.
- [ ] **D3.** Revue adversariale (sous-agent) et correctifs.

## Risques

- **Simuler un échec de job dans un IT sans attendre les délais Flowable.** On réduit le nombre de tentatives ou on
  exécute les jobs à la main (`managementService.executeJob`).
- **`truncated` et pagination en mémoire.** Comportement documenté (D-INCIDENT-SCAN), testé sur la borne.
- **Instance locale.** Le backend de recette tourne depuis l'autre worktree. La recette de fin de lot se fera en le
  relançant depuis ce worktree.
