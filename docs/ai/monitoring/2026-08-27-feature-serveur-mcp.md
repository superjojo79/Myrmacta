---
phase: monitoring
title: Monitoring & Observability
description: Define monitoring strategy, metrics, alerts, and incident response
status: not-started
created: 2026-08-27
updated: 2026-08-27
feature: serveur-mcp
phase1: 2026-08-27
phase3: 2026-08-27
---

# Monitoring & Observability — `serveur-mcp`

Réutiliser l'observabilité livrée (PR #59) : MDC `tenantId`, logs ECS.

## Key Metrics

- Compteur / timer par tool MCP (succès, erreur, `dryRun`) — à câbler
  en implémentation si Micrometer reste le port management existant.
- Pas de KPI métier dupliquant le snapshot SLA.

## Logging Strategy

Champs : `mcp.tool`, `mcp.actor` (JWT `sub`), `mcp.dry_run`.
Pas de payload dossier / XML complet (risque secrets / volumétrie).
Pas de `Dossier.fields()` dans les logs (D-LOG-GUARD).

## Alerts & Notifications

Pas d'alerte nouvelle V1. Taux d'erreur MCP = même canal ops que M2M.

## Health Checks

Liveness/readiness existants. MCP n'ajoute pas d'actuator public.
