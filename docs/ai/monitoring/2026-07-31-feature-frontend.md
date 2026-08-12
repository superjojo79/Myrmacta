---
phase: monitoring
title: Monitoring & Observability
description: Define monitoring strategy, metrics, alerts, and incident response
feature: frontend
updated: 2026-08-12
---

# Monitoring & Observability

## SPA / BFF

- Erreurs boot session : `auth.bootError` (pas de redirect sur 5xx).
- SSE notifications : reconnect 5 s ; panneau `failed` si déconnecté.
- Cap client inbox notifs : 100 items.

## Backend (existant)

S’appuyer sur logs Spring / métriques Micrometer déjà prévues hors scope
FE V1. Surveiller 401/403 sur `/api/auth/me`, 409 viz, latence
`/api/workflow-tasks/mine`.
