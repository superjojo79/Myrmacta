---
phase: monitoring
title: Monitoring & Observability
description: Define monitoring strategy, metrics, alerts, and incident response
status: deferred
created: 2026-08-18
updated: 2026-08-18
feature: edition-champs-dossier
---

# Monitoring & Observability — `edition-champs-dossier`

Aligné TODO §29 (ouvert au niveau projet). Pas de métrique Micrometer
nouvelle : l'endpoint PATCH fields existe déjà.

## Key Metrics

- 409 (statut / version) et 400 validation déjà produits par l'API.
- Spike 409 version : contention édition concurrente (ServiceTask + UI).

## Logging Strategy

L'audit `sensitive_field_changed` backend (INFO, sans valeur) reste la
seule trace d'écriture sensitive. Le SPA ne logue pas les champs.

## Alerting

Pas d'alerte nouvelle.

## Incident Response

Échec UI ≠ perte de données. Workaround : PATCH API direct (M2M hors
scope). Rollback = revert artefact FE.
