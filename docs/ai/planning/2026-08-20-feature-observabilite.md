---
phase: planning
title: Project Planning & Task Breakdown
description: Break down work into actionable tasks and estimate timeline
status: draft
created: 2026-08-20
updated: 2026-08-20
feature: observabilite
phase1: 2026-08-20
---

# Project Planning & Task Breakdown — `observabilite`

> Squelette Phase 1. **Remplir en Phase 4** (`dev-planning`) après
> requirements **reviewed** et design **reviewed**. Ne pas implémenter
> depuis cette page.

## Milestones (prévisionnel)

- [ ] M1 — Actuator + logs JSON + MDC `tenantId`
- [ ] M2 — Cinq métriques §29
- [ ] M3 — Q1 (audit lecture) selon décision Phase 2
- [ ] M4 — SPA `/admin/observabilite` + GET snapshot (D-FE)
- [ ] M5 — Tests + ArchUnit + Vitest + Playwright

## Task Breakdown

À détailler en Phase 4 (TDD, fichiers, commandes `verify`).

## Dependencies

- Q1–Q6 **actées** (Phase 2). Design Phase 3 : noms métriques, seuils,
  API Flowable, format JSON logs.

## Timeline & Estimates

Pas d’estimation calendaire. Enchaînement : 2 → 3 → 4 → 5…

## Risks & Mitigation

| Risque | Mitigation |
|---|---|
| Prometheus vs OIDC BFF | **Q2 = A** (port management) |
| Fuite MDC thread pool jobs | try/finally + tests |
| Cardinalité labels | pas de `dossierId` en label Prometheus |

## Resources Needed

- Docs §29 / FILE-ATTENTE.
- Tests existants `SecurityAuditLogTest`, `SseNotificationAdapterTest`,
  `WorkflowPersistenceTest`.
