---
phase: monitoring
title: Monitoring & Observability
description: Define monitoring strategy, metrics, alerts, and incident response
status: deferred
created: 2026-08-17
updated: 2026-08-17
feature: preview-documents
---

# Monitoring & Observability — `preview-documents`

Aligné TODO §29 (toujours ouvert au niveau projet). Cette feature n'ajoute
pas de métriques Micrometer dédiées.

## Key Metrics

- 429 HTML : quota `app.document.preview.rate-limit-per-minute` (120).
- 429 PDF preview / generate : quota generate partagé (30).
- 413 body size (filtre partagé).
- Âge outbox / jobs Flowable : hors scope (inchangé).

## Logging Strategy

Pas de log du `htmlContent` ni des `variables` (peuvent contenir des données
métier / PII). Erreurs déjà mappées `ProblemDetail` sans payload.

## Alerting

Pas d'alerte nouvelle. Un spike 429 sur `/api/documents/preview` signale un
debounce cassé ou un abus — à corréler plus tard avec §29.

## Incident Response

Preview en échec ≠ génération de production. Workaround : `POST /generate`
existant. Rollback = revert artefact, aucun store à reconcilier.
