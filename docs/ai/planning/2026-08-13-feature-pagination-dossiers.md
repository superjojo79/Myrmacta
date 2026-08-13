---
phase: planning
title: Project Planning & Task Breakdown
description: Break down work into actionable tasks and estimate timeline
status: completed
created: 2026-08-13
updated: 2026-08-13
feature: pagination-dossiers
design_aligned: 2026-08-13
phase3_sync: 2026-08-13
phase4: 2026-08-13
phase5: 2026-08-13
phase6: 2026-08-13
sequencing: layered
---

# Project Planning & Task Breakdown

## Milestones
**What are the major checkpoints?**

- [x] **M1 — Domaine** : records + ports in/out search (TDD)
- [x] **M2 — Persistence** : Specs + projection adapter + indexes Liquibase
- [x] **M3 — Use case** : ACL VIEW + batch labels + wiring `DossierConfig`
- [x] **M4 — API REST** : `GET /api/dossiers` liste + validation params
- [x] **M5 — Frontend** : `listDossiers` + Supervision liste/filtres/URL
- [x] **M6 — Preuves & docs** : IT/MockMvc/Vitest verts ; deferred/TODO notes

## Task Breakdown
**What specific work needs to be done?**

Statuts : `[ ]` not started · `[~]` in progress · `[x]` done · `[!]` blocked

### M1 — Domaine

- [x] **T1. Records domaine search**
  - **Outcome :** `SearchDossiersQuery`, `DossierListItem`, `DossierPage`
    (fields D-ITEM ; `dossierTypeLabel` rempli plus tard par UC).
  - **Depends on :** —
  - **Validation :** compile ; ArchUnit domaine pur.
  - **Tests liés :** ArchUnit.

- [x] **T2. Ports `SearchDossiersUseCase` + `DossierSearchPort`**
  - **Outcome :** interfaces `port/in` + `port/out` selon design DQ4
    (filter + page/size → `DossierPage` items sans label type).
  - **Depends on :** T1
  - **Validation :** compile.
  - **Tests liés :** —

### M2 — Persistence

- [x] **T3. Liquibase `008-dossier-list-indexes`**
  - **Outcome :** 3 indexes DQ3 sur `dossier` ; include dans
    `db.changelog-master.yaml`.
  - **Depends on :** —
  - **Validation :** Liquibase IT / boot test schema.
  - **Tests liés :** IT schema / search.

- [x] **T4. Specs + `JpaDossierSearchAdapter` (projection)**
  - **Outcome :** `SpringDataDossierRepository` étendu
    `JpaSpecificationExecutor` ; Specs status / type-in / reference
    prefix CI / tenant ; `count(spec)` + query projetée **sans** colonne
    `fields` ; tri `updatedAt desc` ; mapping → `DossierListItem`
    (`dossierTypeLabel=""`).
  - **Depends on :** T2, T3
  - **Validation :** IT persistence search.
  - **Tests liés :** Unit/IT Specs — status, type, prefix CI, totals,
    pagination, no-fields projection.

### M3 — Use case

- [x] **T5. `SearchDossiersUseCaseImpl` + tests**
  - **Outcome :** mirror ACL `ListDossierTypes` (admin bypass, VIEW set,
    Service → deny, short-circuit vide, filter type hors ACL → vide) ;
    normalize `reference` blank ; appel port ; batch labels via
    `DossierTypeRepository.findAll` (map code→label).
  - **Depends on :** T2, T4
  - **Validation :** unitaires UC verts.
  - **Tests liés :** Unit — SearchDossiersUseCaseImpl (tous scénarios
    testing § UC).

- [x] **T6. Wiring `DossierConfig`**
  - **Outcome :** beans `DossierSearchPort` + `SearchDossiersUseCase`.
  - **Depends on :** T4, T5
  - **Validation :** contexte Spring monte (IT existante / smoke).
  - **Tests liés :** IT boot.

### M4 — API REST

- [x] **T7. DTOs + parsing validation query**
  - **Outcome :** `DossierListItemResponse`, `DossierPageResponse` ;
    helper parse `page`/`size`/`sort`/`status`/`dossierTypeCode`/
    `reference` (D-VALID / D-PAGE / D-PARAMS) → 400 ProblemDetail.
  - **Depends on :** T1
  - **Validation :** unit parse ou MockMvc.
  - **Tests liés :** Validation — page/size/sort/status.

