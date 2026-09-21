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

- [ ] M1 — Réglages SLA étendus (schéma, domaine, API, snapshot à l’activation)
- [ ] M2 — Use case d’alerting + job + fan-out notification
- [ ] M3 — Compteurs opérationnels (API)
- [ ] M4 — Export BI (use case, job, liste, téléchargement, rétention)
- [ ] M5 — UI admin + `/rapports` (Vitest et Playwright)
- [ ] M6 — Guide utilisateur, OpenAPI golden, verify complet

Ordre : M1 avant M2 et M4 (ils lisent les réglages). M3 est indépendant
de M2/M4 et peut suivre M1. M5 après les contrats API. M6 en dernier.
Le lot C ne partage aucun fichier de cette liste.

## Task Breakdown

### M1 — Réglages

- [ ] T1.1 Migration Liquibase colonnes `tenant_sla_settings` + table `tenant_sla_alert_webhook`. Dépend de rien. Preuve : IT démarrage changelog. Tests : T-IT-1.
- [ ] T1.2 Étendre le record, le port et l’upsert : bornes, e-mails, webhooks du tenant activés, plafond 20. Preuve : tests unitaires use case. Tests : T-SET-1, T-SET-2, T-SET-5.
- [ ] T1.3 Snapshot à l’activation alerting, sans `SendNotificationUseCase`, y compris réactivation. Preuve : test use case avec dossiers scoré / non scoré. Tests : T-SET-3, T-SET-4.
- [ ] T1.4 `PUT`/`GET` admin, 403 sinon, golden OpenAPI plus tard en M6. Preuve : MockMvc. Tests : T-IT-1.

### M2 — Alerting

- [ ] T2.1 Table `sla_alert_state` + port. Dépend de T1.1. Tests : T-ALR-3 (persistance du statut).
- [ ] T2.2 `EvaluateSlaAlertsUseCase` : opt-in, échéance, transitions, fenêtre manquée, ré-entrée, non scoré, clos. Dépend de T1.3 et T2.1. Tests : T-ALR-1 à T-ALR-9.
- [ ] T2.3 Fan-out `SendNotificationUseCase` (trois canaux, webhook désactivé sauté, payload sans champ sensible, échec n’avance pas l’état du dossier). Dépend de T2.2. Tests : T-ALR-10, T-ALR-11, T-ALR-12.
- [ ] T2.4 `SlaAlertJob` tick 1 min, MDC tenant. Dépend de T2.3. Tests : T-IT-2.

### M3 — Widgets

- [ ] T3.1 Port + adaptateur Flowable (dead-letter, tâches actives sans assigné, tenant). Dépend de rien côté réglages. Tests : T-OPS-1, T-IT-3.
- [ ] T3.2 `GET /api/reports/operations` rôles rapport. Dépend de T3.1. Tests : T-IT-3.

### M4 — Export BI

- [ ] T4.1 Table `bi_export_file` + port. Dépend de T1.1 pour les colonnes de rétention lues sur les réglages. Tests : T-BI-5.
- [ ] T4.2 Production CSV par type et CSV SLA (réutiliser le masquage et les colonnes existants, pas un second formateur). Dépend de T4.1. Tests : T-BI-3, T-BI-4.
- [ ] T4.3 `RunBiExportUseCase` : opt-in, échéance 24 h par défaut, store, transaction, delete best-effort, horloge. Dépend de T4.2. Tests : T-BI-1, T-BI-2, T-BI-6.
- [ ] T4.4 Rétention y compris export désactivé. Dépend de T4.3. Tests : T-BI-5, T-BI-8.
- [ ] T4.5 Liste paginée et téléchargement, filtre `VIEW`, 404 si interdit. Dépend de T4.1. Tests : T-BI-7, T-IT-4.
- [ ] T4.6 `BiExportJob`. Dépend de T4.3 et T4.4. Tests : T-IT-4.

### M5 — UI

- [ ] T5.1 Client `slaSettings.ts` + formulaire admin (alerting, e-mails, webhooks, BI). Dépend de T1.4. Tests : T-E2E-1, Vitest vue admin.
- [ ] T5.2 `RapportsView` compteurs + liste + téléchargement. Dépend de T3.2 et T4.5. Tests : T-E2E-2, T-E2E-3, T-E2E-4, T-E2E-5.

### M6 — Clôture documentaire et verify

- [ ] T6.1 `GUIDE-UTILISATEUR.md` (formulaire admin + `/rapports`). Dépend de T5.1 et T5.2.
- [ ] T6.2 Golden OpenAPI. Dépend de T1.4, T3.2, T4.5.
- [ ] T6.3 `./mvnw verify`, Vitest, Playwright. Dépend de tout le reste. Tests : T-E2E-4.

## Dependencies

- M2 et M4 lisent M1. M5 lit les API. M3 ne lit pas l’alerting.
- Pas de dépendance au lot C.
- Pas de nouvelle variable d’environnement.

## Risks

- L’export dossiers manuel est streamé dans le contrôleur. T4.2 doit
  extraire la construction de ligne (colonnes + masquage) sans dupliquer
  la règle sensible. Si l’extraction touche `DossierExportController`,
  garder le comportement HTTP identique (test de non-régression déjà
  présent, à rejouer).
- Dead-letter Flowable : confirmer en T3.1 que la query est scopée au
  tenant de l’instance (schéma unique). Si la query n’a pas de filtre
  tenant explicite, le documenter comme équivalent « une instance = un
  tenant » et ne pas inventer un filtre fragile.
- Transaction outbox + état d’alerte : l’état du dossier et les lignes
  d’outbox doivent commiter ensemble (T-ALR-12).

## Progress

Phase 4 seulement. Aucune tâche d’implémentation commencée. Prochaine
étape : Phase 5, T1.1 en TDD.
