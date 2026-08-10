---
phase: implementation
title: Implementation Guide
description: Technical implementation notes, patterns, and code guidelines
status: draft
created: 2026-08-10
updated: 2026-08-10
phase4_sync: 2026-08-10
---

# Implementation Guide

## Development Setup

- Worktree `.worktrees/feature-visualisation-workflow`
- Branch `feature-visualisation-workflow`
- JDK 25 (`java.version` pom) — installer si l’environnement n’a que 21
- Dépendances : Flowable déjà au pom ; notifications SSE déjà livrées
- Planning **ready** : démarrer par **T1 → T4 → T6**

## Code Structure

```
domain/workflow/model          — views ; WorkflowTypeConfig+observers
domain/workflow/port/in|out    — visualization + participant + use cases
domain/workflow/usecase        — get* / reassign (authz ici)
domain/dossier/model|port      — DossierWorkflowLink.startedBy ; AttachWorkflow
infrastructure/flowable        — VisualizationAdapter, ParticipantAdapter,
                                 WorkflowStateEventListener ; OutboxRelay startedBy
infrastructure/web/workflow    — WorkflowLinkVisualizationController
application/workflow           — ManualStartOrchestrator (pass sub)
infrastructure/persistence/…  — 007 ; element list ; link by piid
```

## Implementation Notes

- Suivre design `reviewed` + requirements `reviewed`.
- Ne pas ajouter de code Vue / bpmn-js (D-SCOPE A).
- Authz **dans les use cases** (DD-AUTHZ-LAYER) ; port participants pur.
- Panneau dossier : repos directs, **pas** `GetDossierUseCase`.
- SSE : `payload.type=WORKFLOW_STATE_CHANGED` via `SendNotificationUseCase`.
- TDD par tâche (skill `tdd`) ; phase 6 après chaque tâche done.

## Deviations from Design

Aucune (implémentation non démarrée).
