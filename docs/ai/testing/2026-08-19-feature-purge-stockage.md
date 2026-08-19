---
phase: testing
title: Testing Strategy
description: Define testing approach, test cases, and quality assurance
status: verified
created: 2026-08-19
updated: 2026-08-19
feature: purge-stockage
phase1: 2026-08-19
phase5: 2026-08-19
phase8: 2026-08-19
phase9: 2026-08-19
todo_ref: TODO.md §22 · design Phase 3 reviewed
---

# Testing Strategy — `purge-stockage`

> Aligné design Phase 3. **Q10 = B** : E2E-PURGE-1..5 + suite existante.
> Phase 8 exécutée (trous Phase 7 refermés + preuves `verify`).

## Test Coverage Goals

- Unitaires domaine + adapters `delete` (Q2).
- IT persistence : graphe PG + idempotence + échec mid-flight.
- `./mvnw verify` (ArchUnit inclus).
- FE : `cd frontend && npm run test` **et** `npm run test:e2e` (Q10=B).

## Unit Tests

### Port stockage (probable Q2)

- [x] `delete` objet présent → absent.
- [x] `delete` objet déjà absent → succès.
- [x] `delete` ref invalide (tenant / `..`) → erreur.

### Use case purge (après Q1)

- [x] Happy path : store + PG + signal de fin.
- [x] Idempotence 2ᵉ passe.
- [x] Échec store → pas de succès terminal.
- [x] Éléments sans `storageRef` → skip store, PG ok.
- [x] Relations source et cible.
- [x] Garde `purgeEligibleAt` : skip **origin RETENTION** si futur ; origin
      ADMIN traité tout de suite (Q11).

## Integration Tests

- [x] Liquibase si migration.
- [x] GET dossier 404 après purge réussie.
- [x] Enqueue CLOTURE/ARCHIVE → PENDING origin ADMIN ; ouvert → 4xx.
- [x] GET `/api/purge-outbox` 200 admin / 403 autres ; `counts.processing`.
- [x] CSRF absent sur POST enqueue → 403.
- [x] Non-régression `retention-dossier-type` (insert `PENDING`).

## End-to-End Tests

- [x] **E2E-PURGE-1 :** `TENANT_ADMIN` voit l’écran lecture file (compteurs
      / liste PENDING/FAILED/COMPLETED).
- [x] **E2E-PURGE-2 :** non-admin : pas d’accès mutation / écran file.
- [x] **E2E-PURGE-3 :** pas de bouton force purge (Q6).
- [x] **E2E-PURGE-4 :** enqueue `CLOTURE`/`ARCHIVE` → PENDING visible.
- [x] **E2E-PURGE-5 :** enqueue dossier ouvert → 4xx (Q11).
- [x] Suite existante (dont E2E-RET-*) verte.

## Test Data

- Dossier `ARCHIVE` + `purgeEligibleAt` passé + ATTACHMENT + NOTE +
  relation + workflow link.
- Outbox `DOSSIER_PURGE_REQUESTED` `PENDING`.
- Store FS temp / S3 mock.

## Test Reporting & Coverage

```bash
./mvnw verify -Dmaven.compiler.release=21
cd frontend && npm run test
cd frontend && npm run test:e2e
```

Phase 5 (2026-08-19) :

```text
./mvnw verify -Dmaven.compiler.release=21
# BUILD SUCCESS — Tests run: 832, Failures: 0
# jacoco: All coverage checks have been met.

cd frontend && npm run test -- --pool=forks --maxWorkers=2
# Test Files  40 passed | Tests  120 passed

cd frontend && CI=1 npm run test:e2e
# 32 passed
```

## Phase 8 — files & gaps closed

Unitaires :

- `src/test/java/com/flowforge/domain/dossier/usecase/ProcessPurgeOutboxUseCaseImplTest.java`
  (happy path, skip RETENTION, ADMIN ignore clock, store fail, idempotence,
  claim perdu, échec PG après store)
- `src/test/java/com/flowforge/domain/dossier/usecase/EnqueueDossierPurgeUseCaseImplTest.java`
  (CLOTURE, ARCHIVE, UNIQUE, ouvert, 404, non-admin)
- `src/test/java/com/flowforge/domain/dossier/usecase/ListPurgeOutboxUseCaseImplTest.java`
  (counts dont processing, clamp 200, 403)
- `src/test/java/com/flowforge/infrastructure/storage/FilesystemStorageAdapterTest.java`
- `src/test/java/com/flowforge/infrastructure/storage/S3StorageAdapterTest.java`
- `src/test/java/com/flowforge/infrastructure/flowable/FlowableWorkflowEngineAdapterDeleteTest.java`
- `src/test/java/com/flowforge/infrastructure/dossier/PurgeOutboxJobTest.java`

IT / MockMvc :

- `src/test/java/com/flowforge/infrastructure/web/dossier/PurgeOutboxApiTest.java`
- `src/test/java/com/flowforge/infrastructure/persistence/dossier/PurgeGraphPersistenceIT.java`
- `src/test/java/com/flowforge/infrastructure/persistence/dossier/RetentionSchemaIT.java`

Frontend :

- `frontend/src/api/purgeOutbox.spec.ts`
- `frontend/src/views/admin/PurgeOutboxAdminView.spec.ts`
- `frontend/src/components/dossier/DossierPurgeRequestButton.spec.ts`
- `frontend/e2e/pilotage.spec.ts` (E2E-PURGE-1..5)

## Phase 8 evidence (2026-08-19)

Targeted (avant verify) :

```text
./mvnw -Dmaven.compiler.release=21 -Dskip.frontend=true \
  -Dtest=ProcessPurgeOutboxUseCaseImplTest,ListPurgeOutboxUseCaseImplTest,EnqueueDossierPurgeUseCaseImplTest,PurgeGraphPersistenceIT,PurgeOutboxApiTest test
# Tests run: 27, Failures: 0 — BUILD SUCCESS
```

Full gate (cette session) :

```text
./mvnw verify -Dmaven.compiler.release=21
# BUILD SUCCESS — Tests run: 839, Failures: 0
# jacoco: All coverage checks have been met.

cd frontend && npm run test -- --pool=forks --maxWorkers=2
# Test Files  40 passed | Tests  120 passed

cd frontend && CI=1 npm run test:e2e
# 32 passed (E2E-PURGE-1..5)
```

## Phase 9 evidence (2026-08-19)

```text
./mvnw verify -Dmaven.compiler.release=21
# BUILD SUCCESS — Tests run: 839, Failures: 0
# jacoco: All coverage checks have been met.

cd frontend && npm run test -- --pool=forks --maxWorkers=2
# Test Files  40 passed | Tests  120 passed

cd frontend && CI=1 npm run test:e2e
# 32 passed (E2E-PURGE-1..5)
```

## Manual Testing

- Smoke rétention courte → purge → FS/S3 + PG.
- Runbook tenant : relecture doc, pas d’exécution destructive en CI.

## Performance Testing

Hors scope charge V1 ; batch borné si job (Q1=B).

## Bug Tracking

- Orphelin store après succès terminal = blocker.
- Régression insert outbox §21 = blocker.
- `RetentionSchemaIT` : ne pas utiliser `pg_indexes` (tests H2) —
  `information_schema.indexes`.
- Liste MockMvc : `hasItem` plutôt que `items[0]` (autres PENDING).
