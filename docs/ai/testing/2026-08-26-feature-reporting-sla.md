---
phase: testing
title: Testing Strategy
description: Define testing approach, test cases, and quality assurance
status: in-progress
created: 2026-08-26
updated: 2026-08-26
feature: reporting-sla
---

# Testing Strategy — `reporting-sla`

## Test Coverage Goals

- Backend Jacoco **BUNDLE** LINE ≥ 85 % / BRANCH ≥ 75 % (`./mvnw verify`).
- Nouvelles classes reporting : viser 100 % des branches métier
  (`SlaClock`, `SlaStatus`, `SlaPeriod`, assembleurs, use cases).
- Frontend : `npm run test` (Vitest) **et** `npm run test:e2e`
  (Playwright) — les deux obligatoires.
- OpenAPI golden régénéré (`/api/reports/sla/summary`,
  `clearSlaTargetHours`).

## Unit Tests

### `SlaClock` / `SlaReportAssembler`

- [x] **UT-CLOCK-1** Clos : elapsed = closedAt - createdAt.
- [x] **UT-CLOCK-2** Ouvert : elapsed = now - createdAt.
- [x] **UT-CLOCK-3** ARCHIVE sans closedAt : fin = updatedAt (A-ARCHIVE-CLOCK).
- [x] **UT-ASM-1** Types sans cible exclus du % conformité.
- [x] **UT-ASM-2** Hors délai = elapsedHours > slaTargetHours.
- [x] **UT-ASM-3** Dénominateur 0 → compliancePercent null, overdue 0.
- [x] **UT-ASM-4** Médiane paire/impaire.
- [x] **UT-ASM-5** Delta mean vs période précédente.
- [x] **UT-ASM-6** Tendance volume ; stable si ~0 ; précédent 0 et courant > 0 → +100 % convention **documentée dans le test**.
- [x] **UT-ASM-7** Parts % somment à ~100 (arrondi dernier type).
- [x] **UT-ASM-8** Manager « Non assignés » slaPercent null.
- [x] **UT-ASM-9** Step means en heures.

### `SlaStatus` / `SlaPeriod` / summary

