---
phase: testing
title: Tests — D6 (socle MCP low-code)
---

- Domaine : `DraftUseCasesTest` (proposition, conflit, publication humaine/directe, ordre config → déploiement, garde d'horodatage, droits), `LineDiffTest`.
- Démarrage : `McpGovernanceStartupGuardTest` (environnement inconnu, direct en production, allowlist M2M).
- Acteur et modes : `McpActorScopeTest` (portée, restauration, imbrication, fil), `McpToolModesTest` (tout outil classé ; conception sans donnée de dossier ; opération sans écriture de configuration).
- Outils : `DeployWorkflowToolTest`, `DeployDecisionTableToolTest` (brouillon par défaut, direct si autorisé, dryRun, remise en ligne).
- Persistance : `DraftPersistenceTest` (aller-retour provenance/config, purge ciblée, journal), `BpmnDraftJpaEntityTest`.
- API : `DraftsApiTest` (liste, publication humaine, 409, droits, écart, reprise humaine) ; intégration MCP : `McpToolCallIntegrationTest` (brouillon, `publish_draft` refusé), `McpDirectPublicationIntegrationTest` (mode direct hors production), `McpEndpointApiTest`.
- Frontend : `drafts.spec.ts`, `DraftsAdminView.spec.ts` ; e2e `E2E-DRAFTS-1`.
