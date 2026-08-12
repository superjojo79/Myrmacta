---
phase: planning
title: Project Planning & Task Breakdown
description: Break down work into actionable tasks and estimate timeline
status: ready
phase5_started: 2026-08-12
created: 2026-08-12
updated: 2026-08-12
feature: frontend
design_aligned: 2026-08-12
stakeholder_decisions: 2026-08-12
phase2_sync: 2026-08-12
phase3_sync: 2026-08-12
phase4: 2026-08-12
sequencing: two-waves
---

# Project Planning & Task Breakdown

Plan **phase 4** — dérivé des docs `requirements` / `design`
(`reviewed`) et `testing` (`draft`, scénarios `- [ ]`). **Aucune tâche
d’implémentation démarrée.**

Traçage `task` CLI : **indisponible** (`npx ai-devkit@latest task list
--json` → `unknown command 'task'`).

Branche agent : `cursor/feature-frontend-21f2` · lifecycle
`feature-frontend`.

## Milestones
**What are the major checkpoints?**

### Vague 1 — Instructeur (**D-SEQ**)
- [x] **M1 — Scaffold & qualité** : Vite vue-ts, deps, gates CI, Node 22
- [x] **M2 — Auth BFF & shell** : `/me`+`id`, CSRF, layout maquette
- [ ] **M3 — Backend deltas** : `/mine` enrichi, elements list, auth id
- [ ] **M4 — Dossier cœur FE** : create, détail, status, viz, start, tasks
- [ ] **M5 — Compléments vague 1** : elements UI, SSE, Supervision, inbox UI
- [ ] **M6 — Packaging & preuves vague 1** : Maven→static, E2E, verify

### Vague 2 — Admin
- [ ] **M7 — Admin types + ACL**
- [ ] **M8 — Admin templates / connectors / workflow-configs**
- [ ] **M9 — Preuves admin & clôture docs**

## Task Breakdown
**What specific work needs to be done?**

Statuts : `[ ]` not started · `[~]` in progress · `[x]` done · `[!]` blocked

### M1 — Scaffold & qualité

- [x] **T1. Init Vite `frontend/`**
  - **Outcome :** projet `vue-ts` sous `frontend/` ; `strict: true` ;
    `base: './'` (**DD-BASE**) ; arborescence
    `src/{app,api,stores,views,components,styles}`.
  - **Deps :** aucune.
  - **Validation :** `npm run build` produit `dist/`.
  - **Tests :** smoke build (prépare Vitest T3).

- [x] **T2. Dépendances UI & outils**
  - **Outcome :** primevue + themes + primeicons ; pinia ; axios ;
    vue-router ; bpmn-js ; vitest + VTU + jsdom ; playwright ; eslint
    vue-ts ; prettier ; vue-tsc ; `engines.node` = 22.
  - **Deps :** T1.
  - **Validation :** install sans peer errors bloquants.
  - **Tests :** configs vitest/playwright présentes.

- [x] **T3. Gates scripts + README FE**
  - **Outcome :** scripts `typecheck|lint|test|test:e2e|build` ; README
    court ; placeholder spec vert.
  - **Deps :** T2.
  - **Validation :** `npm run typecheck && lint && test` OK sur skeleton.
  - **Tests :** placeholder Vitest.

### M2 — Auth BFF & shell

- [x] **T4. Axios CSRF + ProblemDetail + base API**
  - **Outcome :** defaults `withCredentials` / XSRF ; helper erreur
    typée ; résolution préfixe API vs `BASE_URL`/`baseURI` (**DD-BASE**).
  - **Deps :** T2.
  - **Validation :** unit defaults + prefix helper.
  - **Tests :** Axios / CSRF / base (testing doc).

- [x] **T5. Backend `AuthUserResponse.id`**
  - **Outcome :** record `(id, name, email, roles)` ; `id =
    OidcUser.getSubject()` ; MockMvc.
  - **Deps :** aucune (parallèle T4).
  - **Validation :** MockMvc `/api/auth/me` JSON contient `id`.
  - **Tests :** AuthController / AuthUserResponse IT.

