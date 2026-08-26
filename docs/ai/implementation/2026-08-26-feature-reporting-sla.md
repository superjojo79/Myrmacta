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
  model/          SlaReport, TenantSlaSettings, SlaClock, assembler
  port.in/        GetSlaReportUseCase, Get/UpsertTenantSlaSettingsUseCase
  port.out/       SlaReportQueryPort, TenantSlaSettingsRepository
  usecase/        *Impl
com.flowforge.infrastructure
  config/ReportingConfig
  persistence/dossier/  sla hours + TenantSlaSettingsJpa*
  reporting/            SlaReportQueryFacade, Flowable adapter
  web/reporting/        controllers
  web/csv/CsvWriter     extrait de dossier
```

Frontend : `views/pilotage/RapportsView.vue`, `api/slaReport.ts`,
`api/slaSettings.ts`.

## Implementation Notes

Suivre le design DD-*. Ne pas annoter le domaine. Ne pas introduire
Chart.js. Extraire `CsvWriter` **avant** le contrôleur CSV rapport
pour ne pas casser `DossierExportController`.

`DossierType` : `slaTargetHours` en **dernier** paramètre du record ;
`inherit` passe `null` ; PATCH HTTP `null` = keep (pas de clear V1).

Inbox : champ `slaRemainingHours` sur le DTO existant.

**Écart design (acceptable) :** le port out est exposé en méthodes
(`countDossiers` / `findDossiers` / Flowable) plutôt qu'un unique
`loadCohort(SlaCohortQuery)`. La façade infra
`SlaReportQueryFacade` reste le seul adaptateur du use case
(`DD-ONE-PORT`). `Clock.systemUTC()` est inline dans
`ReportingConfig` (pas de bean `Clock` global).

## Integration Points

- ACL : copier le split `SearchDossiersUseCaseImpl` (TA/WA vs
  `aclRepository.findBySubject`).
- Flowable : réutiliser la façon dont l'inbox résout le dossier
  (`WorkflowEnginePort` / variables).
- OpenAPI : régénérer golden via le test existant.

## Error Handling

| Cas | HTTP |
|---|---|
| Viewer / service | 403 AccessDenied (existant) |
| Période invalide / > 366 j / cap 10k | 400 ProblemDetail |
| Settings GET absent | 404 |
| slaTargetHours ≤ 0 | 400 validation type |

Logs : WARN si Flowable échoue (dégrade), jamais `Dossier.fields()`.

## Performance Considerations

Index `created_at`. Cap 10k. Pas de N+1 : une liste dossiers, un
group-by elements, queries Flowable bornées aux process ids de la
cohorte.

## Security Notes

- Pas de champs dossier dans le rapport.
- CSV anti-formule.
- Settings write TA only (`TenantAdminGuard`).
- DM : agrégats seulement sur types VIEW.
