---
phase: testing
title: Testing Strategy
description: Define testing approach, test cases, and quality assurance
---

# Testing Strategy — `extension-mcp-flux-metier`

> Dérivé des « Success Criteria » du document requirements, mis à jour après Phase 3 (design close
> — D2/D5 tranchés). À affiner en Phase 4 (planning) et Phase 8 (dev-testing). Même style que la
> suite `serveur-mcp` (`McpToolCallIntegrationTest`, `McpAccessContextFactoryTest`,
> `McpEndpointApiTest`).

## Test Coverage Goals

- Unit + IT réel (HTTP M2M à travers `/api/m2m/mcp`, pas de mock du transport) pour chacun des 7
  nouveaux tools — même niveau d'exigence que M7 de `serveur-mcp`
  (`McpToolCallIntegrationTest`, « IT HTTP M2M réelles sur les 9 tools »).
- Test de régression **par use case touché** (pas seulement par tool MCP) prouvant que le
  garde-fou domaine rejette un acteur sans rôle requis — c'est le cœur de la trouvaille
  D-MCP-EXT-RBAC-GUARD, doit être vérifié au niveau `ManageConnectorInstanceUseCaseImpl`,
  `TaskQuestionSchemaUseCaseImpl`/`GetTaskQuestionSchemaUseCaseImpl` et
  `WorkflowTypeConfigUseCaseImpl` directement, pas seulement via le tool — avec le périmètre exact
  par méthode fixé par D5 (`WorkflowTypeConfigUseCaseImpl` : `create`/`update` seulement).
- Régression explicite sur `infrastructure/mcp/tool/DeployWorkflowTool.java` : le passage de
  l'acteur déjà résolu (`accessContextFactory.currentActor()`) à
  `saveWorkflowTypeConfigUseCase.create`/`update` ne doit rien changer au comportement observable
  du tool `deploy_workflow` pour un appelant `API_SERVICE` déjà valide.
- Alignement Jacoco LINE ≥ 85 % / BRANCH ≥ 75 % (seuils actuels), classe par classe (convention du
  dépôt depuis `fiabilisation-tests-plateforme`, TODO §28).

## Unit Tests

### `ManageConnectorInstanceUseCaseImpl` (garde-fou ajouté)

- [ ] `create` rejette un acteur sans `TENANT_ADMIN` ni `API_SERVICE` (`AccessDeniedException`)
- [ ] `create` accepte `TENANT_ADMIN` (régression — comportement REST existant préservé)
- [ ] `create` accepte `API_SERVICE` (nouveau, chemin MCP)
- [ ] `update` — mêmes 3 cas que `create`
- [ ] `update` avec secret vide/masque préserve le secret existant (régression —
  `mergeConfig`/`isBlankOrMask` inchangés)

### `TaskQuestionSchemaUseCaseImpl`/`GetTaskQuestionSchemaUseCaseImpl` (garde-fou ajouté sur TOUTES les opérations, D5)

- [ ] `save` rejette un acteur sans `TENANT_ADMIN`/`WORKFLOW_ADMIN` ni `API_SERVICE`
- [ ] `save` accepte `TENANT_ADMIN` et `WORKFLOW_ADMIN` (régression REST)
- [ ] `save` accepte `API_SERVICE` (nouveau)
- [ ] `delete` — mêmes cas
- [ ] `getByKey` — mêmes cas (contrairement à `WorkflowTypeConfigUseCaseImpl`, la lecture est
      gardée ici aussi, cf. D5/D-MCP-EXT-RBAC-SCOPE)
- [ ] `list` — mêmes cas
- [ ] `TenantAdminGuard.requireTenantOrWorkflowAdminOrApiService` (nouvelle méthode, D2=c) : test
      unitaire dédié sur `TenantAdminGuard` lui-même (accepte `TENANT_ADMIN`/`WORKFLOW_ADMIN`/
      `API_SERVICE`, rejette tout le reste) — avant de vérifier ses appelants

### `WorkflowTypeConfigUseCaseImpl` (correctif pré-existant, PR #82 — périmètre restreint, D-MCP-EXT-RBAC-SCOPE)

- [ ] `create`/`update` rejettent un acteur sans rôle requis (écart pré-existant comblé)
- [ ] `create`/`update` acceptent toujours `TENANT_ADMIN`/`WORKFLOW_ADMIN` (régression REST/admin
      BPMN) et `API_SERVICE` (régression `deploy_workflow`, déjà utilisé en prod)
