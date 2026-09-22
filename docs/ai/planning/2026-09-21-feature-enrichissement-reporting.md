---
phase: planning
title: Project Planning & Task Breakdown
description: Break down work into actionable tasks and estimate timeline
status: ready
created: 2026-09-21
updated: 2026-09-21
feature: enrichissement-reporting
phase4: 2026-09-21
---

# Project Planning & Task Breakdown — `enrichissement-reporting`

## Milestones

- [x] M1 — Réglages SLA étendus (schéma, domaine, API, snapshot à l’activation)
- [x] M2 — Use case d’alerting + job + fan-out notification
- [x] M3 — Compteurs opérationnels (API)
- [x] M4 — Export BI (use case, job, liste, téléchargement, rétention)
- [x] M5 — UI admin + `/rapports` (Vitest et Playwright)
- [x] M6 — Guide utilisateur, OpenAPI golden, verify complet

Ordre : M1 avant M2 et M4 (ils lisent les réglages). M3 est indépendant
de M2/M4 et peut suivre M1. M5 après les contrats API. M6 en dernier.
Le lot C ne partage aucun fichier de cette liste.

## Task Breakdown

### M1 — Réglages

- [x] T1.1 Migration Liquibase colonnes `tenant_sla_settings` (y compris `bi_enabled_at`) + table `tenant_sla_alert_webhook`. Dépend de rien. Preuve : IT démarrage changelog. Tests : T-IT-1.
- [x] T1.2 Étendre le record, le port et l’upsert : bornes, e-mails, webhooks du tenant activés, plafond 20. Preuve : tests unitaires use case. Tests : T-SET-1, T-SET-2, T-SET-5.
- [x] T1.3 Snapshot à l’activation alerting, sans `SendNotificationUseCase`, y compris réactivation. Preuve : test use case avec dossiers scoré / non scoré. Tests : T-SET-3, T-SET-4.
- [x] T1.4 `PUT`/`GET` admin, 403 sinon, golden OpenAPI plus tard en M6. Preuve : MockMvc. Tests : T-IT-1.

### M2 — Alerting

- [x] T2.1 Table `sla_alert_state` + port. Dépend de T1.1. Tests : T-ALR-3 (persistance du statut).
- [x] T2.2 `EvaluateSlaAlertsUseCase` sur `SlaAlertCandidatePort` (tous les dossiers scorés, pas la période du rapport) : opt-in, échéance, transitions, fenêtre manquée, ré-entrée, non scoré, clos. Dépend de T1.3 et T2.1. Tests : T-ALR-1 à T-ALR-9, T-ALR-13.
- [x] T2.3 Fan-out un événement par destinataire, webhook désactivé filtré avant `send`, transaction par dossier. Dépend de T2.2. Tests : T-ALR-10, T-ALR-11, T-ALR-12.
- [x] T2.4 `SlaAlertJob` tick 1 min, MDC tenant. Dépend de T2.3. Tests : T-IT-2.

### M3 — Widgets

- [x] T3.1 Port + adaptateur Flowable (dead-letter, tâches actives sans assigné, tenant). Dépend de rien côté réglages. Tests : T-OPS-1, T-IT-3.
- [x] T3.2 `GET /api/reports/operations` rôles rapport. Dépend de T3.1. Tests : T-IT-3.

### M4 — Export BI

- [x] T4.1 Table `bi_export_file` + port. Dépend de T1.1 pour les colonnes de rétention lues sur les réglages. Tests : T-BI-5.
- [x] T4.2 CSV par type avec suppression inconditionnelle des clés sensibles (pas `ExportDossiersUseCase` + `System`) et CSV SLA via `AccessContext.System`. Dépend de T4.1. Tests : T-BI-3, T-BI-4, T-BI-9.
- [x] T4.3 `RunBiExportUseCase` : opt-in, ancre `bi_enabled_at`, store, transaction, delete best-effort. Dépend de T4.2. Tests : T-BI-1, T-BI-2, T-BI-6.
- [x] T4.4 Rétention y compris export désactivé. Dépend de T4.3. Tests : T-BI-5, T-BI-8.
- [x] T4.5 Liste paginée et téléchargement, filtre `VIEW`, 404 si interdit. Dépend de T4.1. Tests : T-BI-7, T-IT-4.
- [x] T4.6 `BiExportJob`. Dépend de T4.3 et T4.4. Tests : T-IT-4.

### M5 — UI

- [x] T5.1 Client `slaSettings.ts` + formulaire admin (alerting, e-mails, webhooks, BI). Dépend de T1.4. Tests : T-E2E-1, Vitest vue admin.
- [x] T5.2 `RapportsView` compteurs + liste + téléchargement. Store SSE : événement `notification`. Dépend de T3.2 et T4.5. Tests : T-E2E-2, T-E2E-3, T-E2E-4, T-E2E-5, T-SSE-1.

### M6 — Clôture documentaire et verify

- [x] T6.1 `GUIDE-UTILISATEUR.md` (formulaire admin + `/rapports`). Dépend de T5.1 et T5.2.
- [x] T6.2 Golden OpenAPI. Dépend de T1.4, T3.2, T4.5.
- [x] T6.3 `./mvnw verify`, Vitest, Playwright. Dépend de tout le reste. Tests : T-E2E-4.

## Dependencies

- M2 et M4 lisent M1. M5 lit les API. M3 ne lit pas l’alerting.
- Pas de dépendance au lot C.
- Pas de nouvelle variable d’environnement.

## Risks

- L’export dossiers manuel masque selon l’acteur
  (`DossierFieldRedactor`). Le CSV BI retire toujours les clés
  sensibles, sans passer par `AccessContext.System`. Si T4.2 touche
  `DossierExportController`, l’export manuel admin reste en clair.
- Dead-letter Flowable : confirmer en T3.1 que la query est scopée au
  tenant de l’instance (schéma unique). Si la query n’a pas de filtre
  tenant explicite, le documenter comme équivalent « une instance = un
  tenant » et ne pas inventer un filtre fragile.
- Transaction outbox + état d’alerte : l’état du dossier et les lignes
  d’outbox doivent commiter ensemble (T-ALR-12).

## Progress

Phase 5 réalisée (2026-09-21) : réglages, alerting, compteurs, export BI,
UI et guide. Preuve : tests unitaires, `ReportingEnrichmentApiTest`,
Vitest et Playwright (`E2E-RPT-1`, `E2E-RPT-2`). `./mvnw verify` est la
clôture de T6.3.
