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

> À détailler après Q1 (migration Liquibase / scheduler / flag).

## Application release

- Rolling restart probable.
- Config store S3/FS inchangée (seul `delete` pourrait s’ajouter — Q2).

## Runbook tenant (brouillon, Q8)

Si Q8 = hors code : décommission instance → `DROP SCHEMA` → purge préfixe
`tenantId/` (S3 ou FS). Ne pas confondre avec la purge dossier unitaire.

## Rollback

Dépend du mécanisme (Q1) : désactiver worker vs revert TX.
