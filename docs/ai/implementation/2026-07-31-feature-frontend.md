---
phase: implementation
status: completed
feature: frontend
created: 2026-08-12
updated: 2026-08-12
phase5_started: 2026-08-12
phase5_completed: 2026-08-12
phase6_synced: 2026-08-12
phase7_checked: 2026-08-12
title: Implementation Guide
description: Technical implementation notes, patterns, and code guidelines
---

# Implementation Guide

## Status

Phases **5–7** closes. Implémentation T1–T28 livrée ; Phase 7 check
d’alignement design/requirements **OK** après correctifs ciblés
(SensitiveValue, route admin, templates lifecycle, SSE reconnect,
connector-definitions).

## Development Setup

```bash
cd frontend && npm install
npm run typecheck && npm run lint && npm run test && npm run test:coverage && npm run build
npm run test:e2e
./mvnw -Dmaven.compiler.release=21 -Dskip.frontend=true test
./mvnw -Dmaven.compiler.release=21 verify   # Node 22 via frontend-maven-plugin
```

Node **22+**. JDK env cloud = 21 → `-Dmaven.compiler.release=21`.
` -Dskip.frontend=true` pour tests Java sans rebuild SPA.

## Code Structure

```
frontend/src/{app,api,stores,views/{pilotage,admin},components/{dossier,workflow,notifications},styles}
domain.dossier — ListDossierElementsUseCase (+ DossierElementView)
domain.workflow — ListMyTasksUseCase (+ MyWorkflowTask)
infrastructure.web — SpaFallbackConfig ; DossierController.elements ; WorkflowTaskController.mine
pom.xml — frontend-maven-plugin Node 22.14.0 → target/.../static/
```

## Phase 7 — Check Implementation

### Design summary (référence)

SPA Vue/TS embarquée BFF ; `base:'./'` ; `/me`+`id` ; inbox `/mine` ;
elements sans `storageRef` ; start + Idempotency-Key ; complete `{}` ;
SSE ; Supervision sans KPI ; admin vague 2 ; packaging Node 22 + SPA
fallback.

### Alignement fichier / intent

| Zone | Intent design | Statut |
|---|---|---|
| Auth / CSRF / oidc redirect | D-ME, BFF | OK (`/oauth2/authorization/oidc`) |
| Shell sans Rapports | D-MOCKGAP | OK |
| Create / detail / status / viz | D-ACTIONS, D-STATUS | OK |
| Start + Idempotency-Key | DD-START | OK |
| Complete `{}` / reassign TA\|WA | D-COMPLETE | OK |
| Elements DTO + UI disabled Add | DD-ELEMENTS, D-ELEMENTS | OK |
| Inbox `/mine` | DD-INBOX-* | OK |
| SSE fail-soft + reconnect | NFR | OK (reconnect 5s) |
| Sensitive mask sauf TA | D-SENSITIVE | OK (reveal retiré en P7) |
| Admin types/ACL/templates/connectors/WF | D-ADMIN-RES | OK (activate/archive/new-version + definitions) |
| Route WF admin | `/admin/workflow-types` | OK (renommé en P7) |
| Packaging Node 22 + SPA fallback | D-PACK, A-SPA | OK |
| Qualité Vitest ≥80 % / E2E mock | D-E2E | OK (86 % lines ; 6 E2E) |

### Deviations (sévérité)

| Écart | Sévérité | Traitement |
|---|---|---|
| Stores `admin/*` absents (vues appellent API) | Faible | Accepté — moins d’indirection V1 |
| `observerUserIds` non édités en formulaire dédié (CRUD config basique) | Faible | Follow-up UX |
| `./mvnw verify` **avec** FE non exécuté dans cette session cloud (long download Node) | Moyenne | Plugin présent ; preuves FE via npm ; Java via `-Dskip.frontend` |
| Coverage exclut `main.ts` / `App.vue` / `BpmnViewer.vue` | Faible | Documenté ; seuil lignes reste ≥80 % |

### Preuves fraîches (Phase 7)

- `npm run typecheck` → exit 0
- `npm run lint` → exit 0
- `npm run test` → 53 passed
- `npm run test:coverage` → lines **86.14 %**
- `npm run test:e2e` → 6 passed
- `./mvnw … -Dtest=ListDossierElementsUseCaseImplTest,ListMyTasksUseCaseImplTest,AuthControllerTest,SpaFallbackConfigTest,DossierApiTest#listElements_returnsViewsWithoutStorageRef test` → exit 0

### Follow-ups

1. Exécuter `./mvnw verify` (sans skip.frontend) en CI pour peupler `static/`.
2. Checklist manuelle context-path `/{tenantId}/` (DD-BASE).
3. Édition riche `observerUserIds` / activate-archive UX polish.

## Completed (Phase 5)

| Tasks | Notes |
|---|---|
| T1–T8 | Scaffold, auth id, shell, inbox `/mine` |
| T9–T18 | Elements API+UI, dossier cœur, SSE, supervision, tasks |
| T19–T21 | Maven Node 22, E2E, evidence vague 1 |
| T22–T28 | Admin + preuves ; P7 correctifs alignement |

## Next

Phase 8 — `dev-testing` (couverture / scénarios) puis Phase 9 — `dev-review`.
