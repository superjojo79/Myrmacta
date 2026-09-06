---
phase: implementation
title: Implementation Guide
description: Technical implementation notes, patterns, and code guidelines
---

# Implementation Guide — `extension-mcp-flux-metier` (TODO §56)

> Phase 5 (implémentation TDD) close, M1–M9 enchaînés — `./mvnw verify` : 1653 tests, 0 échec.
> Phase 7 (check implementation) close — 1 bug dryRun trouvé et corrigé (`DefineConnectorInstanceTool`,
> +4 tests, 1657 tests). Phase 8 (dev-testing) close — 3 gaps de pagination (page/size jamais
> fournis explicitement sur les 3 nouveaux tools de liste) + 6 branches non testées sur
> `DefineConnectorInstanceTool` trouvés via Jacoco classe par classe et comblés (+9 tests). 1 gap
> pré-existant documenté sans correctif (`ManageConnectorInstanceUseCaseImpl`, 69.4 % BRANCH, hors
> périmètre de cette feature). `./mvnw verify` final (JDK 25, installé dans ce bac à sable en
> Phase 7 — absent au démarrage du conteneur) : **1666 tests, 0 échec**, ArchUnit conforme,
> couverture Jacoco `BUNDLE` tenue (LINE ≥ 85 % / BRANCH ≥ 75 %). Frontend : **289/289 Vitest**,
> 0 régression (zéro fichier `frontend/**` modifié, confirmé par `git diff --stat`). Playwright non
> exécutable dans ce bac à sable (binaire `chrome-headless-shell` absent, pré-existant, sans rapport
> avec cette feature) — non bloquant, feature backend-only.

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
- **`dryRun` sur `define_connector_instance`** — sur `create`+`dryRun`, valide `name`/`connectorType`
  requis puis `config` contre le schéma du type via `ConnectorCatalog` (aucune I/O, correctif
  Phase 7 — voir plus bas, la première version sautait cette validation) ; sur `update`+`dryRun`,
  appelle `get(id, tenantId, actor)` pour prouver existence + autorisation sans persister (pas de
  validation de `config` fusionné : le merge secret-préservant est privé à l'use case, documenté
  dans la Javadoc du tool comme limite assumée, pas un oubli).
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

## Phase 7 — Check Implementation

Revue fichier par fichier de tout ce qui est listé sous « Fichiers modifiés », comparé au design et
aux requirements — pas une relecture du résumé Phase 5, mais une nouvelle lecture du code livré.

**Points vérifiés explicitement (conformes) :**

1. **Pattern adaptateur fin** — les 7 nouveaux tools MCP ne contiennent aucune logique métier
   propre : ils parsent/valident la forme des arguments MCP, résolvent l'acteur via
   `McpAccessContextFactory`, délèguent au port `in` existant, et mettent en forme le résultat
   (`ConnectorInstanceMcpView` pour le masquage). Même forme que les 9 tools préexistants.
2. **Garde-fou en tête de méthode, sans exception** — relu `ManageConnectorInstanceUseCaseImpl`
   (6/6 méthodes), `TaskQuestionSchemaUseCaseImpl` (4/4), `WorkflowTypeConfigUseCaseImpl`
   (`create`/`update`, 2/2) : l'appel à `tenantAdminGuard.require...` est bien la première
   instruction de chaque méthode concernée. Note : `update()` de
   `ManageConnectorInstanceUseCaseImpl` appelle en interne `restore()`, qui revérifie le garde —
   redondant mais inoffensif (même acteur, même garde), non corrigé (pas un bug).
3. **Asymétrie D5 réellement implémentée, pas juste non testée** — confirmé dans le code (pas
   seulement dans les tests) : `SaveWorkflowTypeConfigUseCase`/`GetWorkflowTypeConfigUseCase` —
   seules `create`/`update` ont un paramètre acteur ; `getByKey`/`list` n'en ont toujours aucun,
   ni dans l'interface ni dans l'impl. Confirmé à l'identique côté REST :
   `WorkflowTypeConfigController.list`/`get` n'appellent toujours que
   `accessContextFactory.requireOidcPrincipal(authentication)` (vérif OIDC générique), sans
   transmettre d'acteur au use case — alors que `create`/`update` le font. Asymétrie cohérente de
   bout en bout.