- [ ] `list`/`getByKey` restent accessibles à un acteur **sans** rôle admin/`API_SERVICE` — preuve
      explicite de non-régression sur `list_workflow_types` (déjà en production, PR #82), pas
      seulement absence de garde par omission

### `DefineDossierTypeTool` (étendu)

- [ ] `documentTemplateKeys` absent/`null` en update préserve la valeur existante (comportement
      actuel, régression)
- [ ] `documentTemplateKeys` fourni en création est persisté
- [ ] `documentTemplateKeys` fourni en update remplace la valeur existante
- [ ] `dryRun=true` avec `documentTemplateKeys` ne persiste rien (régression du contrat `dryRun`
      existant)

### Nouveaux tools — lecture

- [ ] `list_connector_definitions` retourne le catalogue, jamais de secret (catalogue statique,
      pas de config d'instance)
- [ ] `list_connector_instances` masque tout champ secret (`PASSWORD`/`*secret`/`*password`)
- [ ] `get_connector_instance` masque tout champ secret ; 404/erreur explicite si id inconnu ou
      tenant différent
- [ ] `list_task_question_schemas` pagine correctement (si un port `list` existe — sinon
      documenter la source utilisée, voir design D4)
- [ ] `get_task_question_schema` — erreur explicite si `schemaKey` inconnu

### Nouveaux tools — écriture

- [ ] `define_connector_instance` création : secret fourni en clair, persisté chiffré
      (vérifier via le repository, jamais en clair en base)
- [ ] `define_connector_instance` mise à jour : secret vide/masque préserve l'existant (même
      contrat que l'admin REST)
- [ ] `define_connector_instance` `dryRun=true` : aucune persistance, pas de `createdBy`/secret
      chiffré écrit
- [ ] `define_connector_instance` type de connecteur inconnu → erreur explicite
      (`ConnectorDefinition` introuvable dans le catalogue)
- [ ] `define_task_question_schema` création/mise à jour avec des `FieldDefinition` valides
- [ ] `define_task_question_schema` `dryRun=true` : aucune persistance
- [ ] `define_task_question_schema` collision de nom de variable avec les 4 variables plateforme
      réservées (`dossierId`/`dossierWorkflowLinkId`/`tenantId`/`dossierFields`) — réutilise
      `WorkflowVariables.isReservedOrKnownKey` (TODO §55), rejet explicite

## Integration Tests

- [ ] IT HTTP M2M réelle par tool (7 nouveaux + `define_dossier_type` étendu), à travers la vraie
      chaîne Spring Security `/api/m2m/mcp` — pas de mock du filtre JWT (même exigence que M7 de
      `serveur-mcp`, qui avait justement pour but de prouver l'atteignabilité réelle du endpoint,
      contrairement au trou trouvé post-merge sur `connecteur-callback`, TODO §53/§54).
- [ ] Round-trip complet : `define_connector_instance` (create) → `list_connector_instances` (le
      voit, secret masqué) → `get_connector_instance` (le récupère, secret masqué) →
      `define_connector_instance` (update, secret préservé).
- [ ] Round-trip `define_task_question_schema` → `list_task_question_schemas` →
      `get_task_question_schema`.
- [ ] Scénario métier bout en bout (justifie le nom de la feature) : `define_connector_instance` →
      `define_dossier_type` (avec `documentTemplateKeys`) → `define_task_question_schema` →
      `deploy_workflow` référençant les trois — prouve qu'un flux métier complet est atteignable
      sans sortir du canal MCP.
- [ ] Régression : les 9 tools existants + `WorkflowBpmnDeploymentController`,
      `ConnectorAdminController`, `TaskQuestionSchemaController` (REST) continuent de fonctionner
      pour un acteur `TENANT_ADMIN`/`WORKFLOW_ADMIN` humain après l'ajout du paramètre acteur aux
      4 interfaces `port.in` modifiées.

## End-to-End Tests

- Feature backend-only pressentie (comme `serveur-mcp`) — pas de scénario Playwright attendu, à
  reconfirmer en Phase 2 une fois D4 (design) tranché. Si `list_task_question_schemas` révèle un
  écart frontend (peu probable), documenter et ajouter un scénario dédié à ce moment-là plutôt que
  de le deviner ici.

## Test Data

- Réutilisation des fixtures existantes : `ConnectorDefinition` de test déjà présentes pour
  `ConnectorAdminControllerTest`/`ManageConnectorInstanceUseCaseImplTest` ; `TaskQuestionSchema`
  de test déjà présentes pour `TaskQuestionSchemaApiTest`.
- Nouveau JWT M2M de test avec rôle `API_SERVICE` uniquement (sans `TENANT_ADMIN`/`WORKFLOW_ADMIN`)
  pour prouver que le garde-fou accepte bien ce rôle seul — pattern déjà utilisé par
  `McpToolCallIntegrationTest`.

## Test Reporting & Coverage

- `./mvnw verify -Dmaven.compiler.release=21` — Jacoco LINE ≥ 85 % / BRANCH ≥ 75 %, vérifié classe
  par classe sur les fichiers modifiés/ajoutés (convention TODO §28), pas seulement à l'agrégat.
- Si le périmètre reste backend-only : `cd frontend && npm run test` en régression pure (aucun
  fichier `frontend/**` attendu modifié).

## Manual Testing

- Aucune UI nouvelle attendue — pas de checklist manuelle UI. Si D4 (design) révèle un écran
  admin manquant pour `list`, une checklist manuelle sera ajoutée à ce moment.

## Performance Testing

- Non applicable — CRUD ponctuel sur tables déjà indexées, aucun nouveau chemin chaud.

## Bug Tracking

- Tout écart réel trouvé en TDD (Phase 5) ou en revue (Phase 7/8) est documenté dans
  `docs/ai/implementation/2026-09-06-feature-extension-mcp-flux-metier.md`, même convention que
  les cycles précédents de ce dépôt (ex. TODO §55 : 2 gaps Phase 2, 1 écart Phase 7, 4 écarts
  Phase 8).
