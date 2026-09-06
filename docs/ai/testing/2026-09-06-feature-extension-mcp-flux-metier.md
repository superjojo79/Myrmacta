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

> Statut Phase 8 : toutes les cases ci-dessous vérifiées contre les fichiers de test réels (pas
> supposées cochées). 3 gaps réels trouvés et comblés en Phase 8 (voir « Bug Tracking ») :
> `list_connector_definitions`/`list_connector_instances`/`list_task_question_schemas` ne
> testaient que `page`/`size` absents (jamais fournis explicitement), contrairement au pattern déjà
> établi par `ListWorkflowTypesToolTest` (4 combinaisons) ; `DefineConnectorInstanceTool` ne
> testait pas les branches `isBlank()` (vs `null`), `config` `null` sur create, `enabled` explicite,
> ni `restore=true`.

### `ManageConnectorInstanceUseCaseImpl` (garde-fou ajouté)

- [x] `create` rejette un acteur sans `TENANT_ADMIN` ni `API_SERVICE` (`AccessDeniedException`) —
      `create_withoutRequiredRole_isDenied`
- [x] `create` accepte `TENANT_ADMIN` (régression — comportement REST existant préservé) — test
      pré-existant conservé
- [x] `create` accepte `API_SERVICE` (nouveau, chemin MCP) — `create_apiService_isAllowed`
- [x] `update`/`get`/`list`/`softDelete`/`restore` — même refus sans rôle requis
      (`update_withoutRequiredRole_isDenied`, `get_withoutRequiredRole_isDenied`,
      `list_withoutRequiredRole_isDenied`, `softDelete_withoutRequiredRole_isDenied`,
      `restore_withoutRequiredRole_isDenied`)
- [x] `update` avec secret vide/masque préserve le secret existant (régression —
      `mergeConfig`/`isBlankOrMask` inchangés) — `update_preservesExistingPasswordWhenMaskedValueIsSubmitted`
      (pré-existant, non affecté par l'ajout de l'acteur)

### `TaskQuestionSchemaUseCaseImpl`/`GetTaskQuestionSchemaUseCaseImpl` (garde-fou ajouté sur TOUTES les opérations, D5)

- [x] `save` rejette un acteur sans `TENANT_ADMIN`/`WORKFLOW_ADMIN` ni `API_SERVICE` —
      `save_withoutRequiredRole_isDenied`
- [x] `save` accepte `TENANT_ADMIN` et `WORKFLOW_ADMIN` (régression REST) —
      `save_tenantAdmin_isAllowed`, `save_workflowAdmin_isAllowed`
- [x] `save` accepte `API_SERVICE` (nouveau) — `save_apiService_isAllowed`
- [x] `delete` — mêmes cas — `delete_withoutRequiredRole_isDenied`, `delete_apiService_isAllowed`
- [x] `getByKey` — mêmes cas (contrairement à `WorkflowTypeConfigUseCaseImpl`, la lecture est
      gardée ici aussi, cf. D5/D-MCP-EXT-RBAC-SCOPE) — `getByKey_withoutRequiredRole_isDenied`,
      `getByKey_apiService_isAllowed`
- [x] `list` — mêmes cas — `list_withoutRequiredRole_isDenied`, `list_apiService_isAllowed`
- [x] `TenantAdminGuard.requireTenantOrWorkflowAdminOrApiService` (nouvelle méthode, D2=c) : test
      unitaire dédié sur `TenantAdminGuard` lui-même (accepte `TENANT_ADMIN`/`WORKFLOW_ADMIN`/
      `API_SERVICE`, rejette tout le reste) — 5 tests dans `TenantAdminGuardTest`

### `WorkflowTypeConfigUseCaseImpl` (correctif pré-existant, PR #82 — périmètre restreint, D-MCP-EXT-RBAC-SCOPE)

- [x] `create`/`update` rejettent un acteur sans rôle requis (écart pré-existant comblé) —
      `create_withoutRequiredRole_isDenied`, `update_withoutRequiredRole_isDenied`
