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

Deux écarts **moyens** sur la SPA (période custom + filtre type) —
API complète, UI partielle. Pas de retour Phase 3 : le design est
juste ; ce sont des restes d’implémentation, non bloquants pour le
contrat MCP (`GET /summary`).

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
`api/slaReport.ts`, `api/slaSettings.ts`, `DossierTypesAdminView`
(heures + Retirer le SLA), `AppShell` `canSeeReports`,
`MobileTasksView` badge.

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
| `RapportsView` | Cartes, barres CSS, CSV, planifier disabled ; **pas** de filtre type ; `datetime-local` brut |
| Admin types | Bouton « Retirer le SLA » (flag) + objectif % |
| Nav / inbox UI | `canSeeReports` ; badge remaining |

## Deviations

| Sévérité | Écart | Traitement |
|---|---|---|
| **Moyenne** | SPA `/rapports` : aucun filtre `dossierTypeCode` alors que l’API et le design (« filtres ») le prévoient. | Follow-up UI (API déjà là). Non bloquant MCP. |
| **Moyenne** | Inputs `datetime-local` envoyés tels quels (`YYYY-MM-DDTHH:mm`, sans offset). `SlaPeriod` n’accepte que Instant ISO ou `YYYY-MM-DD`. Période **custom** SPA peut 400. Défaut 30 j (champs vides) OK. | Follow-up : convertir en Instant UTC ou dates civiles avant GET. |
| Basse | KPI/barres inlinés dans `RapportsView` au lieu des composants `SlaKpiCards` / etc. | Cosmétique ; comportement identique. |
| Basse | Clear SLA = bouton ligne, pas « vider le champ en édition ». | A-CLEAR-HOURS couvert. |
| Basse | 400 cohorte : detail `SLA cohort too large: N`, pas de propriété ProblemDetail `code=SLA_COHORT_TOO_LARGE`. | HTTP 400 présent ; code nommé optionnel. |
| Basse | `SlaReportAccess` hors package `usecase` (ArchUnit `*Impl`). | Documenté ; domaine pur. |
| Accepté (DD) | Port out multi-méthodes ; `Clock.systemUTC()` inline `ReportingConfig`. | DD-FACADE. |
| Basse | Inbox affiche des **heures**, pas « jours si ≥ 24 h » (A-HOURS). | Snapshot déjà en jours. |

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
- CSV anti-formule.
- Settings write **et** GET : TA only.
- DM : agrégats types VIEW seulement.
- `API_SERVICE` : 403 Spring sur les routes rapport.

## Follow-ups (hors Phase 7)

1. SPA : filtre type + normalisation `from`/`to` (civil ou Instant UTC).
2. Optionnel : `ProblemDetail.code = SLA_COHORT_TOO_LARGE`.
3. Phase 8 lifecycle (`dev-testing`) si demandée — preuves M8 déjà
   dans le testing doc (session précédente) ; rejouer verify en Phase 8.
4. FILE-ATTENTE / DEFERRED : « en revue » jusqu’au merge (A-NO-FLAG).

## Success criteria (code review, pas re-verify)

Couverts par le code : persist hours + clear ; settings TA 404/PUT ;
snapshot/CSV/summary authz ; SPA réelle CSS + schedule disabled ;
inbox remaining ; OpenAPI ; Liquibase 016.

Partiels : filtre type SPA ; date picker format.

Non rejoué ici : `./mvnw verify` / Vitest / Playwright (M8 session
précédente — Phase 8 les reprend).
