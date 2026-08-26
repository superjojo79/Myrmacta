---
phase: monitoring
title: Monitoring & Observability
description: Define monitoring strategy, metrics, alerts, and incident response
status: planned
created: 2026-08-26
feature: reporting-sla
---

# Monitoring & Observability — `reporting-sla`

## Key Metrics

**Pas de nouvelle jauge Prometheus SLA métier** (A-NO-PROM /
observabilité reste ops).

Suivre via logs ECS existants :

- `GET /api/reports/sla` latence (access log / Micrometer HTTP
  existant)
- WARN `sla.flowable.degraded` si History/TaskService échoue
- 400 `SLA_COHORT_TOO_LARGE` (cap 10k) — signal de passage à un
  snapshot matérialisé

## Monitoring Tools

Actuator / Prometheus déjà livrés (`observabilite`). Pas d'écran
Grafana.

## Logging Strategy

- INFO : pas de dump de cohorte.
- WARN : dégradation Flowable (sans ids dossier en masse).
- Jamais `fields` dossier (allowlist ArchUnit inchangée).

## Alerts & Notifications

Pas d'alerte produit V1 sur le % SLA (c'est un écran métier, pas un
SLI scrape). Ops : alerter seulement si taux 400 cap 10k anormal.

## Dashboards

SPA `/rapports` = dashboard métier. `/admin/observabilite` inchangé.

## Incident Response

Rapport vide / étapes vides + WARN Flowable → vérifier moteur
workflow, pas le schéma PG. 403 DM → ACL types.

## Health Checks

Pas de HealthIndicator SLA (ne doit pas DOWN l'instance si 0 dossier).
