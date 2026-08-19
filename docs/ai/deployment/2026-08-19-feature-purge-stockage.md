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

> **Q8 = A** : pas d’automatisation tenant dans l’app. Phase 5 livrée.

## Application release

- Rolling restart ; job `PurgeOutboxJob` (`app.purge.poll-delay` défaut **1h**,
  `app.purge.batch-size` **50**) ; `DocumentStoragePort.delete`.
- Liquibase **011** : colonnes `purge_outbox.origin`, `last_error` ;
  index `idx_purge_outbox_status_created_at`.
- Config S3/FS inchangée (credentials / root).
- APIs `TENANT_ADMIN` : `GET /api/purge-outbox`,
  `POST /api/dossiers/{id}/purge-requests`.

## Runbook — suppression de tenant (Q8 = A, hors code app)

Conformément à la décision n° 13 (1 instance = 1 tenant) :

1. Décommissionner l’instance Spring Boot (retirer du reverse-proxy).
2. `DROP SCHEMA <tenant> CASCADE` (tables métier + `ACT_*` du schéma).
3. Purger le préfixe `s3://<bucket>/<tenantId>/` **ou** `<root>/<tenantId>/`.
4. Révoquer client OIDC / secrets connecteurs du tenant.
5. Ne **pas** utiliser le job `purge-stockage` (périmètre = dossiers
   unitaires via outbox).

## Rollback

Désactiver le job (delay / flag) laisse les `PENDING` intacts.
