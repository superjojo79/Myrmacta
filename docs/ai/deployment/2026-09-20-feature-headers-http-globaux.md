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
n’est écrit que si la requête est reconnue HTTPS (`X-Forwarded-Proto`)
**et** que le pair TCP immédiat (unwrap des wrappers forwarded) est
loopback ou RFC1918 (A4-HSTS-001). Ne pas changer `framework` →
`native` (décision OIDC existante).

Ops (voir [DEPLOIEMENT.md](../../../DEPLOIEMENT.md)) :

- `docker run -p 127.0.0.1:8081:8080` — ne pas publier `:8080`/`:8081`
  hors loopback.
- Reverse-proxy de confiance : `RequestHeader unset X-Forwarded-*`
  puis poser `X-Forwarded-Proto: https`.
- Ne pas poser un HSTS reverse-proxy **plus court** que celui de l’app.

## Database Migrations

Aucune.

## Rollback Plan

1. `APP_SECURITY_HEADERS_ENABLED=false` + restart — le customizer
   devient no-op (défauts Spring conservés).
2. Pas de rebuild, pas de rollback Liquibase.
