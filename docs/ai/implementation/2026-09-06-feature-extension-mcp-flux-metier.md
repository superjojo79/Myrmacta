---
phase: implementation
title: Implementation Guide
description: Technical implementation notes, patterns, and code guidelines
---

# Implementation Guide — `extension-mcp-flux-metier` (TODO §56)

> Phase 5 (implémentation TDD) close, M1–M9 enchaînés. `./mvnw verify` : **1653 tests, 0 échec**,
> ArchUnit conforme, couverture Jacoco tenue. Frontend : **289/289 Vitest**, 0 régression (zéro
> fichier `frontend/**` modifié, confirmé par `git diff --stat`). Playwright non exécutable dans ce
> bac à sable (binaire `chrome-headless-shell` absent, pré-existant, sans rapport avec cette
> feature) — non bloquant, feature backend-only.

## Ce qui a été livré

7 nouveaux tools MCP + extension de `define_dossier_type`, sur `POST /api/m2m/mcp` :

| Tool | Use case / port |
|---|---|
| `list_connector_definitions` | `ConnectorCatalog.listAll()` (pas de use case, catalogue statique) |
| `list_connector_instances` | `ManageConnectorInstanceUseCase.list` |
| `get_connector_instance` | `ManageConnectorInstanceUseCase.get` |
| `define_connector_instance` | `ManageConnectorInstanceUseCase.create`/`update` |
| `list_task_question_schemas` | `GetTaskQuestionSchemaUseCase.list` |
| `get_task_question_schema` | `GetTaskQuestionSchemaUseCase.getByKey` |
| `define_task_question_schema` | `SaveTaskQuestionSchemaUseCase.save` |
| `define_dossier_type` (étendu) | `documentTemplateKeys` optionnel, absent = préservé en update |

Plus le correctif de sécurité D-MCP-EXT-RBAC-GUARD/D5 (garde-fou domaine sur
`ManageConnectorInstanceUseCaseImpl` toutes opérations, `TaskQuestionSchemaUseCaseImpl`/
`GetTaskQuestionSchemaUseCaseImpl` toutes opérations, `WorkflowTypeConfigUseCaseImpl`
`create`/`update` seulement) et la nouvelle méthode `TenantAdminGuard
.requireTenantOrWorkflowAdminOrApiService`.

## Fichiers modifiés

**Nouveau**

- `infrastructure/mcp/tool/{ListConnectorDefinitionsTool,ListConnectorInstancesTool,GetConnectorInstanceTool,DefineConnectorInstanceTool,ListTaskQuestionSchemasTool,GetTaskQuestionSchemaTool,DefineTaskQuestionSchemaTool}.java`
- `infrastructure/mcp/tool/ConnectorInstanceMcpView.java` (masquage secrets, réutilise
  `ManageConnectorInstanceUseCaseImpl.isSecretKey`/`SECRET_MASK`)
- Tests unitaires miroir de chaque tool + `WorkflowTypeConfigUseCaseImplTest`,
  `TaskQuestionSchemaUseCaseImplTest` (nouveaux, la garde n'avait aucun test dédié avant)

**Modifié**

- `domain/dossier/TenantAdminGuard.java` (+`requireTenantOrWorkflowAdminOrApiService`)
- `domain/connector/port/in/ManageConnectorInstanceUseCase.java` /
  `usecase/ManageConnectorInstanceUseCaseImpl.java` (+ acteur sur toutes les méthodes, + garde ;
  `isSecretKey` passé `public` pour être réutilisé par `ConnectorInstanceMcpView`)
- `domain/workflow/port/in/{SaveTaskQuestionSchemaUseCase,DeleteTaskQuestionSchemaUseCase,GetTaskQuestionSchemaUseCase}.java`
  / `usecase/TaskQuestionSchemaUseCaseImpl.java` (+ acteur sur toutes les méthodes, + garde ;
  javadoc obsolète « no domain guard needed here » retiré)
- `domain/workflow/port/in/SaveWorkflowTypeConfigUseCase.java` /
  `usecase/WorkflowTypeConfigUseCaseImpl.java` (+ acteur sur `create`/`update` uniquement ; `list`/
  `getByKey` inchangés)
- `infrastructure/mcp/tool/DefineDossierTypeTool.java` (+`documentTemplateKeys`)
- `infrastructure/mcp/tool/DeployWorkflowTool.java` (transmet l'acteur déjà résolu à
  `create`/`update`)
- `infrastructure/web/connector/ConnectorAdminController.java`,
  `infrastructure/web/workflow/TaskQuestionSchemaController.java`,
  `infrastructure/web/workflow/WorkflowTypeConfigController.java` (passent l'acteur déjà
  disponible via `Authentication`/`AccessContextFactory`)
- `infrastructure/config/{ConnectorConfig,WorkflowConfig}.java` (injectent `TenantAdminGuard`,
  bean déjà existant depuis `DossierConfig`, réutilisé tel quel)
- `McpEndpointApiTest` (9 → 16 tools attendus — seule régression trouvée en Phase 5, corrigée)

## Écarts réels trouvés en Phase 5 (TDD), pas anticipés par le design

1. **Périmètre du garde-fou plus large que prévu** — en recensant tous les appelants avant
   d'écrire le code (pas après), `ConnectorPortsContractTest` (contrat de test existant avec une
   implémentation anonyme de `ManageConnectorInstanceUseCase`) est apparu comme appelant
   supplémentaire non listé en design — corrigé en même temps que `DeployWorkflowToolTest`.
