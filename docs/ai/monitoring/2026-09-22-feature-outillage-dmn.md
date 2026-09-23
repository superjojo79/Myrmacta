---
phase: monitoring
title: Monitoring & Observability
description: Monitoring, alerting, and operational visibility
status: done
created: 2026-09-22
updated: 2026-09-23
feature: outillage-dmn
---

# Monitoring & Observability — `outillage-dmn`

Pas de métrique Micrometer nouvelle. Deux logs info portent
`decisionKey` et `version` : `dmn.evaluate` dans
`FlowableDmnEvaluationAdapter`, `dmn.redeploy` dans le contrôleur REST et
dans `RedeployDecisionTableTool`. Les erreurs métier restent des
ProblemDetail 400 / 404 / 409, sans compteur dédié.
