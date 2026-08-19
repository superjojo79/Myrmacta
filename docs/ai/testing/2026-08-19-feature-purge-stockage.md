---
phase: testing
title: Testing Strategy
description: Define testing approach, test cases, and quality assurance
status: ready
created: 2026-08-19
updated: 2026-08-19
feature: purge-stockage
phase1: 2026-08-19
phase3_aligned: 2026-08-19
requirements_phase2: 2026-08-19
todo_ref: TODO.md §22 · requirements/design purge-stockage
---

# Testing Strategy — `purge-stockage`

> Dérivé des success criteria requirements + design (job outbox, delete
> store, graphe PG). **D-FE :** pas de nouveau parcours SPA ; Phase 8 =
> non-régression suite E2E existante (pas de nouveaux specs Playwright
> dédiés purge).

## Test Coverage Goals

- **Unitaires domaine :** 100 % du use case purge + transitions status +
  garde `purgeEligibleAt`.
- **Adaptateurs storage :** `delete` FS + S3 (mock client) — happy path,
  missing = no-op, path escape / tenant mismatch.
- **IT persistence :** claim atomique, graphe delete, idempotence,
  `FAILED` sur erreur injectée.
- **ArchUnit / verify :** non-régression hexagone.
- **FE :** si zéro diff SPA → exemption Phase 8 documentée + suite E2E
  existante verte ; sinon Vitest + Playwright obligatoires
  (conventions FlowForge).

## Unit Tests

### `ProcessPurgeOutboxUseCaseImpl`

- [ ] Claim `PENDING` → charge dossier → delete storageRefs → delete PG →
      `COMPLETED`.
- [ ] Objet store absent → continue → `COMPLETED`.
- [ ] Dossier PG déjà absent → `COMPLETED` (idempotent).
- [ ] Échec `delete` store → `FAILED` ; PG **non** détruit.
- [ ] `purgeEligibleAt > now` → **skip sans claim** (reste `PENDING`).
- [ ] Lignes `FAILED` réinclues au batch suivant (A-RETRY-IMMEDIATE).
- [ ] Éléments sans `storageRef` → aucun appel store ; PG purgé.
- [ ] Relations source **et** cible touchées.
- [ ] Batch `limit` : au plus N claims.
- [ ] Claim concurrent échoué (`claim=false`) → ignore la ligne.

### `PurgeOutboxStatus` / record

- [ ] Tous les statuts sérialisables ; pas de régression insert §21
      `PENDING`.

### `DocumentStoragePort` contract (delete)

- [ ] FS : fichier présent supprimé ; absent no-op ; `..` / mauvais tenant
      → exception.
- [ ] S3 : `DeleteObject` appelé ; `NoSuchKey` → no-op succès.

## Integration Tests

- [ ] Liquibase 011 appliqué : statuts + index `(status, created_at)` (+
      `last_error` si D-ERROR-COL).
- [ ] IT : insert `PENDING` (comme rétention) → job/use case → dossier 404 /
      repo empty ; outbox `COMPLETED`.
- [ ] IT : double exécution → stable `COMPLETED`, pas d’exception.
- [ ] IT : claim race (deux threads) → une seule victoire.
- [ ] IT MockMvc : `GET /api/dossiers/{id}` 404 après purge (si contexte
      web IT disponible).
- [ ] Rétention non-régression : `EvaluateDossierRetention*` +
      `RetentionSchemaIT` toujours verts.

## End-to-End Tests

- [ ] **D-FE :** aucun nouveau spec Playwright purge ; exécuter
      `npm run test:e2e` (suite existante, dont E2E-RET-*) en non-régression
      avant claim Phase 8/9.
- [ ] Pas de parcours admin purge (D-UI / D-HTTP).

## Test Data

- Fixture dossier `ARCHIVE` + `purgeEligibleAt` passé + 1 `ATTACHMENT` + 1
  `NOTE` + 1 relation + 1 workflow link (+ PI mock).
- Outbox `DOSSIER_PURGE_REQUESTED` `PENDING` UNIQUE dossier.
- Storage FS temp dir (IT) / S3Client mock (unit).

## Test Reporting & Coverage

| Gate | Commande |
|---|---|
| Backend | `./mvnw verify -Dmaven.compiler.release=21` |
| Vitest | `cd frontend && npm run test` (si FE touché) |
| Playwright | `cd frontend && npm run test:e2e` (si FE touché **ou** preuve
  non-régression suite existante) |

Couverture : nouveau code use case + adapters delete + claim repo.

## Manual Testing

- Smoke local : configurer rétention courte → attendre job archive →
  attendre job purge → vérifier FS/S3 + PG.
- Runbook tenant-delete : relecture doc deployment (pas d’exécution destructive
  en CI).

## Performance Testing

- Hors scope charge : batch borné suffisant V1.
- Garde-fou : une passe ≤ `batch-size` claims ; pas de full-table scan sans
  index status.

## Bug Tracking

- Régressions §21 (outbox insert) = blocker.
- Orphelin store après `COMPLETED` = blocker (ordre D-ORDER violé).
- Double delete concurrent corrompant autre dossier = blocker.
