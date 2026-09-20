---
phase: deployment
title: Deployment Strategy
description: Define deployment process, infrastructure, and release procedures
status: reviewed
created: 2026-09-20
updated: 2026-09-20
feature: headers-http-globaux
lot: A4
---

# Deployment Strategy — `headers-http-globaux` (lot A4)

Pas de nouveau artefact, pas de migration, pas de Compose. Le lot
s’active au boot Spring via `application.yml`.

## Environment Configuration

| Variable | Défaut | Local | Prod |
|---|---|---|---|
| `APP_SECURITY_HEADERS_ENABLED` | `true` | laisser `true` (ne pas couper la CSP) | `true` ; `false` uniquement en urgence |
| Profils Spring | — | `local` : pas de HSTS | profil ≠ `local`/`test` : HSTS `max-age=31536000; includeSubDomains` (pas de `preload`) |

`server.forward-headers-strategy: framework` est déjà en place : HSTS
n’est écrit par Spring que si la requête est reconnue HTTPS
(`X-Forwarded-Proto`).

Ne pas poser un HSTS reverse-proxy **plus court** que celui de l’app
(voir [DEPLOIEMENT.md](../../../DEPLOIEMENT.md)).

## Database Migrations

Aucune.

## Rollback Plan

1. `APP_SECURITY_HEADERS_ENABLED=false` + restart — le customizer
   devient no-op (défauts Spring conservés).
2. Pas de rebuild, pas de rollback Liquibase.
