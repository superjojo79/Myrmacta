---
phase: planning
title: Project Planning & Task Breakdown
description: Break down work into actionable tasks and estimate timeline
status: complete
created: 2026-08-26
updated: 2026-08-26
feature: reporting-sla
phase4: 2026-08-26
---

# Project Planning & Task Breakdown — `reporting-sla`

Phase 4 **réalignée** sur requirements Phase 2 + design Phase 3.
M1–M7 = premier jet (snapshot/SPA). **M9–M11** = écarts A+B
(summary, `SlaStatus`, **DD-CLEAR-FLAG**, période civile). **M8** =
verify global en dernier.

## Milestones

- [x] **M1** Domaine SLA snapshot : clock, assembleur, settings, `DossierType`
- [x] **M2** Persistence Liquibase 016 + JPA + query cohorte
- [x] **M3** Port Flowable + façade
- [x] **M4** Use cases + HTTP snapshot/CSV/settings
- [x] **M5** Inbox `slaRemainingHours`
- [x] **M6** SPA Rapports + nav + admin types/settings
- [x] **M7** Inbox UI + E2E snapshot + docs produit (texte « en revue »)
- [x] **M9** Domaine summary : `SlaStatus`, `SlaPeriod`, assembleur, use case
- [x] **M10** HTTP summary + PATCH `clearSlaTargetHours` + OpenAPI
- [x] **M11** Admin FE clear SLA + clients TS + Vitest
- [x] **M8** `./mvnw verify` + Vitest + Playwright globaux

## Task Breakdown

### M1–M7 — Premier jet (fait)

Voir l’historique : T1.*–T7.* cochés. Conservés. Ne pas retravailler
sauf régression.

### M9 — Domaine summary / statut / période

- [x] **T9.1** `SlaStatus` + tests UT-STATUS-*  
      Outcome : `ON_TRACK` / `AT_RISK` / `BREACHED` / `null` selon
      D-AT-RISK + A-AT-RISK-CMP ; clos sans AT_RISK.  
      Dépend : T1.1  
      Tests : UT-STATUS-1..4
- [x] **T9.2** `SlaPeriod` parse Instant ISO **ou** date civile UTC
      (`to` exclusif minuit suivant) ; défaut 30 j ; max 366.  
      Tests : UT-PERIOD-1..4  
      Design : DD-PERIOD-PARSE
- [x] **T9.3** `SlaSummary` + `SlaSummaryAssembler` groupBy type/statut  
      Tests : UT-SUM-1..3  
      Dépend : T9.1
- [x] **T9.4** `GetSlaSummaryUseCaseImpl` authz/cap/groupBy défaut/400  
      Tests : UT-SUM-UC-1..5  
      Dépend : T9.2, T9.3, T4.1 (même port/ACL)

### M10 — API + PATCH clear

- [x] **T10.1** `GET /api/reports/sla/summary` + 400 groupBy invalide  
      Tests : IT-API-8, IT-API-9  
      Dépend : T9.4
- [x] **T10.2** Snapshot/CSV : accepter `from`/`to` date civile via `SlaPeriod`  
      Tests : IT-API-10  
      Dépend : T9.2
- [x] **T10.3** PATCH `clearSlaTargetHours` (DD-CLEAR-FLAG) ; 400 si flag+valeur  
      Tests : IT-API-11, UT-TYPE-2  
      Dépend : T4.5
- [x] **T10.4** OpenAPI golden : summary + `clearSlaTargetHours`  
      Tests : IT-API-7 étendu (`-Dopenapi.update=true` si besoin)

### M11 — Frontend admin

- [x] **T11.1** `updateDossierType` accepte `clearSlaTargetHours`  
      Tests : dossierTypes.spec si existant, sinon DossierTypesAdminView.spec
- [x] **T11.2** Bouton « Retirer le SLA » sur une ligne type (clear flag)  
      Tests : Vitest admin  
      Dépend : T10.3, T11.1

### M8 — Vérification globale

- [x] **T8.1** `./mvnw verify -Dmaven.compiler.release=21` (ou JAVA_HOME JDK 25 + release 21)
- [x] **T8.2** `cd frontend && npm run test` **et** `npm run test:e2e`  
      Dépend : T7.2, T11.2
- [x] **T8.3** Docs planning/implementation/testing à jour (Phase 6 continue)

## Dependencies

```
T1.1 → T9.1 → T9.3 → T9.4 → T10.1
T9.2 → T9.4, T10.2
T4.5 → T10.3 → T11.1 → T11.2
T10.1 + T10.3 → T10.4
T11.2 + T7.2 → T8.2
T10.* + T9.* → T8.1
```

## Timeline & Estimates

Pas de calendrier. Ordre : M9 → M10 → M11 → M8.
Effort : invasif **modéré** — nouveau use case + DTO PATCH + OpenAPI ;
SPA admin petit ; snapshot déjà livré.

## Risks & Mitigation

| Risque | Mitigation |
|---|---|
| Instant vs date civile MockMvc | Tests ISO **et** `YYYY-MM-DD` |
| OpenAPI golden drift | `openapi.update=true` une fois, commit golden |
| PATCH keep vs clear | Tests keep-on-omit **et** clear flag |
| Jacoco seuils | Couvrir `SlaStatus` / `SlaPeriod` / summary UC |
| Playwright flaky | Mocks E2E inchangés pour snapshot ; pas de page summary V1 |

## Resources Needed

- JDK du snapshot (`JAVA_HOME` si besoin `/tmp/jdk/...`) ; `./mvnw`.
- Node `frontend/` ; Playwright Chromium.
- Task tracing indisponible.

## Traceability testing → tasks

| Scénario | Tâche |
|---|---|
| UT-CLOCK-* / UT-ASM-* | T1.1, T1.2 (fait) |
| UT-STATUS-* | T9.1 |
| UT-PERIOD-* | T9.2 |
| UT-SUM-* / UT-SUM-UC-* | T9.3, T9.4 |
| UT-GET-* / IT-API-1..6 | M4 (fait) |
| IT-API-8..11 | T10.1–T10.3 |
| IT-API-7 OpenAPI | T10.4 |
| UT-TYPE-2 clear | T10.3 |
| Vitest admin clear | T11.2 |
| E2E-SLA-* / E2E-9 | T7.2 (fait) |
| verify | T8.1, T8.2 |

## Progress

Phase 4 **complete**. Phase 5 M9–M11 **et M8 complete**.

Preuves M8 (2026-08-26) :

- `./mvnw verify -Dmaven.compiler.release=21` → BUILD SUCCESS,
  Jacoco « All coverage checks have been met », Tests run: 1262.
- `cd frontend && npm run test` → 62 files / 206 tests passed.
- `cd frontend && npm run test:e2e` → 53 passed (dont E2E-SLA-6).

Prochaine étape lifecycle : Phase 7 **complete** (2026-08-26) —
alignement design OK, 2 écarts SPA moyens documentés (filtre type,
`datetime-local`). Ensuite : Phase 8 testing si demandée.