2. **Mockito et méthodes `default`** — `TaskQuestionSchemaRepository.page(...)`/
   `WorkflowTypeConfigRepository.page(...)` sont des méthodes `default` déléguant à `findAll(...)` ;
   un mock Mockito standard ne les exécute pas (retourne `null`) — les tests stubbent directement
   `page(...)`, pas `findAll(...)`, découvert à l'exécution (2 tests rouges corrigés).
3. **`McpEndpointApiTest.toolsList_registersExactlyTheNineExpectedTools`** — seule régression
   trouvée par `./mvnw verify` : comptage figé à 9 tools, corrigé (16 tools, liste complétée,
   nom de test renommé).

## Décisions d'implémentation non couvertes explicitement par le design

- **`define_connector_instance` : create/update par présence de `id`, pas un upsert par nom** —
  `ConnectorInstance` est identifiée par UUID serveur (pas un `code` choisi par l'appelant comme
  `DossierType`), donc le tool suit le split REST (`POST` sans id vs `PATCH /{id}`) plutôt que le
  pattern upsert de `define_dossier_type`. Absent de `id` → `name`/`connectorType` requis,
  `config` validé par `ConnectorDefinition.validate`. Présent → mise à jour partielle
  (`name`/`enabled`/`config`/`restore` tous optionnels), même contrat que `UpdateCommand`.
- **`dryRun` sur `define_connector_instance` : pas de validation sur `create`** — mirroir exact du
  contrat déjà établi par `define_dossier_type` (`create`+`dryRun` = stub, aucune I/O) ; sur
  `update`+`dryRun`, appelle `get(id, tenantId, actor)` pour prouver existence + autorisation sans
  persister.
- **`dryRun` sur `define_task_question_schema` : validation réelle sur `create` et `update`** —
  contrairement au point précédent, construire un `TaskQuestionSchema` est une opération domaine
  pure (pas d'I/O) : son constructeur compact valide déjà (champs non vides, noms uniques, pas de
  `FILE`, pas de collision de variable réservée) — le dry run construit l'objet et le jette après
  validation, sans jamais appeler `save`. Documenté explicitement dans la Javadoc du tool pour ne
  pas laisser croire à une incohérence avec le point précédent.

## Sécurité

Voir « Ce qui a été livré » — le correctif de garde-fou domaine est la pièce de sécurité centrale
de cette feature : avant elle, tout appelant qui invoquait `ManageConnectorInstanceUseCaseImpl`,
`TaskQuestionSchemaUseCaseImpl` ou `WorkflowTypeConfigUseCaseImpl` (create/update) directement
(comme le fait tout tool MCP) contournait entièrement le RBAC REST (`@PreAuthorize`), qui n'est
qu'une annotation sur le contrôleur. Testé par des cas explicites acteur-sans-rôle par méthode
(pas seulement « ça compile »), pour chacun des 3 use cases. Secrets connecteurs : jamais
sérialisés en clair par les 3 tools de lecture/écriture connecteur (`ConnectorInstanceMcpView`,
testé).
