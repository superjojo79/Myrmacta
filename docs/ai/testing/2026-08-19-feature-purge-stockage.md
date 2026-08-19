---
phase: testing
title: Testing Strategy
description: Define testing approach, test cases, and quality assurance
status: draft
created: 2026-08-19
updated: 2026-08-19
feature: purge-stockage
phase1: 2026-08-19
todo_ref: TODO.md §22 · requirements purge-stockage (Q1–Q10 actées)
---

# Testing Strategy — `purge-stockage`

> Scénarios alignés Q1–Q10. **Q10 = B** : specs Playwright dédiés + suite
> existante.

## Test Coverage Goals

- Unitaires domaine + adapters `delete` (Q2).
- IT persistence : graphe PG + idempotence + échec mid-flight.
- `./mvnw verify` (ArchUnit inclus).
- FE : `cd frontend && npm run test` **et** `npm run test:e2e` (Q10=B).

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

- [ ] **E2E-PURGE-1 :** `TENANT_ADMIN` voit l’écran lecture file (compteurs
      / liste PENDING/FAILED/COMPLETED).
- [ ] **E2E-PURGE-2 :** non-admin : pas d’accès mutation / écran file.
- [ ] **E2E-PURGE-3 :** pas de bouton force purge (Q6).
- [ ] **E2E-PURGE-4 :** enqueue API/mock → ligne PENDING visible (Q7).
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