- [x] **T6. Auth store + boot**
  - **Outcome :** Pinia `auth` ; `init` → `/me` ; 401 → OAuth redirect ;
    logout ; getters TA/WA ; wiring `main.ts`.
  - **Deps :** T4, T5.
  - **Validation :** Vitest auth store.
  - **Tests :** Auth store ; E2E-1 (plus tard T20).

- [x] **T7. Shell layout + tokens**
  - **Outcome :** nav Pilotage (Supervision, Tâches) + Admin (rôles) ;
    **pas** Rapports (**D-MOCKGAP**) ; CSS tokens maquette + thème
    PrimeVue ; header user (`name`/`email`).
  - **Deps :** T6.
  - **Validation :** composant shell Vitest.
  - **Tests :** shell ; E2E-9 (nav).

### M3 — Backend deltas

- [x] **T8. Inbox `GET /api/workflow-tasks/mine`**
  - **Outcome :** `ListMyTasksUseCase` + port Flowable
    `taskAssignee(sub)` ; enrichissement `dossierId` (var) +
    `dossierReference` + `linkId` ; skip sans dossierId ; plafond 100 ;
    authz authentifié (**DD-INBOX-DTO/AUTHZ**) ; ArchUnit OK.
  - **Deps :** T5 (sub disponible pour IT oidc).
  - **Validation :** unit UC + MockMvc `/mine`.
  - **Tests :** Inbox backend ; prépare E2E-7.

- [ ] **T9. `GET /api/dossiers/{id}/elements`**
  - **Outcome :** `ListDossierElementsUseCase` ; DTO sans `storageRef` ;
    `downloadable` ; authz lecture dossier (**DD-ELEMENTS-DTO**).
  - **Deps :** T5 (contexte auth IT).
  - **Validation :** unit + MockMvc (assert absence storageRef).
  - **Tests :** Elements MockMvc ; prépare T15.

### M4 — Dossier cœur FE

- [ ] **T10. API clients TS**
  - **Outcome :** modules typés auth/dossiers/types/workflows/viz/mine/
    elements/notifications ; start envoie `Idempotency-Key`.
  - **Deps :** T4.
  - **Validation :** unit mapping fixtures.
  - **Tests :** client + fixtures.

- [ ] **T11. CreateDossier + DynamicFields + SensitiveValue**
  - **Outcome :** form `dossierTypeCode` + `label` + fields ;
    masquage affichage sensitive (**D-SENSITIVE**) ; POST create.
  - **Deps :** T7, T10.
  - **Validation :** Vitest Create/DynamicFields/SensitiveValue.
  - **Tests :** CreateDossier ; SensitiveValue ; E2E-2.

- [ ] **T12. DossierDetailView + PATCH status**
  - **Outcome :** load id/ref ; champs ; StatusSelect enum +
    `expectedVersion` (**D-STATUS**) ; navigation links.
  - **Deps :** T11.
  - **Validation :** Vitest dossier store status ; E2E-3.
  - **Tests :** Dossier store ; E2E-3.

- [ ] **T13. BpmnViewer + history/tasks panels**
  - **Outcome :** lazy Viewer ; overlays active/completed/failed ;
    timeline ; tasks ; gestion 409 sans PI.
  - **Deps :** T12.
  - **Validation :** Vitest BpmnViewer ; E2E-4 (diagramme).
  - **Tests :** BpmnViewer ; partie E2E-4.

- [ ] **T14. StartWorkflowDialog + complete + reassign**
  - **Outcome :** défaut `type.workflowKey` + option autre configs
    (**DD-START**) ; Idempotency-Key UUID ; complete `{}`
    (**D-COMPLETE**) ; reassign TA/WA only.
  - **Deps :** T13.
  - **Validation :** Vitest dialog/actions ; E2E-4/5/6.
  - **Tests :** StartWorkflowDialog ; complete/reassign ; E2E-4..6.

