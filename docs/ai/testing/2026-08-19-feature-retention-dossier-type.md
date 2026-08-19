---
phase: testing
title: Testing Strategy
description: Define testing approach, test cases, and quality assurance
status: draft
created: 2026-08-19
updated: 2026-08-19
feature: retention-dossier-type
phase1: 2026-08-19
---

# Testing Strategy — `retention-dossier-type`

## Test Coverage Goals

- Domaine + adapters : Jacoco global `./mvnw verify` (seuil projet ≥ 80 %).
- Frontend unit : Vitest (`cd frontend && npm run test`).
- Frontend E2E : Playwright (`cd frontend && npm run test:e2e`) — **obligatoire**
  (surface admin SPA).

## Unit Tests

### `RetentionPolicyResolver`

- [ ] Héritage → jours = défaut tenant.
- [ ] Override jours → jours type.
- [ ] `NEVER` → empty (pas d’expiration).
- [ ] Défaut tenant ≤ 0 rejeté à la construction settings (pas au resolver).

### `TenantRetentionSettings` / upsert use case

- [ ] Upsert refuse `defaultRetentionDays ≤ 0`.
- [ ] Non-`TENANT_ADMIN` → accès refusé (`TenantAdminGuard`).

### Create / Update `DossierType` (rétention)

- [ ] `retentionNever=true` + `retentionDays` non null → erreur validation.
- [ ] `retentionDays ≤ 0` → erreur.
- [ ] Persistance héritage / jours / NEVER round-trip.

### `EvaluateDossierRetentionUseCase`

- [ ] Batch `CLOTURE` lus (limit) ; résolution politique en mémoire (D-SCAN).
- [ ] `CLOTURE` expiré (`expiration <= now`) → `ARCHIVE` + `purgeEligibleAt` + outbox PENDING.
- [ ] Non clos / `closedAt` null / NEVER / non expiré → inchangé.
- [ ] Déjà outbox (unique dossier) → no-op idempotent.
- [ ] Settings absents → no-op.
- [ ] `ARCHIVE` métier préexistant sans `purgeEligibleAt` → **non** repris
      (uniquement `CLOTURE`) — D-FORCE.
- [ ] `updatedBy` = `system:retention-job`.

### Domaine `Dossier`

- [ ] Extension `purgeEligibleAt` sur copie immuable (withRetentionArchive…).

## Integration Tests

### Persistence / Liquibase

- [ ] Migration crée colonnes type/dossier + table `purge_outbox`.
- [ ] Round-trip settings + type + outbox (H2 MODE=PostgreSQL).

### MockMvc

- [ ] `PUT/GET /api/tenant/retention-settings` — 200 admin ; 403 autres rôles ;
      400 durée ≤ 0 ; 404 GET si absent (si D-BOOTSTRAP retenu).
- [ ] Create/update type avec champs rétention — reflet dans GET.
- [ ] `DossierResponse` expose `purgeEligibleAt` null par défaut.

### Job (slice / IT)

- [ ] Scheduler/use case branché : un dossier seed `CLOTURE` expiré devient
      `ARCHIVE` + 1 ligne outbox.

## End-to-End Tests

- [ ] **E2E-RET-1** : admin ouvre settings rétention, PUT durée, reload GET OK.
- [ ] **E2E-RET-2** : admin crée/édite un type avec override jours ou NEVER ;
      liste reflète les champs.
- [ ] **E2E-RET-3** : non-admin (si mock rôle) n’accède pas à la mutation
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

À remplir Phase 8 avec sorties fraîches :

```text
./mvnw verify -Dmaven.compiler.release=21
cd frontend && npm run test
cd frontend && npm run test:e2e
```

## Manual Testing

Hors CI : PostgreSQL réel, job cron, vérifier absence d’appels stockage objet.

## Performance Testing

Scan indexé borné par `batch-size` ; pas de charge Flowable dédiée ici.

## Bug Tracking

`TODO.md` §22 pour la consommation outbox ; pas de fermeture §22 dans ce lot.
