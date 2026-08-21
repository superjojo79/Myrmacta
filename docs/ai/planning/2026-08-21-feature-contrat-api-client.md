---
phase: planning
title: Project Planning & Task Breakdown
description: Break down work into actionable tasks and estimate timeline
status: reviewed
created: 2026-08-21
updated: 2026-08-21
feature: contrat-api-client
phase4: 2026-08-21
phase5: 2026-08-21
phase7: 2026-08-21
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

- [x] **M1** — Renommage `AdminListSorts` → `ListSorts` + 6 constantes
- [x] **M2** — Pagination JPA (`dossiers/{id}/elements`, `dossiers/{id}/workflows`)
- [x] **M3** — Pagination Flowable native (`workflow-tasks/mine`, `workflow-links/{id}/history`, `.../tasks`)
- [x] **M4** — Pagination mémoire (`connector-definitions`)
- [x] **M5** — Documentation OpenAPI des 3 endpoints `@Hidden`
- [x] **M6** — Adaptation SPA (`api/*.ts` via `listAllPages`) + tests Vitest
- [x] **M7** — Qualité (ArchUnit, OpenAPI golden, `./mvnw verify`, régression frontend complète, mise à jour TODO/FILE-ATTENTE)

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
- **Status :** done (2026-08-21) — `ListSortsTest` 2/2 verts, 6 appelants
  existants renommés, compile propre.

### M2 — Pagination JPA

#### T2.1 `DossierElementRepository.pageByDossierId`
- **Outcome :** nouvelle méthode `PageResult<DossierElement>
  pageByDossierId(UUID, PageQuery)` sur le port + `JpaDossierElementRepositoryAdapter`
  (pattern `SpringPages.of`/`SpringPages.map`, cf. `JpaDossierTypeRepositoryAdapter`).
  `ListDossierElementsUseCaseImpl.list` migré vers `PageResult<DossierElementView>`.
  `DossierController.listElements` expose `page`/`size`/`sort` →
  `PageResponse<DossierElementView>`. Ancienne méthode
  `findByDossierIdOrderByAddedAtAsc(UUID)` **conservée** (réutilisée par
  `WorkflowLinkVisualizationUseCaseImpl` et `ProcessPurgeOutboxUseCaseImpl`,
  non touchés par cette feature).
- **Deps :** T1.1.
- **Tests :** scénarios `JpaDossierElementRepositoryAdapter.pageByDossierId`
  + intégration `GET /api/dossiers/{id}/elements` du testing doc.
- **Status :** done (2026-08-21) — `DossierElementPersistenceTest` 4/4,
  `ListDossierElementsUseCaseImplTest` 2/2, `DossierApiTest` 71/71
  (3 tests JSON adaptés à l'enveloppe `PageResponse`, 1 nouveau test
  page/size/sort invalide).

#### T2.2 `DossierWorkflowLinkRepository.pageByDossierId`
- **Outcome :** nouvelle méthode `PageResult<DossierWorkflowLink>
  pageByDossierId(UUID, PageQuery)` + `JpaDossierWorkflowLinkRepositoryAdapter`
  (même pattern). `AttachWorkflowUseCaseImpl.getHistory` migré.
  `DossierWorkflowController.history` expose `page`/`size`/`sort`.
- **Deps :** T1.1.
- **Tests :** scénarios `JpaDossierWorkflowLinkRepositoryAdapter.pageByDossierId`
  + intégration `GET /api/dossiers/{id}/workflows` du testing doc.
- **Status :** done (2026-08-21) — `AttachWorkflowUseCaseImplTest` 5/5,
  `WorkflowPersistenceTest` 7/7, `WorkflowApiTest` 9/9, `OutboxRelayIT`
  2/2 (tous les appelants `getHistory` migrés vers `PageQuery`/`PageResult`).

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
- **Status :** done (2026-08-21) — `FlowableWorkflowTaskInboxAdapterTest`
  2/2 (nouveau, mocks purs `TaskService`/`TaskQuery`, pattern
  `FlowableWorkflowVisualizationAdapterTest`), `ListMyTasksUseCaseImplTest`
  1/1, `WorkflowApiTest` 9/9 (assertion `/mine` ajoutée). Note documentée
  en javadoc : le filtrage post-pagination des tâches orphelines peut
  rendre `content().size() < size` sans invalider la page.

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
- **Status :** done (2026-08-21) — `WorkflowLinkVisualizationUseCaseImplTest`
  18/18 (2 nouveaux), `FlowableWorkflowVisualizationAdapterTest` 7/7
  (2 nouveaux, vérifient `listPage`/`count` natifs et non `.list()`
  complet), `WorkflowApiTest` 9/9 (3 assertions JSON adaptées à
  `PageResponse`).

### M4 — Pagination mémoire

#### T4.1 `ConnectorDefinitionController.list` (slice trié)
- **Outcome :** contrôleur trie `catalog.listAll()` par `type` puis
  applique `PageResult.slice(sorted, query)` ; expose `page`/`size`/`sort`
  (`ListSorts.CONNECTOR_DEFINITIONS`). Pas de changement sur
  `ConnectorCatalog`/`ConnectorRegistry` (D-CONN-DEF-MECH).
- **Deps :** T1.1.
- **Tests :** scénarios `ConnectorDefinitionController.list` + intégration
  `GET /api/connector-definitions` du testing doc.
- **Status :** done (2026-08-21) — `ConnectorAdminApiTest` 10/10
  (2 nouveaux : tri déterministe par type, size respecté, sort invalide
  → 400).

### M5 — Documentation OpenAPI des 3 endpoints `@Hidden`

