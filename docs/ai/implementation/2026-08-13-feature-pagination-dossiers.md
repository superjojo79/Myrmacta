---
phase: implementation
title: Implementation Guide
description: Technical implementation notes, patterns, and code guidelines
status: ready
created: 2026-08-13
updated: 2026-08-13
feature: pagination-dossiers
phase4_sync: 2026-08-13
phase5: 2026-08-13
phase6: 2026-08-13
---

# Implementation Guide

## Development Setup
**How do we get started?**

- Worktree : `.worktrees/feature-pagination-dossiers`
- Branche : `feature-pagination-dossiers`
- `JAVA_HOME` JDK 25 ; `./mvnw test` → **593** tests OK
- `cd frontend && npm run test -- --run` → **68** tests OK ; `npm run typecheck` OK

## Code Structure
**How is the code organized?**

| Zone | Fichiers clés |
|---|---|
| Domain | `SearchDossiersQuery`, `DossierListItem`, `DossierPage`, `SearchDossiersUseCase`, `DossierSearchPort`, `SearchDossiersUseCaseImpl` |
| Infra | `DossierSpecifications`, `JpaDossierSearchAdapter`, Liquibase `008-dossier-list-indexes` |
| Web | `DossierListQueryParser`, `DossierPageResponse` / `DossierListItemResponse`, `DossierController#list` |
| FE | `listDossiers`, types page/item, `SupervisionView` (état local + URL) |

## Implementation Notes
**Key technical details to remember:**

### Core Features
- T1–T12 livrés (Phase 5).
- Routage : `params=ref` prioritaire sur liste.
- Projection Criteria **sans** colonne `fields`.
- ACL mirror `ListDossierTypesUseCaseImpl`.

### Patterns & Best Practices
- Specs + `count(spec)` + multiselect Criteria.
- Labels type en batch UC (`findAll` → map).

### Deviations
- Aucune majeure vs design DQ1–DQ5.

## Integration Points
**How do pieces connect?**

- `DossierConfig` bean `SearchDossiersUseCase`.
- Supervision consomme `GET /api/dossiers` + `GET /api/dossier-types`.

## Evidence (Phase 5)

```text
./mvnw test → Tests run: 593, Failures: 0, Errors: 0 — BUILD SUCCESS
frontend npm test → 28 files / 68 tests passed
frontend npm run typecheck → OK
```

## Progress
Phases 1–6 closes côté impl. **Next :** Phase 7 Check Implementation (formelle) → Phase 8 → 9.
