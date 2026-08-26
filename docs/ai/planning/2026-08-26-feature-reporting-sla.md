---
phase: planning
title: Project Planning & Task Breakdown
description: Break down work into actionable tasks and estimate timeline
status: planned
created: 2026-08-26
updated: 2026-08-26
feature: reporting-sla
---

# Project Planning & Task Breakdown — `reporting-sla`

## Milestones

- [x] **M1** Domaine SLA : clock, assembleur, settings, extension `DossierType`
- [x] **M2** Persistence Liquibase 016 + JPA types/settings + query cohorte
- [x] **M3** Port Flowable (tâches actives + historic userTasks) + façade
- [x] **M4** Use cases + HTTP snapshot/CSV/settings + OpenAPI
- [x] **M5** Inbox `slaRemainingHours`
- [x] **M6** SPA Rapports + nav + admin types/settings
- [x] **M7** Inbox UI + E2E + docs produit
- [ ] **M8** `./mvnw verify` + Vitest + Playwright globaux

## Task Breakdown

### M1 — Fondation domaine

- [x] **T1.1** `SlaClock` + tests UT-CLOCK-*  
      Outcome : elapsed selon D-SLA-CLOCK / A-ARCHIVE-CLOCK.  
      Validation : test unitaire rouge puis vert.  
      Tests : UT-CLOCK-1..3
- [x] **T1.2** `SlaReportAssembler` + tests UT-ASM-*  
      Outcome : snapshot métier pur.  
      Tests : UT-ASM-1..9
- [x] **T1.3** `TenantSlaSettings` + validation 1..100  
      Tests : inclus UT-SET-2 au niveau modèle
- [x] **T1.4** `DossierType.slaTargetHours` + `inherit`/`withUpdate` + Create/Update commands  
      Tests : UT-TYPE-1 + DossierTypeTest étendu  
      Dépend : T1.3 non bloquant

### M2 — Persistence

- [x] **T2.1** Changelog `016-reporting-sla.yaml` (colonne, table, index) + master  
      Tests : context Spring existant charge Liquibase (IT persistence)
- [x] **T2.2** JPA `DossierType` mappe `slaTargetHours`  
      Tests : round-trip persistence type (étendre test existant)
- [x] **T2.3** `TenantSlaSettings` repo + entity  
      Dépend : T2.1
- [x] **T2.4** `SlaReportQuery` JPA : cohorte createdAt + count PRODUCED_DOC  
      Tests : IT-JPA-1  
      Dépend : T2.1

### M3 — Flowable

- [x] **T3.1** Adapter tâches actives (assignee, dossierId via variables/lien)  
      Tests : unit mock History/TaskService
- [x] **T3.2** Adapter historic userTask durations  
      Tests : unit mock
- [x] **T3.3** `SlaReportQueryFacade` compose JPA+Flowable ; dégrade Flowable (DD-FLOWABLE-DEGRADE)  
      Dépend : T2.4, T3.1, T3.2

### M4 — API

- [x] **T4.1** `GetSlaReportUseCaseImpl` authz/période/cap/défaut 95  
      Tests : UT-GET-1..8  
      Dépend : T1.2, T3.3
- [x] **T4.2** Get/Upsert settings use cases + controller  
      Tests : UT-SET-1, IT-API-5
- [x] **T4.3** `SlaReportController` JSON  
      Tests : IT-API-1..3
- [x] **T4.4** Extraire `CsvWriter` + `SlaReportExportController`  
      Tests : IT-API-4  
      Dépend : T4.1
- [x] **T4.5** DTOs types `slaTargetHours` + OpenAPI golden  
      Tests : IT-API-6, IT-API-7  
      Dépend : T1.4, T2.2
- [x] **T4.6** `ReportingConfig` beans + exception handler 400

### M5 — Inbox

- [x] **T5.1** Calcul remaining + DTO  
      Tests : UT-INBOX-1..3, IT-INBOX-1  
      Dépend : T1.1, T1.4

### M6 — SPA rapports + admin

- [x] **T6.1** API clients + types TS  
- [x] **T6.2** `RapportsView` + sous-composants + Vitest  
      Tests : coverage page  
      Dépend : T6.1
- [x] **T6.3** Nav + route ; `isDossierManager`  
      Tests : AppShell.spec (lien visible DM/TA, caché viewer)
- [x] **T6.4** Admin types champ SLA + bloc settings  
      Tests : DossierTypesAdminView.spec  
      Dépend : T4.2, T4.5

### M7 — Inbox UI + E2E + docs

- [x] **T7.1** Badge SLA `MobileTasksView`  
      Tests : MobileTasksView.spec
- [x] **T7.2** Playwright E2E-SLA-1..5 + E2E-9 actualisé  
      Tests : testing doc E2E-*
- [x] **T7.3** FILE-ATTENTE / DEFERRED / GUIDE-UTILISATEUR — écran Rapports en revue

### M8 — Vérification globale

- [ ] **T8.1** `./mvnw verify`  
- [ ] **T8.2** `cd frontend && npm run test` + `test:e2e`  
      Dépend : T7.2

## Dependencies

```
T1.1 → T1.2 → T4.1
T1.3 → T2.3 → T4.2
T1.4 → T2.2 → T4.5
T2.1 → T2.2, T2.3, T2.4
T2.4 + T3.1 + T3.2 → T3.3 → T4.1 → T4.3, T4.4
T4.1 + T6.1 → T6.2
T5.1 → T7.1
T6.2 + T6.3 + T7.1 → T7.2 → T8.*
```

M1–M3 surtout backend ; M6 parallèle dès que le JSON snapshot est figé (design).

## Timeline & Estimates

Pas de calendrier. Ordre technique ci-dessus. Effort : BC neuf + SPA
complète + extension types/inbox — invasif modéré (record `DossierType`
touché partout, tests à recompiler).

## Risks & Mitigation

| Risque | Mitigation |
|---|---|
| `DossierType` record : nombreux `new` tests | Factory `inherit` déjà là ; ajouter overload / dernier champ |
| Flowable lien tâche → dossier | Réutiliser le mapping existant inbox (`WorkflowEnginePort`) |
| H2 vs PG percentiles | Assembleur Java (**DD-ASSEMBLE-DOMAIN**) |
| E2E-9 casse | Actualiser explicitement, pas skip |
| CSV package-private | Extraire avant le 2e appelant |
| Cohorte mémoire | Cap 10k |

## Resources Needed

- JDK / Maven wrapper du dépôt ; Node frontend.
- Pas Docker requis (H2 + mocks Flowable).
- Maquette HTML = référence UX.

## Traceability testing → tasks

| Scénario | Tâche |
|---|---|
| UT-CLOCK-* | T1.1 |
| UT-ASM-* | T1.2 |
| UT-TYPE-1 | T1.4 |
| UT-SET-* | T1.3, T4.2 |
| UT-GET-* | T4.1 |
| IT-JPA-1 | T2.4 |
| IT-API-1..3 | T4.3 |
| IT-API-4 | T4.4 |
| IT-API-5 | T4.2 |
| IT-API-6..7 | T4.5 |
| UT/IT-INBOX | T5.1 |
| E2E-SLA-* / E2E-9 | T7.2 |
| Vitest SPA | T6.2, T6.3, T6.4, T7.1 |
| verify global | T8.1, T8.2 |
