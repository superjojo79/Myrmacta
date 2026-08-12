---
phase: implementation
status: in-progress
feature: frontend
created: 2026-08-12
updated: 2026-08-12
phase5_started: 2026-08-12
title: Implementation Guide
description: Technical implementation notes, patterns, and code guidelines
---

# Implementation Guide

## Status

Phase 5 en cours. Complété : **T1–T8**. Suivant : **T9** (elements list API).

## Development Setup

```bash
cd frontend && npm install
npm run dev          # HMR
npm run typecheck && npm run lint && npm run test && npm run build
./mvnw -Dmaven.compiler.release=21 -Dtest=AuthControllerTest test
```

Node **22+**. JDK env cloud = 21 → toujours `-Dmaven.compiler.release=21`.

## Code Structure

```
frontend/src/{app,api,stores,views,components,styles}
```

## Completed (Phase 5)

| Task | Notes |
|---|---|
| T1 | Vite vue-ts, `base: './'`, arborescence design |
| T2 | PrimeVue 5, Pinia, Axios, vue-router, bpmn-js, Vitest, Playwright, ESLint |
| T3 | Scripts gates + README ; smoke `scaffold.spec.ts` |
| T4 | `api/http.ts` CSRF + `resolveApiBaseUrl` (DD-BASE) |
| T5 | `AuthUserResponse(id,name,email,roles)` + AuthControllerTest |
| T6 | Pinia `auth` store + boot `init()` |

## Design deviations

- Aucune majeure. `@primevue/themes` npm marque deprecated → migration
  `@primeuix/themes` prévue à T7 si besoin.

## Edge cases

- Auth 401 → redirect Keycloak.
- Context-path : premier segment de `baseURI` comme préfixe Axios.

## Next

T7 shell + vue-router ; puis T8/T9 backend inbox/elements.
