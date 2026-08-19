---
phase: planning
title: Project Planning & Task Breakdown
description: Break down work into actionable tasks and estimate timeline
status: draft
created: 2026-08-19
updated: 2026-08-19
feature: purge-stockage
phase1: 2026-08-19
---

# Project Planning & Task Breakdown — `purge-stockage`

> Phase 1 : clarifications **Q1–Q10 terminées**. **Pas de plan
> d’implémentation figé** tant que Phase 2 (revue) et Phase 3 (design)
> ne sont pas faites.

## Milestones

- [ ] Phase 1 — requirements draft + clarifications Q1… (en cours)
- [ ] Phase 2 — revue requirements (après Q1–Q10)
- [ ] Phase 3 — revue design
- [ ] Phase 4 — plan T1… TDD
- [ ] Phases 5–9 — implémentation, tests, revue

## Task Breakdown

Les tâches T1–T11 du tour précédent sont **retirées** : elles
présupposaient Q1=B et des D-* non actés.

## Dependencies

- `retention-dossier-type` livré (PR #55) — outbox + `purgeEligibleAt`.
- `DocumentStoragePort` S3/FS existants (sans `delete` aujourd’hui).

## Risks

| Risque | Note |
|---|---|
| Trancher Q1 trop tôt / trop tard | FILE-ATTENTE : bloqué tant que §22 ouvert |
| Confondre purge dossier et tenant | Q8 |

## Timeline & Estimates

Hors scope agent (pas d’estimation calendaire).