### M5 — Compléments vague 1

- [ ] **T15. ElementsList UI**
  - **Outcome :** consomme `/dossiers/{id}/elements` ; download content ;
    Ajouter `disabled` + tooltip (**D-ELEMENTS**).
  - **Deps :** T9, T12.
  - **Validation :** Vitest ElementsList.
  - **Tests :** Elements FE unit.

- [ ] **T16. Notifications SSE**
  - **Outcome :** EventSource `/api/notifications/stream` ; drawer ;
    mark-read local (**A-NOTIF-READ**) ; fail-soft.
  - **Deps :** T7.
  - **Validation :** Vitest notification store ; E2E-8.
  - **Tests :** Notifications ; E2E-8.

- [ ] **T17. SupervisionView MVP**
  - **Outcome :** ouverture ref/id + lien créer ; **aucun** faux KPI
    (**D-MOCKGAP**).
  - **Deps :** T12.
  - **Validation :** Vitest Supervision ; E2E navigation.
  - **Tests :** Supervision ; E2E-9 (nav).

- [ ] **T18. MobileTasksView via `/mine`**
  - **Outcome :** liste inbox ; empty state ; complete depuis liste ;
    layout responsive (**A-MOBILE**).
  - **Deps :** T8, T14.
  - **Validation :** Vitest tasks store/view ; E2E-7.
  - **Tests :** MobileTasks ; E2E-7.

### M6 — Packaging & preuves vague 1

- [ ] **T19. frontend-maven-plugin + SPA fallback**
  - **Outcome :** Node **22** (**DD-NODE**) ; `npm ci` + `build` ;
    copy `dist` → `static/` ; gitignore bundles ; WebMvc fallback
    `index.html` (**A-SPA-FALLBACK**).
  - **Deps :** T1…T18 (build FE vert).
  - **Validation :** `./mvnw verify` peuplé static ; optionnel MockMvc
    `GET /` → index.
  - **Tests :** packaging / SPA fallback IT.

- [ ] **T20. Playwright E2E-1…9 + coverage Vitest**
  - **Outcome :** session mock (**D-E2E**) ; parcours vague 1 ; coverage
    ≥ 80 % `frontend/src`.
  - **Deps :** T19 (ou dist local).
  - **Validation :** `npm run test:e2e` + coverage report.
  - **Tests :** E2E-1…9 ; coverage gate.

- [ ] **T21. Evidence vague 1 + docs sync**
  - **Outcome :** coller sorties verify/coverage dans testing/
    implementation ; cocher scénarios vague 1 ; lint feature.
  - **Deps :** T20.
  - **Validation :** `npx ai-devkit@latest lint --feature frontend`.
  - **Tests :** N/A docs.

### M7 — Admin types + ACL

- [ ] **T22. UI CRUD dossier-types**
  - **Outcome :** list/create/patch types (`TENANT_ADMIN`) ; field
    definitions editor basique.
  - **Deps :** T21 (vague 1 done) ou au minimum T7+T10 si parallèle
    **interdit** par D-SEQ — **deps = T21**.
  - **Validation :** Vitest admin types ; E2E-10 (partiel).
  - **Tests :** Admin types ; E2E-10.

- [ ] **T23. UI ACL type**
  - **Outcome :** grant/revoke ACL sur `/api/dossier-types/{code}/acl`.
  - **Deps :** T22.
  - **Validation :** Vitest ACL actions.
  - **Tests :** Admin ACL.

### M8 — Admin reste (**D-ADMIN-RES**)

- [ ] **T24. UI document-templates**
  - **Outcome :** list + create/patch + activate/archive/new-version.
  - **Deps :** T21.
  - **Validation :** Vitest templates smoke.
  - **Tests :** Admin templates.

