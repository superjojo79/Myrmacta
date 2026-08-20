---
phase: deployment
title: Deployment Strategy
description: Define deployment process, infrastructure, and release procedures
status: draft
created: 2026-08-20
updated: 2026-08-20
feature: observabilite
phase1: 2026-08-20
phase3: 2026-08-20
requirements_ref: docs/ai/requirements/2026-08-20-feature-observabilite.md
design_ref: docs/ai/design/2026-08-20-feature-observabilite.md
---

# Deployment Strategy — `observabilite`

> Design **reviewed**. **Q2 = A** : port management. Index Liquibase
> notification (**DD-INDEX-NOTIF**) — pas de table d’audit (**Q1 = B**).

## Infrastructure

Une JVM / tenant (décision n° 13).

- Port **management** `${MANAGEMENT_PORT:8081}` : scrape Prometheus
  **réseau interne seulement**.
- Reverse-proxy public : SPA `/admin/observabilite` via BFF (session) ;
  **ne pas** publier `8081` / `/actuator`.
- Logs stdout JSON ECS → stack logs du hébergeur.

## Deployment Pipeline

Inchangé : `./mvnw verify` + job frontend Playwright. Pas de codegen.

## Environment Configuration

```yaml
management:
  server:
    port: ${MANAGEMENT_PORT:8081}
  endpoints:
    web:
      exposure:
        include: health,prometheus
  endpoint:
    health:
      probes:
        enabled: true
logging:
  structured:
    format:
      console: ecs
```

Prod : `app.tenant.id` obligatoire (déjà). Tests : port management `0`.

## Deployment Steps

1. Déployer le JAR (Liquibase applique `012-observability-indexes` si
   présent).
2. Ouvrir le port management **réseau interne** seulement.
3. Ajouter le scrape Prometheus (une cible / instance / tenant) sur
   `/actuator/prometheus`.
4. Vérifier `tenantId` dans un log ECS de smoke.
5. Smoke UI : `TENANT_ADMIN` ouvre `/admin/observabilite` ; un
   `WORKFLOW_ADMIN` ne voit pas le lien.

## Database Migrations

- **Pas** de table d’audit (**Q1 = B**).
- **Oui** : index `ix_notification_outbox_status_created_at`
  `(status, created_at)` — **DD-INDEX-NOTIF**.
- Outbox déclenchement et purge : index déjà présents, aucun changeset
  supplémentaire.

## Secrets Management

Pas de nouveau secret applicatif. Le scrape interne ne nécessite pas
d’OIDC (**Q2 = A**) : la protection est **réseau** (port non routé).

## Rollback Plan

Pas de feature flag V1. Rollback = revert du déploiement (actuator +
MDC + index). L’index notification est inoffensif s’il reste après
rollback applicatif ; le droper seulement si on revert aussi Liquibase.
