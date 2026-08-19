---
phase: implementation
title: Implementation
description: Track implementation progress against the plan
status: done
created: 2026-08-19
updated: 2026-08-19
feature: purge-stockage
phase1: 2026-08-19
phase5: 2026-08-19
planning_ref: docs/ai/planning/2026-08-19-feature-purge-stockage.md
design_ref: docs/ai/design/2026-08-19-feature-purge-stockage.md
---

# Implementation — `purge-stockage`

## Shipped (Phase 5)

### Backend — stockage (T1–T3)

- `DocumentStoragePort.delete(tenantId, storageRef)` : missing = succès ;
  ref invalide (tenant / `..`) → `DocumentStorageException`.
- `FilesystemStorageAdapter` : `Files.deleteIfExists` après `resolveSafe`.
- `S3StorageAdapter` : `deleteObject` ; `NoSuchKey` / HTTP 404 = succès.
  Jamais MinIO en dur.

### Backend — outbox (T4–T5)

- `PurgeOutboxStatus` : `PENDING` | `PROCESSING` | `COMPLETED` | `FAILED`.
- `PurgeOutboxOrigin` : `RETENTION` | `ADMIN`.
- `PurgeOutboxRecord` + factory `pending(...)` + `lastError`.
- `PurgeOutboxPayloads` : JSON domaine sans Jackson.
- Job rétention §21 insère origin `RETENTION`.
- Liquibase `011-purge-outbox-worker.yaml` : colonnes `origin` (défaut
  `RETENTION`), `last_error` varchar(1024), index
  `idx_purge_outbox_status_created_at`.
- JPA : `claim` atomique PENDING|FAILED → PROCESSING ;
  `markCompleted` / `markFailed` (`@Modifying` + `clearAutomatically`).
- `findRetryable` = PENDING ∪ FAILED, plus anciens d’abord.
  UNIQUE(`dossier_id`) inchangé ; ligne `COMPLETED` conservée.

### Backend — graphe PG (T6)

- `DossierElementRepository.deleteAllByDossierId`
- `DossierRelationRepository.deleteRelationsTouching` (source **ou** cible)
- `DossierWorkflowLinkRepository.deleteAllByDossierId`
- `DossierRepository.deleteById`

### Backend — use cases + Flowable (T7–T9)

- `WorkflowEnginePort.deleteProcessInstanceBestEffort` : Flowable
  `deleteProcessInstance` ; PI absente = no-op ; autres erreurs loguées
  (ACT_HI résiduel accepté Q5).
- `ProcessPurgeOutboxUseCaseImpl.processBatch(now, limit)` :
  1. skip horloge **seulement** origin `RETENTION` si `purgeEligibleAt > now`
     (sans claim) ;
  2. claim → deletes store (éléments avec `storageRef`) → WF best-effort →
     graphe PG → `COMPLETED` ;
  3. échec store/runtime → `FAILED` + `last_error`, **pas** de deletes PG ;
  4. dossier déjà absent : nettoie le graphe restant puis `COMPLETED`.
- `EnqueueDossierPurgeUseCaseImpl` : `TENANT_ADMIN`, statut
  `CLOTURE`/`ARCHIVE` sinon 409, 404 inconnu, idempotent UNIQUE,
  origin `ADMIN`.
- `ListPurgeOutboxUseCaseImpl` : `TENANT_ADMIN`, limite défaut 50, max 200.

### Backend — HTTP + job (T10–T11)

- `GET /api/purge-outbox` : items + counts ; `TENANT_ADMIN` + CSRF session.
- `POST /api/dossiers/{id}/purge-requests` : 201 created / 200 already
  exists / 409 ouvert / 404 / 403.
- `DossierNotEligibleForPurgeException` → 409.
- `PurgeOutboxJob` + `PurgeProperties` (`app.purge.poll-delay` 1h,
  `batch-size` 50). **Pas** de `@Transactional` sur le job (I/O store).

### Frontend (T12–T14)

- Client `frontend/src/api/purgeOutbox.ts`.
- Vue `/admin/purge-outbox` lecture : `PurgeOutboxAdminView` +
  `PurgeOutboxCounts` + `PurgeOutboxQueue`. Nav **File purge**.
  Pas de bouton force/retry (Q6).
- `DossierPurgeRequestButton` sur le détail dossier (`TENANT_ADMIN`).
- Playwright E2E-PURGE-1..5 dans `frontend/e2e/pilotage.spec.ts`.
- Route `/admin/workflow-types` conservée.

## Design deviations

| Id | Severity | Note |
|---|---|---|
| HTTP idempotent | info | Design 204 **ou** 200 ; livré **200** si UNIQUE déjà présent. |
| PROCESSING crash | low | Claim sans recovery V1 : une ligne peut rester `PROCESSING` après crash JVM. Accepté (hors design recovery). |
| Enqueue UI | info | Bouton sur le **détail dossier**, pas sur la page file (Q6 lecture seule). |
| CHECK status | info | `status` reste `varchar(32)` sans CHECK DB (010) ; élargissement applicatif seulement. |

## Alignment (Phase 5)

Verdict : **couvre Q1–Q11 / D-\*** du design reviewed.

- ALIGNED : D-MECH, D-PORT, D-ORDER, D-SCOPE-PG, D-WF, D-UI, D-ENQUEUE-HTTP,
  D-STATUS, D-ORIGIN, D-CLAIM, D-RETRY, D-KEEP-OUTBOX, D-ERROR-COL, D-SCHED,
  D-LIST, D-UI-LOC, D-ELIGIBLE-GUARD.
- Sécurité : `@PreAuthorize TENANT_ADMIN` + `TenantAdminGuard` ; CSRF BFF.
- Hexagone : domaine pur ; adapters infra ; ArchUnit CI.

## Evidence (Phase 5, 2026-08-19)

```text
./mvnw verify -Dmaven.compiler.release=21
# BUILD SUCCESS — Tests run: 832, Failures: 0
# jacoco: All coverage checks have been met.

cd frontend && npm run test -- --pool=forks --maxWorkers=2
# Test Files  40 passed | Tests  120 passed

cd frontend && CI=1 npm run test:e2e
# 32 passed (E2E-PURGE-1..5 + suite existante dont E2E-RET-*)
```

IT ciblés (H2 index + API) : `RetentionSchemaIT`, `RetentionPersistenceIT`,
`PurgeGraphPersistenceIT`, `PurgeOutboxApiTest` → 15 tests, 0 failures.

## Follow-ups (hors Phase 5)

- Phase 7 check implementation / Phase 8 testing / Phase 9 review.
- Recovery `PROCESSING` coincé ; GC orphelins store (Q9 = non) ;
  backoff `nextAttemptAt`.
