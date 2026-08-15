---
phase: planning
title: Project Planning & Task Breakdown
description: Break down work into actionable tasks and estimate timeline
status: reviewed
created: 2026-08-14
updated: 2026-08-15
feature: relations-dossiers
design_aligned: 2026-08-14
phase4: 2026-08-14
sequencing: layered
---

# Project Planning & Task Breakdown

## Milestones
**What are the major checkpoints?**

- [x] **M1 — Domaine** : records `DossierRelation`/`RelationType`/`DossierRelationView`/`RelationDirection` + ports in/out
- [x] **M2 — Persistence** : Liquibase `dossier_relation` (+ `idempotency_key` + index unique partiel), entité/adaptateur JPA
- [x] **M3 — Use cases manuels** : création (ACL EDIT+VIEW), liste bidirectionnelle (ACL filtrée), wiring `DossierConfig`
- [x] **M4 — API REST** : DTOs + `DossierController` (`POST`/`GET .../relations`) + mapping 409
- [x] **M5 — Panneau `WorkflowLinkDossierView`** : ferme le gap `relations: []` en dur (endpoint existant, non consommé par le FE actuel mais objectif primaire du scope)
- [x] **M6 — ServiceTask BPMN** : `ChildDossierRelationConfirmer` (idempotence) + `CreateChildDossierDelegate`
- [x] **M7 — Frontend** : section "Dossiers liés" + "Lier un dossier" dans `DossierDetailView.vue` (page unique — voir note de découverte ci-dessous)
- [x] **M8 — Preuves & docs** : suite de tests verte, `DEFERRED-FEATURES.md` §1.4 mis à jour

### Note de découverte (impacte le séquencement)

`DossierDetailView.vue` est la **seule** page qui affiche à la fois les
champs du dossier, `ElementsList` et le diagramme BPMN
(`BpmnViewer`/`TaskList`/`HistoryTimeline`) — via des appels séparés
(`GET /api/dossiers/{id}/elements`, `GET /api/workflow-links/{linkId}/diagram`,
etc.), **jamais** via `GET /api/workflow-links/{linkId}/dossier`
(`WorkflowLinkVisualizationUseCaseImpl.getDossier()` /
`WorkflowLinkDossierView`). Cet endpoint existe déjà mais n'est consommé
par aucune UI aujourd'hui. Conséquence sur le plan :
- **M7 (frontend)** ne nécessite qu'**une seule** intégration — la section
  "Dossiers liés" dans `DossierDetailView.vue`, alimentée directement par
  le nouvel endpoint `GET /api/dossiers/{id}/relations` (M4), au même
  niveau que `elements`.
- **M5** (fix `WorkflowLinkDossierView.relations`) reste dans le scope —
  c'est un objectif primaire des requirements et un endpoint documenté,
  potentiellement consommé par d'autres clients (mobile, intégration
  future) — mais n'est **pas bloquant** pour ce que l'utilisateur voit
  dans l'app aujourd'hui. Peut être fait en parallèle de M7.

## Task Breakdown
**What specific work needs to be done?**

Statuts : `[ ]` not started · `[~]` in progress · `[x]` done · `[!]` blocked

### M1 — Domaine

