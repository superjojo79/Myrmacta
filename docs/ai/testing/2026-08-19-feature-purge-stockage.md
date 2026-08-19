---
phase: testing
title: Testing Strategy
description: Define testing approach, test cases, and quality assurance
status: draft
created: 2026-08-19
updated: 2026-08-19
feature: purge-stockage
phase1: 2026-08-19
todo_ref: TODO.md §22 · requirements purge-stockage (Q1 ouvert)
---

# Testing Strategy — `purge-stockage`

> Scénarios **stables** ci-dessous. Les cas job/claim/statuts se
> précisent **après Q1**. **Q6 = B** : SPA admin lecture → Vitest +
> Playwright **obligatoires** (détail specs Q10).

## Test Coverage Goals

- Unitaires domaine + adapters `delete` (Q2).
- IT persistence : graphe PG + idempotence + échec mid-flight.
- `./mvnw verify` (ArchUnit inclus).
- FE : `npm run test` + `npm run test:e2e` si impact SPA / mocks ; sinon
  preuve de non-régression suite existante (Q10).

## Unit Tests

### Port stockage (probable Q2)

- [ ] `delete` objet présent → absent.
- [ ] `delete` objet déjà absent → succès.
- [ ] `delete` ref invalide (tenant / `..`) → erreur.

### Use case purge (après Q1)

- [ ] Happy path : store + PG + signal de fin.
- [ ] Idempotence 2ᵉ passe.
- [ ] Échec store → pas de succès terminal.
- [ ] Éléments sans `storageRef` → skip store, PG ok.
- [ ] Relations source et cible.
- [ ] Garde `purgeEligibleAt` (si retenue).

## Integration Tests

- [ ] Liquibase si migration.
- [ ] GET dossier 404 après purge réussie.
- [ ] Non-régression `retention-dossier-type` (insert `PENDING`).

## End-to-End Tests

- [ ] Q10 : pas de nouveau spec **sauf** impact SPA.
- [ ] Suite existante (dont E2E-RET-*) verte.

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

## Manual Testing

- Smoke rétention courte → purge → FS/S3 + PG.
- Runbook tenant : relecture doc, pas d’exécution destructive en CI.

## Performance Testing

Hors scope charge V1 ; batch borné si job (Q1=B).

## Bug Tracking

- Orphelin store après succès terminal = blocker.
- Régression insert outbox §21 = blocker.
