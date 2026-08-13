---
phase: implementation
title: Implementation Guide
description: Technical implementation notes, patterns, and code guidelines
status: reviewed
created: 2026-08-13
updated: 2026-08-13
feature: pagination-dossiers
phase4_sync: 2026-08-13
phase5: 2026-08-13
phase6: 2026-08-13
phase7: 2026-08-13
phase8: 2026-08-13
phase9: 2026-08-13
phase9_result: ready-with-followups
checked: 2026-08-13
reviewed: 2026-08-13
alignment: aligned
---

# Implementation Guide

## Development Setup
**How do we get started?**

- Worktree : `.worktrees/feature-pagination-dossiers`
- Branche : `feature-pagination-dossiers`
- `JAVA_HOME` JDK 25 ; `./mvnw test`
- `cd frontend && npm run test -- --run` ; `npm run typecheck`

## Code Structure
**How is the code organized?**

| Zone | Fichiers clés |
|---|---|
| Domain | `SearchDossiersQuery`, `DossierListItem`, `DossierPage`, `SearchDossiersUseCase`, `DossierSearchPort`, `SearchDossiersUseCaseImpl` |
| Infra | `DossierSpecifications`, `JpaDossierSearchAdapter`, Liquibase `008-dossier-list-indexes` |
| Web | `DossierListQueryParser`, `DossierPageResponse` / `DossierListItemResponse`, `DossierController#list` |
| FE | `listDossiers`, types page/item, `SupervisionView` (état local + URL) |

## Phase 7 — Check Implementation (2026-08-13)

Revue fichier-par-fichier vs design/requirements `reviewed` : **ALIGNED**.

| Checkpoint | Résultat |
|---|---|
| UC ACL / labels / Service deny | aligned |
| Port Filter null vs restricted | aligned |
| Specs + projection sans `fields` + tri | aligned |
| Liquibase 008 (3 indexes) | aligned |
| Parser D-VALID / D-PAGE | aligned |
| Controller list + `ref` precedence + DTOs | aligned |
| `DossierConfig` wiring | aligned |
| FE list + Supervision URL + open-by-ref | aligned |
| Tests | aligned (MockMvc D-VALID page/size/sort ajoutés en Phase 7) |

### Deviations / notes (non bloquant)

| Sévérité | Note | Suivi |
|---|---|---|
| important (accepté) | Controller appelle toujours `AccessContextFactory.user()` — deny Service couvert en unitaire UC ; M2M hors `/api/dossiers` (chaîne séparée) | Aligné pattern types ; pas de MockMvc 403 Service sur liste |
| nice | Labels via `findAll` types (pas N+1) | OK V1 |
| nice | Tie-break `id ASC` en plus de `updatedAt DESC` | Stable, compatible |
| nice | Supervision `onMounted` + `watch(query)` peut double-fetch | Follow-up UX |

### Preuves fraîches

```text
./mvnw -Dtest=DossierApiTest#listDossiers_invalidPageOrSizeOrSort_returns400 test → BUILD SUCCESS
Phase 5 baseline : ./mvnw test → 593 OK ; frontend 68 OK
```

**Phase 7 close** — implementation `checked` / `aligned`.

## Phase 9 — Code Review (2026-08-13)

**Résultat :** READY to merge with follow-ups (`phase9_result: ready-with-followups`).

### Design alignment

| Intent (design) | Impl | Verdict |
|---|---|---|
| UC + ACL VIEW + empty page / Service 403 | `SearchDossiersUseCaseImpl` | match |
| Specs + Criteria projection sans `fields` | `JpaDossierSearchAdapter` multiselect | match |
| Indexes Liquibase 008 | changelog master + IT | match |
| Parser D-VALID / envelope D-PAGE | `DossierListQueryParser` + DTOs | match |
| `?ref=` wins over list | dual `@GetMapping` + MockMvc | match |
| Supervision local state + URL sync + open-by-ref | `SupervisionView.vue` | match |

### Blocking — fixed in Phase 9

| # | Finding | Fix |
|---|---|---|
| B1 | Prefixe `reference` : `%` / `_` traités comme wildcards LIKE | Escape Criteria `like(..., '\\')` + test IT littéral `DOS-%WILD` |
| B2 | `Filter.restricted` acceptait `Set.of()` (Javadoc non-empty) | Guard `IllegalArgumentException` + test |

### Important follow-ups (non-blocking)

| # | Item | Recommendation |
|---|---|---|
| I1 | `SupervisionView` `syncRoute` + `watch(route.query)` peut double-fetch | Charger uniquement via watch, ou flag skip |
| I2 | Labels types via `findAll` | Batch `findByCodes` sur la page (perf catalogue large) |
| I3 | Manual UX smoke (clavier / deep-link / Network sans `fields`) | Checklist QA |

### Nice-to-have

- Accessibilité clavier sur lignes du tableau
- Escape LIKE documenté dans design (comportement littéral explicite)

### Evidence (fresh Phase 9)

```text
JAVA_HOME=/tmp/jdk/jdk-25.0.4+7 ./mvnw test
→ Tests run: 596, Failures: 0, Errors: 0, Skipped: 0
cd frontend && npm run test -- --run → 28 files / 68 tests passed
npm run typecheck → exit 0
npx ai-devkit@latest lint --feature pagination-dossiers → All checks passed
task CLI : unavailable (unknown command 'task')
```

**Phase 9 close** — implementation `reviewed` ; prêt PR / merge.

## Implementation Notes
**Key technical details to remember:**

- T1–T12 livrés ; DQ1–DQ5 respectés.
- Routage : `params=ref` prioritaire sur liste.
- Projection Criteria **sans** colonne `fields`.
- Prefixe référence = littéral (LIKE metacharacters échappés).

## Progress
Phases 1–9 closes. Feature READY (`ready-with-followups`). Manual UX smoke reste checklist QA.
