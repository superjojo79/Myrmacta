---
phase: monitoring
title: Monitoring & Observability
description: Define monitoring strategy, metrics, alerts, and incident response
status: reviewed
created: 2026-09-20
updated: 2026-09-20
feature: headers-http-globaux
lot: A4
---

# Monitoring & Observability — `headers-http-globaux` (lot A4)

Pas de nouveau log, métrique, ni alerte (design : en-têtes silencieux).

## Key Metrics

Aucun KPI métier. Les HeaderWriter Spring n’ajoutent pas de latence
mesurable hors budget existant.

## Logging Strategy

Pas d’événement dédié au kill switch ni à l’émission CSP/HSTS.

## Health Checks

Inchangés. L’actuator `:8090` porte désormais CSP / XFO / nosniff
(chaîne `@Order(0)`). Smoke ops optionnel : `curl -I` HTTPS depuis le
proxy de confiance (`X-Forwarded-Proto: https`, pair TCP loopback) pour
voir HSTS — hors gate de cycle.

## Incident Response

Si la SPA casse en prod (violation CSP navigateur) :

1. Kill switch `APP_SECURITY_HEADERS_ENABLED=false` (rollback immédiat).
2. Rejouer D-CSP-STRING avec preuve console — **interdit** de basculer
   en Report-Only sans rejouer D-CSP-POLICY.
