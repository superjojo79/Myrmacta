---
phase: design
title: System Design & Architecture
description: Define the technical architecture, components, and data models
---

# System Design & Architecture — `extension-mcp-flux-metier`

> Phase 3 (`dev-design`) close — dernier point de conception ouvert (D2, emplacement du garde-fou
> partagé) tranché via `AskUserQuestion`. S'appuie sur
> [docs/ai/requirements/2026-09-06-feature-extension-mcp-flux-metier.md](../requirements/2026-09-06-feature-extension-mcp-flux-metier.md)
> et sur le code réel (tous les appelants des 4 interfaces `port.in` modifiées recensés, règles
> ArchUnit vérifiées avant de trancher D2).

## Architecture Overview

Même pattern que `serveur-mcp` (PR #82) : adaptateurs entrants fins dans `infrastructure.mcp.tool`,
zéro logique métier propre, appel direct aux use cases/ports domaine existants.

```mermaid
graph TD
    Agent[Agent MCP] -->|HTTPS + JWT M2M API_SERVICE| MCP[/api/m2m/mcp Streamable HTTP/]
    MCP --> T1[list_connector_definitions]
    MCP --> T2[list_connector_instances]
    MCP --> T3[get_connector_instance]
    MCP --> T4[define_connector_instance]
    MCP --> T5[list_task_question_schemas]
    MCP --> T6[get_task_question_schema]
    MCP --> T7[define_task_question_schema]
    MCP --> T8[define_dossier_type existant, étendu]

    T1 --> Catalog[ConnectorCatalog]
    T2 --> ManageConn[ManageConnectorInstanceUseCase]
    T3 --> ManageConn
    T4 --> ManageConn
    T5 --> GetQSchema[GetTaskQuestionSchemaUseCase]
    T6 --> GetQSchema
    T7 --> SaveQSchema[SaveTaskQuestionSchemaUseCase]
    T8 --> DossierTypeUC[Create/UpdateDossierTypeUseCase]

    ManageConn -->|toutes opérations| Guard1["TenantAdminGuard.requireTenantAdminOrApiService\n(méthode existante, réutilisée telle quelle)"]
    SaveQSchema -->|toutes opérations| Guard2["TenantAdminGuard.requireTenantOrWorkflowAdminOrApiService\n(nouvelle méthode, D2=c)"]
    DossierTypeUC --> Guard3["TenantAdminGuard.requireTenantAdminOrApiService\n(existant, inchangé)"]

    ManageConn --> ConnRepo[(ConnectorInstance\nAES-GCM secrets)]
    SaveQSchema --> QSchemaRepo[(TaskQuestionSchema)]
    DossierTypeUC --> DossierTypeRepo[(DossierType)]

    subgraph "Correctif pré-existant (PR #82)"
        DeployWorkflow[deploy_workflow existant] -->|create/update seulement| WfConfigUC[WorkflowTypeConfigUseCaseImpl]
        WfConfigUC --> Guard4["TenantAdminGuard.requireTenantOrWorkflowAdminOrApiService\n(même nouvelle méthode que SaveQSchema)"]
        WfConfigUC -.list/getByKey inchangés, aucun garde.-> OpenRead[(reste ouvert)]
    end

    Guard1 -.domain.dossier.TenantAdminGuard, réutilisé par domain.connector\nD2=c : dépendance transverse assumée, précédent déjà accepté\n(domain.workflow → domain.dossier.model.FieldDefinition, TODO §55).-> Guard2
```

**Composants clés**

- 7 nouveaux tools `infrastructure.mcp.tool.*`, même famille que les 9 existants
  (`DefineDossierTypeTool`, `DeployWorkflowTool`, etc.).
- Aucune nouvelle entité domaine : `ConnectorInstanceData`, `ConnectorDefinition`,
  `TaskQuestionSchema` existent déjà (livrés respectivement par `serveur-mcp`/connecteurs et TODO
  §55) — cette feature les expose au MCP, elle ne les invente pas.
- Un garde-fou domaine à généraliser (voir « Design Decisions ») — actuellement seul
  `domain.dossier.TenantAdminGuard` existe, réservé au bounded context `dossier`.

## Data Models

Aucun nouveau modèle domaine. Réutilisation stricte de l'existant :

| Type | Fichier | Champs pertinents pour le MCP |
|---|---|---|
| `ConnectorDefinition` | `domain.connector.model.ConnectorDefinition` | `getType()`, `getConfigSchema()`, `getCapability()` — catalogue statique, pas de persistance |
| `ConnectorInstanceData` | `domain.connector.model.ConnectorInstanceData` | `id`, `tenantId`, `name`, `connectorType`, `enabled`, `config` (secrets chiffrés au repos, déchiffrés en mémoire par l'adaptateur JPA) |
| `TaskQuestionSchema` | `domain.workflow.model.TaskQuestionSchema` | `schemaKey`, liste de `FieldDefinition` (réutilisé de `domain.dossier.model`) |
| `DossierType` | `domain.dossier.model.DossierType` | `documentTemplateKeys` (déjà existant, simplement rendu accessible en écriture MCP) |

## API Design

### Nouveaux tools MCP

| Tool | Use case / port réutilisé | Écriture ? | `dryRun` |
|---|---|---|---|
| `list_connector_definitions` | `ConnectorCatalog.listAll()` (port.out, pas de use case — même source que `ConnectorDefinitionController`) | non | — |
| `list_connector_instances` | `ManageConnectorInstanceUseCase.list` | non | — |
| `get_connector_instance` | `ManageConnectorInstanceUseCase.get` | non | — |
| `define_connector_instance` | `ManageConnectorInstanceUseCase.create`/`update` | oui | oui |
| `list_task_question_schemas` | `GetTaskQuestionSchemaUseCase.list` (existe déjà, vérifié Phase 2) | non | — |
| `get_task_question_schema` | `GetTaskQuestionSchemaUseCase.get` | non | — |
| `define_task_question_schema` | `SaveTaskQuestionSchemaUseCase.save` | oui | oui |

### `define_dossier_type` (étendu)

Ajout d'un paramètre optionnel `documentTemplateKeys: List<String>` — absent ou `null` = ne
touche pas la valeur existante en update, `[]` explicite en création (comportement actuel
inchangé pour tout appelant qui ne fournit pas ce champ).

### Authentification / autorisation

Inchangé au niveau transport : JWT M2M existant → `AccessContext.User` avec rôle `API_SERVICE`
(`McpAccessContextFactory`, inchangé). **Ce qui change : chaque use case domaine touché doit
désormais vérifier ce rôle lui-même**, voir « Design Decisions ».

## Component Breakdown

**Backend — nouveau**

- `infrastructure/mcp/tool/ListConnectorDefinitionsTool.java`
- `infrastructure/mcp/tool/ListConnectorInstancesTool.java`
- `infrastructure/mcp/tool/GetConnectorInstanceTool.java`
- `infrastructure/mcp/tool/DefineConnectorInstanceTool.java`
- `infrastructure/mcp/tool/ListTaskQuestionSchemasTool.java`
- `infrastructure/mcp/tool/GetTaskQuestionSchemaTool.java`
- `infrastructure/mcp/tool/DefineTaskQuestionSchemaTool.java`

**Backend — modifié**

- `infrastructure/mcp/tool/DefineDossierTypeTool.java` (+`documentTemplateKeys`)
- `domain/dossier/TenantAdminGuard.java` (D2=c) : nouvelle méthode
  `requireTenantOrWorkflowAdminOrApiService(AccessContext.User actor)` (accepte `TENANT_ADMIN`,
  `WORKFLOW_ADMIN` ou `API_SERVICE`) — `requireTenantAdminOrApiService` existante réutilisée telle
  quelle pour les connecteurs (RBAC REST `TENANT_ADMIN`-only).
- `domain/connector/port/in/ManageConnectorInstanceUseCase.java` : `AccessContext.User actor` en
  paramètre sur **toutes** les méthodes (`create`, `update`, `get`, `list`, `softDelete`,
  `restore`) — miroir exact du `@PreAuthorize` classe de `ConnectorAdminController` (D5).
- `domain/connector/usecase/ManageConnectorInstanceUseCaseImpl.java` (+ appel
  `TenantAdminGuard.requireTenantAdminOrApiService` en tête de chaque méthode).
- `domain/workflow/port/in/SaveTaskQuestionSchemaUseCase.java`,
  `DeleteTaskQuestionSchemaUseCase.java`, `GetTaskQuestionSchemaUseCase.java` : `AccessContext.User
  actor` en paramètre sur **toutes** les méthodes (`save`, `delete`, `getByKey`, `list`) — miroir
  exact de `TaskQuestionSchemaController`, qui gate aussi les lectures (D5).
- `domain/workflow/usecase/TaskQuestionSchemaUseCaseImpl.java`,
  `GetTaskQuestionSchemaUseCaseImpl.java` (+ appel
  `TenantAdminGuard.requireTenantOrWorkflowAdminOrApiService` en tête de chaque méthode).
- `domain/workflow/port/in/SaveWorkflowTypeConfigUseCase.java` (+ paramètre acteur sur `create`/
  `update` **uniquement** — **correctif sur du code déjà livré en PR #82**, `GetWorkflowTypeConfigUseCase`
  (`list`/`getByKey`) reste inchangé, cf. D5).
- `domain/workflow/usecase/WorkflowTypeConfigUseCaseImpl.java` (+ appel
  `TenantAdminGuard.requireTenantOrWorkflowAdminOrApiService` dans `create`/`update` seulement).
- Appelants REST à mettre à jour pour passer l'acteur déjà disponible via `Authentication`
  (pattern déjà utilisé par `DossierController`/`CreateDossierTypeUseCaseImpl`) :
  `ConnectorAdminController`, `TaskQuestionSchemaController`, `WorkflowTypeConfigController`
  (uniquement ses méthodes `create`/`update` — `list`/`getByKey` d'un contrôleur non touchées).
- `infrastructure/mcp/tool/DeployWorkflowTool.java` : appel MCP existant à
  `saveWorkflowTypeConfigUseCase.create(config)`/`update(config)` — l'acteur est **déjà résolu**
  dans la méthode (`accessContextFactory.currentActor()`, ligne 125) mais pas transmis ; ajout du
  paramètre à l'appel existant, changement minimal.

**Backend — inchangé, réutilisé tel quel**

- `ConnectorSecretCipher` (chiffrement AES-GCM) — pas de nouveau mécanisme de secret.
- `ManageConnectorInstanceUseCaseImpl.mergeConfig`/`isBlankOrMask` (préservation du secret sur
  update sans le repasser).

**Frontend**

- Aucun changement attendu (feature backend-only, comme `serveur-mcp`) — à reconfirmer en Phase 2
  (dev-requirements review) une fois le composant `list`/`get` de `TaskQuestionSchema` confirmé.

## Design Decisions

### D1 — Garde-fou domaine : nouveau paramètre acteur sur 5 interfaces `port.in` (breaking, assumé)

Constat vérifié dans le code (Phase 1, précisé Phase 2/3 par D5) : ni `ManageConnectorInstanceUseCase`,
ni `SaveTaskQuestionSchemaUseCase`/`DeleteTaskQuestionSchemaUseCase`/`GetTaskQuestionSchemaUseCase`,
ni `SaveWorkflowTypeConfigUseCase` n'acceptent aujourd'hui un `AccessContext.User` en paramètre —
contrairement à `CreateDossierTypeUseCase`/`UpdateDossierTypeUseCase`, qui le font déjà et
embarquent `TenantAdminGuard`. Ajouter le garde-fou domaine (décision D-MCP-EXT-RBAC-GUARD,
requirements ; périmètre exact par D-MCP-EXT-RBAC-SCOPE/D5) implique donc de **changer la
signature de 5 interfaces `port.in`** (le compte est passé de 4 à 5 en Phase 2 : `D5` a établi que
`TaskQuestionSchemaController` gate aussi ses lectures, donc `GetTaskQuestionSchemaUseCase` a
besoin de l'acteur lui aussi, pas seulement `Save`/`Delete`), et de mettre à jour tous leurs
appelants REST/MCP existants (qui ont déjà l'acteur via `Authentication`/`accessContextFactory`,
juste pas transmis aujourd'hui — voir « Component Breakdown »).

C'est un changement plus large que le périmètre visible depuis l'analyse initiale — assumé
explicitement par l'utilisateur (choix « Ajouter le garde-fou domaine partout », requirements),
documenté ici pour ne pas être découvert en Phase 5.

### D2 — Emplacement du garde-fou partagé (résolu, Phase 3, option **(c)**)

Vérifié avant de trancher : aucune règle ArchUnit n'interdit une dépendance entre sous-packages du
domaine (seules `domain_has_no_framework_dependency` et `domain_never_depends_on_infrastructure`
existent, `ArchitectureRulesTest.java`) — et un précédent déjà assumé existe :
`domain.workflow` dépend déjà de `domain.dossier.model.FieldDefinition`/`FieldType`, décision
explicite de TODO §55 (« dépendance déjà existante et assumée »). Étendre `TenantAdminGuard` sur
place n'est donc pas un précédent nouveau.

**Décision : (c)** — `TenantAdminGuard` (`domain.dossier`) gagne une méthode
`requireTenantOrWorkflowAdminOrApiService(AccessContext.User actor)`, réutilisée telle quelle par
`domain.connector` (via `requireTenantAdminOrApiService`, déjà existante — pas de nouvelle méthode
nécessaire pour les connecteurs) et `domain.workflow` (via la nouvelle méthode). Aucun nouveau
package, aucune duplication, cohérent avec le pattern déjà accepté. La classe reste dans
`domain.dossier` malgré son usage désormais transverse — renommage/déplacement jugé non justifié
par un simple gain cosmétique (options (a) dupliquer, (b) déplacer, écartées pour cette raison).

### D3 — Secrets connecteurs sur le canal MCP

Décision actée (requirements, D-MCP-EXT-SECRET) : même flux que l'admin REST — secret en clair
dans l'appel `define_connector_instance`, chiffré à l'écriture (`ConnectorSecretCipher`,
inchangé), masqué (`SECRET_MASK = "********"`) sur toute lecture MCP
(`list_connector_instances`/`get_connector_instance`), miroir de
`ConnectorResponseSecretMaskingTest`.

### D4 — `list_task_question_schemas` : use case manquant ? (résolu, Phase 2)

Vérifié dans le code : `GetTaskQuestionSchemaUseCase.list(PageQuery)` existe déjà
(`domain/workflow/port/in/GetTaskQuestionSchemaUseCase.java`), utilisé par
`TaskQuestionSchemaController.list`. `list_task_question_schemas` l'appelle directement, aucun
nouveau port nécessaire. De même, `list_connector_definitions` appelle directement
`ConnectorCatalog.listAll()` (port `out`, catalogue statique en mémoire) — même pattern que
`ConnectorDefinitionController` (REST), pas de use case dédié à créer.

### D5 — Périmètre exact du garde-fou domaine par use case (résolu, Phase 2, D-MCP-EXT-RBAC-SCOPE)

Le RBAC REST diffère par contrôleur — vérifié précisément en Phase 2 :

| Use case | RBAC REST équivalent | Garde-fou domaine à ajouter |
|---|---|---|
| `ManageConnectorInstanceUseCaseImpl` | `ConnectorAdminController` : `@PreAuthorize` **au niveau classe** — toutes les routes (list/get/create/delete) | Sur **toutes** les opérations (`create`, `update`, `get`, `list`, `softDelete`, `restore`) |
| `TaskQuestionSchemaUseCaseImpl` / `GetTaskQuestionSchemaUseCaseImpl` | `TaskQuestionSchemaController` : `@PreAuthorize` explicite sur **chaque** méthode, y compris `list`/`getByKey` | Sur **toutes** les opérations |
| `WorkflowTypeConfigUseCaseImpl` | `WorkflowTypeConfigController` : `@PreAuthorize` **uniquement** sur `create`/`update` — `GET` (list/getByKey) ouverts à tout utilisateur authentifié | **Uniquement** sur `create`/`update` — ne pas toucher `list`/`getByKey` (régresserait `list_workflow_types`, déjà en production depuis PR #82, sans aucune demande fonctionnelle pour ça) |

## Non-Functional Requirements

- **Sécurité** : aucun secret en clair au repos ni dans les logs (`ConnectorSecretCipher`
  inchangé) ; garde-fou domaine testé par un cas où un acteur sans rôle admin/`API_SERVICE` est
  rejeté par le use case lui-même, pas seulement par un `@PreAuthorize` REST contournable par un
  appelant qui invoque le use case directement (c'est exactement le trou que cette feature comble).
- **Compatibilité** : les 9 tools existants et les endpoints REST déjà couverts
  (`ConnectorAdminController`, `TaskQuestionSchemaController`, `WorkflowTypeConfigController`) ne
  doivent subir aucune régression de comportement — seul le paramètre acteur est ajouté en
  interne, le comportement pour un acteur déjà autorisé (rôles REST habituels, `API_SERVICE` MCP)
  reste identique ; `WorkflowTypeConfigController.list`/`getByKey` et `list_workflow_types`
  restent ouverts sans garde (D5), preuve de non-régression explicite en Phase 8.
- **Performance** : aucune, ces tools sont des opérations CRUD ponctuelles sur des tables déjà
  indexées (pas de nouveau chemin chaud).
- **Couverture** : Jacoco LINE ≥ 85 % / BRANCH ≥ 75 % (seuils actuels du dépôt), zéro régression.