- [x] **T1. Records domaine `DossierRelation`/`RelationType`/`DossierRelationView`/`RelationDirection`**
  - **Outcome :** `domain/dossier/model/{DossierRelation, RelationType, DossierRelationView, RelationDirection}.java` — pur Java, aucune annotation Spring/JPA/Flowable. `DossierRelation` inclut `idempotencyKey` nullable. `DossierRelationView.relatedDossier` de type `DossierListItem` (existant, `pagination-dossiers`).
  - **Outcome :** `domain/dossier/model/DuplicateDossierRelationException.java` — exception domaine pour le mapping 409 (violation de contrainte unique mappée par l'adaptateur JPA, voir T4).
  - **Depends on :** —
  - **Validation :** compile ; ArchUnit `domain_has_no_framework_dependency` / `domain_never_depends_on_infrastructure`.
  - **Tests liés :** `DossierRelation` (domaine) — auto-relation, ArchUnit.

- [x] **T2. Ports `DossierRelationRepository` / `CreateDossierRelationUseCase` / `ListDossierRelationsUseCase`**
  - **Outcome :** `domain/dossier/port/out/DossierRelationRepository.java`
    (`save`, `findByDossier(String tenantId, UUID dossierId)`,
    `findByIdempotencyKey(String tenantId, UUID sourceDossierId, String idempotencyKey)`) ;
    `domain/dossier/port/in/{CreateDossierRelationUseCase, ListDossierRelationsUseCase}.java`.
  - **Depends on :** T1
  - **Validation :** compile ; ArchUnit `usecase_impl_naming_convention` (préparatoire, s'applique aux impl en T5/T6).
  - **Tests liés :** —

### M2 — Persistence

- [x] **T3. Liquibase `009-dossier-relation-tables.yaml`**
  - **Outcome :** table `dossier_relation` (FK `dossier(id)` ×2, `CHECK` anti-auto-relation, `UNIQUE(tenant_id, source_dossier_id, target_dossier_id, type)`, colonne `idempotency_key` nullable) + 2 index (`source`, `target`) + index unique partiel `uq_dossier_relation_idempotency` (`WHERE idempotency_key IS NOT NULL`) ; `include` dans `db.changelog-master.yaml`.
  - **Depends on :** —
  - **Validation :** boot H2 (MODE=PostgreSQL) + IT schema.
  - **Tests liés :** IT persistence (schema).

- [x] **T4. `DossierRelationJpaEntity` + `SpringDataDossierRelationRepository` + `DossierRelationJpaAdapter`**
  - **Outcome :** entité JPA ; repo Spring Data (`findBy(tenantId, sourceDossierId OR targetDossierId)`, `findByTenantIdAndSourceDossierIdAndIdempotencyKey`) ; adaptateur implémentant `DossierRelationRepository`. `save()` catch `DataIntegrityViolationException` (contrainte `uq_dossier_relation`) → relance `DuplicateDossierRelationException` (T1).
  - **Depends on :** T2, T3
  - **Validation :** IT persistence (save, findByDossier bidirectionnel, findByIdempotencyKey, violation unique → exception domaine).
  - **Tests liés :** IT — persistence save/find, mapping exception unique.

### M3 — Use cases manuels

- [x] **T5. `CreateDossierRelationUseCaseImpl`**
  - **Outcome :** valide `source != target` (400, `IllegalArgumentException`) ; charge source (404 si absent/hors tenant via `DossierNotFoundException`) ; `assertCanEdit` source (403) ; charge cible (404) ; `assertCanView` cible (403, D-CREATE-ACL) ; construit `DossierRelation` (`idempotencyKey = null`, création manuelle) ; `save()`.
  - **Depends on :** T2, T4
  - **Validation :** unitaires verts (mocks repository/policy).
  - **Tests liés :** `CreateDossierRelationUseCaseImpl` — tous scénarios testing §.

- [x] **T6. `ListDossierRelationsUseCaseImpl`**
  - **Outcome :** `assertCanView` sur le dossier interrogé ; `repository.findByDossier(tenantId, dossierId)` ; pour chaque relation, détermine `direction` (comparaison UUID) et l'"autre" dossier ; **nouveau** petit mapper `Dossier` → `DossierListItem` (le mapper existant vit dans `JpaDossierSearchAdapter.toItem`, construit depuis une projection JPA brute — ici on part d'un `Dossier` domaine déjà chargé via `DossierRepository.findById`, donc un mapper dédié `DossierListItem.from(Dossier, String dossierTypeLabel)` ou équivalent) ; résout `dossierTypeLabel` via `DossierTypeRepository.findByCode` (par relation — volume faible, pas de batching nécessaire en V1, cohérent avec l'hypothèse Scalabilité du design) ; filtre silencieusement (omission) les relations dont l'"autre" dossier n'est pas VIEW-accessible (D-ACL) ; bypass admin via `DossierAccessPolicy` existant.
  - **Depends on :** T2, T4
  - **Validation :** unitaires verts.
  - **Tests liés :** `ListDossierRelationsUseCaseImpl` — tous scénarios testing §.

- [x] **T7. Wiring `DossierConfig`**
  - **Outcome :** beans `DossierRelationRepository` (adaptateur), `CreateDossierRelationUseCase`, `ListDossierRelationsUseCase`.
  - **Depends on :** T4, T5, T6
  - **Validation :** contexte Spring monte (IT boot existante).
  - **Tests liés :** IT boot.

### M4 — API REST

- [x] **T8. DTOs + mapping exception**
  - **Outcome :** `infrastructure/web/dossier/{DossierRelationResponse, CreateDossierRelationRequest}.java` (response inclut `relatedDossier` au format déjà utilisé par `DossierPageResponse`/`DossierListItem` — pas de nouveau format). `DossierExceptionHandler` : `DuplicateDossierRelationException` → 409 `ProblemDetail`.
  - **Depends on :** T1
  - **Validation :** unit mapping.
  - **Tests liés :** —

- [x] **T9. `DossierController` — `POST`/`GET .../relations`**
  - **Outcome :** `@PostMapping("/{id}/relations")` (`assertCanEdit` implicite via use case) ; `@GetMapping("/{id}/relations")`. Auth via `AccessContextFactory.user` (même patron que `listElements`).
  - **Depends on :** T7, T8
  - **Validation :** MockMvc.
  - **Tests liés :** MockMvc — 201, 200 bidirectionnel, 400/403/404/409, régression `elements`/`status`/liste paginée inchangés.

### M5 — Panneau `WorkflowLinkDossierView`

- [x] **T10. `WorkflowLinkVisualizationUseCaseImpl.getDossier()` — peupler `relations`**
  - **Outcome :** `WorkflowLinkDossierView.relations` retype `List<Object>` → `List<DossierRelationView>` ; `getDossier()` appelle `ListDossierRelationsUseCase.list(dossierId, actor)` au lieu de `List.of()`. Mise à jour `WorkflowConfig` (injection du nouveau port).
  - **Depends on :** T2, T6, T7
  - **Validation :** IT — `WorkflowApiTest` ou test dédié.
  - **Tests liés :** IT — panneau dossier visualisation workflow inclut les relations réelles.

### M6 — ServiceTask BPMN

- [x] **T11. `ChildDossierRelationConfirmer` (infrastructure/dossier, `@Transactional`)**
  - **Outcome :** lookup `findByIdempotencyKey` → si trouvé, renvoie `childDossierId` existant (aucune écriture) ; sinon, `CreateDossierUseCase.create(...)` puis `CreateDossierRelationUseCase.create(...)` (avec `idempotencyKey`), les deux dans la même transaction (propagation `REQUIRED` par défaut, rejoint les `@Transactional` des adaptateurs JPA) ; appelle les deux use cases avec `AccessContext.system("flowable-delegate")`.
  - **Depends on :** T4, T5, T7
  - **Validation :** unitaires (mock use cases + repository) — replay, création, course concurrente (contrainte unique).
  - **Tests liés :** `ChildDossierRelationConfirmer` — tous scénarios testing §.

- [x] **T12. `CreateChildDossierDelegate` (infrastructure/flowable, bean `createChildDossierTask`)**
  - **Outcome :** lit les variables de process (`dossierId`, `childDossierTypeCode`, `childDossierLabel`, `childDossierFields`, `relationType`, `workflowProcessKey`, `childWorkflowVariables`) ; valide `relationType ∈ {PARENT_CHILD, SUBSEQUENT}` (échec explicite sinon) ; dérive `idempotencyKey = processInstanceId + ":" + activityId` ; appelle `ChildDossierRelationConfirmer` puis `ManualStartOrchestrator.start(childDossierId, workflowProcessKey, childWorkflowVariables, idempotencyKey, "workflow")`.
  - **Depends on :** T11
  - **Validation :** unitaires (mock delegate execution).
  - **Tests liés :** `CreateChildDossierDelegate` — tous scénarios testing §.

- [x] **T13. Process BPMN de test + IT bout en bout**
  - **Outcome :** fixture process BPMN dédiée (classpath test) exerçant `createChildDossierTask` ; IT vérifiant dossier enfant + relation + `DossierWorkflowLink` actif créés, et qu'un rejeu ne duplique rien.
  - **Depends on :** T12
  - **Validation :** IT Flowable verte.
  - **Tests liés :** Test d'intégration BPMN (testing § Integration Tests).

### M7 — Frontend

- [x] **T14. Client API `listRelations`/`createRelation` + types**
  - **Outcome :** extension `frontend/src/api/dossiers.ts` + types (`DossierRelation`, `RelationDirection`, `RelationType`) alignés sur le contrat JSON de T8/T9.
  - **Depends on :** T9 (contrat) — peut stubber en parallèle
  - **Validation :** Vitest client.
  - **Tests liés :** FE — client API relations.

- [x] **T15. `RelationsList.vue`**
  - **Outcome :** affichage lecture (type, direction/libellé inversé, résumé dossier lié, lien vers `/dossiers/{id}`).
  - **Depends on :** T14
  - **Validation :** Vitest composant.
  - **Tests liés :** FE — affichage, libellés par type/direction.

- [x] **T16. `LinkDossierDialog.vue`**
  - **Outcome :** formulaire de création — recherche du dossier cible via `GET /api/dossiers` (réutilise le store/composant de recherche `pagination-dossiers`), sélection `type` (5 valeurs), soumission `POST .../relations`, erreurs (400/403/404/409) affichées.
  - **Depends on :** T14
  - **Validation :** Vitest composant.
  - **Tests liés :** FE — soumission, erreurs, résultat inséré sans reload.

- [x] **T17. Intégration `DossierDetailView.vue`**
  - **Outcome :** section "Dossiers liés" (`RelationsList`) + action "Lier un dossier" (`LinkDossierDialog`, visible seulement si l'utilisateur a EDIT sur le type du dossier courant) ajoutées à côté d'`ElementsList` (voir note de découverte — page unique, pas de second point d'intégration).
  - **Depends on :** T15, T16
  - **Validation :** Vitest vue + Playwright.
  - **Tests liés :** FE — intégration vue ; E2E parcours "Lier un dossier", navigation, panneau visualisation workflow (même page).

### M8 — Preuves & docs

- [x] **T18. Suite de tests complète + couverture**
  - **Outcome :** `./mvnw test` vert (module `dossier` + `flowable`) ; `npm run test` / `npm run test:e2e` verts ; Jacoco/Vitest ≥ 80 % zones touchées ; checklist testing doc cochée ou justifiée.
  - **Depends on :** T3–T17
  - **Validation :** commandes CI locales.
  - **Tests liés :** tous scénarios testing restants.

- [x] **T19. Docs implementation + `DEFERRED-FEATURES.md`**
  - **Outcome :** notes `docs/ai/implementation/2026-08-14-feature-relations-dossiers.md` ; `DEFERRED-FEATURES.md` §1.4 (relations livrées, retirer "lot suivant") ; `analysis doc/04-dossiers.md` synchronisé si le modèle livré diverge du modèle documenté (colonne `idempotency_key` notamment, absente du doc d'origine).
  - **Depends on :** T18
  - **Validation :** `lint --feature relations-dossiers`.
  - **Tests liés :** —

## Traceability — testing → tasks

| Scénario testing | Tasks |
|---|---|
| `DossierRelation` (domaine) — auto-relation | T1 |
| `CreateDossierRelationUseCaseImpl` — ACL/404/400/409/5 types | T5, T8 |
| `ListDossierRelationsUseCaseImpl` — bidirectionnel/ACL/pas de `fields` | T6 |
| `ChildDossierRelationConfirmer` — idempotence/course | T11 |
| `CreateChildDossierDelegate` — variables/types restreints/idempotence bout en bout | T12, T13 |
| MockMvc `POST`/`GET .../relations` + régression | T9, T18 |
| Panneau `WorkflowLinkVisualizationUseCaseImpl.getDossier()` | T10 |
| IT BPMN `createChildDossierTask` | T13 |
| FE client + `RelationsList`/`LinkDossierDialog` | T14, T15, T16 |
| E2E Playwright (lier, naviguer, panneau) | T17, T18 |
| Régression Playwright existante | T18 |
| Docs `DEFERRED-FEATURES` §1.4 | T19 |

## Dependencies
**What needs to happen in what order?**

```text
T1 → T2 → T4 → T5 → T7 → T9 ┐
          T2 → T6 ─────────┼→ T9 → T14 → T15 → T17 → T18 → T19
T3 ↗                        │              T16 ↗
T2 → T4 → T11 → T12 → T13 ──┘
T6 → T7 → T10 (parallèle à T8/T9, dépend de T7)
T1 → T8 ↗
```

- T3 (Liquibase) et T1/T2 (domaine) démarrent en parallèle, aucune dépendance croisée.
- T10 (panneau visualisation workflow) peut être fait n'importe quand après T6/T7 — non bloquant pour T14–T17 (voir note de découverte).
- T14/T15/T16 peuvent démarrer dès que le contrat JSON de T8 est figé (mock axios), en parallèle de T9.
- T11–T13 (ServiceTask) sont indépendants de M4/M7 — peuvent être menés en parallèle par un second flux de travail après T4/T5.

## Timeline & Estimates
**When will things be done?**

| Milestone | Effort indicatif |
|---|---|
| M1 — Domaine | 0.5 j |
| M2 — Persistence | 0.5 j |
| M3 — Use cases manuels | 1 j |
| M4 — API REST | 0.5 j |
| M5 — Panneau workflow | 0.5 j |
| M6 — ServiceTask BPMN | 1.5–2 j |
| M7 — Frontend | 1.5 j |
| M8 — Preuves & docs | 0.5 j |
| **Total** | **~6.5–7.5 j** |

## Risks & Mitigation
**What could go wrong?**

| Risque | Mitigation |
|---|---|
| `ChildDossierRelationConfirmer` : la propagation `REQUIRED` par défaut ne joint pas la transaction si un des use cases appelés ouvre explicitement sa propre frontière (`@Transactional(propagation = REQUIRES_NEW)` caché quelque part) | Vérifier explicitement en T11 que `CreateDossierUseCaseImpl`/`CreateDossierRelationUseCaseImpl` ne déclarent aucune propagation custom ; test d'intégration dédié simulant un crash entre les deux écritures (rollback complet attendu) |
| Contrainte unique partielle (`WHERE idempotency_key IS NOT NULL`) non supportée identiquement par H2 (tests) vs PostgreSQL (prod) | Valider le changeset Liquibase sur IT H2 MODE=PostgreSQL dès T3, comme fait pour `008-dossier-list-indexes` (pagination-dossiers) |
| `ListDossierRelationsUseCaseImpl` : résolution `dossierTypeLabel` par relation (pas de batching) devient un point chaud si un dossier accumule beaucoup de relations | Accepté en V1 (hypothèse Scalabilité du design — volume faible) ; à revisiter si un dossier dépasse quelques dizaines de relations |
| `CreateChildDossierDelegate` : erreur de configuration process (variable manquante, `relationType` hors liste restreinte) découverte seulement à l'exécution | Messages d'exception explicites (T12) + IT bout en bout (T13) sur le process de test dédié |
| Frontend : `LinkDossierDialog` doit réutiliser la recherche de `pagination-dossiers` sans dupliquer sa logique | Vérifier en T16 si un composant/store de recherche est déjà extractible tel quel, sinon extraire un composable partagé avant de dupliquer |

## Resources Needed
**What do we need to succeed?**

- Worktree `feature-relations-dossiers` (déjà en place) + JDK 25 + Node FE
- Skills : `tdd`, `verify`, `vue`/`vitest`/`vue-testing-best-practices` pour M7, `playwright-best-practices` pour T13/T17/T18
- Docs requirements/design `reviewed` (Phases 1–3 closes) ; testing doc comme référence de scénarios

## Progress (Phase 6 — 2026-08-15)

T1–T19 implémentés sur `cursor/relations-dossiers-impl-5f98`. Écarts notés :
- unicité `idempotency_key` via contrainte UNIQUE (NULLs distincts) plutôt qu'index partiel `WHERE` — H2 2.4 refuse la syntaxe partielle ;
- le `ServiceTask` ouvre un enfant `DRAFT` → `OUVERT` avant `ManualStartOrchestrator` (les configs workflow existantes n'autorisent pas `DRAFT`).

Compléments Phase 5 (enchaînement) :
- IT T10 : `WorkflowApiTest.visualizationDossierPanelIncludesRealRelations` + unitaire `getDossier_includesRelationsReturnedByListUseCase` ;
- persistence : deux relations manuelles (`idempotency_key` NULL) depuis la même source ;
- E2E-14 : section « Dossiers liés » visible avec un lien workflow actif.

Preuves T18 (session) :
- `./mvnw test -Dskip.frontend=true -Dmaven.compiler.release=21` — exit 0
- `cd frontend && npm run test` — 75/75
- `cd frontend && npm run test:e2e` — 15/15 (E2E-11 locator resserré sur `strong` « Lié »)
- `npx ai-devkit@latest lint --feature relations-dossiers` — OK

Phase 7 (2026-08-15) : check implementation **reviewed**. Aligné design/requirements.
Écarts documentés (aucun majeur) : UNIQUE vs index partiel ; DRAFT→OUVERT ; SPA
n'appelle pas `GET .../workflow-links/{id}/dossier` ; `canLink` basé sur le rôle.
Preuves relancées : 640 / 75 / 15.

Phase 8 (2026-08-15) : testing **reviewed**. Gaps unitaires fermés
(validations create/list, parse ServiceTask, course confirmer, labels FE).
Jacoco zones relations 100 % (204/204) ; Vitest 78/78, coverage lines 88.32 % ;
Playwright 15/15.

Phase 9 (2026-08-15) : code review **passée**. Aucun blocker. Findings basses
documentées (409 générique, `canLink` rôle, BPMN smoke en main, N+1 V1).
Preuves relancées : 644 / 78 / 15.

Lifecycle feature `relations-dossiers` : Phases 1–9 closes. Prêt pour revue humaine.

## Sequencing notes

- Ordre **layered** : domaine → persistence → use cases manuels → API →
  (panneau workflow + ServiceTask en parallèle) → frontend → preuves.
- Après chaque tâche implémentée → Phase 6 (`dev-planning`, maj planning)
  puis reprise Phase 5 (`dev-implementation`).
- Phase 7 (Check Implementation) quand T1–T19 `[x]`.
