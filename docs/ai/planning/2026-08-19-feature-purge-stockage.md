---
phase: planning
title: Project Planning & Task Breakdown
description: Break down work into actionable tasks and estimate timeline
status: ready
created: 2026-08-19
updated: 2026-08-19
feature: purge-stockage
sequencing: layered
phase1: 2026-08-19
phase4: 2026-08-19
design_aligned: 2026-08-19
requirements_ref: docs/ai/requirements/2026-08-19-feature-purge-stockage.md
design_ref: docs/ai/design/2026-08-19-feature-purge-stockage.md
testing_ref: docs/ai/testing/2026-08-19-feature-purge-stockage.md
---

# Project Planning & Task Breakdown — `purge-stockage`

Plan **Phase 4** figé après requirements `reviewed` et design `reviewed`.

Task tracing : indisponible (`npx ai-devkit@latest task` → unknown command).

TDD obligatoire (skill `tdd`) : test rouge → code → vert. Avant claim :
`./mvnw verify` ; FE = non-régression `npm run test` + `npm run test:e2e`
(D-FE, pas de nouveaux specs purge).

## Milestones

- [ ] **M1 — `DocumentStoragePort.delete` (FS + S3)**
- [ ] **M2 — Statuts outbox + claim + Liquibase 011**
- [ ] **M3 — Deletes graphe PG (ports repos)**
- [ ] **M4 — `ProcessPurgeOutboxUseCase` + Flowable best-effort**
- [ ] **M5 — Job `@Scheduled` + config**
- [ ] **M6 — Docs produit + verify final**

## Task Breakdown

Statuts : `[ ]` not started · `[~]` in progress · `[x]` done · `[!]` blocked

### M1 — Port stockage delete

- [ ] **T1. Étendre `DocumentStoragePort` + contract test (TDD)**
  - **Outcome :** `void delete(tenantId, storageRef)` ; contrat manquant =
    succès ; mauvais tenant / `..` → `DocumentStorageException`.
  - **Depends on :** —
  - **Validation :** `DocumentGenerationPortsContractTest` (ou équivalent)
    mis à jour.
  - **Tests liés :** testing § DocumentStoragePort delete.

- [ ] **T2. `FilesystemStorageAdapter.delete` (TDD)**
  - **Outcome :** `Files.deleteIfExists` via `resolveSafe` ; idempotent.
  - **Depends on :** T1
  - **Validation :** unit FS temp dir.
  - **Tests liés :** testing § FS adapter.

- [ ] **T3. `S3StorageAdapter.delete` (TDD)**
  - **Outcome :** `DeleteObject` ; `NoSuchKey` / 404 → succès.
  - **Depends on :** T1
  - **Validation :** unit mock `S3Client`.
  - **Tests liés :** testing § S3 adapter.

### M2 — Outbox lifecycle

- [ ] **T4. Étendre `PurgeOutboxStatus` + Liquibase 011 (TDD/IT)**
  - **Outcome :** `PROCESSING` / `COMPLETED` / `FAILED` ; colonne
    `last_error` nullable ; index `(status, created_at)` ; CHECK/enum OK
    H2+PG.
  - **Depends on :** —
  - **Validation :** `RetentionSchemaIT` étendu ou `PurgeSchemaIT` ;
    insert §21 `PENDING` non régressé.
  - **Tests liés :** testing § Liquibase 011.

- [ ] **T5. Étendre `PurgeOutboxRepository` — find / claim / mark (TDD)**
  - **Outcome :** `findRetryable(limit)` = `PENDING` ∪ `FAILED` order by
    `created_at` ; `claim(id)` atomique depuis PENDING|FAILED →
    PROCESSING ; `markCompleted` / `markFailed(id, error)`.
  - **Depends on :** T4
  - **Validation :** IT claim race + mark.
  - **Tests liés :** testing § claim atomique / FAILED retry
    (A-RETRY-IMMEDIATE).

