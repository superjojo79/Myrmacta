---
phase: deployment
title: Deployment & Release
description: Plan deployment strategy and release process
status: draft
created: 2026-08-19
updated: 2026-08-19
feature: openapi-pagination-admin
phase1: 2026-08-19
---

# Deployment & Release — `openapi-pagination-admin`

## Application release

- **Breaking :** listes admin JSON (array → envelope). Déployer **le même**
  artefact Boot (SPA embarquée) : pas de fenêtre où l’ancien front parle au
  nouvel API.
- Propriété `app.openapi.enabled` **false** par défaut. Prod : ne pas
  activer sauf besoin ops ponctuel (admins seulement).
- Pas de migration Liquibase prévue V1.
- CI : le job doit inclure le diff `openapi/openapi.yaml`.

## Configuration

```yaml
app:
  openapi:
    enabled: false   # true en local/CI export seulement
```

Override local : `app.openapi.enabled=true` (le post-processor active springdoc).
Ne pas s’appuyer uniquement sur un placeholder YAML `${app.openapi.enabled}` — springdoc
reste on par défaut (`matchIfMissing=true`).

## Rollback

Revenir à l’artefact précédent (SPA+API). Pas de rollback partiel API-only.

## Hors scope

Provisioning tenant, Keycloak smoke, activation Swagger prod par défaut.
