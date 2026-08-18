---
phase: deployment
title: Deployment Strategy
description: Define deployment process, infrastructure, and release procedures
status: ready
created: 2026-08-17
updated: 2026-08-17
feature: preview-documents
---

# Deployment Strategy — `preview-documents`

## Infrastructure

Aucune brique nouvelle. Même instance Spring Boot par tenant, SPA embarquée
dans `static/`.

## Deployment Pipeline

Build existant : `./mvnw verify` (frontend-maven-plugin → `static/`) + job CI
`frontend` (Vitest + Playwright).

## Environment Configuration

Réutilise `app.document.generate.max-body-bytes` (filtre taille partagé).
Nouvelle propriété optionnelle `app.document.preview.rate-limit-per-minute`
(défaut **120**). `/preview/pdf` continue d'utiliser
`app.document.generate.rate-limit-per-minute` (30).

## Deployment Steps

1. Merger la PR après CI verte.
2. Déployer l'artefact Boot habituel (preview = code applicatif).
3. Smoke : `/admin/templates` en tant que `TENANT_ADMIN`.

## Database Migrations

Aucune.

## Secrets Management

Inchangé.

## Rollback Plan

Revert du déploiement / revert git. Aucun état persistant à nettoyer
(preview mémoire uniquement).