- [x] `create`/`update` acceptent toujours `TENANT_ADMIN`/`WORKFLOW_ADMIN` (régression REST/admin
      BPMN) et `API_SERVICE` (régression `deploy_workflow`, déjà utilisé en prod) —
      `create_tenantAdmin_isAllowed`, `create_workflowAdmin_isAllowed`, `create_apiService_isAllowed`
- [x] `list`/`getByKey` restent accessibles à un acteur **sans** rôle admin/`API_SERVICE` — preuve
      explicite de non-régression sur `list_workflow_types` (déjà en production, PR #82), pas
      seulement absence de garde par omission — `getByKey_withoutAnyRole_isStillAllowed_noRegression`,
      `list_withoutAnyRole_isStillAllowed_noRegression`

### `DefineDossierTypeTool` (étendu)

- [x] `documentTemplateKeys` absent/`null` en update préserve la valeur existante (comportement
      actuel, régression) — `documentTemplateKeysOmitted_onUpdate_preservesExisting`
- [x] `documentTemplateKeys` fourni en création est persisté —
      `documentTemplateKeysProvided_onCreate_arePersisted`
- [x] `documentTemplateKeys` fourni en update remplace la valeur existante —
      `documentTemplateKeysProvided_onUpdate_replacesExisting`
- [x] `documentTemplateKeys` de mauvais type rejeté — `documentTemplateKeysWrongType_isRejected`
      (trouvaille testing utile en plus du plan initial)
- [x] `dryRun=true` ne persiste rien, y compris avec `documentTemplateKeys` fourni (même
      `dryRun_doesNotSave`/`dryRun_onUpdatePath_doesNotSave` que le reste du tool — aucun code
      spécifique à `documentTemplateKeys` dans la branche dryRun, donc pas de test dédié
      nécessaire au-delà de ceux déjà là)

### Nouveaux tools — lecture

- [x] `list_connector_definitions` retourne le catalogue, jamais de secret (catalogue statique,
      pas de config d'instance) — `listsCatalogSortedByType` ; `page`/`size` fournis explicitement
      — `pageAndSizeProvided_slicesAccordingly` (**ajouté en Phase 8**, gap trouvé)
- [x] `list_connector_instances` masque tout champ secret (`PASSWORD`/`*secret`/`*password`) —
      `masksSecretConfigValues` ; `page`/`size` fournis — `pageAndSizeProvided_relayedAsGiven`
      (**ajouté en Phase 8**, gap trouvé)
- [x] `get_connector_instance` masque tout champ secret (`masksSecretConfigValues`) ; erreur
      explicite si id inconnu — `unknownId_propagatesNotFound`
- [x] `list_task_question_schemas` pagine correctement (port `list` existant, D4) —
      `relaysToUseCase` ; `page`/`size` fournis — `pageAndSizeProvided_relayedAsGiven` (**ajouté
      en Phase 8**, gap trouvé)
- [x] `get_task_question_schema` — erreur explicite si `schemaKey` inconnu —
      `unknownSchemaKey_throwsNotFound`

### Nouveaux tools — écriture

- [x] `define_connector_instance` création : secret fourni en clair côté appelant, jamais renvoyé
      en clair par le tool (`responseSecretsAreMasked` + IT `defineConnectorInstance_createsAndMasksSecretOnRead`,
      qui vérifie en plus via lecture repository directe que le secret est bien accessible en clair
      côté serveur — le chiffrement en base lui-même est la responsabilité, déjà testée, de
      l'adaptateur de persistance, hors périmètre de cette feature)
- [x] `define_connector_instance` mise à jour : secret vide/masque préserve l'existant — délégué
      tel quel à `ManageConnectorInstanceUseCaseImpl.update` (aucune logique de merge propre au
      tool), déjà couvert par `update_preservesExistingPasswordWhenMaskedValueIsSubmitted`
      au niveau use case — pas de duplication de test au niveau tool
- [x] `define_connector_instance` `dryRun=true` : aucune persistance —
      `create_dryRun_doesNotPersist`/`update_dryRun_checksExistenceWithoutPersisting`
      (`verifyNoInteractions`/`never()` sur `create`/`update`, donc ni `createdBy` ni secret
      chiffré ne peuvent être écrits)
- [x] `define_connector_instance` type de connecteur inconnu → erreur explicite —
      `create_dryRun_unknownConnectorType_isRejected`
- [x] `define_connector_instance` — branches `isBlank()` (vs `null`), `config` `null` sur create,
      `enabled` explicite `false`, `restore=true` (**5 tests ajoutés en Phase 8**, gap de couverture
      de branches trouvé via Jacoco classe par classe — `create_blankName_isRejected`,
      `create_blankConnectorType_isRejected`, `blankId_isTreatedAsCreate`,
      `create_nullConfig_defaultsToEmptyMap`, `create_explicitEnabledFalse_isRespected`,
      `update_withRestoreTrue_passesRestoreFlag`)
- [x] `define_task_question_schema` création/mise à jour avec des `FieldDefinition` valides —
      tests dédiés dans `DefineTaskQuestionSchemaToolTest`
- [x] `define_task_question_schema` `dryRun=true` : aucune persistance — vérifié (construction de
      l'objet valide mais `save` jamais appelé)
- [x] `define_task_question_schema` collision de nom de variable avec les variables plateforme
      réservées — `reservedVariableName_isRejected`, réutilise
      `WorkflowVariables.isReservedOrKnownKey` (TODO §55)

## Integration Tests

- [x] IT HTTP M2M réelle par tool (7 nouveaux + `define_dossier_type` étendu), à travers la vraie
      chaîne Spring Security `/api/m2m/mcp` — `McpToolCallIntegrationTest` (14 tests, dont
      `listConnectorDefinitions_returns200`, `defineConnectorInstance_createsAndMasksSecretOnRead`,
      `defineConnectorInstance_dryRun_rejectsInvalidConfigWithoutPersisting`,
      `defineTaskQuestionSchema_persistsAndIsVisibleToDirectRepositoryRead`,
      `listAndGetTaskQuestionSchemas_return200`, `defineDossierType_withDocumentTemplateKeys_persists`)
- [x] Round-trip connecteur : `define_connector_instance` (create) → lecture directe repository
      (secret en clair côté serveur) → `get_connector_instance` (secret masqué) —
      `defineConnectorInstance_createsAndMasksSecretOnRead`. Round-trip `list_connector_instances`
      couvert séparément au niveau unitaire (`masksSecretConfigValues`) plutôt que dupliqué en IT.
- [x] Round-trip `define_task_question_schema` → repository → `list_task_question_schemas`/
      `get_task_question_schema` — `defineTaskQuestionSchema_persistsAndIsVisibleToDirectRepositoryRead`,
      `listAndGetTaskQuestionSchemas_return200`.
- [x] Scénario métier bout en bout (justifie le nom de la feature) —
      `businessFlow_connectorDossierTypeQuestionSchemaAndWorkflow_areAllReachableThroughMcpOnly` :
      `define_connector_instance` → `define_dossier_type` (avec `documentTemplateKeys`) →
      `define_task_question_schema` → `deploy_workflow` référençant les trois.
- [x] Régression : les 9 tools existants + `WorkflowBpmnDeploymentController`,
      `ConnectorAdminController`, `TaskQuestionSchemaController` (REST) continuent de fonctionner
      pour un acteur `TENANT_ADMIN`/`WORKFLOW_ADMIN` humain — tests REST existants conservés/mis à
      jour dans `ConnectorAdminControllerTest`/`TaskQuestionSchemaControllerTest`/
      `WorkflowTypeConfigControllerTest` (appel avec `Authentication` réel), `deployWorkflow_*`
      dans `McpToolCallIntegrationTest`.

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

- `./mvnw verify` (JDK 25, installé dans ce bac à sable en Phase 7 — absent au démarrage du
  conteneur) — **1666 tests, 0 échec**, gate Jacoco `BUNDLE` LINE ≥ 85 % / BRANCH ≥ 75 % passée.
- Vérification **classe par classe** sur les 14 classes nouvelles/modifiées par cette feature
  (convention TODO §28), via `target/site/jacoco/jacoco.csv` — pas seulement l'agrégat :

  | Classe | LINE | BRANCH |
  |---|---|---|
  | `TenantAdminGuard` | 100 % | 100 % |
  | `DeployWorkflowTool` | 100 % | 100 % |
  | `DefineDossierTypeTool` | 97.0 % | 95.8 % |
  | `GetConnectorInstanceTool` | 100 % | — (pas de branche) |
  | `DefineTaskQuestionSchemaTool` | 94.3 % | 80.0 % |
  | `DefineConnectorInstanceTool` | 100 % | 95.8 % (70.8 % avant Phase 8) |
  | `ListTaskQuestionSchemasTool` | 100 % | 100 % (50 % avant Phase 8) |
  | `ListConnectorInstancesTool` | 100 % | 100 % (50 % avant Phase 8) |
  | `ListConnectorDefinitionsTool` | 100 % | 100 % (50 % avant Phase 8) |
  | `ConnectorInstanceMcpView` | 100 % | 83.3 % |
  | `GetTaskQuestionSchemaTool` | 100 % | — (pas de branche) |
  | `WorkflowTypeConfigUseCaseImpl` | 100 % | 100 % |
  | `TaskQuestionSchemaUseCaseImpl` | 100 % | 100 % |
  | `ManageConnectorInstanceUseCaseImpl` | 97.4 % | **69.4 %** (sous le seuil — voir note) |

  **3 gaps réels trouvés et comblés en Phase 8** : les 3 nouveaux tools de liste ne testaient que
  `page`/`size` absents, jamais fournis explicitement (contrairement au pattern déjà établi et
  imité par serveur-mcp — `ListWorkflowTypesToolTest`, 4 tests page/size) — corrigé par l'ajout
  d'un test dédié par tool. `DefineConnectorInstanceTool` avait 6 branches non testées
  (`isBlank()` vs `null` sur `id`/`name`/`connectorType`, `config` `null` sur create, `enabled`
  explicite `false`, `restore=true`) — 6 tests ajoutés, BRANCH 70.8 % → 95.8 %.

  **1 gap identifié et volontairement non corrigé** : `ManageConnectorInstanceUseCaseImpl` reste à
  69.4 % BRANCH, sous le seuil de 75 % du dépôt. Vérifié via `git diff` contre le point de départ
  de cette feature : cette classe existait avant `extension-mcp-flux-metier` et ce cycle n'y a
  ajouté que l'appel au garde-fou (`tenantAdminGuard.requireTenantAdminOrApiService(actor)`, une
  ligne sans branche) sur les 6 méthodes — aucune des branches non couvertes (`mergeConfig`,
  `isBlankOrMask`, la fusion `name`/`enabled` sur update) n'a été ajoutée ou modifiée par cette
  feature. Combler ce gap demanderait des tests sur de la logique métier pré-existante et
  indépendante du sujet de ce cycle — même principe déjà appliqué en Phase 7 (import mort
  pré-existant laissé intact) : ne pas élargir le périmètre du correctif au-delà de ce que la
  feature touche réellement. Le gate `verify` (agrégat `BUNDLE`) reste vert malgré ce point, donc
  aucun blocage. Signalé pour un futur cycle dédié à cette classe si le seuil doit être strictement
  respecté classe par classe.
- Frontend : `cd frontend && npm run test` — **289/289 Vitest**, 0 régression (zéro fichier
  `frontend/**` modifié, feature backend-only, confirmé par `git diff --stat`).

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
- Phase 8 : 3 gaps de test comblés (pagination page/size sur les 3 nouveaux tools de liste) + 6
  tests de branches manquantes ajoutés sur `DefineConnectorInstanceTool` ; 1 gap pré-existant
  identifié et documenté sans correctif (`ManageConnectorInstanceUseCaseImpl`, hors périmètre de
  cette feature). Détail dans « Test Reporting & Coverage » ci-dessus.
