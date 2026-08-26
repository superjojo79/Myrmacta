---
phase: testing
title: Testing Strategy
description: Define testing approach, test cases, and quality assurance
status: planned
created: 2026-08-26
updated: 2026-08-26
feature: reporting-sla
---

# Testing Strategy — `reporting-sla`

## Test Coverage Goals

- Backend Jacoco **BUNDLE** LINE ≥ 85 % / BRANCH ≥ 75 % (`./mvnw verify`).
- Nouvelles classes reporting : viser 100 % des branches métier
  (`SlaClock`, `SlaReportAssembler`, use cases).
- Frontend : `npm run test` (Vitest) **et** `npm run test:e2e`
  (Playwright) — les deux obligatoires.
- OpenAPI golden régénéré, test enabled/disabled inchangé hors chemins
  nouveaux.

## Unit Tests

### `SlaClock` / `SlaReportAssembler`

- [ ] **UT-CLOCK-1** Clos : elapsed = closedAt - createdAt.
- [ ] **UT-CLOCK-2** Ouvert : elapsed = now - createdAt.
- [ ] **UT-CLOCK-3** ARCHIVE sans closedAt : fin = updatedAt (A-ARCHIVE-CLOCK).
- [ ] **UT-ASM-1** Types sans cible exclus du % conformité.
- [ ] **UT-ASM-2** Hors délai = elapsedHours > slaTargetHours.
- [ ] **UT-ASM-3** Dénominateur 0 → compliancePercent null, overdue 0.
- [ ] **UT-ASM-4** Médiane paire/impaire.
- [ ] **UT-ASM-5** Delta mean vs période précédente.
- [ ] **UT-ASM-6** Tendance volume ; stable si ~0 ; précédent 0 et courant > 0 → +100 % convention **documentée dans le test**.
- [ ] **UT-ASM-7** Parts % somment à ~100 (arrondi dernier type).
- [ ] **UT-ASM-8** Manager « Non assignés » slaPercent null.
- [ ] **UT-ASM-9** Step means en heures.

### Use cases

- [ ] **UT-GET-1** TA/WA : allowedTypeCodes null (unrestricted).
- [ ] **UT-GET-2** DM : filtre ACL VIEW ; vide → snapshot vide pas 403.
- [ ] **UT-GET-3** Viewer / Service → AccessDeniedException.
- [ ] **UT-GET-4** from ≥ to → IllegalArgumentException / 400 mapping.
- [ ] **UT-GET-5** durée > 366 j → 400.
- [ ] **UT-GET-6** défaut 30 j si from/to null.
- [ ] **UT-GET-7** settings absents → target 95.
- [ ] **UT-GET-8** cap 10k → exception dédiée.
- [ ] **UT-SET-1** Upsert TA OK ; non-TA denied.
- [ ] **UT-SET-2** percent 0 ou 101 rejeté.
- [ ] **UT-TYPE-1** Create/Update accepte slaTargetHours > 0 et null ; rejette 0 et négatif.

### Inbox remaining

- [ ] **UT-INBOX-1** Cible présente → remaining = target - elapsed (peut être négatif).
- [ ] **UT-INBOX-2** Pas de cible → null.
- [ ] **UT-INBOX-3** Pas de dossier lié → null.

## Integration Tests

- [ ] **IT-JPA-1** `SlaReportQuery` charge la cohorte createdAt et counts PRODUCED_DOC.
- [ ] **IT-API-1** `GET /api/reports/sla` 200 TA, JSON chemins KPI.
- [ ] **IT-API-2** 403 viewer (MockMvc oidcLogin).
- [ ] **IT-API-3** 400 période inversée.
- [ ] **IT-API-4** CSV content-type + anti-formule sur un label type `=CMD`.
- [ ] **IT-API-5** GET settings 404 puis PUT 200 puis GET 200.
- [ ] **IT-API-6** PATCH type slaTargetHours round-trip.
- [ ] **IT-API-7** OpenAPI golden contient `/api/reports/sla` et sla-settings.
- [ ] **IT-INBOX-1** mine payload inclut slaRemainingHours.

## End-to-End Tests

- [ ] **E2E-SLA-1** Nav « Rapports & SLA » visible manager ; page titre ; cartes KPI mockées.
- [ ] **E2E-SLA-2** Export déclenche GET csv (route mock).
- [ ] **E2E-SLA-3** Planifier l'envoi disabled.
- [ ] **E2E-SLA-4** Viewer : pas de lien Rapports.
- [ ] **E2E-SLA-5** Inbox affiche « SLA » remaining quand le mock tâche le fournit.
- [ ] **E2E-9** **mis à jour** : Supervision toujours sans faux KPI ; **ne plus** exiger l'absence totale de « Rapports » (le lien existe). Conserver l'absence de KPI **sur Supervision**.

## Test Data

- Fixtures assembleur : 4 dossiers, 2 types (un sans cible), 1 PRODUCED_DOC.
- Playwright : étendre `mockSession` avec `GET /api/reports/sla` et
  `/api/tenant/sla-settings` ; CLAIM_TYPE + `slaTargetHours: 48`.

## Test Reporting & Coverage

```bash
./mvnw verify -Dmaven.compiler.release=21
cd frontend && npm run test
cd frontend && npm run test:e2e
```

Jacoco classe par classe sur `domain/reporting/**` et contrôleurs
`reports` / `sla-settings`. Frontend coverage seuils existants.

## Manual Testing

- Période custom dans le date picker SPA.
- Type filter.
- Viewer 403 network.

## Performance Testing

Pas de bench CI. Cap 10k testé unitairement (exception), pas en charge.

## Bug Tracking

Écarts trouvés en Phase 7/8 → TDD + cases testing cochées.
