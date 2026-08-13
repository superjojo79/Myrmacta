---
phase: implementation
title: Implementation Guide
description: Technical implementation notes, patterns, and code guidelines
status: checked
created: 2026-08-13
updated: 2026-08-13
feature: pagination-dossiers
phase4_sync: 2026-08-13
phase5: 2026-08-13
phase6: 2026-08-13
phase7: 2026-08-13
phase8: 2026-08-13
checked: 2026-08-13
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

## Implementation Notes
**Key technical details to remember:**

- T1–T12 livrés ; DQ1–DQ5 respectés.
- Routage : `params=ref` prioritaire sur liste.
- Projection Criteria **sans** colonne `fields`.

## Progress
Phases 1–8 closes (`testing` status `verified` 2026-08-13). **Next :** Phase 9 Code Review (`dev-review`) + PR. Manual UX smoke reste checklist QA.
