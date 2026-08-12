---
phase: deployment
title: Deployment Strategy
description: Define deployment process, infrastructure, and release procedures
feature: frontend
updated: 2026-08-12
---

# Deployment Strategy

## Build

- SPA Vite sous `frontend/` ; `base: './'` (**DD-BASE**).
- `frontend-maven-plugin` (Node **22**) → `src/main/resources/static/`.
- Skip local Java-only : `./mvnw … -Dskip.frontend=true`.
- **CI / release :** `./mvnw verify` **sans** skip.frontend pour peupler
  `static/` (follow-up REV-VERIFY).

## Context-path

Reverse-proxy `/{tenantId}/` : assets relatifs + `resolveApiBaseUrl()`
pour API, OAuth, logout, SSE. Checklist manuelle post-deploy recommandée.

## Rollback

Retirer le JAR / image précédente ; pas de migration Liquibase spécifique
à cette feature FE. Artefacts `static/` régénérés au build.
