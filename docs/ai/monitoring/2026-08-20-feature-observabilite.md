---
phase: monitoring
title: Monitoring & Observability
description: Define monitoring, alerting, and observability strategy
status: draft
created: 2026-08-20
updated: 2026-08-20
feature: observabilite
phase1: 2026-08-20
phase3: 2026-08-20
requirements_ref: docs/ai/requirements/2026-08-20-feature-observabilite.md
design_ref: docs/ai/design/2026-08-20-feature-observabilite.md
---

# Monitoring & Observability — `observabilite`

> Aligné design **reviewed** (Phase 3). Ce lot **est** le socle §29
> **plus** l’écran admin (D-FE). Noms et seuils **figés**.

## Key Metrics

Scrape **uniquement** le port management (`MANAGEMENT_PORT`, défaut
`8081`) : `GET /actuator/prometheus`. **Aucun** label `tenant` /
`dossierId`.

| SLI | Prometheus | Unité |
|---|---|---|
| Âge PENDING outbox déclenchement | `flowforge_outbox_trigger_oldest_pending_age_seconds` | s |
| Âge PENDING outbox notification | `flowforge_outbox_notification_oldest_pending_age_seconds` | s |
| Âge unfinished outbox purge (PENDING∪PROCESSING∪FAILED) | `flowforge_outbox_purge_oldest_unfinished_age_seconds` | s |
| Jobs Flowable dead-letter | `flowforge_flowable_jobs_dead_letter` | count |
| Jobs Flowable avec exception (retry) | `flowforge_flowable_jobs_exception` | count |
| Timer PDF | `flowforge_pdf_render_seconds_count`, `_sum`, `_max` | s |
| Webhooks `WEBHOOK`+`DEAD` | `flowforge_notification_webhook_dead` | count |
| `SseEmitter` actifs | `flowforge_sse_emitters_active` | count |

Outbox vide → jauge `0`. Croissance monotone de l’âge déclenchement →
relais `OutboxRelayScheduler` coincé ou Flowable down.

Hors objectif produit (bonus JVM Micrometer si exposé) : RPS, heap, GC.

## Monitoring Tools

- **Logs :** stdout JSON ECS (`logging.structured.format.console=ecs`)
  → agrégateur du tenant ops (hors dépôt). MDC : `tenantId`,
  `dossierId`, `processInstanceId`.
- **Métriques :** Prometheus interne sur le port management (**Q2 = A**).
- **UI :** `GET /api/observability` → `/admin/observabilite` (mêmes
  SLI + `severity` calculée serveur).
- **Pas** d’APM / Grafana-as-code / Alertmanager in-repo.

## Logging Strategy

- WARN `SECURITY_AUDIT` : dénis (`access_denied`, `tenant_mismatch`).
- INFO `SECURITY_AUDIT` : `sensitive_field_changed` (existant) ;
  `dossier_read` / `element_content_read` (Q1=B, succès seulement).
- DEBUG métier existant inchangé.
- Sensible : jamais valeurs `fields`, HMAC, HTML, bytes PJ.
- Rétention logs : runbook hébergeur.

## Alerts & Notifications

Seuils **identiques** à `ObservabilityThresholds` / cartes admin
(**DD-THRESHOLDS**). Ops peut alerter côté Prometheus avec les mêmes
chiffres ; pas d’alerte in-app.

| Condition | Sévérité carte | Action |
|---|---|---|
| Âge outbox (chacune) ≥ 300 s | crit | Relais / DB / Flowable |
| Âge outbox ≥ 60 s et < 300 s | warn | Surveiller le relais |
| `flowforge_flowable_jobs_dead_letter` ≥ 10 | crit | Cockpit / retry manuel |
| DLQ Flowable 1–9 | warn | Idem |
| `flowforge_flowable_jobs_exception` > 0 | warn/crit selon table | Jobs encore en retry |
| `flowforge_notification_webhook_dead` ≥ 5 | crit | URL cible / SSRF guard / secret |
| PDF mean ≥ 5 s | crit | Templates / images (ordres `07`) |
| SSE ≥ 200 | crit | Fuite emitters / charge navigateur |

## Dashboards

Hors dépôt Grafana. L’écran admin **est** le dashboard produit V1.
Les noms Prometheus ci-dessus servent un dashboard ops externe.

## Incident Response

1. Filtrer logs ECS sur `tenantId` (= instance) puis `dossierId`.
2. Lire les trois âges outbox + webhook DEAD + les deux jauges Flowable.
3. Corréler `processInstanceId` si workflow.

## Health Checks

- `/actuator/health/liveness` et `/readiness` sur le **port
  management**.
- **Pas** de DOWN métier sur lag d’outbox (**DD-HEALTH**).
