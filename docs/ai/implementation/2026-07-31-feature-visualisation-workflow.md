---
phase: implementation
title: Implementation Guide
description: Technical implementation notes, patterns, and code guidelines
status: draft
created: 2026-08-10
updated: 2026-08-10
---

# Implementation Guide

## Development Setup

- Worktree `.worktrees/feature-visualisation-workflow`
- Branch `feature-visualisation-workflow`
- JDK 25 (`java.version` pom) — installer si l’environnement n’a que 21
- Dépendances : Flowable déjà au pom ; notifications SSE déjà livrées

## Code Structure

```
domain/workflow/model          — WorkflowDiagramView, ActivityHistoryEntry,
                                 WorkflowTaskView ; WorkflowTypeConfig+observers
domain/workflow/port/in|out    — visualization + participant + use cases
domain/workflow/usecase        — get* / reassign
infrastructure/flowable        — VisualizationAdapter, ParticipantAdapter,
                                 WorkflowStateEventListener
infrastructure/web/workflow    — WorkflowLinkVisualizationController
infrastructure/persistence/…  — 007 observers ; element list ; link by piid
```

## Implementation Notes

- Suivre design `draft` + décisions D-* requirements.
- Ne pas ajouter de code Vue / bpmn-js (D-SCOPE A).
- Réutiliser `SendNotificationUseCase` pour SSE (ne pas créer un nouveau
  canal).
- Pattern authz : guard participant en bordure HTTP/use case ; port
  participants pur (liste userIds).

## Deviations from Design

Aucune (implémentation non démarrée).
