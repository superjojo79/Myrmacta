---
phase: testing
title: Testing Strategy
description: Define testing approach, test cases, and quality assurance
status: completed
created: 2026-08-12
updated: 2026-08-12
feature: frontend
design_aligned: 2026-08-12
stakeholder_decisions: 2026-08-12
phase2_sync: 2026-08-12
phase3_sync: 2026-08-12
phase4_sync: 2026-08-12
---

# Testing Strategy

## Test Coverage Goals
**What level of testing do we aim for?**

- **Vitest + VTU :** stores, API helpers, DynamicFields / SensitiveValue,
  BpmnViewer, StartWorkflowDialog, Supervision, CreateDossier, admin smoke.
- **Playwright (**D-E2E**) :** session mockée ; parcours vague 1 ; smoke admin.
- **Statique :** `vue-tsc --noEmit`, ESLint, Prettier.
- **Backend delta :** Auth `id` ; `/mine` enrichi + authz ; elements DTO ;
  `./mvnw verify` (plugin FE Node 22 + Jacoco).
- **Hors CI :** Keycloak réel ; Rapports SLA ; upload éléments.

Cible coverage Vitest ≥ **80 %** lignes `frontend/src` (hors bootstrap).

## Unit Tests

### Auth (**D-ME**)
- [x] `/me` mappe `id` + `name` + roles ; 401 → redirect OAuth
- [x] getters `isTenantAdmin` / `isWorkflowAdmin`

### Axios / CSRF / base (**DD-BASE**)
- [x] defaults credentials + XSRF names
- [x] ProblemDetail → erreur typée
- [x] préfixe API cohérent avec `BASE_URL` / baseURI

### SensitiveValue (**D-SENSITIVE**)
- [x] `sensitive` + non-TA → `••••` ; TA → clair ; non-sensitive → valeur

### CreateDossier / DynamicFields (**D-ACTIONS**)
- [x] `dossierTypeCode` + `label` + required ; POST create ; ProblemDetail

### Dossier store
- [x] loadById / loadByRef ; workflows ; start
- [x] PATCH status + `expectedVersion` (**D-STATUS**)
- [x] diagram 409 sans PI
- [x] complete `{}` + reassign (no-call si non admin)

### StartWorkflowDialog (**DD-START**)
- [x] défaut = `type.workflowKey`
- [x] option autre → workflow-type-configs
- [x] header `Idempotency-Key` non blank

### Inbox (**DD-INBOX-DTO**, **DD-INBOX-AUTHZ**)
- [x] Backend assignee=sub ; skip sans dossierId ; ≤100 ; enrichit ref+linkId
- [x] FE empty state ; complete rafraîchit ; pas de `?assigneeId=`

### Elements (**DD-ELEMENTS-DTO**)
- [x] Backend sans `storageRef` ; `downloadable` ; authz
- [x] FE liste + content ; Ajouter disabled

### BpmnViewer / Notifications / Supervision
- [x] importXML + markers + destroy
- [x] EventSource + mark-read local + fail-soft
- [x] resolve ref ; pas de faux KPI

### Admin vague 2
- [x] Types/ACL/templates/connectors (`secretConfigured`)/workflow-configs smoke

### Backend MockMvc
- [x] `/api/auth/me` contient `id`
- [x] `/api/workflow-tasks/mine` (unit UC)
- [x] `/api/dossiers/{id}/elements` sans storageRef

## Integration Tests

- [x] Vitest + msw/vi.mock store↔api
- [x] Optionnel : `GET /` → `index.html` (SPA fallback)

## End-to-End Tests (Playwright, session mock)

- [x] **E2E-1** Boot (`id` présent)
- [x] **E2E-2** Créer dossier → détail
- [x] **E2E-3** PATCH status
- [x] **E2E-4** Start + Idempotency-Key + diagramme
- [x] **E2E-5** Complete `{}`
- [x] **E2E-6** Reassign admin vs manager
- [x] **E2E-7** Inbox `/mine`
- [x] **E2E-8** SSE panneau
- [x] **E2E-9** Nav sans Rapports
- [x] **E2E-10** Admin create dossier-type

## Test Data / Reporting

Fixtures DTO + BPMN minimal ; user `{ id, name, email, roles }`.

```bash
cd frontend && npm run typecheck && npm run lint && npm run test -- --coverage
cd frontend && npm run test:e2e
./mvnw verify -Dmaven.compiler.release=21
```

## Manual / Perf

Maquette, mobile, context-path (**DD-BASE**), Keycloak hors CI, secrets
admin. Perf hors gate ; inbox ≤ 100.

## Traceability

| Décision | Tests |
|---|---|
| D-ME | Auth + E2E-1 |
| D-ACTIONS / D-STATUS / D-COMPLETE / DD-START | E2E-2..5 |
| DD-INBOX-* | `/mine` + E2E-7 |
| DD-ELEMENTS-DTO | MockMvc + FE |
| D-SENSITIVE / D-MOCKGAP | SensitiveValue ; E2E-9 |
| D-ADMIN / A-CONN-SECRET | Admin ; E2E-10 |
| DD-NODE / D-PACK / D-E2E | verify Node 22 + Playwright |
