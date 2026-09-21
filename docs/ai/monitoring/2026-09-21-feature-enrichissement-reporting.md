---
phase: monitoring
title: Monitoring & Observability
description: Define monitoring strategy, metrics, alerts, and incident response
status: noted
created: 2026-09-21
updated: 2026-09-21
feature: enrichissement-reporting
---

# Monitoring & Observability — `enrichissement-reporting`

Les jobs ouvrent un MDC `tenantId` comme `EvaluateDossierRetentionJob`.
Un échec de scan ou d’export est un `warn` avec le tenant, sans corps
de notification et sans valeur de champ sensible.

Pas de nouvelle métrique Micrometer exigée par les exigences. Le
compteur dead-letter existant (`flowforge.flowable.jobs.dead.letter`)
reste la sonde technique ; le widget `/rapports` est la sonde métier.

Les alertes produit (e-mail, webhook, SSE) sont le mécanisme demandé
par le cycle. Elles ne remplacent pas l’actuator.
