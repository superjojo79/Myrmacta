---
phase: monitoring
title: Monitoring
description: Monitoring notes for retention-dossier-type
status: deferred
created: 2026-08-19
updated: 2026-08-19
feature: retention-dossier-type
todo_ref: TODO.md §29
---

# Monitoring — `retention-dossier-type`

Hors scope V1 détaillé (§29). Minimum souhaitable en implémentation :

- Log / compteur : dossiers archivés par run job ; skips (NEVER, settings
  absents).
- Taille / âge `purge_outbox` PENDING — consommation = feature
  `purge-stockage`.

MDC `tenantId` / `dossierId` : alignement futur §29, pas bloquant Phase 1.
