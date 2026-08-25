---
phase: monitoring
title: Monitoring & Observability
status: draft
created: 2026-08-25
updated: 2026-08-25
feature: durcissement-securite-post-revue
---

# Monitoring & Observability — `durcissement-securite-post-revue`

> **Hors phase.** Le port 8090 / bind localhost dépend de D-42 (ouvert).

## Notes prématurées (non opposables)

Scrape Prometheus : `http://127.0.0.1:8090/actuator/prometheus` (défaut).
Chemins et métriques `flowforge_*` inchangés. DD-MGMT-SEC : toujours
`permitAll` sur le port management, désormais non routable hors loopback
par défaut.
