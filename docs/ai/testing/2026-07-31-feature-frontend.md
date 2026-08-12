---
phase: testing
title: Testing Strategy
description: Define testing approach, test cases, and quality assurance
status: draft
created: 2026-08-12
updated: 2026-08-12
feature: frontend
design_aligned: 2026-08-12
stakeholder_decisions: 2026-08-12
---

# Testing Strategy

## Test Coverage Goals
**What level of testing do we aim for?**

- **Vitest + VTU :** stores, API helpers, DynamicFields / SensitiveValue,
  BpmnViewer (importXML mock), Supervision, CreateDossier, admin forms
  smoke.
- **Playwright (**D-E2E = A**) :** session mockée (stub `/api/auth/me`
  avec `id`) ; parcours vague 1 ; smoke vague 2 admin.
- **Statique :** `vue-tsc --noEmit`, ESLint, Prettier.
- **Backend delta :** tests `AuthController` / `AuthUserResponse` pour
  champ `id` ; `./mvnw verify` (plugin FE + Jacoco existant).
- **Hors CI :** Keycloak réel ; Rapports SLA ; upload éléments.

Cible coverage Vitest ≥ **80 %** lignes `frontend/src` (hors bootstrap).

## Unit Tests

### Auth (**D-ME**)
- [ ] `/me` mappe `id` + roles ; 401 → redirect OAuth
- [ ] getters `isTenantAdmin` / `isWorkflowAdmin`

### Axios / CSRF
- [ ] defaults credentials + XSRF names
- [ ] ProblemDetail → erreur typée

### SensitiveValue (**D-SENSITIVE**)
- [ ] `sensitive` + non-TA → `••••`
- [ ] TA → valeur claire
- [ ] non-sensitive → valeur

### CreateDossier / DynamicFields (**D-ACTIONS**)
- [ ] charge types ; valide required ; POST create
- [ ] erreur ProblemDetail affichée

### Dossier store
- [ ] loadById / loadByRef ; workflows ; start workflow
- [ ] diagram 409 sans PI
- [ ] complete + reassign (reassign no-call si non admin)

### Elements (**D-ELEMENTS**)
- [ ] liste + lien content ; bouton Ajouter disabled

### BpmnViewer
- [ ] importXML + markers ; destroy unmount ; XML vide

### Notifications (**A-NOTIF-READ**)
- [ ] EventSource ; push liste ; mark-read local ; fail-soft

### Supervision (**D-MOCKGAP**)
- [ ] resolve ref → navigate ; pas de KPI mockés rendus comme réels

### MobileTasks (**A-MOBILE**)
- [ ] filtre `assigneeId === auth.id`

### Admin vague 2
- [ ] Types list/create/patch smoke
- [ ] ACL add/remove
- [ ] Templates activate/archive smoke
- [ ] Connectors CRUD smoke (secret non réaffiché si redact)
- [ ] WorkflowTypeConfigs observers

### Backend
- [ ] `AuthUserResponse` JSON contient `id` = subject OIDC (MockMvc)

## Integration Tests

- [ ] Vitest + msw/vi.mock chaînes store↔api
- [ ] Optionnel : MockMvc `GET /` → `index.html` si fallback ajouté

## End-to-End Tests (Playwright, session mock)

- [ ] **E2E-1** Boot shell authentifié (`id` présent)
- [ ] **E2E-2** Créer dossier → détail
- [ ] **E2E-3** Start workflow + diagramme canvas
- [ ] **E2E-4** Complete task
- [ ] **E2E-5** Reassign visible admin / absent manager
- [ ] **E2E-6** SSE → entrée panneau
- [ ] **E2E-7** Nav sans entrée Rapports
- [ ] **E2E-8** Admin : créer un dossier-type (vague 2)

## Test Data

- Fixtures DTO alignées backend ; BPMN XML minimal ; user
  `{ id: 'user-1', email, roles }` ± TA/WA.
- Playwright : route stubs `/api/**` ; pas Keycloak CI.

## Test Reporting & Coverage

```bash
cd frontend && npm run typecheck && npm run lint && npm run test -- --coverage
cd frontend && npm run test:e2e
./mvnw verify -Dmaven.compiler.release=21
```

Evidence Phase 8 : coller sorties + %.

## Manual Testing

- [ ] Alignement maquette shell/détail
- [ ] Mobile viewport tâches
- [ ] Context-path tenant
- [ ] Smoke Keycloak hors CI
- [ ] Admin secrets non leakés à l’écran

## Performance Testing

Hors gate V1 ; lazy admin + bpmn-js.

## Traceability

| Décision / critère | Tests |
|---|---|
| D-ME | Auth store ; AuthController IT ; E2E-1 |
| D-ACTIONS | Create/start/complete ; E2E-2..5 |
| D-ELEMENTS | Elements unit |
| D-SENSITIVE | SensitiveValue |
| D-MOCKGAP | Supervision ; E2E-7 |
| D-ADMIN-RES | Admin units ; E2E-8 |
| D-E2E / D-PACK | Playwright mock ; verify + static |
