---
phase: implementation
status: reviewed
feature: frontend
created: 2026-08-12
updated: 2026-08-12
phase5_started: 2026-08-12
phase5_completed: 2026-08-12
phase6_synced: 2026-08-12
phase7_checked: 2026-08-12
phase8_tested: 2026-08-12
phase9_reviewed: 2026-08-12
title: Implementation Guide
description: Technical implementation notes, patterns, and code guidelines
---

# Implementation Guide

## Status

Phases **5–9** closes. Implémentation T1–T28 livrée ; Phase 7 check
d’alignement **OK** ; Phase 8 tests OK ; Phase 9 review **prêt à
merger** après correctifs (voir ci-dessous).

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

## Phase 9 — Code Review (2026-08-12)

**Verdict :** prêt à merger (PR #38). Blockers corrigés ; follow-ups
non bloquants documentés.

### Checklist finale

| Critère | Statut |
|---|---|
| Design match (D-* / DD-*) | OK |
| Pas de trou logique bloquant | OK après soft-fail viz + logout form |
| Sécurité (BFF, CSRF logout, guard admin FE) | OK (API reste source de vérité) |
| Points d’intégration SPA / Maven / SSE | OK |
| Tests couvrent les changements | OK (voir evidence) |
| Docs à jour | OK |

### Findings phase 9

| Sévérité | ID | Issue | Action |
|---|---|---|---|
| Blocking | REV-VIZ | `loadVisualization` soft-failait seulement le 409 → 403/5xx cassait le détail | **Corrigé** soft-fail toute erreur |
| Important | REV-AUTH | Redirect OIDC sur toute erreur `/me` | **Corrigé** 401/403 seulement + `bootError` |
| Important | REV-BASE | OAuth/logout sans préfixe `DD-BASE` | **Corrigé** `tenantPath` + `resolveApiBaseUrl` |
| Important | REV-LOGOUT | Logout XHR ne suivait pas la chaîne OIDC | **Corrigé** form POST + CSRF cookie |
| Important | REV-ROUTE | Pas de `watch` sur `route.params.id` | **Corrigé** reload détail |
| Important | REV-BPMN | BPMN vide sans `linkId` | **Corrigé** `:unavailable` |
| Important | REV-ADMIN | Routes admin sans guard FE | **Corrigé** `meta.roles` + `beforeEach` |
| Nice | REV-ENC | `elementId` non encodé dans URL | **Corrigé** `encodeURIComponent` |
| Nice | REV-SSE-CAP | Inbox notifs non bornée | **Corrigé** cap 100 |
| Follow-up | REV-SENS | Redaction serveur champs sensitive | Hors V1 FE |
| Follow-up | REV-VERIFY | `./mvnw verify` sans `-Dskip.frontend` en CI | Packaging / CI |

### Correctifs phase 9

- `stores/auth.ts` : redirect 401/403, `tenantPath`, logout form+CSRF, `bootError`
- `stores/dossier.ts` : soft-fail visualisation pour toute erreur
- `DossierDetailView.vue` : `watch` params + BPMN unavailable sans link
- `routes.ts` + `main.ts` : guard rôles admin
- `api/elements.ts` : encode URI ; `notifications.ts` : cap 100

### Evidence (phase 9)

```text
cd frontend && npm run typecheck && npm run lint && npm run test
→ 65 passed / 28 files, exit 0
npm run test:coverage → lines 90.96 % (≥ 80 %)
npm run test:e2e → 11 passed
JAVA_HOME=…/jdk-25.0.4+7 ./mvnw -Dskip.frontend=true \
  -Dtest=SpaFallbackConfigTest,AuthControllerTest,DossierApiTest,\
ListDossierElementsUseCaseImplTest,ListMyTasksUseCaseImplTest test
→ Failures: 0, Errors: 0
npx ai-devkit@latest lint --feature frontend → All checks passed
```

### Follow-ups post-merge

- Redaction API champs sensitive (serveur)
- CI `./mvnw verify` sans skip.frontend pour peupler `static/`
- Checklist manuelle context-path `/{tenantId}/` (DD-BASE)
- Guard FE admin : course possible avant `init()` `/me` (API protège)

**Lifecycle :** phase 9 terminée — prêt à merger.