- [x] **T8. `DossierController` mapping liste**
  - **Outcome :** `@GetMapping` sans `ref` → liste ; `params=ref`
    inchangé ; auth via `AccessContextFactory.user` ; envelope JSON
    D-ITEM.
  - **Depends on :** T5, T6, T7
  - **Validation :** MockMvc.
  - **Tests liés :** MockMvc — 200 envelope, ACL vide, type hors ACL,
    400, 401, 403 Service si exercable, régression `?ref=`,
    `?ref=&page=` → get-by-ref, JSON sans `fields`.

### M5 — Frontend

- [x] **T9. Client `listDossiers` + types**
  - **Outcome :** types envelope/item ; `listDossiers(params)` dans
    `api/dossiers.ts` (params D-PARAMS).
  - **Depends on :** T8 (contrat) — peut stubber en parallèle
  - **Validation :** Vitest client.
  - **Tests liés :** FE — listDossiers params.

- [x] **T10. `SupervisionView` liste + filtres + URL**
  - **Outcome :** tableau paginé ; filtres status/type/reference ;
    sync URL (R5) ; état **local** (DQ5) ; clic → `/dossiers/{id}` ;
    formulaire ouvrir-par-ref/UUID conservé ; select types via API
    types existante.
  - **Depends on :** T9
  - **Validation :** Vitest Supervision.
  - **Tests liés :** FE — display, URL replace/hydrate, navigation,
    open-by-ref intact.

### M6 — Preuves & docs

- [x] **T11. Suite tests + couverture**
  - **Outcome :** `./mvnw test` vert ; `frontend` Vitest (+ coverage)
    sans régression seuil ; gaps testing doc cochés ou justifiés.
  - **Depends on :** T3–T10
  - **Validation :** commandes CI locales.
  - **Tests liés :** tous scénarios testing restants.

- [x] **T12. Docs implementation + deferred**
  - **Outcome :** notes `implementation` ; maj
    `DEFERRED-FEATURES.md` / note TODO §24 (tranche liste) ;
    checklist manuelle smoke.
  - **Depends on :** T11
  - **Validation :** lint `--feature pagination-dossiers`.
  - **Tests liés :** —

## Traceability — testing → tasks

| Scénario testing | Tasks |
|---|---|
| UC ACL / labels / Service | T5 |
| Specs / projection no-fields / IT filters | T4, T11 |
| Validation query params | T7, T8 |
| MockMvc liste + régression `ref` | T8, T11 |
| FE listDossiers + Supervision URL | T9, T10, T11 |
| Indexes présents | T3, T12 |
| Docs deferred §24 | T12 |

## Dependencies
**What needs to happen in what order?**

```text
T1 → T2 → T4 → T5 → T6 → T8
     T3 ↗
T1 → T7 ↗
T8 → T9 → T10 → T11 → T12
```

- T9/T10 peuvent démarrer dès que le contrat JSON T7/T8 est figé
  (mock axios).
- Pas de dépendance externe IdP/MinIO.

## Timeline & Estimates
**When will things be done?**

| Milestone | Effort indicatif |
|---|---|
| M1 | 0.5 j |
| M2 | 1 j |
| M3 | 0.5–1 j |
| M4 | 0.5–1 j |
| M5 | 1 j |
| M6 | 0.5 j |
| **Total** | **~4–5 j** |

## Risks & Mitigation
**What could go wrong?**

| Risque | Mitigation |
|---|---|
| Specs + projection Criteria délicate sur H2 | IT early T4 ; fallback query explicite colonnes |
| Collision mapping Spring `ref` vs liste | Tests MockMvc `?ref=&page=` dès T8 |
| JSON `fields` chargé par erreur | Assert projection / absence clé dans MockMvc |
| FE casse open-by-ref | Garder form + tests dédiés T10 |
| Index DESC syntax H2/Liquibase | Valider changeset sur IT H2 MODE=PostgreSQL |

## Resources Needed
**What do we need to succeed?**

- Worktree `feature-pagination-dossiers` + JDK 25 + Node FE
- Skills : `tdd`, `verify`, `vue` / `vitest` pour M5
- Docs requirements/design/testing `reviewed`/`aligned`

## Sequencing notes

- Ordre **layered** : domaine → persistence → UC → API → FE → preuves.
- Après chaque tâche implémentée → Phase 6 (maj planning) puis reprise
  Phase 5.
- Phase 7 quand T1–T12 done.
