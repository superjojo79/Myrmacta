---
phase: monitoring
title: Monitoring & Observability
description: Define monitoring, alerting, and observability strategy
status: draft
created: 2026-08-20
updated: 2026-08-20
feature: observabilite
phase1: 2026-08-20
requirements_ref: docs/ai/requirements/2026-08-20-feature-observabilite.md
---

# Monitoring & Observability — `observabilite`

> Ce lot **est** le socle §29. Noms Prometheus définitifs = Phase 3.
> Alerting = runbook ops, **pas** Alertmanager dans le dépôt (non-goal).

## Key Metrics

### Santé déclenchement (n° 1)

- Âge du plus ancien événement **PENDING** de l’outbox workflow
  (secondes). Croissance monotone → relais `OutboxRelayScheduler`
  coincé ou Flowable down.

### Runtime

- Jobs Flowable failed / dead-letter (count).
- Timer PDF : `max` / percentiles vs ordres `07` (50 ms – 2 s).
- Webhooks sortants `DEAD` (count).
- `SseEmitter` actifs (count) — saturation JVM / fuites unregister.

### Hors V1 (ne pas inventer)

- RPS HTTP générique, heap, GC : métriques JVM Micrometer **si**
  actuator les expose par défaut — bonus, pas un objectif produit.
- Âge outbox notification / purge : **Q3** reco A = hors lot.

## Monitoring Tools

- **Logs :** stdout JSON (D-LOG) → agrégateur du tenant ops (hors
  dépôt).
- **Métriques :** Prometheus scrape du **port management** (Q2 reco A).
- **Pas** d’APM / Grafana-as-code dans ce repo.

## Logging Strategy

- Niveaux : WARN `SECURITY_AUDIT` dénis ; INFO audit lecture (Q1=B) et
  champs `sensitive` déjà livrés ; DEBUG métier existant inchangé.
- MDC : `tenantId`, `dossierId`, `processInstanceId`.
- Rétention logs : runbook hébergeur (hors app).
- Sensible : jamais valeurs `fields`, HMAC, HTML, bytes PJ.

## Alerts & Notifications

Seuils **indicatifs** (ops ajuste) :

| Condition | Sévérité | Action |
|---|---|---|
| Âge outbox déclenchement > 5 min | critical | Relais / DB / Flowable |
| Jobs Flowable DLQ > 0 et croissant | warning | Cockpit / retry manuel |
| Webhooks DEAD croissant | warning | URL cible / SSRF guard / secret |
| Timer PDF p99 >> 2 s | warning | Templates / images |
| SSE count croissant sans users | warning | fuite emitters |

Pas d’alerte in-app.

## Dashboards

Hors dépôt. Documenter les noms de métriques pour un dashboard ops
externe après Phase 3.

## Incident Response

1. Filtrer logs sur `tenantId` (= instance) puis `dossierId`.
2. Lire âge outbox + DLQ webhook + Flowable DLQ.
3. Corréler `processInstanceId` si workflow.

## Health Checks

- `/actuator/health/liveness` et `readiness` (Boot).
- **Pas** de DOWN métier sur lag outbox (A-HEALTH).
