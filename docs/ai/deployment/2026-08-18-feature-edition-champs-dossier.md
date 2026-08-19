---
phase: deployment
title: Deployment Strategy
description: Define deployment process, infrastructure, and release procedures
status: ready
created: 2026-08-18
updated: 2026-08-18
feature: edition-champs-dossier
---

# Deployment Strategy — `edition-champs-dossier`

## Infrastructure

Aucune brique nouvelle. SPA embarquée dans `static/` (décision n° 27).

## Deployment Pipeline

Build existant : `./mvnw verify` (frontend-maven-plugin) + job CI
`frontend` (Vitest + Playwright).

## Environment Configuration

Aucune propriété nouvelle.

## Deployment Steps

1. Merger la PR après CI verte.
2. Déployer l'artefact Boot habituel.
3. Smoke : détail d'un dossier DRAFT en tant que `DOSSIER_MANAGER`.

## Database Migrations

Aucune.

## Secrets Management

N/A.
