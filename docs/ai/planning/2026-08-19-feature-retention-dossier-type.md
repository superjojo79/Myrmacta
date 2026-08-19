---
phase: planning
title: Project Planning & Task Breakdown
description: Break down work into actionable tasks and estimate timeline
status: ready
created: 2026-08-19
updated: 2026-08-19
feature: retention-dossier-type
sequencing: layered
phase1: 2026-08-19
phase4: 2026-08-19
design_aligned: 2026-08-19
requirements_ref: docs/ai/requirements/2026-08-19-feature-retention-dossier-type.md
design_ref: docs/ai/design/2026-08-19-feature-retention-dossier-type.md
testing_ref: docs/ai/testing/2026-08-19-feature-retention-dossier-type.md
---

# Project Planning & Task Breakdown — `retention-dossier-type`

Plan **Phase 4** figé après requirements `reviewed` et design `reviewed`.

Task tracing : indisponible (`npx ai-devkit@latest task` → unknown command).

TDD obligatoire (skill `tdd`) : test rouge → code → vert. Avant claim :
`./mvnw verify` (+ Vitest/Playwright pour le FE).

## Milestones

- [ ] **M1 — Settings tenant (domaine + persist + API)**
- [ ] **M2 — Override `DossierType` + resolver**
- [ ] **M3 — Dossier `purgeEligibleAt` + `purge_outbox`**
- [ ] **M4 — `EvaluateDossierRetention` + job**
- [ ] **M5 — Admin SPA + E2E**
- [ ] **M6 — Docs produit + verify final**

## Task Breakdown

Statuts : `[ ]` not started · `[~]` in progress · `[x]` done · `[!]` blocked

### M1 — Settings tenant

- [ ] **T1. Domaine settings + use cases (TDD)**
  - **Outcome :** `TenantRetentionSettings` ; `Get` / `UpsertTenantRetentionSettingsUseCase` ;
    `TenantAdminGuard` ; refuse `defaultRetentionDays ≤ 0`.
  - **Depends on :** —
  - **Validation :** tests unitaires use case (403 non-admin, 400 ≤ 0).
  - **Tests liés :** testing § `TenantRetentionSettings` / upsert.

- [ ] **T2. Liquibase + JPA settings**
  - **Outcome :** table singleton `tenant_retention_settings` (PK `tenant_id`) ;
    adapter port out ; pas de seed (D-BOOTSTRAP).
  - **Depends on :** T1
  - **Validation :** IT persistence round-trip H2 MODE=PostgreSQL.
  - **Tests liés :** testing § Persistence settings.

- [ ] **T3. API `GET/PUT /api/tenant/retention-settings` (TDD MockMvc)**
  - **Outcome :** controller + DTOs ; GET **404** si absent ; PUT upsert ;
    `@PreAuthorize TENANT_ADMIN`.
  - **Depends on :** T2
  - **Validation :** MockMvc 200/404/400/403.
  - **Tests liés :** testing § MockMvc settings.

### M2 — Override type + politique

- [ ] **T4. Étendre `DossierType` + validation rétention (TDD)**
  - **Outcome :** `retentionDays` / `retentionNever` ; validation exclusivité ;
    create/update use cases + patch héritage (A-PATCH-INHERIT).
  - **Depends on :** —
  - **Validation :** tests create/update (NEVER+days invalide, days ≤ 0).
  - **Tests liés :** testing § Create/Update `DossierType`.

- [ ] **T5. `RetentionPolicyResolver` (TDD)**
  - **Outcome :** héritage / jours / NEVER → `OptionalInt`.
  - **Depends on :** T4
  - **Validation :** 3 cas unitaires (+ settings ≤ 0 hors resolver).
  - **Tests liés :** testing § `RetentionPolicyResolver`.

- [ ] **T6. Liquibase/JPA colonnes type + DTOs/API type**
  - **Outcome :** colonnes + migration héritage ; `DossierTypeResponse` /
    create/patch exposent toujours les champs (D-TYPE-VIS).
  - **Depends on :** T4
  - **Validation :** IT round-trip + MockMvc create/patch/get/list.
  - **Tests liés :** testing § Persistence type + MockMvc type.

### M3 — Dossier + outbox

- [ ] **T7. Étendre `Dossier` — `purgeEligibleAt` + `withRetentionArchive` (TDD)**
  - **Outcome :** champ + méthode domaine `CLOTURE`→`ARCHIVE` +
    `purgeEligibleAt` ; acteur passé en param (D-JOB-ACTOR au call site).
  - **Depends on :** —
  - **Validation :** tests record / transition refusée si pas CLOTURE.
  - **Tests liés :** testing § Domaine `Dossier`.

- [ ] **T8. `purge_outbox` + port (TDD)**
  - **Outcome :** `PurgeOutboxRecord` ; repo insert/exists ; Liquibase table
    **UNIQUE(`dossier_id`)** (D-OUTBOX-UNIQ) ; status `PENDING`.
  - **Depends on :** —
  - **Validation :** IT insert + conflit unique.
  - **Tests liés :** testing § Persistence outbox.

- [ ] **T9. Exposer `purgeEligibleAt` sur `DossierResponse` + mapping JPA dossier**
  - **Outcome :** colonne dossier + DTO toujours présent nullable (D-PURGE-FIELD).
  - **Depends on :** T7
  - **Validation :** MockMvc GET dossier `purgeEligibleAt: null` par défaut ;
    IT colonne.
  - **Tests liés :** testing § MockMvc `DossierResponse`.

