---
phase: implementation
title: Implementation Guide
description: Technical implementation notes, patterns, and code guidelines
status: done
created: 2026-09-21
updated: 2026-09-21
feature: enrichissement-reporting
phase7: 2026-09-21
phase9: 2026-09-21
---

# Implementation Guide — `enrichissement-reporting`

Phase 7 (2026-09-21) : le code de `cursor/enrichissement-reporting-1d3d`
(commit `50c4bd0`, puis cette revue) est aligné sur le design revu et
sur les exigences. Aucun écart ne justifie un retour en phase 3 ou en
phase 5. Les écarts ci-dessous sont des suites, pas des comportements
contraires aux critères de succès.

Le domaine reste sans Spring. Les jobs `SlaAlertJob` et `BiExportJob`
passent une transaction par franchissement (alerte) ou par commit de
catalogue (BI). Le CSV BI dossiers retire les clés sensibles via
`DossierFieldRedactor.withoutSensitiveKeys`. Le compteur dead-letter
reprend la requête de `FlowableJobMetricsBinder` (une instance = un
tenant) ; les tâches ouvertes filtrent `taskTenantId`.

## Alignement

| Décision | Livré |
|---|---|
| D-ALERT-CHANNEL / D-ALERT-RECIPIENTS | `EvaluateSlaAlertsUseCaseImpl` : un `NotificationEvent` par e-mail, par webhook encore activé, par participant. Webhooks éteints retirés avant `send`. |
| D-ALERT-THRESHOLD / D-ALERT-DEDUP | `SlaStatus.of` + `SlaClock`. Même statut : pas d’écriture. `AT_RISK` ou `BREACHED` : une alerte de ce statut. Retour `ON_TRACK` : mémoriser sans envoi. |
| D-ALERT-ACTIVATION | `UpsertTenantSlaSettingsUseCaseImpl` : false → true remplace `sla_alert_state`, pose `alertingEnabledAt`, efface `lastAlertScanAt`, n’appelle pas `SendNotificationUseCase`. Le `PUT` est `@Transactional`. |
| D-DASHBOARD-WIDGETS | `GET /api/reports/operations`. Indépendant des interrupteurs. Rôles du rapport. |
| D-BI-MECHANISM / D-BI-SCOPE / D-BI-MASK-ALWAYS | `BiExportCsvRenderer` + `DocumentStoragePort`. Pas d’`ExportDossiersUseCase` avec `System`. CSV SLA via `GetSlaReportUseCase` et `AccessContext.system("bi-export")`. |
| D-BI-SCHEDULE / D-BI-ACTIVATION | `RunBiExportUseCaseImpl.isDue` : ancre `biEnabledAt`, pas d’export immédiat si `lastBiExportAt` est null. |
| D-BI-DELIVERY / D-BI-DOSSIER-SHAPE | Table `bi_export_file`. Liste et téléchargement : `SlaReportAccess.allowedTypeCodes` dans la requête ; 404 si invisible. |
| A-POLL | Tick 60 s. L’échéance est dans le use case. |
| A-MISSED-WINDOW | Seul le statut courant `BREACHED` est envoyé. |
| A-BROWSER-NO-RETRY | Pas d’inbox. Le store écoute l’événement SSE `notification`. |
| Lot C | Aucun fichier ClamAV, Compose ou `GETTING-STARTED.md` dans ce cycle. |

Liquibase `022-reporting-enrichment.yaml` porte les colonnes et tables
du design. Le golden OpenAPI et `GUIDE-UTILISATEUR.md` décrivent le
formulaire admin et `/rapports`. Le texte d’alerte visible est le nom
d’énumération `AT_RISK` / `BREACHED`, le même vocabulaire que la table
de transition.

## Écarts

