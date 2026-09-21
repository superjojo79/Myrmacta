---
phase: implementation
title: Implementation Guide
description: Technical implementation notes, patterns, and code guidelines
status: done
created: 2026-09-21
updated: 2026-09-21
feature: enrichissement-reporting
---

# Implementation Guide — `enrichissement-reporting`

Phase 5 livrée sur la branche `cursor/enrichissement-reporting-1d3d`.

Le domaine reste sans Spring. Les jobs `SlaAlertJob` et `BiExportJob`
passent une transaction par franchissement (alerte) ou par commit de
catalogue (BI). Le CSV BI dossiers retire les clés sensibles via
`DossierFieldRedactor.withoutSensitiveKeys`. Le compteur dead-letter
reprend la requête de `FlowableJobMetricsBinder` (une instance = un
tenant) ; les tâches ouvertes filtrent `taskTenantId`.

Le plan détaillé est
[2026-09-21-feature-enrichissement-reporting.md](../planning/2026-09-21-feature-enrichissement-reporting.md).
