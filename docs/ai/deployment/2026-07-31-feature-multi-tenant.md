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

Instance Spring Boot **dédiée par tenant** (décision n° 13). `app.tenant.id` =
constante d’instance (= futur nom de schéma).

## Environment Configuration

| Variable / propriété | Rôle |
|---|---|
| `app.tenant.id` | Identifiant tenant (regex `^[a-z][a-z0-9_]{0,62}$`) — **requis** |

Surcharger `app.tenant.id` par instance au déploiement (ne pas laisser le défaut
`tenant_dev` en prod). `context-path` / search_path documentés en yaml commenté —
activation hors tranche.

## Database Migrations

Aucune dans cette tranche (pas de bootstrap schéma / Liquibase ici).

## Deployment Steps

1. Provisionner l’id tenant (ops / point 31).
2. Déployer avec `app.tenant.id=<tenant>` valide.
3. Boot fail-fast si id invalide / absent.
4. Smoke : contexte démarre ; `TenantIdValidator` OK.

## Secrets Management

N/A spécifique — secrets DB/OIDC gérés par features aval.

## Rollback Plan

Redéployer la version précédente. Changement additif ; `app.tenant.id` requis est
intentionnel (breaking soft au boot).
