---
phase: implementation
title: Implementation Guide
description: Technical implementation notes, patterns, and code guidelines
status: ready
created: 2026-08-13
updated: 2026-08-13
feature: pagination-dossiers
phase4_sync: 2026-08-13
---

# Implementation Guide

## Development Setup
**How do we get started?**

- Worktree : `.worktrees/feature-pagination-dossiers`
- Branche : `feature-pagination-dossiers`
- JDK 25 + `./mvnw test`
- Frontend : `cd frontend && npm ci && npm run test`

## Code Structure
**How is the code organized?**

Voir design Phase 3 (`SearchDossiersUseCase`, `DossierSearchPort`,
`JpaDossierSearchAdapter` + Specs, `DossierController` list mapping,
`SupervisionView` état local).

## Implementation Notes
**Key technical details to remember:**

### Core Features
- Planning T1–T12 (Phase 4) — not started.
- DQ1 Specs ; DQ4 projection sans `fields` ; DQ2 labels UC ; DQ3 indexes
  `008-dossier-list-indexes` ; DQ5 FE local state.

### Patterns & Best Practices
- Mirror ACL de `ListDossierTypesUseCaseImpl`.
- Ne pas casser `GET /api/dossiers?ref=`.
- TDD sur UC (T5) et Specs/IT (T4) avant API large.

## Integration Points
**How do pieces connect?**

- `DossierConfig` beans ; Liquibase master include 008 ;
  `api/dossiers.listDossiers` → Supervision.

## Progress
Phases 1–4 docs closes. **Next :** Phase 5 — Execute Plan (T1).
