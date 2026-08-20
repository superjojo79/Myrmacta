---
phase: deployment
title: Deployment Strategy
description: Define deployment process, infrastructure, and release procedures
status: draft
created: 2026-08-20
updated: 2026-08-20
feature: observabilite
phase1: 2026-08-20
---

# Deployment Strategy — `observabilite`

> Notes ops. **Q1 = B** : pas de migration Liquibase. **Q2 = A** : port
> management.

## Infrastructure

Une JVM / tenant (décision n° 13). Ajout prévu :

- Port **management** (**Q2 = A**) scrape Prometheus interne.
- Reverse-proxy public : SPA `/admin/observabilite` via BFF (session) ;
  **ne pas** publier le port management.
- Logs stdout JSON → stack logs existante du hébergeur.

## Deployment Pipeline

Inchangé : `./mvnw verify` + job frontend Playwright. Pas de codegen.

## Environment Configuration

À figer Phase 3, prévisionnel :

```yaml
management:
  server:
    port: ${MANAGEMENT_PORT:8081}
  endpoints:
    web:
      exposure:
        include: health,prometheus
logging:
  structured:
    format:
      console: ecs   # ou logstash — Phase 3
```

Prod : `app.tenant.id` obligatoire (déjà).

## Deployment Steps

1. Ouvrir le port management **réseau interne** seulement.
2. Ajouter le scrape Prometheus (instance / tenant).
3. Vérifier `tenantId` dans un log de smoke.
4. Smoke UI : `TENANT_ADMIN` ouvre `/admin/observabilite`.

## Database Migrations

Aucune (**Q1 = B**).

## Secrets Management

Pas de nouveau secret applicatif (**Q2 = A**).

## Rollback Plan

Retirer actuator / rétablir logs texte : feature flag non prévu V1
(dépendance pom). Rollback = revert du déploiement. MDC filter seul
est inoffensif si logs non parsés.