### M4 — Évaluation + job

- [ ] **T10. Port `findClosedForRetention(limit)` + adapter (TDD/IT)**
  - **Outcome :** `CLOTURE` + `closed_at IS NOT NULL`, `ORDER BY closed_at, id`,
    limit ; index `(status, closed_at)` (D-SCAN, A-BATCH-COUNT).
  - **Depends on :** T7, T9
  - **Validation :** IT requête / ordre / limit.

- [ ] **T11. `EvaluateDossierRetentionUseCase` (TDD)**
  - **Outcome :** settings absents → no-op ; resolve mémoire ; archive +
    outbox en **une** transaction ; idempotence unique ; D-FORCE ;
    `updatedBy=system:retention-job` ; **pas** `UpdateDossierStatusUseCase`
    (D-ARCHIVE-PATH).
  - **Depends on :** T1, T5, T7, T8, T10
  - **Validation :** tous les cas testing § Evaluate… .
  - **Tests liés :** testing § `EvaluateDossierRetentionUseCase`.

- [ ] **T12. Job `@Scheduled` + yaml (TDD/IT slice)**
  - **Outcome :** `app.retention.poll-delay` défaut `1h` ;
    `app.retention.batch-size` défaut `100` (D-SCHED) ; câblage Spring.
  - **Depends on :** T11
  - **Validation :** IT : seed `CLOTURE` expiré → ARCHIVE + 1 outbox.
  - **Tests liés :** testing § Job.

### M5 — Frontend

- [ ] **T13. Clients API + types TS (TDD Vitest)**
  - **Outcome :** `retentionSettings` GET/PUT ; champs rétention sur types
    dossier ; type `purgeEligibleAt` sur dossier si types partagés.
  - **Depends on :** T3, T6, T9 (contrats)
  - **Validation :** `cd frontend && npm run test` (specs clients).
  - **Tests liés :** support E2E-RET-* .

- [ ] **T14. UI admin — bloc rétention + champs type (TDD Vitest)**
  - **Outcome :** bloc sur / près de `DossierTypesAdminView` (D-UI-LOC) ;
    create/edit type jours / NEVER / héritage ; gestion 404 settings.
  - **Depends on :** T13
  - **Validation :** specs composants / vue admin.
  - **Tests liés :** parcours UI unitaires.

- [ ] **T15. Playwright E2E-RET-1/2/3**
  - **Outcome :** mocks settings + dossier-types ; admin config ; override
    type ; non-admin sans mutation settings.
  - **Depends on :** T14
  - **Validation :** `cd frontend && npm run test:e2e`.
  - **Tests liés :** testing § End-to-End.

### M6 — Clôture docs

- [ ] **T16. Docs produit + success criteria + verify**
  - **Outcome :** cocher success criteria requirements ; maj FILE-ATTENTE /
    DEFERRED (feature implémentée ; §22 suivant) ; TODO §21 → implémenté /
    quasi clos ; evidence verify.
  - **Depends on :** T12, T15
  - **Validation :** `./mvnw verify` + `npm run test` + `npm run test:e2e`
    (sorties fraîches, skill `verify`).

## Traceability — scenarios testing → tasks

| Scenario testing | Task(s) |
|---|---|
| Resolver héritage / jours / NEVER | T5 |
| Settings upsert validation + guard | T1, T3 |
| Type rétention validation + round-trip | T4, T6 |
| Evaluate… (tous cas) | T11 |
| `withRetentionArchive` | T7 |
| Liquibase settings/type/outbox/dossier | T2, T6, T8, T9 |
| MockMvc settings / type / purgeEligibleAt | T3, T6, T9 |
| Job IT | T12 |
| E2E-RET-1/2/3 | T15 |

## Dependencies (ordre)

```text
T1 → T2 → T3 ──────────────┐
T4 → T5 ─────────────┐     │
T4 → T6 ─────────────┤     │
T7 → T9 → T10 ──┐    │     │
T8 ─────────────┼→ T11 → T12 ┐
                │            │
T3, T6, T9 → T13 → T14 → T15 ┼→ T16
T12 ─────────────────────────┘
```

Parallélisable : M1 ∥ M2 ∥ (T7/T8) puis convergence T11.

## Timeline & Estimates

Pas d’estimation calendaire. Effort : domaine + Liquibase + job + admin SPA ;
pas de purge object store.

## Risks & Mitigation

| Risque | Mitigation |
|---|---|
| ARCHIVE métier vs purge | D-FORCE + tests T11 |
| Double insert outbox | UNIQUE + tests conflit T8/T11 |
| Settings absents | D-BOOTSTRAP ; job no-op + UI 404→formulaire |
| Scope §22 | Pas d’adaptateur S3 ; outbox PENDING only |
| H2 vs UNIQUE PG | IT MODE=PostgreSQL ; documenter si partial index refusé |
| A-BATCH-COUNT (NEVER consomme le budget) | Accepté V1 ; index + delay 1h |
| Patch héritage ambigu | A-PATCH-INHERIT + tests T4/T6 |

## Resources Needed

- Skills : `flowforge-conventions`, `tdd`, `verify`, `vue*`, `vitest`,
  `playwright-best-practices`.
- Branche : `cursor/retention-dossier-type-9ed2`.

## Next step (Phase 5)

Commencer par **T1** (settings domaine TDD), puis T2/T3 en enchaînement ;
en parallèle possible T4 dès que prêt.
