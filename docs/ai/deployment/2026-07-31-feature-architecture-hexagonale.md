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

Socle Spring Boot (fondation hexagonale). Pas de dépendance runtime DB / Keycloak /
Flowable pour cette feature (exclusions autoconfig).

## Environment Configuration

Aucune variable dédiée. JDK **25** ; artefact Maven standard.

## Database Migrations

Aucune (pas de JPA/Liquibase dans cette tranche).

## Deployment Steps

1. Build : `./mvnw -B verify`
2. Déployer le jar Spring Boot.
3. Smoke : contexte démarre ; ArchUnit / suite verte en CI.

## Secrets Management

N/A pour cette fondation.

## Rollback Plan

Redéployer la version précédente — changement additif (packages + stubs in-memory).