### M3 — Graphe PG

- [ ] **T6. Ports delete éléments / relations / links / dossier (TDD/IT)**
  - **Outcome :** méthodes repo (ou port purge dédié) :
    delete elements by dossier ; relations src|tgt ; workflow links ;
    dossier by id. Ordre respecté côté use case.
  - **Depends on :** —
  - **Validation :** IT graphe minimal puis assert empty.
  - **Tests liés :** testing § Integration graphe.

### M4 — Use case + Flowable

- [ ] **T7. `WorkflowEnginePort.deleteProcessInstanceBestEffort` (TDD)**
  - **Outcome :** méthode port + adapter Flowable
    (`runtimeService.deleteProcessInstance` catch-all log) ; PI absente =
    no-op.
  - **Depends on :** —
  - **Validation :** unit mock RuntimeService.
  - **Tests liés :** testing § D-WF.

- [ ] **T8. `ProcessPurgeOutboxUseCase` (TDD)**
  - **Outcome :** pour chaque candidat : A-ELIGIBLE-GUARD skip sans claim ;
    claim → load elements → delete storageRefs → D-WF sur PI des links →
    delete graphe PG → COMPLETED ; erreur store/PG → FAILED + last_error ;
    batch `limit` (A-BATCH).
  - **Depends on :** T1–T7
  - **Validation :** tous cas unitaires testing § ProcessPurgeOutboxUseCase.
  - **Tests liés :** testing § Unit use case.

- [ ] **T9. Câblage Spring `DossierConfig` / beans purge**
  - **Outcome :** use case bean ; pas d’exposition HTTP.
  - **Depends on :** T8
  - **Validation :** contexte charge (slice / verify).

### M5 — Job

- [ ] **T10. `PurgeOutboxJob` + `app.purge.*` (TDD/IT)**
  - **Outcome :** `@Scheduled` fixedDelay défaut 1h ; batch-size 50 ;
    appelle use case ; yaml `.env.example` / `application*.yml` documentés.
  - **Depends on :** T9
  - **Validation :** IT : seed PENDING + élément FS → COMPLETED + 404
    dossier.
  - **Tests liés :** testing § Job IT / MockMvc 404.

### M6 — Clôture docs

- [ ] **T11. Docs produit + success criteria + verify**
  - **Outcome :** cocher success criteria requirements ; maj TODO §22 /
    FILE-ATTENTE / DEFERRED ; runbook deployment déjà draft ; evidence
    `./mvnw verify` + `npm run test` + `npm run test:e2e` (D-FE
    non-régression).
  - **Depends on :** T10
  - **Validation :** skill `verify` sorties fraîches.

## Traceability — scenarios testing → tasks

| Scenario testing | Task(s) |
|---|---|
| Port delete contract / FS / S3 | T1, T2, T3 |
| Liquibase 011 + status | T4 |
| Claim atomique / FAILED retry | T5 |
| Graphe PG delete | T6 |
| Flowable best-effort | T7 |
| Use case (tous cas + eligible guard) | T8 |
| Job IT + GET 404 | T10 |
| E2E non-régression (D-FE) | T11 |
| Rétention non-régression | T4, T11 |

## Risks

| Risque | Mitigation |
|---|---|
| Orphelins store si PG avant store | D-ORDER + test échec mid-flight |
| FK relation bloque delete dossier | T6 relations src\|tgt avant dossier |
| Flowable exception abort TX | D-WF hors / avant TX PG critique ; best-effort catch |
| Double claim | T5 UPDATE atomique |
| Régression insert §21 PENDING | IT Retention* verts |

## Sequencing notes

T1∥T4∥T6∥T7 possibles en parallèle ; T8 après T1–T7 ; T10 après T8–T9 ;
T11 dernier.

## Next (Phase 5)

Démarrer **T1** (port `delete` TDD) puis T2/T3.
