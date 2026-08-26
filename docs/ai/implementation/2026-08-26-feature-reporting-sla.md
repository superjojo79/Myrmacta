---
phase: implementation
title: Implementation Guide
description: Technical implementation notes, patterns, and code guidelines
status: in-progress
created: 2026-08-26
updated: 2026-08-26
feature: reporting-sla
---

# Implementation Guide — `reporting-sla`

## Development Setup

Workspace cloud : branche `cursor/reporting-sla-9009`, checkout
`/workspace` (no-worktree). Stack : Java 25 / Spring Boot 4.1 /
Vue 3. Commands : `./mvnw verify -Dmaven.compiler.release=21`,
`cd frontend && npm run test`, `npm run test:e2e`.

TDD obligatoire (skill `tdd`) : rouge → vert → refactor par tâche.

## Code Structure

```
com.flowforge.domain.reporting
  model/          SlaClock, SlaStatus, SlaPeriod, SlaReport*, SlaSummary*
  port.in/        GetSlaReportUseCase, GetSlaSummaryUseCase, settings
  port.out/       SlaReportQueryPort, TenantSlaSettingsRepository
  usecase/        *Impl + SlaReportAccess (ACL partagée)
com.flowforge.infrastructure
  config/ReportingConfig
  persistence/dossier/  sla hours + TenantSlaSettingsJpa*
  reporting/            SlaReportQueryFacade, Flowable adapter
  web/reporting/        snapshot, summary, CSV, settings
  web/dossier/          SlaTargetHoursPatch (DD-CLEAR-FLAG)
  web/csv/CsvWriter
```

Frontend : `views/pilotage/RapportsView.vue`, `api/slaReport.ts`,
`api/slaSettings.ts`, `DossierTypesAdminView` (heures + clear).

## Implementation Notes

Suivre le design DD-*. Ne pas annoter le domaine. Ne pas introduire
Chart.js.

`DossierType.slaTargetHours` dernier paramètre du record. PATCH :
omis = **keep** ; `clearSlaTargetHours: true` = persist `null` ;
flag + valeur → 400. **Pas** `null` JSON = clear.

Période : query params `String` → `SlaPeriod.resolve` (ISO Instant
ou `YYYY-MM-DD` UTC ; `to` civil = minuit lendemain exclusif).

Summary : réutilise `countDossiers` / `findDossiers` puis
`SlaSummaryAssembler` (**DD-SUMMARY-REUSE**). `groupBy` omis →
`dossierTypeCode` ; `day`/`week`/`month` → 400.

Inbox : champ `slaRemainingHours` sur le DTO existant.

**Écart design (acceptable) :** le port out est exposé en méthodes
(`countDossiers` / `findDossiers` / Flowable) plutôt qu'un unique
`loadCohort`. `Clock.systemUTC()` inline dans `ReportingConfig`.

## Integration Points

- ACL : `SlaReportAccess` (même split TA/WA vs VIEW type).
- OpenAPI : golden `openapi/openapi.yaml` régénéré
  (`-Dopenapi.update=true`).

## Error Handling

| Cas | HTTP |
|---|---|
| Viewer / service | 403 AccessDenied |
| Période invalide / > 366 j / cap 10k / groupBy calendaire | 400 |
| Settings GET absent | 404 |
| slaTargetHours ≤ 0 | 400 |
| clearSlaTargetHours + slaTargetHours | 400 |

## Performance Considerations

Index `created_at`. Cap 10k. Summary = même liste dossiers, groupement
en mémoire.

## Security Notes

- Pas de champs dossier dans le rapport.
- CSV anti-formule.
- Settings write TA only (`TenantAdminGuard`).
- DM : agrégats seulement sur types VIEW.
- GET summary : mêmes rôles que le snapshot.
