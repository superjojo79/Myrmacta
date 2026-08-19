---
phase: monitoring
title: Monitoring & Observability
description: Define monitoring, alerting, and observability strategy
status: draft
created: 2026-08-19
updated: 2026-08-19
feature: purge-stockage
phase1: 2026-08-19
---

# Monitoring & Observability — `purge-stockage`

> Socle minimal V1 ; lot complet = TODO §29 `observabilite`.

## Logs

- Par claim : `outboxId`, `dossierId`, résultat (`COMPLETED`/`FAILED`),
  durée.
- Erreurs : message court dans `last_error` + log WARN/ERROR sans payload
  massif.

## Metrics (prépare §29)

- Compteur `purge.outbox.completed` / `purge.outbox.failed`.
- Gauge / timer âge du plus vieux `PENDING` (si Micrometer déjà présent ;
  sinon TODO §29).

## Alerts (ops)

- Croissance soutenue de `PENDING` / `FAILED` → investigation store ou PG.
- Job silent (pas de run) → scheduler / instance down.

## Hors scope

- MDC complet `tenantId`/`dossierId`/`processInstanceId` (§29).
- Dashboard supervision UI.
