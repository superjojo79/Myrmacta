---
phase: design
title: Design — D6 (socle MCP low-code)
status: reviewed
created: 2026-10-01
feature: d6-socle-mcp-low-code
lot: D6
---

# Design — D6

## Flux
```
agent MCP ─ deploy_workflow / deploy_decision_table / redeploy_decision_table
   └─► ProposeDraftUseCase  (validations de déploiement, conflit de brouillon) ─► bpmn_draft / dmn_draft (provenance MCP|ASSISTANT)
humain ─ écran « Brouillons à publier » / agent en mode direct ─ publish_draft
   └─► ManageDraftsUseCase.publish  (garde d'horodatage → valider config → écrire config → déployer → journaliser → supprimer le brouillon)
```

## Modèle
- `Provenance {UI, MCP, ASSISTANT}` ; `BpmnDraft(+provenance, proposedConfig, pilotUserId)`, `DmnDraft(+provenance, note, pilotUserId)` (constructeurs historiques = UI).
- `ProposedWorkflowConfig` : la configuration de workflow proposée avec un brouillon BPMN, appliquée **à la publication seulement**.
- `ConfigPublication` (journal) : type, clé, version, provenance et auteur du brouillon, publicateur, date, mode `MANUAL|DIRECT`.
- `PendingDraft` : vue de liste, avec écart indicatif (`LineDiff`, différence de multi-ensembles de lignes).

## Cas d'usage (domaine)
- `ProposeDraftUseCase` : écrit un brouillon ; refuse l'écrasement d'un brouillon d'un autre auteur/provenance sans `replaceExistingDraft` (409) ; valide avec la configuration **soumise**.
- `ManageDraftsUseCase` : `listPending`, `publish`, `discard`, `recentPublications`. Règles : un `API_SERVICE` seul ne publie que si la politique autorise la publication directe ; mode `MANUAL` réservé aux humains et exige l'horodatage relu ; `DRAFT_CHANGED` (409) sinon.
- `PublicationPolicy` (port) ← `app.mcp.publication.mode`.

## Infrastructure
- Migration 031 : colonnes `provenance` (défaut `UI`), `proposed_config` (JSON texte), `note`, `pilot_user_id`, table `config_publication`.
- `McpGovernanceStartupGuard` : `app.environment` ∈ local|staging|production (défaut production) ; `direct` refusé en production ; `trusted-client-ids` obligatoire hors local si M2M actif.
- `DraftRetentionJob` : `DELETE` conditionnel des brouillons non-UI, idempotent (sûr multi-pods, D5).
- REST `/api/admin/drafts` (TENANT_ADMIN, WORKFLOW_ADMIN) ; SPA `DraftsAdminView`.
- MCP : `McpActorScope` (acteur in-process, provenance ASSISTANT), `McpToolModes` (registre + test), outils `list_pending_drafts`, `publish_draft`.

## Alternatives écartées
Brouillons pour tous les artefacts (D-SOCLE-DRAFT-SCOPE) ; `publish_*` par artefact ; rôles dérivés des scopes JWT (A) ; annotation de mode sur chaque outil (registre central + test plus sûr).