4. **Masquage des secrets** — `ConnectorInstanceMcpView.from(...)` est le seul point de sortie des
   3 tools connecteur (`list`/`get`/`define`) vers le protocole MCP ; aucun ne renvoie
   `ConnectorInstanceData` brut. Vérifié qu'aucun message d'exception (`ConnectorValidationException`,
   `UnknownConnectorException`) n'interpole de valeur de `config` — ils ne portent que des noms de
   champs/types, jamais de valeurs.
5. **Contrat `dryRun`** — `define_task_question_schema` : conforme, la validation (construction de
   `TaskQuestionSchema`, dont le constructeur compact valide) a toujours lieu avant le
   `if (rawDryRun)`, sur les deux branches create/update. `define_connector_instance` : **non
   conforme, corrigé** — voir ci-dessous.

**Écart trouvé et corrigé :**

- **Bug — `DefineConnectorInstanceTool.defineCreate` sautait toute validation en `dryRun`.**
  L'ordre initial était : `if (dryRun) return stub;` placé *avant* les contrôles `name`/
  `connectorType` requis et avant toute validation de `config`. Un appel
  `define_connector_instance(dryRun=true)` sans `name`, ou avec un `config` invalide pour le
  `connectorType`, réussissait silencieusement — à l'opposé du contrat annoncé (« validates
  without persisting ») et incohérent avec `define_dossier_type`, où toute validation
  structurelle a lieu avant le retour dryRun. Corrigé : les contrôles `name`/`connectorType`
  passent avant le test `dryRun`, et une nouvelle validation `connectorCatalog.get(connectorType)
  .validate(resolvedConfig)` est ajoutée dans la branche dryRun (miroir non dupliqué du chemin
  réel — `ConnectorCatalog` est désormais injecté dans le tool). Tests : 3 nouveaux tests
  unitaires (`create_dryRun_stillValidatesConfigAgainstCatalog`,
  `create_dryRun_unknownConnectorType_isRejected`, `create_dryRun_missingName_isStillRejected`,
  `DefineConnectorInstanceToolTest` → 10/10 verts) + 1 nouveau test d'intégration HTTP réel
  (`defineConnectorInstance_dryRun_rejectsInvalidConfigWithoutPersisting`,
  `McpToolCallIntegrationTest` → 14/14 verts).

**Observations examinées et écartées (pas des bugs) :**

- *Le garde-fou domaine n'est-il pas redondant avec le fait que le transport MCP est déjà réservé
  au rôle `API_SERVICE` (chaîne de sécurité Order 1) ?* Non écarté à tort : le garde vérifie un
  rôle *métier* (`TENANT_ADMIN`/`WORKFLOW_ADMIN`/`API_SERVICE`) porté par l'acteur résolu depuis le
  JWT M2M, alors que la chaîne Spring Security vérifie seulement que le token M2M est valide et
  porte `API_SERVICE` au niveau transport. Un agent M2M authentifié reste un acteur métier soumis
  au RBAC domaine — les deux contrôles ont des objets différents, aucun n'est redondant avec
  l'autre.
- *`define_connector_instance` en `dryRun` sur `create` n'appelle aucun garde explicite avant la
  validation `ConnectorCatalog`* — vérifié : ce n'est pas un trou RBAC, `ConnectorCatalog.get(...)
  .validate(...)` est une opération purement locale (schéma statique en mémoire, aucun accès
  tenant), il n'y a rien à protéger par un garde-fou tant qu'aucune donnée du tenant n'est lue ou
  écrite. Le chemin non-dryRun, lui, reste protégé par le garde dans
  `ManageConnectorInstanceUseCaseImpl.create`.

**Conclusion Phase 7 :** implémentation alignée avec le design et les requirements ; un écart réel
trouvé et corrigé (dryRun connecteur), aucune autre déviation, aucune fuite de secret identifiée,
aucun appelant manquant supplémentaire (balayage `grep` sur les 5 interfaces modifiées : tous les
appelants recensés étaient déjà connus et à jour). Pas de retour en Phase 3/Phase 5 nécessaire.
