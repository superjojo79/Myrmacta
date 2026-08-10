---
phase: implementation
title: Implementation Guide
description: Technical implementation notes, patterns, and code guidelines
status: verified
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
- Planning **T1–T13 done** : backend visualization slice implemented and
  verified with targeted workflow tests plus full Maven verify.

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

Aucune déviation fonctionnelle connue. Détails d’implémentation notables :

- `WorkflowStateEventListener` compare `FlowableEventType.name()` and extracts
  `processInstanceId` reflectively to stay compatible with Flowable 8 event
  concrete classes ; listener remains `isFailOnException=false`.
- Listener dependency on `WorkflowParticipantPort` is `@Lazy` to avoid a
  ProcessEngine bootstrap cycle while registering Flowable event listeners.
- `FlowableWorkflowVisualizationAdapter` reads native BPMN XML through
  `RepositoryService.getProcessModel(...)` after tenant-scoped runtime/historic
  process resolution.

## Implemented Files (T1–T11)

- Domain models/ports : `WorkflowDiagramView`, `ActivityHistoryEntry`,
  `WorkflowTaskView`, `WorkflowLinkDossierView`,
  `WorkflowProcessInstanceMissingException`,
  `WorkflowProcessInstanceNotFoundException`,
  `WorkflowVisualizationPort`, `WorkflowParticipantPort`.
- Use cases : `WorkflowLinkVisualizationUseCaseImpl` implements diagram,
  history, tasks, dossier panel, and reassign authz in domain.
- Persistence : `DossierElementRepository.findByDossierIdOrderByAddedAtAsc`,
  `DossierWorkflowLinkRepository.findByProcessInstanceId`, JPA adapters, and
  Liquibase `007-workflow-viz-columns.yaml`.
- StartedBy wiring : `AttachWorkflowUseCase.reserve/reserveOrResume(...,
  startedBy)`, `ManualStartOrchestrator.start(..., startedBy)`,
  `DossierWorkflowController` OIDC subject, and `OutboxRelayScheduler`
  `system:outbox`.
- Flowable infra : `FlowableWorkflowVisualizationAdapter`,
  `FlowableWorkflowParticipantAdapter`, `WorkflowStateEventListener`.
- Web/API : `WorkflowLinkVisualizationController`, `ReassignWorkflowTaskRequest`,
  `WorkflowExceptionHandler` 409 mapping for missing process instance.
- Tests : `WorkflowLinkVisualizationUseCaseImplTest`,
  `FlowableWorkflowParticipantAdapterTest`, `WorkflowStateEventListenerTest`,
  `WorkflowSchemaIT`, extended `WorkflowApiTest`, persistence helper coverage.

## Verification Evidence

- `npx ai-devkit@latest lint --feature visualisation-workflow` → OK.
- `./mvnw -q test -Dtest='*Workflow*Test,*Workflow*IT,*Attach*,*Visualization*,*Participant*,*WorkflowLink*,*WorkflowState*'`
  → OK ; 68 matching surefire tests, 0 failures/errors/skips.
- `./mvnw -q verify` → OK ; 530 surefire tests, 0 failures/errors/skips,
  ArchUnit/Jacoco verify phase completed.
