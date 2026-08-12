---
phase: testing
title: Testing Strategy
description: Define testing approach, test cases, and quality assurance
status: draft
created: 2026-08-12
updated: 2026-08-12
feature: frontend
design_aligned: pending-review
---

# Testing Strategy

## Test Coverage Goals
**What level of testing do we aim for?**

- **Unitaires / composants (Vitest + VTU) :** stores Pinia, clients API
  (mock Axios), composants shell, BpmnViewer (importXML mocké), listes
  tâches, rendu erreurs `ProblemDetail`.
- **E2E (Playwright) :** boot session (auth mockée), ouverture dossier
  par ref, affichage diagramme (stub XML), complete task, SSE
  notification (mock EventSource si besoin).
- **Qualité statique :** `vue-tsc --noEmit`, ESLint, Prettier check.
- **Régression backend :** `./mvnw verify` reste vert (packaging static
  ne casse pas Boot / Security).
- **Hors scope :** Keycloak réel en CI ; Rapports SLA ; admin
  connecteurs/templates ; perf Lighthouse bloquant.

Cible : **≥ 80 %** lignes sur `frontend/src` (Vitest coverage), hors
`main.ts` bootstrap et types générés/manuels purs.

## Unit Tests
**What individual components need testing?**

### Auth store
- [ ] `init` OK → `authenticated` + user peuplé depuis `/api/auth/me`
- [ ] `init` 401 → redirect `window.location` vers
      `/oauth2/authorization/keycloak` (spy)
- [ ] getters rôles : `isWorkflowAdmin` true si `WORKFLOW_ADMIN` ou
      `TENANT_ADMIN`

### Axios / CSRF helpers
- [ ] defaults `withCredentials`, `xsrfCookieName`, `xsrfHeaderName`
- [ ] interceptor mappe 403/404 ProblemDetail vers erreur typée

### Dossier store
- [ ] `loadById` / `loadByRef` peuplent summary
- [ ] charge workflows puis sélectionne link actif
- [ ] `loadDiagram` ignore / surface 409 sans PI
- [ ] `completeTask` POST puis refresh tasks
- [ ] `reassignTask` refusé UX si non admin (pas d’appel) ; admin appelle API

### BpmnViewer component
- [ ] appelle `importXML` avec XML fourni
- [ ] applique markers active/completed/failed
- [ ] destroy viewer au unmount
- [ ] XML vide → état vide / message

### Notification store / drawer
- [ ] ouverture EventSource sur `/api/notifications/stream`
- [ ] push événement → liste
- [ ] erreur stream → flag reconnect sans throw

### SupervisionView
- [ ] submit ref → navigate `/dossiers/:id` après resolve `?ref=`
- [ ] erreur 404 → message ProblemDetail

### Guards UI
- [ ] bouton Réassigner absent sans rôle admin
- [ ] complete visible pour utilisateur authentifié (backend tranche)

## Integration Tests
**How do we test component interactions?**

Peu d’IT « serveur » FE. Couverture par :

- [ ] Vitest composant + store + Axios mock (msw ou vi.mock)
- [ ] Smoke Boot : resource `static/index.html` servie `GET /` (MockMvc
      ou `@SpringBootTest` web) **si** fallback ajouté — sinon note
      manuelle packaging

## End-to-End Tests
**What user flows need validation?**

- [ ] **E2E-1 Session** : arrivée app avec cookie session mock → shell
      visible ; sans session → redirect OAuth (assert URL)
- [ ] **E2E-2 Dossier** : ouvrir par ref → champs + workflows listés
- [ ] **E2E-3 Viz** : diagramme canvas présent après stub `/diagram`
- [ ] **E2E-4 Complete** : complete task → toast/refresh (stub POST 204/200)
- [ ] **E2E-5 Reassign** : rôle admin → POST reassign ; rôle manager →
      contrôle absent
- [ ] **E2E-6 SSE** : événement injecté → entrée panneau notifications

## Test Data
**What data do we use for testing?**

- Fixtures JSON alignées DTOs backend (dossier sinistre mock
  `DOS-SINISTRE-2026-000042`, diagram XML minimal BPMN 2.0).
- MSW / Playwright route stubs pour `/api/**`.
- Utilisateur mock : `{ fullName, email, roles: ['DOSSIER_MANAGER'] }`
  et variant admin.

## Test Reporting & Coverage
**How do we verify and communicate test results?**

```bash
cd frontend && npm run typecheck && npm run lint && npm run test -- --coverage
cd frontend && npm run test:e2e
./mvnw verify -Dmaven.compiler.release=21
```

- Seuils Vitest coverage documentés dans `vitest.config.ts`.
- Evidence Phase 8 : coller sorties + % coverage dans
  `docs/ai/testing/…` et implementation.

## Manual Testing
**What requires human validation?**

- [ ] Alignement visuel vs maquette (shell, détail dossier) desktop
- [ ] Vue mobile tâches (viewport étroit)
- [ ] Smoke Keycloak réel hors CI
- [ ] Context-path tenant (`/{tenantId}/`) assets + API
- [ ] Accessibilité basique (clavier, focus)

## Performance Testing
**How do we validate performance?**

- Hors gate V1. Budget informel : first contentful shell < 3 s en dev
  proxy ; Viewer BPMN lazy.

## Bug Tracking
**How do we manage issues?**

- Bugs FE dans suivi projet / PR ; régression = rejouer Vitest +
  Playwright smoke + `./mvnw verify`.

## Traceability requirements → tests

| Success criteria / story | Tests |
|---|---|
| Session BFF + CSRF | Auth store ; E2E-1 ; Axios defaults |
| Dossier détail | Dossier store ; E2E-2 |
| bpmn-js overlays | BpmnViewer ; E2E-3 |
| Complete / reassign | Store actions ; E2E-4/5 |
| SSE notifications | Notification store ; E2E-6 |
| Gates qualité | typecheck, lint, coverage scripts CI |
