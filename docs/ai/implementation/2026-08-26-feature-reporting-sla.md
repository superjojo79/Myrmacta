---
phase: implementation
title: Implementation Guide
description: Technical implementation notes, patterns, and code guidelines
status: complete
created: 2026-08-26
updated: 2026-08-26
feature: reporting-sla
phase5: 2026-08-26
phase7: 2026-08-26
phase8: 2026-08-26
phase9: 2026-08-26
---

# Implementation Guide — `reporting-sla`

Phase 7 **Check Implementation** (2026-08-26) contre design Phase 3
(`reviewed`) et requirements Phase 2 (`reviewed`). Lint feature docs
OK ; branche `feature-reporting-sla` MISS attendu. Task tracing
indisponible (`unknown command 'task'`).

## Alignment status

**Aligné** sur les D-* et le contrat BFF (snapshot + summary + settings
+ PATCH clear + SPA `/rapports` + inbox). Aucun écart d’architecture
(hexagone, ACL type VIEW, pas Chart.js, pas de table de faits).

Phase 8 a fermé les deux écarts SPA moyens (filtre type + Instant UTC)
et le code ProblemDetail `SLA_COHORT_TOO_LARGE`. Pas de retour Phase 3.

## Development Setup

Workspace cloud : branche `cursor/reporting-sla-9009`, checkout
`/workspace` (no-worktree). Stack : Java 25 / Spring Boot 4.1 /
Vue 3. Commands : `./mvnw verify -Dmaven.compiler.release=21`,
`cd frontend && npm run test`, `npm run test:e2e`.

## Design recap (ce qui devait exister)

- **A+B** : snapshot maquette + summary `ON_TRACK`/`AT_RISK`/`BREACHED`.
- Authz **C** : TA/WA unrestricted ; DM ACL VIEW ; viewer/service 403.
- Horloge `SlaClock` ; statut `SlaStatus` 80 % entier ; période
  `SlaPeriod` 30 j / 366 j / date civile UTC exclusive.
- PATCH **DD-CLEAR-FLAG** ; settings TA ; cap 10k ; CSV anti-formule ;
  inbox `slaRemainingHours` ; nav `canSeeReports` ; planifier disabled.

## Code Structure (livré)

```
com.flowforge.domain.reporting
  SlaReportAccess.java          ACL partagée (hors usecase — ArchUnit *Impl)
  model/   SlaClock, SlaStatus, SlaPeriod, SlaReport*, SlaSummary*
  port.in/ GetSlaReportUseCase, GetSlaSummaryUseCase, Get/UpsertTenantSlaSettings
  port.out/SlaReportQueryPort, TenantSlaSettingsRepository
  usecase/ *Impl
com.flowforge.infrastructure
  config/ReportingConfig
  persistence/dossier/  sla hours + TenantSlaSettingsJpa* + JpaSlaReportQueryAdapter
  reporting/SlaReportQueryFacade
  flowable/FlowableSlaReportAdapter   DD-FLOWABLE-DEGRADE
  web/reporting/  snapshot, summary, CSV, settings
  web/dossier/    SlaTargetHoursPatch
  web/csv/CsvWriter
db/changelog/changes/016-reporting-sla.yaml
```

Frontend : `RapportsView.vue` (KPI + barres CSS inlinés),
`api/slaPeriod.ts` (`toSlaPeriodParam`), `api/slaReport.ts`,
`api/slaSettings.ts`, `DossierTypesAdminView` (heures + Retirer le SLA),
`AppShell` `canSeeReports`, `MobileTasksView` badge.

## File-by-file vs design

| Composant | Verdict |
|---|---|
| `SlaClock` / `elapsedHoursInt` / overdue / remainingHours | Aligné D-SLA-CLOCK, A-ARCHIVE-CLOCK, A-TASK-REMAINING |
| `SlaStatus.of` | Aligné D-AT-RISK + A-AT-RISK-CMP ; clos sans AT_RISK |
| `SlaPeriod.resolve` | Aligné DD-PERIOD-PARSE, D-PERIOD |
| `SlaReportAssembler.compliancePercent` | Aligné A-COMPLIANCE (non-overdue / types avec cible) ; overdueCount = BREACHED |
| `SlaSummary` / `SlaSummaryAssembler` | Shape + groupBy défaut/400 alignés ; DD-SUMMARY-REUSE |
| `SlaReportAccess` + GET snapshot/summary/CSV | D-AUTHZ = C ; vide ACL → 200 vide |
| `GetSlaReportUseCaseImpl` / `GetSlaSummaryUseCaseImpl` | Cap 10k ; période via `SlaPeriod` ; Query `String` from/to |
| `SlaReportController` `/summary` | Aligné ; `@PreAuthorize` TA/WA/DM |
| `SlaReportExportController` | CSV agrégats + `CsvWriter` ; pas de fields |
| `SlaTargetHoursPatch` + PATCH types | DD-CLEAR-FLAG ; TA only (contrôleur types) |
| `TenantSlaSettingsController` | GET/PUT TA only (DD-SETTINGS-TA) ; 404 si absent |
| `016-reporting-sla.yaml` | Colonne hours, table settings, `idx_dossier_created_at` |
| `JpaSlaReportQueryAdapter` | `createdAt ∈ [from,to[` ; pas de filtre statut (A-COHORT-STATUS) |
| `SlaReportQueryFacade` + Flowable adapter | DD-FACADE ; WARN + listes vides si Flowable down |
| `ListMyTasksUseCaseImpl` | remaining via `SlaClock` + now (pas dueDate) |
| OpenAPI golden | `/api/reports/sla/summary`, `clearSlaTargetHours` |
| `RapportsView` | Cartes, barres CSS, CSV, planifier disabled ; filtre type ; `toSlaPeriodParam` (datetime-local → Instant UTC) |
| Admin types | Bouton « Retirer le SLA » (flag) + objectif % |
| Nav / inbox UI | `canSeeReports` ; badge remaining |