| Écart | Sévérité | Suite |
|---|---|---|
| `EvaluateSlaAlertsUseCaseImpl` enregistre `lastAlertScanAt` en réécrivant l’agrégat chargé au début du scan. `JpaTenantSlaSettingsRepositoryAdapter.save` remplace la ligne et les webhooks. Un `PUT` concurrent (e-mails, webhooks, horloge BI) pendant le scan est écrasé. Le commit BI, lui, recharge les réglages avant d’écrire `lastBiExportAt`. | moyenne | Recharger les réglages avant l’horloge d’alerte, sur le modèle de `BiExportCommitPort`. Hors critères de succès (une instance, un admin). Pas de correctif dans cette phase. |
| L’exception d’un dossier est avalée dans le use case. Le job journalise le nombre d’échecs, pas la cause (le domaine ne peut pas utiliser SLF4J). | basse | Remonter la cause au job. |
| `BiExportCsvRenderer.render` assemble tous les CSV avant le premier `store`. Le NFR « un CSV à la fois » n’est pas tenu. | basse | Découper rendu et stockage si le volume des types l’exige. |
| `SlaAlertCandidatePort` liste aussi les dossiers sans `slaTargetHours`. Le use case les ignore. Pas de filtre `createdAt`. | basse | Comportement de la table de transition respecté. |
| Les jobs ont `initialDelay = 60_000` en plus de `fixedDelay = 60_000`. | basse | Accepté : pas de tick au démarrage des tests. |
| Échec Flowable des compteurs : log warn et 0. | basse | Accepté : la page reste lisible. |
| Purge sautée si l’export dû lève : l’exception sort avant `purge`. | basse | Conforme à « ne pas avancer l’horloge ». La purge reprend au tick non dû ou quand le BI est coupé. |
| T-ALR-5 est couvert par `scan_atRiskThenBreached_sendsOnlyBreached` (phase 8). | close | — |
| Le rollback outbox d’un envoi raté est le `TransactionTemplate` du job (`REQUIRED` sur l’adaptateur outbox). Le test unitaire prouve que l’état n’est pas sauvé et que l’horloge avance. Pas d’IT qui force un rollback JDBC au milieu du fan-out. | basse | Phase 8 si l’on veut une preuve base. |

## Fichiers principaux

- Domaine : `TenantSlaSettings`, `UpsertTenantSlaSettingsUseCaseImpl`, `EvaluateSlaAlertsUseCaseImpl`, `RunBiExportUseCaseImpl`, `ListBiExportsUseCaseImpl`, `DownloadBiExportUseCaseImpl`, `GetOperationalCountsUseCaseImpl`.
- Infrastructure : `SlaAlertJob`, `BiExportJob`, `BiExportCsvRenderer`, `FlowableOperationalCountsAdapter`, `JpaSlaAlertCandidateAdapter`, `JpaBiExportFileRepositoryAdapter`, `OperationalReportController`, `TenantSlaSettingsController`.
- Frontend : `DossierTypesAdminView.vue`, `RapportsView.vue`, `slaSettings.ts`, `operations.ts`, `biExports.ts`, `notifications.ts`.

Le plan détaillé est
[2026-09-21-feature-enrichissement-reporting.md](../planning/2026-09-21-feature-enrichissement-reporting.md).

## Revue phase 9

Checklist : alignement design tenu, contrats API et Liquibase réversibles (`022` a un rollback), rôles du rapport sur les nouveaux GET, 404 si le CSV est invisible, clés sensibles absentes du CSV stocké, anti-formule via `CsvWriter`, code de type limité à `[A-Za-z0-9_-]` donc sûr dans le nom de fichier. Tests frais de la phase 8 verts, y compris Playwright.

Aucun point bloquant. Suites non bloquantes déjà listées : réécriture des réglages en fin de scan d’alerte, cause d’échec non journalisée, CSV BI tous en mémoire, pas d’IT JDBC du rollback outbox. Les colonnes fixes du CSV dossiers sont dupliquées avec `DossierExportController` ; les garder alignées à la main.

## Traçage

`npx ai-devkit@latest task list --name enrichissement-reporting --json`
répond `error: unknown command 'task'`. Aucun événement de phase émis.
`npx ai-devkit@latest lint --feature enrichissement-reporting` valide
les documents ; le contrôle de branche `feature-enrichissement-reporting`
reste en échec volontaire (branche cloud `cursor/enrichissement-reporting-1d3d`).
