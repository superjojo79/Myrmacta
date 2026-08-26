---
phase: deployment
title: Deployment Strategy
description: Define deployment process, infrastructure, and release procedures
status: planned
created: 2026-08-26
feature: reporting-sla
---

# Deployment Strategy — `reporting-sla`

## Infrastructure

Inchangé : une JVM Boot par tenant, PostgreSQL schéma dédié, SPA
embarquée `static/`. Pas de nouveau service, pas de Grafana.

## Deployment Pipeline

Même CI (`.github/workflows/ci.yml`) : `./mvnw verify` + job
`frontend` (Vitest + Playwright).

## Environment Configuration

Aucune nouvelle property obligatoire. Settings SLA absents → objectif
95 % calculé (D-SETTINGS). Colonne `sla_target_hours` nullable.

## Deployment Steps

1. Merge PR → Liquibase `016-reporting-sla` au boot.
2. `TENANT_ADMIN` saisit cibles heures par type + objectif % (sinon
   conformité `null` / n/a).
3. Smoke : `/rapports` 200 avec session TA.

## Database Migrations

Fichier `src/main/resources/db/changelog/changes/016-reporting-sla.yaml` :

- `dossier_type.sla_target_hours` INT NULL
- table `tenant_sla_settings` (PK `tenant_id`)
- index `idx_dossier_created_at`

Rollback Liquibase explicite (`dropColumn` / `dropTable` /
`dropIndex`). Pas de backfill.

## Secrets Management

Aucun secret nouveau.

## Rollback Plan

Revert PR + rollback changeset 016. Colonne nullable = compatible
avant saisie. Champ JSON inbox `slaRemainingHours` ignoré par d'anciens
clients.
