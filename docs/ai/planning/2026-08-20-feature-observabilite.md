---
phase: planning
title: Project Planning & Task Breakdown
description: Break down work into actionable tasks and estimate timeline
status: draft
created: 2026-08-20
updated: 2026-08-20
feature: observabilite
phase1: 2026-08-20
phase3: 2026-08-20
---

# Project Planning & Task Breakdown — `observabilite`

> Squelette. **Remplir en Phase 4** maintenant que le design est
> **reviewed**. Ne pas implémenter depuis cette page.

## Milestones (prévisionnel — détailler Phase 4)

- [ ] M1 — Actuator port management + logs ECS + MDC `tenantId`
- [ ] M2 — Ports lecture + binders (3 âges, Flowable ×2, PDF, webhook
      DEAD, SSE)
- [ ] M3 — Q1 audit lecture `SECURITY_AUDIT`
- [ ] M4 — `GET /api/observability` + SPA `/admin/observabilite`
- [ ] M5 — ArchUnit + Vitest + Playwright + OpenAPI

## Task Breakdown

À détailler en Phase 4 (TDD, fichiers, commandes `verify`). Design
figé : `docs/ai/design/2026-08-20-feature-observabilite.md`.

## Dependencies

- Q1–Q6 actées (Phase 2).
- A-* figés Phase 3 (DD-JSON=ecs, DD-PROM-NAMES, DD-FLOWABLE-API,
  DD-THRESHOLDS, DD-EMPTY-AGE, DD-HEALTH, DD-INDEX-NOTIF).

## Timeline & Estimates

Pas d’estimation calendaire. Enchaînement : 3 → **4** → 5…

## Risks & Mitigation

| Risque | Mitigation |
|---|---|
| Prometheus vs OIDC BFF | **Q2 = A** + chaîne Order 0 |
| Fuite MDC thread pool | `MdcScope` try/finally + tests |
| Cardinalité labels | pas de `dossierId` / `tenant` Prometheus |
| API Flowable 8 vs javadoc | DD-FLOWABLE-API ; vérifier `ExecuteAsyncRunnable` en TDD |

## Resources Needed

- Design reviewed, testing, monitoring.
- Tests existants `SecurityAuditLogTest`, `SseNotificationAdapterTest`,
  `WorkflowPersistenceTest`, `OpenApiEnabledApiTest`.