- [x] **UT-STATUS-1** Sans cible → `null`.
- [x] **UT-STATUS-2** Ouvert sous 80 % → `ON_TRACK` ; ≥ 80 % et ≤ cible → `AT_RISK`.
- [x] **UT-STATUS-3** Ouvert `elapsed > cible` → `BREACHED`.
- [x] **UT-STATUS-4** Clos : jamais `AT_RISK`.
- [x] **UT-PERIOD-1** Défaut 30 j.
- [x] **UT-PERIOD-2** Date civile UTC : `from` minuit, `to` minuit suivant.
- [x] **UT-PERIOD-3** Instant ISO passthrough.
- [x] **UT-PERIOD-4** Inversé / > 366 j / parse invalide → IAE.
- [x] **UT-SUM-1** Groupement type + unscored.
- [x] **UT-SUM-2** Groupement statut (clé = nom d'enum).
- [x] **UT-SUM-3** `groupBy=day` rejeté.

### Use cases

- [x] **UT-GET-1** TA/WA : allowedTypeCodes null (unrestricted).
- [x] **UT-GET-2** DM : filtre ACL VIEW ; vide → snapshot vide pas 403.
- [x] **UT-GET-3** Viewer / Service → AccessDeniedException.
- [x] **UT-GET-4** from ≥ to → IllegalArgumentException / 400 mapping.
- [x] **UT-GET-5** durée > 366 j → 400.
- [x] **UT-GET-6** défaut 30 j si from/to null.
- [x] **UT-GET-7** settings absents → target 95.
- [x] **UT-GET-8** cap 10k → exception dédiée.
- [x] **UT-SUM-UC-1** `groupBy` omis → `dossierTypeCode`.
- [x] **UT-SUM-UC-2** `groupBy=week` → IAE, pas d'appel query.
- [x] **UT-SUM-UC-3** DM ACL VIEW.
- [x] **UT-SUM-UC-4** Viewer denied.
- [x] **UT-SUM-UC-5** cap 10k.
- [x] **UT-SET-1** Upsert TA OK ; non-TA denied.
- [x] **UT-SET-2** percent 0 ou 101 rejeté.
- [x] **UT-TYPE-1** Create/Update accepte slaTargetHours > 0 et null ; rejette 0 et négatif.
- [x] **UT-TYPE-2** `SlaTargetHoursPatch` keep / set / clear / 400 flag+valeur.

### Inbox remaining

- [x] **UT-INBOX-1** Cible présente → remaining = target - elapsed (peut être négatif).
- [x] **UT-INBOX-2** Pas de cible → null.
- [x] **UT-INBOX-3** Pas de dossier lié → null.

## Integration Tests

- [x] **IT-JPA-1** `SlaReportQuery` charge la cohorte createdAt et counts PRODUCED_DOC.
- [x] **IT-API-1** `GET /api/reports/sla` 200 TA, JSON chemins KPI.
- [x] **IT-API-2** 403 viewer (MockMvc oidcLogin).
- [x] **IT-API-3** 400 période inversée.
- [x] **IT-API-4** CSV content-type + anti-formule sur un label type `=CMD`.
- [x] **IT-API-5** GET settings 404 puis PUT 200 puis GET 200.
- [x] **IT-API-6** PATCH type slaTargetHours round-trip.
- [x] **IT-API-7** OpenAPI golden : `/api/reports/sla`, `/summary`, sla-settings, `clearSlaTargetHours`.
- [x] **IT-API-8** `GET /api/reports/sla/summary` 200 + `groupBy`.
- [x] **IT-API-9** `groupBy=week` → 400.
- [x] **IT-API-10** Snapshot `from`/`to` `YYYY-MM-DD`.
- [x] **IT-API-11** PATCH `clearSlaTargetHours` + 400 si combiné avec une valeur.
- [x] **IT-INBOX-1** mine payload inclut slaRemainingHours.

## End-to-End Tests

- [x] **E2E-SLA-1** Nav « Rapports & SLA » visible manager ; page titre ; cartes KPI mockées.
- [x] **E2E-SLA-2** Export déclenche GET csv (route mock).
- [x] **E2E-SLA-3** Planifier l'envoi disabled.
- [x] **E2E-SLA-4** Viewer : pas de lien Rapports.
- [x] **E2E-SLA-5** Inbox affiche « SLA » remaining quand le mock tâche le fournit.
- [x] **E2E-SLA-6** Admin « Retirer le SLA » envoie `{ clearSlaTargetHours: true }`.
- [x] **E2E-9** **mis à jour** : Supervision toujours sans faux KPI ; **ne plus** exiger l'absence totale de « Rapports » (le lien existe). Conserver l'absence de KPI **sur Supervision**.

Vitest : `dossierTypes.spec.ts` (clear flag) + `DossierTypesAdminView.spec.ts`.

## Test Data

- Fixtures assembleur : 4 dossiers, 2 types (un sans cible), 1 PRODUCED_DOC.
- Playwright : `mockSession` avec `GET /api/reports/sla` et
  `/api/tenant/sla-settings` ; CLAIM_TYPE + `slaTargetHours: 48`.

## Test Reporting & Coverage

```bash
./mvnw verify -Dmaven.compiler.release=21
cd frontend && npm run test
cd frontend && npm run test:e2e
```

Jacoco classe par classe sur `domain/reporting/**` et contrôleurs
`reports` / `sla-settings`. Frontend coverage seuils existants.

M8 : preuves fraîches de ces commandes avant de cocher Phase 8.

## Manual Testing

- Période custom dans le date picker SPA.
- Type filter.
- Viewer 403 network.

## Performance Testing

Pas de bench CI. Cap 10k testé unitairement (exception), pas en charge.

## Bug Tracking

IT snapshot `complianceTargetPercent` : isolation `DELETE` settings
avant assert 95 (ordre Surefire vs PUT 90).
