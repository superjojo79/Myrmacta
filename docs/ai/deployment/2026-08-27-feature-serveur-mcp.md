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

Aucune migration Liquibase prévue. Le serveur MCP vit dans la JVM
tenant existante.

## Infrastructure

Même instance Spring Boot. Endpoint interne `/api/m2m/mcp`.
Ne pas exposer aux tenants (cadrage) : filtrage réseau / reverse-proxy
comme le reste de `/api/m2m/**`.

## Deployment Pipeline

Merge → boot. Pas de changelog DB.

## Environment Configuration

Réutilise `app.security.m2m.issuer-uri` / `jwt-secret`.
Propriétés Spring AI MCP (nom, endpoint) à figer en Phase 3.

## Database Migrations

Aucune.

## Rollback Plan

Revert du déploiement applicatif. Pas d'état MCP persisté propre.