#### T5.1 SSE, multipart, webhook
- **Outcome :** retrait `@Hidden` sur `NotificationSseController`,
  `DossierElementUploadController`, `WebhookController`. Aucune
  annotation OpenAPI supplémentaire nécessaire : SSE documente déjà
  `produces = TEXT_EVENT_STREAM_VALUE` (springdoc l'infère automatiquement),
  multipart documente déjà `consumes = "multipart/form-data"` sur un
  `MultipartFile`, webhook expose `@RequestBody byte[]` — springdoc génère
  nativement un schema `string`/`format: byte` (déviation mineure vs le
  schema `object` libre envisagé en design : le handler consomme des
  octets bruts, pas un JSON parsé, donc `byte[]` est le contrat réel et
  plus honnête). Aucun changement de comportement runtime.
- **Deps :** aucune (indépendant de M1–M4).
- **Tests :** scénarios "endpoints `@Hidden`" du testing doc (non-régression
  fonctionnelle + présence dans le golden après régénération — vérifié en
  T7.2).
- **Status :** done (2026-08-21) — compilation propre, comportement
  runtime inchangé (aucun test fonctionnel existant modifié) ; présence
  dans le golden OpenAPI vérifiée en T7.2.

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
- **Status :** done (2026-08-21) — seuls 2 tests mockant `axios.get`
  directement ont dû être ajustés (`clients.spec.ts`, `phase8Api.spec.ts` :
  assertion `{params:{page,size}}` ajoutée) ; les autres fichiers listés
  en prévision (`apiCoverage.spec.ts`, `dossiers.spec.ts`,
  stores/vues) mockent au niveau wrapper, donc non affectés. `npm run
  test` 136/136, `npm run typecheck` et `npm run lint` propres. Aucun
  composant Vue modifié.

### M7 — Qualité

#### T7.1 ArchUnit
- **Outcome :** vérifier qu'aucune fuite de `Pageable`/`Page` Spring Data
  ni de types Flowable n'apparaît hors `infrastructure` ; ajuster si un
  écart apparaît.
- **Deps :** M1–M5.
- **Tests :** `ArchitectureRulesTest` (existant).
- **Status :** done (2026-08-21) — 3/3 verts, `grep` de contrôle confirme
  aucun import `org.springframework.data`/`org.flowable` dans
  `domain/`.

#### T7.2 OpenAPI golden + Verify + mise à jour docs produit
- **Outcome :** `openapi/openapi.yaml` régénéré
  (`-Dopenapi.update=true`), `OpenApiContractIT` vert ensuite sans
  régénération ; `./mvnw verify -Dmaven.compiler.release=21` vert ;
  `npm run test` + `npm run test:e2e` verts (régression complète du SPA,
  pas seulement les vues touchées) ; `TODO.md` §24 clos ; `FILE-ATTENTE.md`
  et `DEFERRED-FEATURES.md` mis à jour après implémentation.
- **Deps :** M1–M6, T7.1.
- **Tests :** evidence Phase 8 collée dans le testing doc.
- **Status :** done (2026-08-21) — `./mvnw verify` : **BUILD SUCCESS**,
  986 tests, 0 échec, Jacoco met. Golden régénéré et stabilité
  déterministe confirmée après correction d'un non-déterminisme de
  schema (`HttpHeaders` sur `WebhookController`). `npm run test` 136/136,
  `typecheck`/`lint` propres, `npm run test:e2e` 39/39 après correction
  de 2 régressions réelles trouvées par l'exécution complète (mocks
  Playwright non enveloppés + pattern glob obsolète — détail dans le
  testing doc, section « Trouvailles Phase 8 »). `TODO.md` §24 clos,
  `FILE-ATTENTE.md` mis à jour.

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

## Progress (Phase 7 — check implementation, clôture des tâches)

Toutes les tâches T1.1–T7.2 **done** (2026-08-21), TDD strict (rouge
confirmé avant chaque implémentation de méthode de port/adaptateur
nouvelle).

- Backend : `./mvnw verify -Dmaven.compiler.release=21` → **BUILD
  SUCCESS**, Tests run **986**, Failures **0**, Jacoco check met. Sweep
  Jacoco par classe (13 fichiers substantiellement modifiés) : 100% du
  code nouveau/modifié couvert ; toutes les branches encore signalées
  « missed » vérifiées pré-existantes (comparaison `git show` ligne par
  ligne), hors périmètre de cette feature.
- Frontend : `npm run test` → 47 fichiers / **136** tests ; `npm run
  typecheck` et `npm run lint` propres ; `npm run test:e2e`
  (`PLAYWRIGHT_CHROMIUM_EXECUTABLE=/opt/pw-browsers/chromium`) → **39**
  passed, après correction de **2 régressions SPA réelles** trouvées par
  l'exécution complète de la suite (détail : testing doc, section
  « Trouvailles Phase 8 »).
- **1 non-déterminisme du golden OpenAPI** trouvé et corrigé
  (`@RequestHeader HttpHeaders` exposait un schema Spring dont l'ordre
  de propriétés varie d'une régénération à l'autre) — déterminisme
  reconfirmé sur 2 régénérations consécutives identiques.
- **1 test obsolète corrigé** (`OpenApiEnabledApiTest` affirmait
  l'absence des 3 opérations désormais volontairement documentées).
- `TODO.md` §24 clos ; `FILE-ATTENTE.md` mis à jour (feature déplacée
  vers « Récemment livré », file d'attente renumérotée).

Aucune régression restante. Prochaine étape : `dev-testing` (Phase 8,
scénarios déjà documentés et exécutés — à formaliser) puis `dev-review`
(Phase 9, revue finale avant merge).
