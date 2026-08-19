---
phase: testing
title: Testing Strategy
description: Define testing approach, test cases, and quality assurance
status: verified
created: 2026-08-19
updated: 2026-08-19
feature: retention-dossier-type
phase1: 2026-08-19
phase4_aligned: 2026-08-19
phase8: 2026-08-19
---

# Testing Strategy — `retention-dossier-type`

## Test Coverage Goals

- Domaine + adapters : Jacoco global `./mvnw verify` (seuil projet ≥ 80 %).
- Frontend unit : Vitest (`cd frontend && npm run test`).
- Frontend E2E : Playwright (`cd frontend && npm run test:e2e`) — **obligatoire**
  (surface admin SPA).

## Unit Tests

### `RetentionPolicyResolver`

- [x] Héritage → jours = défaut tenant.
- [x] Override jours → jours type.
- [x] `NEVER` → empty (pas d’expiration).
- [x] Défaut tenant ≤ 0 rejeté à la construction settings (pas au resolver).

### `TenantRetentionSettings` / upsert use case

- [x] Upsert refuse `defaultRetentionDays ≤ 0`.
- [x] Non-`TENANT_ADMIN` → accès refusé (`TenantAdminGuard`).

### Create / Update `DossierType` (rétention)

- [x] `retentionNever=true` + `retentionDays` non null → erreur validation.
- [x] `retentionDays ≤ 0` → erreur.
- [x] Persistance héritage / jours / NEVER round-trip.

### `EvaluateDossierRetentionUseCase`

- [x] Batch `CLOTURE` lus (limit) ; résolution politique en mémoire (D-SCAN).
- [x] `CLOTURE` expiré (`expiration <= now`) → `ARCHIVE` + `purgeEligibleAt` + outbox PENDING.
- [x] Non clos / `closedAt` null / NEVER / non expiré → inchangé.
- [x] Déjà outbox (unique dossier) → no-op idempotent.
- [x] Settings absents → no-op + `settingsAbsent=true` (job warn D-BOOTSTRAP).
- [x] `ARCHIVE` métier préexistant sans `purgeEligibleAt` → **non** repris
      (uniquement `CLOTURE`) — D-FORCE.
- [x] `updatedBy` = `system:retention-job`.

### Domaine `Dossier`

- [x] Extension `purgeEligibleAt` sur copie immuable (withRetentionArchive…).

### Job (`EvaluateDossierRetentionJobTest`)

- [x] Délègue `tenantId` + `batchSize` au use case.
- [x] Settings absent (`settingsAbsent`) — run complète sans exception.

## Integration Tests

### Persistence / Liquibase

- [x] `RetentionSchemaIT` — tables/colonnes `tenant_retention_settings`,
      `dossier_type.retention_*`, `dossier.purge_eligible_at`, `purge_outbox` + UNIQUE.
- [x] `RetentionPersistenceIT` — round-trip settings + type NEVER ; insert outbox
      idempotent (exists) ; seed `CLOTURE` expiré → evaluate → `ARCHIVE` + outbox.

### MockMvc (`RetentionApiTest`)

- [x] `PUT/GET /api/tenant/retention-settings` — 200 admin ; 403 autres rôles ;
      400 durée ≤ 0 ; 404 GET si absent (D-BOOTSTRAP).
- [x] Create/update type avec champs rétention — reflet dans GET (TENANT_ADMIN).
- [x] `DossierResponse` expose `purgeEligibleAt` null par défaut.

### Job (slice / IT)

- [x] Use case unitaire + `EvaluateDossierRetentionJobTest`.
- [x] `RetentionPersistenceIT.evaluate_seededExpiredCloture_*` — chemin job via UC
      réel (repos JPA), sans attendre le scheduler.

## End-to-End Tests

Fichiers : `frontend/e2e/pilotage.spec.ts` (E2E-RET-*),
`frontend/src/api/retentionSettings.spec.ts`,
`frontend/src/views/admin/DossierTypesAdminView.spec.ts`.

- [x] **E2E-RET-1** : admin ouvre settings rétention, PUT durée, reload GET OK.
- [x] **E2E-RET-2** : admin crée/édite un type avec override jours ou NEVER ;
      liste reflète les champs.
- [x] **E2E-RET-3** : non-admin (si mock rôle) n’accède pas à la mutation
      settings (ou UI masquée) — aligné patterns admin existants.

Mocks Playwright : handlers `/api/tenant/retention-settings` +
`/api/dossier-types`.

## Test Data

- Tenant settings `defaultRetentionDays=30`.
- Type `CLAIM` héritage ; type `LEGAL` `retentionDays=365` ; type `LOG`
  `retentionNever=true`.
- Dossier `CLOTURE` avec `closedAt = now - 31 days` (candidat).
- Dossier `CLOTURE` `closedAt = now - 1 day` (non candidat).

## Test Reporting & Coverage

Phase 8 — commandes :

```text
./mvnw verify -Dmaven.compiler.release=21
cd frontend && npm run test
cd frontend && CI=1 npm run test:e2e
```

## Manual Testing

Hors CI : PostgreSQL réel, job cron, vérifier absence d’appels stockage objet.

## Performance Testing

Scan indexé borné par `batch-size` ; pas de charge Flowable dédiée ici.

## Bug Tracking

`TODO.md` §22 pour la consommation outbox ; pas de fermeture §22 dans ce lot.

## Phase 5 evidence (2026-08-19)

```text
./mvnw verify -Dmaven.compiler.release=21
# BUILD SUCCESS — Jacoco checks met

cd frontend && npm run test
# Test Files  37 passed | Tests  114 passed

cd frontend && CI=1 npm run test:e2e
# 27 passed including E2E-RET-1/2/3
```

## Phase 8 evidence (pending fresh run)

À remplir après `./mvnw verify` + Vitest + Playwright dans cette session.
