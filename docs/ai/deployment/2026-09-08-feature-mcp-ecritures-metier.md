---
phase: deployment
title: Deployment Strategy
description: Define deployment process, infrastructure, and release procedures
status: n/a
feature: mcp-ecritures-metier
---

# Deployment Strategy — `mcp-ecritures-metier`

Aucun changement de déploiement. Même endpoint `POST /api/m2m/mcp`, même JWT M2M.
Pas de migration Liquibase, pas de variable d'environnement nouvelle, pas de
Dockerfile (lot A1). Rollback = revert des 4 `@Component`.
