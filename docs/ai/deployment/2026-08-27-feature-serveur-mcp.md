---
phase: deployment
title: Deployment Strategy
description: Define deployment process, infrastructure, and release procedures
status: not-started
created: 2026-08-27
updated: 2026-08-27
feature: serveur-mcp
phase1: 2026-08-27
phase3: 2026-08-27
---

# Deployment Strategy — `serveur-mcp`

**D-AUDIT = C** : Liquibase **017-mcp-audit-authors.yaml** —
`created_by` / `updated_by` varchar(128) NOT NULL sur `dossier_type`
et `workflow_type_config`, backfill `'system'`. Le serveur MCP vit
dans la JVM tenant existante.

## Infrastructure

Même instance Spring Boot. Endpoint interne `/api/m2m/mcp`
(`spring.ai.mcp.server.streamable-http.mcp-endpoint`). Filtrage
réseau / reverse-proxy comme le reste de `/api/m2m/**` (cadrage :
pas d'exposition tenants).

## Deployment Pipeline

Merge → Liquibase au boot (017) + classpath Spring AI MCP.

## Environment Configuration

Réutilise `app.security.m2m.issuer-uri` / `jwt-secret`.

```yaml
spring.ai.mcp.server.protocol: STREAMABLE
spring.ai.mcp.server.type: SYNC
spring.ai.mcp.server.streamable-http.mcp-endpoint: /api/m2m/mcp
```

Pas de nouvelle variable secrète. BOM `spring-ai.version=2.0.0`.

## Database Migrations

Changelog 017. Rollback = drop des 4 colonnes. Backfill `'system'`.

## Rollback Plan

Revert applicatif. État résiduel possible : `WorkflowTypeConfig`
upserté sans nouvelle version Flowable (A-NO-COMPENSATE) — acceptable,
même forme qu'un PATCH REST config suivi d'un upload BPMN en échec.
