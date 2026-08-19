---
phase: monitoring
title: Monitoring & Observability
description: Define monitoring, alerting, and observability strategy
status: draft
created: 2026-08-19
updated: 2026-08-19
feature: purge-stockage
phase1: 2026-08-19
---

# Monitoring & Observability — `purge-stockage`

> Socle minimal ; lot complet = TODO §29.

## Logs

- Identifiants `dossierId` / outbox si job ; résultat succès/échec.
- Pas de payload massif ni secrets.

## Metrics

À figer après Q1 (compteurs completed/failed, âge PENDING, etc.).

## Alerts

Croissance d’échecs ou file qui ne décroît pas → store ou PG.

## Hors scope

MDC complet (§29), dashboard UI (Q6).
