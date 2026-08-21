---
phase: planning
title: Project Planning & Task Breakdown
description: Break down work into actionable tasks and estimate timeline
status: reviewed
created: 2026-08-21
updated: 2026-08-21
feature: contrat-api-client
phase4: 2026-08-21
requirements_ref: docs/ai/requirements/2026-08-21-feature-contrat-api-client.md
design_ref: docs/ai/design/2026-08-21-feature-contrat-api-client.md
testing_ref: docs/ai/testing/2026-08-21-feature-contrat-api-client.md
---

# Project Planning & Task Breakdown — `contrat-api-client`

> Phase 4 **reviewed**. Design figé (D-SORT-CONST, D-CONN-DEF-MECH,
> D-ELEMENTS-MECH, D-DOSSIER-WORKFLOWS-MECH, D-FLOWABLE-MECH,
> D-TASK-LIMIT, D-HIDDEN-ANNOTATIONS, D-SPA-ADAPT). Backend + adaptation
> minimale des wrappers `frontend/src/api/*.ts` (aucun composant Vue).

## Milestones

- [ ] **M1** — Renommage `AdminListSorts` → `ListSorts` + 6 constantes
- [ ] **M2** — Pagination JPA (`dossiers/{id}/elements`, `dossiers/{id}/workflows`)
- [ ] **M3** — Pagination Flowable native (`workflow-tasks/mine`, `workflow-links/{id}/history`, `.../tasks`)
- [ ] **M4** — Pagination mémoire (`connector-definitions`)
- [ ] **M5** — Documentation OpenAPI des 3 endpoints `@Hidden`
- [ ] **M6** — Adaptation SPA (`api/*.ts` via `listAllPages`) + tests Vitest
- [ ] **M7** — Qualité (ArchUnit, OpenAPI golden, `./mvnw verify`, régression frontend complète, mise à jour TODO/FILE-ATTENTE)

