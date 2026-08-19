---
phase: deployment
title: Deployment & Release
description: Plan deployment strategy and release process
status: draft
created: 2026-08-19
updated: 2026-08-19
feature: purge-stockage
phase1: 2026-08-19
---

# Deployment & Release — `purge-stockage`

## Application release

- Liquibase `011-purge-outbox-status` (statuts + index + `last_error`).
- Config `app.purge.fixed-delay-ms` / `app.purge.batch-size`.
- Aucun endpoint nouveau ; rolling restart OK.
- Vérifier credentials S3 / chemin FS inchangés (seul `delete` ajouté).

## Runbook — suppression de tenant (D-TENANT, hors code app)

Conformément à la décision n° 13 (1 instance = 1 tenant) :

1. Décommissionner l’instance Spring Boot du tenant (retirer du reverse-proxy).
2. `DROP SCHEMA <tenant> CASCADE` (PostgreSQL) — tables métier + `ACT_*`
   Flowable du schéma.
3. Purger le préfixe object store `s3://<bucket>/<tenantId>/` **ou** le
   répertoire filesystem `<root>/<tenantId>/`.
4. Révoquer client OIDC / secrets connecteurs du tenant.
5. Ne **pas** utiliser le job `purge-stockage` (périmètre = dossiers
   unitaires via outbox, pas le tenant entier).

## Rollback

- Désactiver le job (`fixed-delay` très grand / flag si ajouté) laisse les
  `PENDING` intacts.
- Colonnes status élargies restent rétrocompatibles avec inserts `PENDING`.
