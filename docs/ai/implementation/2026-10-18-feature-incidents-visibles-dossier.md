---
phase: implementation
title: Implementation — incidents-visibles-dossier
status: complete
created: 2026-10-18
feature: incidents-visibles-dossier
todo_ref: TODO.md §94
---

# Implementation — incidents-visibles-dossier

## Backend

| Fichier | Rôle |
|---|---|
| `domain/workflow/model/{IncidentState, WorkflowIncident, TenantIncident, WorkflowIncidentNotFoundException, IncidentNotRetryableException}` | Modèle ; `WorkflowIncident` tronque le message à 300 caractères, `withoutMessage()` |
| `domain/workflow/port/out/WorkflowIncidentPort`, `port/in/WorkflowIncidentsUseCase` | Ports |
| `domain/workflow/usecase/WorkflowIncidentsUseCaseImpl` | Garde (participants, éditeurs, TA/WA), message réservé aux admins, rejeu, liste du tenant bornée, vue système MCP |
| `infrastructure/flowable/FlowableWorkflowIncidentAdapter` | `ManagementService` : jobs et minuteurs avec exception (RETRYING), dead letters (INCIDENT), filtre `jobTenantId`, `moveDeadLetterJobToExecutableJob` |
| `infrastructure/flowable/FlowableWorkflowVisualizationAdapter` | Étapes des jobs en échec ajoutées à `failedIds` et `activeIds` |
| `infrastructure/web/workflow/WorkflowIncidentController` | `GET/POST /api/workflow-links/{id}/incidents[/{jobId}/retry]`, `GET /api/admin/incidents` |
| `infrastructure/web/workflow/WorkflowExceptionHandler` | 404 (`WorkflowIncidentNotFoundException`), 409 (`IncidentNotRetryableException`) |
| `infrastructure/security/SecurityAuditLog.incidentRetried` | Audit du rejeu, sans message |
| `infrastructure/mcp/tool/GetDossierTool` | `incidents` sans message |
| `infrastructure/config/WorkflowConfig` | Bean du use case |
| `openapi/openapi.yaml` | Golden régénéré |

## Frontend

`api/incidents.ts`, types ; `components/workflow/IncidentBanner.vue` ; `stores/dossier.ts` (`incidents`,
`retryIncident`, chargement dans `loadVisualization`, échec silencieux) ; `views/pilotage/DossierDetailView.vue` ;
`views/admin/IncidentsAdminView.vue`, route `/admin/incidents`, entrée de menu ; i18n fr/en.

## Écarts au design

- **Diagramme enrichi dans l'adaptateur de visualisation** (et non dans `WorkflowLinkVisualizationUseCaseImpl`), qui reçoit
  `WorkflowIncidentPort` : évite d'élargir un constructeur de use case déjà à 11 paramètres. L'étape en échec est aussi
  ajoutée aux `activeIds` (elle est toujours en attente).
- **Contrôleur dédié** `WorkflowIncidentController` dans `web.workflow` (même gestionnaire d'exceptions) plutôt que
  d'étendre `WorkflowLinkVisualizationController`.

## Revue adversariale (Phase 9)

Sous-agent en lecture seule (javap des jars Flowable 8 pour les comportements du moteur). Aucun High. Correctifs :
M1, M2, L1, L2, L4, L5, L6 et les points Info — détail dans `TODO.md` §94. L3 acté (risque accepté et documenté).
Recette locale (cas R6 de l'exemple riverain) : bandeau, diagramme marqué, correction de l'adresse, rejeu → e-mail reçu
dans Mailpit ; elle a révélé l'état « nouvelle tentative prévue » resté affiché après un rejeu, corrigé par des
relectures programmées.

## Notes

- L'IT moteur dure ~40 s : l'exécuteur asynchrone acquiert jobs et minuteurs par intervalles (~10 s). Attente bornée à
  120 s.
- Suivi après merge de la PR #175 : statut `DEFERRED-FEATURES.md` §7 et `DIFFICULTES.md` §11 de l'exemple.