M1 est un pré-requis pour M2–M5 (constantes de tri). M2, M3, M4, M5 sont
indépendants entre eux. M6 dépend de M2–M5 (les endpoints doivent exposer
`PageResponse` avant d'adapter les wrappers). M7 ferme le lot.

## Task Breakdown

TDD : test rouge → code → vert. Validation : commandes en fin de document.

### M1 — `ListSorts`

#### T1.1 Renommage `AdminListSorts` → `ListSorts`
- **Outcome :** classe `infrastructure.web.ListSorts` (renommage
  mécanique, tous les appelants existants mis à jour), + 6 nouvelles
  constantes (`DOSSIER_ELEMENTS = "addedAt,asc"`,
  `MY_WORKFLOW_TASKS = "createdAt,asc"`,
  `CONNECTOR_DEFINITIONS = "type,asc"`,
  `DOSSIER_WORKFLOWS = "sequence,asc"`,
  `WORKFLOW_LINK_HISTORY = "startedAt,asc"`,
  `WORKFLOW_LINK_TASKS = "createdAt,asc"`).
- **Deps :** aucune.
- **Tests :** scénario `ListSorts` du testing doc (constantes existantes
  + 6 nouvelles).
- **Status :** not started

### M2 — Pagination JPA

#### T2.1 `DossierElementRepository.pageByDossierId`
- **Outcome :** nouvelle méthode `PageResult<DossierElement>
  pageByDossierId(UUID, PageQuery)` sur le port + `JpaDossierElementRepositoryAdapter`
  (pattern `SpringPages.of`/`SpringPages.map`, cf. `JpaDossierTypeRepositoryAdapter`).
  `ListDossierElementsUseCaseImpl.list` migré vers `PageResult<DossierElementView>`.
  `DossierController.listElements` expose `page`/`size`/`sort` →
  `PageResponse<DossierElementView>`.
- **Deps :** T1.1.
- **Tests :** scénarios `JpaDossierElementRepositoryAdapter.pageByDossierId`
  + intégration `GET /api/dossiers/{id}/elements` du testing doc.
- **Status :** not started

#### T2.2 `DossierWorkflowLinkRepository.pageByDossierId`
- **Outcome :** nouvelle méthode `PageResult<DossierWorkflowLink>
  pageByDossierId(UUID, PageQuery)` + `JpaDossierWorkflowLinkRepositoryAdapter`
  (même pattern). `AttachWorkflowUseCaseImpl.getHistory` migré.
  `DossierWorkflowController.history` expose `page`/`size`/`sort`.
- **Deps :** T1.1.
- **Tests :** scénarios `JpaDossierWorkflowLinkRepositoryAdapter.pageByDossierId`
  + intégration `GET /api/dossiers/{id}/workflows` du testing doc.
- **Status :** not started

### M3 — Pagination Flowable native

#### T3.1 `WorkflowTaskInboxPort.findActiveAssignedTo` (signature `PageQuery`)
- **Outcome :** port modifié (`int limit` → `PageQuery query`, retour
  `PageResult<ActiveTaskSnapshot>`) ; `FlowableWorkflowTaskInboxAdapter`
  utilise `taskService.createTaskQuery()...orderByTaskCreateTime().asc()
  .listPage(offset, size)` + `.count()`. `ListMyTasksUseCaseImpl` migré,
  `DEFAULT_LIMIT` supprimé (D-TASK-LIMIT). `WorkflowTaskController.mine`
  expose `page`/`size`.
- **Deps :** T1.1.
- **Tests :** scénarios `FlowableWorkflowTaskInboxAdapter.findActiveAssignedTo`
  + intégration `GET /api/workflow-tasks/mine` (> 100 tâches) du testing doc.
- **Status :** not started

#### T3.2 `WorkflowVisualizationPort.getHistory` / `.listActiveTasks` (signature `PageQuery`)
- **Outcome :** port modifié, `FlowableWorkflowVisualizationAdapter`
  utilise `listPage`/`count` sur `HistoricActivityInstanceQuery` et
  `TaskQuery`. `WorkflowLinkVisualizationUseCaseImpl.getHistory/getTasks`
  migrés. `WorkflowLinkVisualizationController.history/tasks` exposent
  `page`/`size`.
- **Deps :** T1.1.
- **Tests :** scénarios `FlowableWorkflowVisualizationAdapter` +
  intégration `GET /api/workflow-links/{linkId}/history` et `.../tasks`
  du testing doc.
- **Status :** not started

### M4 — Pagination mémoire

#### T4.1 `ConnectorDefinitionController.list` (slice trié)
- **Outcome :** contrôleur trie `catalog.listAll()` par `type` puis
  applique `PageResult.slice(sorted, query)` ; expose `page`/`size`/`sort`
  (`ListSorts.CONNECTOR_DEFINITIONS`). Pas de changement sur
  `ConnectorCatalog`/`ConnectorRegistry` (D-CONN-DEF-MECH).
- **Deps :** T1.1.
- **Tests :** scénarios `ConnectorDefinitionController.list` + intégration
  `GET /api/connector-definitions` du testing doc.
- **Status :** not started

### M5 — Documentation OpenAPI des 3 endpoints `@Hidden`

#### T5.1 SSE, multipart, webhook
- **Outcome :** retrait `@Hidden` sur `NotificationSseController`,
  `DossierElementUploadController`, `WebhookController` ; annotations
  springdoc minimales par endpoint (cf. design, tableau API Design).
  Aucun changement de comportement runtime.
- **Deps :** aucune (indépendant de M1–M4).
- **Tests :** scénarios "endpoints `@Hidden`" du testing doc (non-régression
  fonctionnelle + présence dans le golden après régénération).
- **Status :** not started

### M6 — Adaptation SPA

#### T6.1 Wrappers `api/*.ts` via `listAllPages`
- **Outcome :** `frontend/src/api/dossiers.ts` (`listElements`, endpoint
  `/workflows`), `api/tasks.ts` (`listMyTasks`), `api/workflows.ts`
  (`getWorkflowHistory`, `getWorkflowTasks`), `api/admin.ts`
  (connector-definitions) — chaque fonction interne utilise
  `listAllPages` (déjà présent dans `api/page.ts`), signature publique
  `Promise<T[]>` inchangée (D-SPA-ADAPT).
- **Deps :** M2, M3, M4 (les endpoints doivent renvoyer `PageResponse`
  avant d'adapter les wrappers).
- **Tests :** mise à jour des mocks Vitest existants
  (`phase8Api.spec.ts`, `apiCoverage.spec.ts`, `clients.spec.ts`,
  `dossiers.spec.ts`) pour renvoyer une `Page<T>` au lieu d'un tableau brut.
- **Status :** not started

### M7 — Qualité

#### T7.1 ArchUnit
- **Outcome :** vérifier qu'aucune fuite de `Pageable`/`Page` Spring Data
  ni de types Flowable n'apparaît hors `infrastructure` ; ajuster si un
  écart apparaît.
- **Deps :** M1–M5.
- **Tests :** `ArchitectureRulesTest` (existant).
- **Status :** not started

#### T7.2 OpenAPI golden + Verify + mise à jour docs produit
- **Outcome :** `openapi/openapi.yaml` régénéré
  (`-Dopenapi.update=true`), `OpenApiContractIT` vert ensuite sans
  régénération ; `./mvnw verify -Dmaven.compiler.release=21` vert ;
  `npm run test` + `npm run test:e2e` verts (régression complète du SPA,
  pas seulement les vues touchées) ; `TODO.md` §24 clos ; `FILE-ATTENTE.md`
  et `DEFERRED-FEATURES.md` mis à jour après implémentation.
- **Deps :** M1–M6, T7.1.
- **Tests :** evidence Phase 8 collée dans le testing doc.
- **Status :** not started

## Mapping tests ↔ tâches

| Scénario testing | Tâches |
|---|---|
| `ListSorts` (constantes existantes + 6 nouvelles) | T1.1 |
| `JpaDossierElementRepositoryAdapter.pageByDossierId` + intégration `elements` | T2.1 |
| `JpaDossierWorkflowLinkRepositoryAdapter.pageByDossierId` + intégration `dossiers/{id}/workflows` | T2.2 |
| `FlowableWorkflowTaskInboxAdapter.findActiveAssignedTo` + intégration `workflow-tasks/mine` | T3.1 |
| `FlowableWorkflowVisualizationAdapter` + intégration `workflow-links/{id}/history`+`.../tasks` | T3.2 |
| `ConnectorDefinitionController.list` + intégration `connector-definitions` | T4.1 |
| Non-régression `dossiers/{id}/relations` (D-RELATIONS) | Aucune tâche dédiée — endpoint non touché, test de garde ajouté en T2.1/T2.2 par cohérence |
| Endpoints `@Hidden` (SSE/multipart/webhook) | T5.1 |
| OpenAPI golden (6 endpoints paginés + 3 `@Hidden` retirés) | T5.1, T7.2 |
| Wrappers `api/*.ts` + mocks Vitest | T6.1 |
| Suite Playwright existante (non-régression) | T7.2 |
| ArchUnit | T7.1 |

Tous les scénarios testing ont une tâche.

## Dependencies (ordre)

T1.1 avant tout (constantes de tri). M2 (T2.1, T2.2), M3 (T3.1, T3.2), M4
(T4.1) indépendants entre eux après T1.1. M5 (T5.1) indépendant du reste.
M6 (T6.1) après M2–M5. M7 (T7.1, T7.2) ferme le lot.

## Timeline & Estimates

Pas d'estimation calendaire. Lot unique de développement, toutes les
tâches T1.1–T7.2.

## Risks & Mitigation

| Risque | Mitigation |
|---|---|
| Régression silencieuse sur `ListMyTasksUseCaseImpl` après suppression de `DEFAULT_LIMIT` (client qui ne pagine pas ne verrait plus qu'une page de 20) | Documenté explicitement en design (D-TASK-LIMIT) ; test d'intégration dédié avec > 100 tâches prouvant l'accès à toutes via pagination |
| `listPage`/`count` Flowable mal utilisés (offset/maxResults inversés, désynchronisation avec le tri) | Tests dédiés par adaptateur vérifiant `totalElements` réel et ordre déterministe (T3.1, T3.2) |
| Wrappers SPA (`listAllPages`) génèrent N requêtes séquentielles au lieu d'une — risque de lenteur perçue si une liste est très longue | Accepté explicitement (D-SPA-ADAPT) : aucune des 6 listes n'a un volume réellement problématique aujourd'hui ; à revisiter si un besoin de pagination UI émerge |
| Renommage `AdminListSorts` → `ListSorts` oublie un appelant | Détecté à la compilation (erreur immédiate), pas de risque silencieux |
| Tests Vitest mockant `axios.get` au niveau page cassent silencieusement (mock renvoie un tableau au lieu d'une `Page<T>`) | Chaque mock impacté listé explicitement en M6 (T6.1), `npm run test` doit échouer avant correction (preuve TDD) |

## Resources Needed

Design reviewed, testing doc scénarisé, ports/adapters existants
(`SpringPages`, `PageResult.slice`, `listAllPages` frontend) — aucune
nouvelle dépendance.

## Progress (Phase 4)

Planning initial complet : 7 milestones (M1–M7), 9 tâches (T1.1–T7.2),
toutes tracées et mappées aux scénarios testing (aucun scénario orphelin).
Aucune tâche commencée — l'implémentation (Phase 5) n'a pas encore
démarré, tous les statuts sont `not started`.

Prochaine étape : `dev-implementation` (Phase 5, TDD strict — rouge avant
vert sur chaque nouvelle classe/méthode de production).
