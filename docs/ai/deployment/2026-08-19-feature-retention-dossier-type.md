---
phase: deployment
title: Deployment
description: Deployment notes for retention-dossier-type
status: deferred
created: 2026-08-19
updated: 2026-08-19
feature: retention-dossier-type
---

# Deployment — `retention-dossier-type`

- Liquibase `tenant-migration` : colonnes rétention + `purge_outbox` + index.
- Config yaml : `app.retention.cron`, `app.retention.batch-size`.
- **Avant prod utile :** un `TENANT_ADMIN` doit PUT le défaut tenant (pas de
  seed — design D-BOOTSTRAP).
- Purge physique / port S3 : **non déployé** ici (§22).