- [ ] **T25. UI connectors**
  - **Outcome :** CRUD instances + definitions lecture ; respecte
    `secretConfigured` / config maskée (**A-CONN-SECRET**).
  - **Deps :** T21.
  - **Validation :** Vitest connectors (pas de secret en clair).
  - **Tests :** Admin connectors.

- [ ] **T26. UI workflow-type-configs**
  - **Outcome :** CRUD configs + `observerUserIds` ; alimente aussi
    StartWorkflowDialog « autre process ».
  - **Deps :** T21.
  - **Validation :** Vitest workflow-configs.
  - **Tests :** Admin workflow-configs.

### M9 — Clôture

- [ ] **T27. E2E/Vitest admin smoke**
  - **Outcome :** E2E-10 complet + tests admin restants verts.
  - **Deps :** T22…T26.
  - **Validation :** `npm run test && test:e2e` ; verify.
  - **Tests :** E2E-10 ; admin suites.

- [ ] **T28. Evidence finale + Phase 7/8/9 docs**
  - **Outcome :** testing/implementation/DEFERRED à jour ; lint OK ;
    prêt check implementation / review.
  - **Deps :** T27.
  - **Validation :** lint feature ; docs status coherents.
  - **Tests :** N/A docs.

## Traceability testing → tasks

| Scénario testing | Task(s) |
|---|---|
| Auth /me + redirect | T5, T6, E2E-1←T20 |
| Axios/CSRF/base | T4 |
| SensitiveValue | T11 |
| CreateDossier | T11, E2E-2←T20 |
| PATCH status | T12, E2E-3←T20 |
| BpmnViewer / 409 | T13, E2E-4←T20 |
| Start + Idempotency-Key | T14, E2E-4←T20 |
| Complete `{}` / reassign | T14, E2E-5/6←T20 |
| Inbox `/mine` | T8, T18, E2E-7←T20 |
| Elements DTO + UI | T9, T15 |
| Notifications SSE | T16, E2E-8←T20 |
| Supervision / no Rapports | T7, T17, E2E-9←T20 |
| Admin types/ACL/templates/connectors/WF | T22–T26, E2E-10←T27 |
| Packaging Node 22 + SPA | T19 |
| Coverage ≥ 80 % + verify | T20, T21, T27, T28 |

## Dependencies
**What needs to happen in what order?**

```text
Vague 1:
  T1 → T2 → T3
  T2 → T4 → T6 → T7
  T5 ∥ T4 ; T5 → T6 ; T5 → T8 ; T5 → T9
  T4 → T10 → T11 → T12 → T13 → T14
  T9 → T15 ; T7 → T16 ; T12 → T17 ; T8+T14 → T18
  T1…T18 → T19 → T20 → T21

Vague 2 (après T21) :
  T21 → T22 → T23
  T21 → T24 ∥ T25 ∥ T26
  T22…T26 → T27 → T28
```

Bloquants externes : TODO §24 (liste dossiers) — non bloquant (Supervision
MVP). Pagination inbox >100 — follow-up hors V1.

## Risks & Mitigation

| Risque | Mitigation |
|---|---|
| Context-path casse assets/API | **DD-BASE** ; checklist manuelle T21 |
| Jointure inbox coûteuse | Cap 100 ; tests perf informels |
| Scope admin dilue vague 1 | **D-SEQ** : gate T21 avant M7 |
| Flaky OAuth E2E | Mock session only (**D-E2E**) |
| Plugin Node download CI | Node 22 pinné ; cache Maven |

## Resources Needed

- Node 22 + JDK/Maven existants.
- Skills : `vue`, `vue-best-practices`, `pinia`, `vite`, `vitest`,
  `vue-testing-best-practices`, `playwright-best-practices`,
  `flowforge-conventions`, `tdd`, `verify`.
- Maquette HTML + analysis 11 + design `reviewed`.

## Next

Phase 5 — Execute Plan (`dev-implementation`) en TDD, commencer par
**T1** (puis T2/T3 ou T5 en parallèle backend).
