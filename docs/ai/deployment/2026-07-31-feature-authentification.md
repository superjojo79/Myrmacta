---
phase: deployment
title: Deployment Strategy
description: Define deployment process, infrastructure, and release procedures
status: drafted
updated: 2026-08-01
---

# Deployment Strategy

## Infrastructure
**Where will the application run?**

Instance Spring Boot BFF par tenant. IdP OIDC (Keycloak ou équivalent) externe.

## Environment Configuration

| Variable | Rôle |
|---|---|
| `OIDC_ISSUER_URI` | Issuer OIDC (requis, sans défaut) |
| `OIDC_CLIENT_SECRET` | Secret client (requis, sans défaut) |
| `app.tenant.id` | Tenant d’instance (garde claim `tenant_id`) |

Cookies session / `forward-headers` : voir `application.yml` +
`SecurityCookieProperties`.

## Database Migrations

Aucune dédiée auth (session servlet ; pas de Spring Session JDBC dans cette tranche).

## Deployment Steps

1. Configurer IdP : client OIDC ; claims `tenant_id` + `realm_access.roles` sur
   **ID token**.
2. Exporter `OIDC_*` + `app.tenant.id`.
3. Déployer ; smoke : login → `/api/auth/me` ; anonyme `/api/**` → 401 JSON.
4. Vérifier mismatch `tenant_id` → 403.

## Secrets Management

`OIDC_CLIENT_SECRET` via env / vault — jamais en clair dans le repo.

## Rollback Plan

Redéployer la version précédente. Pas de migration destructive.