## Deviations

| Sévérité | Écart | Traitement |
|---|---|---|
| Clos (P8) | SPA filtre `dossierTypeCode` | Select types + E2E-SLA-8. |
| Clos (P8) | `datetime-local` brut → 400 | `toSlaPeriodParam` → Instant UTC ; E2E-SLA-7. |
| Clos (P8) | ProblemDetail sans `code` cohorte | `SlaCohortTooLargeException.CODE`. |
| Basse | KPI/barres inlinés dans `RapportsView` au lieu des composants `SlaKpiCards` / etc. | Cosmétique ; comportement identique. |
| Basse | Clear SLA = bouton ligne, pas « vider le champ en édition ». | A-CLEAR-HOURS couvert. |
| Basse | `SlaReportAccess` hors package `usecase` (ArchUnit `*Impl`). | Documenté ; domaine pur. |
| Accepté (DD) | Port out multi-méthodes ; `Clock.systemUTC()` inline `ReportingConfig`. | DD-FACADE. |
| Basse | Inbox affiche des **heures**, pas « jours si ≥ 24 h » (A-HOURS). | Snapshot déjà en jours. |
| Basse | `datetime-local` interprété comme UTC (pas fuseau navigateur). | Cohérent avec `Clock.systemUTC()`. |
| Basse | `/rapports` sans `meta.roles` (comme `/supervision`) ; lien masqué, API 403. | Pattern SPA existant. |

## Edge cases handled

- Cible absente → unscored / conformité hors dénominateur / remaining null.
- Clos : jamais AT_RISK ; ARCHIVE sans closedAt → updatedAt.
- `groupBy` omis → type ; calendaire → 400.
- Clear + valeur → 400 ; omit PATCH hours → keep.
- Flowable down → steps/managers vides, KPI dossiers OK.
- CSV anti-formule (`=CMD` → `'=CMD`).

## Error Handling

| Cas | HTTP |
|---|---|
| Viewer / service | 403 |
| Période invalide / > 366 j / cap 10k / groupBy calendaire | 400 |
| Settings GET absent | 404 |
| slaTargetHours ≤ 0 | 400 |
| clearSlaTargetHours + slaTargetHours | 400 |

## Security Notes

- Pas de `fields` dossier dans JSON/CSV rapport.
- CSV anti-formule (`CsvWriter` public + délégation dossier).
- Settings write **et** GET : TA only (`@PreAuthorize`).
- Snapshot/summary/CSV : TA/WA/DM Spring + ACL VIEW domaine (D-AUTHZ C).
- DM filtre type inconnu / hors ACL → cohorte vide 200, pas 403.
- `API_SERVICE` / viewer : 403.
- JPQL paramétré (`:tenantId`, `:from`, `:to`, `:codes`).
- Cap 10k avant `findDossiers`.

## Phase 9 Code Review (2026-08-26)

Revue holistique du diff `origin/main...HEAD` + checklist sécurité.
**Aucun finding bloquant.** Prêt pour revue humaine (PR draft, pas de merge).

### Checklist

| Critère | Verdict |
|---|---|
| Design match | Aligné A+B, D-AUTHZ C, D-PERIOD, DD-CLEAR-FLAG, DD-CAP-10K |
| Logic gaps | Période + filtre SPA fermés en Phase 8 |
| Security | Authz endpoints + ACL ; pas de fuite `fields` ; CSV anti-formule |
| Integration | Liquibase 016 additive (colonne nullable, table settings, index) |
| Tests | Preuves Phase 8 fraîches (1263 / 213 / 55) |
| Docs | requirements/design reviewed ; testing/implementation complete |

### Findings (non bloquants)

| Sévérité | Fichier | Issue | Recommandation |
|---|---|---|---|
| Basse | `FlowableSlaReportAdapter` | `taskService.createTaskQuery().active().list()` puis filtre tenant | Accepté V1 (D-COMPUTE A) ; pager Flowable plus tard |
| Basse | `RapportsView.vue` | KPI/barres inlinés | Extraire `SlaKpiCards` si la vue grossit |
| Basse | `slaPeriod.ts` | datetime-local = UTC | Documenté ; pas de TZ navigateur |
| Info | branche vs `origin/main` | `main` a avancé (`f65afe5`) depuis la base | Rebase/merge `main` avant merge humain |

### Security scan (catégories applicables)

Secrets : aucun. Injection : JPQL paramétré. Authz : PreAuthorize + domaine.
Logique : cap 10k, clear flag 400, DM type hors ACL → vide.
Exposition : agrégats seulement. Ressources : max 366 j + 10k. CSV anti-formule.

## Follow-ups

1. Rebase sur `main` avant merge si la base a divergé.
2. FILE-ATTENTE / DEFERRED : rester « en revue » jusqu’au merge (A-NO-FLAG).
3. Optionnel : extraire cartes KPI ; afficher remaining en jours si ≥ 24 h.

## Success criteria

Couverts : persist hours + clear ; settings TA 404/PUT ; snapshot/CSV/summary
authz ; SPA CSS + schedule disabled + filtre type + Instant UTC ; inbox
remaining ; OpenAPI ; Liquibase 016 ; code cohorte.

Verify Phase 8 (cette session) : `./mvnw verify` 1263, Vitest 213, Playwright 55.
