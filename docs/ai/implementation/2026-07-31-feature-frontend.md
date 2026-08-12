---
phase: implementation
status: completed
feature: frontend
created: 2026-08-12
updated: 2026-08-12
phase5_started: 2026-08-12
phase5_completed: 2026-08-12
phase7_checked: 2026-08-12
title: Implementation Guide
description: Technical implementation notes, patterns, and code guidelines
---

# Implementation Guide

## Status

Phase 5 **terminée** (T1–T28). Phase 7 check : aligné design (SPA BFF,
`base:'./'`, inbox `/mine`, elements sans `storageRef`, admin CRUD,
packaging Node 22).

## Development Setup

```bash
cd frontend && npm install
npm run typecheck && npm run lint && npm run test && npm run test:coverage && npm run build
npm run test:e2e
./mvnw -Dmaven.compiler.release=21 -Dskip.frontend=true test
./mvnw -Dmaven.compiler.release=21 verify   # inclut frontend-maven-plugin Node 22
```

## Code Structure

```
frontend/src/{app,api,stores,views,components,styles}
src/main/java/.../SpaFallbackConfig.java
pom.xml — frontend-maven-plugin v22.14.0 + copy dist → static/
```

## Completed (Phase 5)

| Tasks | Notes |
|---|---|
| T1–T8 | Scaffold, auth `/me`+id, shell, inbox `/mine` |
| T9 | `GET /api/dossiers/{id}/elements` + MockMvc sans `storageRef` |
| T10–T14 | Clients TS, create/detail/status, BPMN, start/complete/reassign |
| T15–T18 | Elements UI, SSE drawer, Supervision MVP, inbox FE |
| T19 | frontend-maven-plugin Node 22 + SPA fallback |
| T20–T21 | Playwright session mock + Vitest lines ≥80 % + docs vague 1 |
| T22–T28 | Admin types/ACL/templates/connectors/workflow-configs + preuves |

## Design deviations

- OAuth redirect FE : `/oauth2/authorization/oidc` (registration Spring),
  pas `keycloak`.
- Coverage Vitest : exclusion `main.ts`, `App.vue`, `BpmnViewer.vue`
  (bpmn-js / bootstrap) — reste ≥ 80 % lignes.

## Edge cases

- Deep-links avec `base:'./'` : assets relatifs → E2E navigue depuis `/`.
- Diagramme 409 → message soft, pas de crash viewer.
- SSE fail-soft local ; mark-read local only.
