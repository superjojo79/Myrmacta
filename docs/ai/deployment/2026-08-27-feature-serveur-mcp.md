---
phase: deployment
title: Deployment Strategy
description: Define deployment process, infrastructure, and release procedures
status: not-started
created: 2026-08-27
updated: 2026-08-27
feature: serveur-mcp
phase1: 2026-08-27
---

# Deployment Strategy — `serveur-mcp`

**D-AUDIT = C** : migration Liquibase — `created_by` / `updated_by`
sur les tables `DossierType` et `WorkflowTypeConfig` (noms physiques
à figer en Phase 3). Le serveur MCP vit dans la JVM tenant existante.

## Infrastructure

Même instance Spring Boot. Endpoint interne `/api/m2m/mcp`.
Ne pas exposer aux tenants (cadrage) : filtrage réseau / reverse-proxy
comme le reste de `/api/m2m/**`.

## Deployment Pipeline

Merge → Liquibase au boot (colonnes auteur) + JVM.

## Environment Configuration

Réutilise `app.security.m2m.issuer-uri` / `jwt-secret`.
Propriétés Spring AI MCP (nom, endpoint) à figer en Phase 3.

## Database Migrations

Changelog **D-AUDIT = C** : `created_by` / `updated_by` sur types
dossier et workflow. Rollback = drop colonnes (Phase 3). Backfill :
valeurs sentinelle pour les lignes existantes (ex. `system`) — détail
Phase 3.

## Rollback Plan

Revert du déploiement applicatif. Pas d'état MCP persisté propre.
