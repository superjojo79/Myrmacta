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
phase7: 2026-08-19
phase8: 2026-08-19
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

## Phase 7 remediations (2026-08-19)

Revue fichier-par-fichier vs design reviewed. Pas de trou logique bloquant.
Tests manquants (overclaim Phase 5) ajoutés en Phase 8 :

| Gap | Fix |
|---|---|
| GET dossier 404 après purge | `PurgeOutboxApiTest.enqueueThenProcess_getDossierReturns404` |
| Relation **cible** | `PurgeGraphPersistenceIT` : relation inverse `other → dossier` |
| Enqueue `ARCHIVE` HTTP | `enqueuePurge_archive_returns201` + unit `enqueue_archive_*` |
| CSRF POST | `enqueuePurge_withoutCsrf_returns403` |
| `counts.processing` | assertion MockMvc + Vitest + E2E-PURGE-1 |
| Claim perdu / échec PG après store | unit `ProcessPurgeOutboxUseCaseImplTest` |
| Limite liste 200 | `ListPurgeOutboxUseCaseImplTest.list_clampsLimitTo200` |

## Design deviations (residual)

| Id | Severity | Note |
|---|---|---|
| HTTP idempotent | info | Design 204 **ou** 200 ; livré **200** si UNIQUE déjà présent. |
| PROCESSING crash | low | Claim sans recovery V1 : une ligne peut rester `PROCESSING` après crash JVM. Accepté. |
| Enqueue UI | info | Bouton sur le **détail dossier**, pas sur la page file (Q6 lecture seule ; A-ENQUEUE-UI). |
| CHECK status | info | `status` reste `varchar(32)` sans CHECK DB (010) ; élargissement applicatif seulement. Design mentionnait « CHECK/enum élargi ». |
| Controllers | info | Design « 2 controllers » ; livré **1** `PurgeOutboxController` (GET + POST). |
| Logs NFR | low | Job logue le **compte** traité, pas `outboxId`/`dossierId` par ligne. Pas de dump `storageRef`. Accepté V1 (`observabilite` §29). |

## Phase 7 alignment (2026-08-19)

Verdict : **implementation covers all Q1–Q11 / D-\*** ; remediations = tests, pas de changement produit.

| Décision | Statut |
|---|---|
| Q1 D-MECH job outbox | OK — `PurgeOutboxJob` → `processBatch` |
| Q2 D-PORT `delete` S3+FS | OK — missing = succès ; jamais MinIO |
| Q3 D-ORDER store → PG | OK — store fail → pas de deletes PG |
| Q4 D-SCOPE-PG graphe | OK — éléments, relations src\|tgt, links, dossier |
| Q5 D-WF best-effort | OK — adapter Flowable ; links toujours supprimés |
| Q6 D-UI lecture | OK — pas de force/retry ; enqueue hors file |
| Q7 POST enqueue | OK — pas de purge dans le thread HTTP |
| Q8 runbook tenant | OK — pas d’API destroy ; doc deployment |
| Q9 pas de GC orphelins | OK |
| Q10 E2E-PURGE-1..5 | OK |
| Q11 CLOTURE/ARCHIVE + ADMIN ignore horloge | OK |
| D-STATUS / D-ORIGIN / D-CLAIM / D-RETRY | OK |
| D-KEEP-OUTBOX / D-ERROR-COL / D-SCHED | OK |
| D-ELIGIBLE-GUARD skip RETENTION only | OK — skip **avant** claim |
| Hexagone | OK — domaine sans Spring ; beans `DossierConfig` |

Sécurité : `@PreAuthorize("hasRole('TENANT_ADMIN')")` + `TenantAdminGuard` ;
CSRF BFF sur POST ; `storageRef` validé (tenant / `..`) ; liste bornée 200 ;
`last_error` trunc 1024. Pas d’IDOR cross-tenant (schéma instance).

## Evidence

- Phase 5 : `./mvnw verify -Dmaven.compiler.release=21` SUCCESS — **832** tests.
- Phase 7/8 targeted : **27** tests, 0 failures.
- Phase 8 gate (cette session) :

```text
./mvnw verify -Dmaven.compiler.release=21
# BUILD SUCCESS — Tests run: 839, Failures: 0
# jacoco: All coverage checks have been met.

cd frontend && npm run test -- --pool=forks --maxWorkers=2
# Test Files  40 passed | Tests  120 passed

cd frontend && CI=1 npm run test:e2e
# 32 passed (E2E-PURGE-1..5 + suite existante)
```

## Follow-ups (hors Phase 7–8)

- Phase 9 review.
- Recovery `PROCESSING` coincé ; backoff `nextAttemptAt` ; logs par `outboxId`
  (§29).
