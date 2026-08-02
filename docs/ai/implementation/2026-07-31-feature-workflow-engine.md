---
phase: implementation
title: Implementation Guide
description: Technical implementation notes, patterns, and code guidelines
---

# Implementation Guide

## Development Setup
**How do we get started?**

- Java: run Maven with `JAVA_HOME=/usr/lib/jvm/java-25-openjdk-amd64`.
- Flowable process engine is enabled; non-process Flowable engines remain excluded.
- H2 tests run in PostgreSQL mode. `WorkflowConfig` forces Flowable's PostgreSQL DDL because the H2 DDL bundled with Flowable 8 uses `IDENTITY`, which is incompatible with the H2 version in this stack.

## Code Structure
**How is the code organized?**

- Domain remains Spring/JPA/Flowable-free:
  - `domain.dossier.usecase.AttachWorkflowUseCaseImpl`
  - `domain.workflow.usecase.*`
  - `domain.workflow.WorkflowVariables`
- Infrastructure adapters:
  - `infrastructure.persistence.workflow.*` for JPA entities/adapters.
  - `infrastructure.flowable.*` for engine adapter, delegates, tenant deployment, outbox/orphan jobs.
  - `infrastructure.web.workflow.*` for REST DTOs/controllers/ProblemDetail mapping.
- `application.workflow.ManualStartOrchestrator` coordinates DD-TX start without framework annotations.

## Implementation Notes
**Key technical details to remember:**

### Core Features
- T6/T7 persistence:
  - `002-workflow-tables.yaml` creates `dossier_workflow_link`, `workflow_type_config`, `outbox_event`, and `workflow_idempotency`.
  - PostgreSQL uses partial unique index `WHERE ended_at IS NULL`; H2 uses a generated nullable `active_dossier_id` unique index equivalent.
  - Config/outbox JSON columns use Hibernate `@JdbcTypeCode(SqlTypes.JSON)`.
- T8 Flowable:
  - `ProcessEngineAutoConfiguration` and `ProcessEngineServicesAutoConfiguration` are no longer excluded.
  - `processes/smoke-process.bpmn20.xml` defines `smokeProcess`: start -> user task -> `updateWorkflowLinkStatusTask` delegate -> end.
  - `TenantProcessDeployment` deploys BPMN resources with `TenantProperties.id()` so tenant-scoped starts work.
- T9-T11 use cases/jobs:
  - Manual start checks idempotency before reserving, reserves, starts Flowable with `businessKey = linkId`, confirms and records idempotency, or aborts with `ORPHAN_START_FAILED`.
  - Reserved variables (`dossierId`, `dossierWorkflowLinkId`, `tenantId`) are rejected for client-provided variables and injected by the platform.
  - `UpdateWorkflowLinkStatusUseCaseImpl` terminates links for terminal outcomes and clears `Dossier.currentWorkflowLinkId`.
  - Legacy `StartWorkflowUseCase` / `WorkflowInstance` / `InMemoryWorkflowRepository`
    removed from `src/main` (DD-STUBS).
- T12 API:
  - `POST /api/dossiers/{id}/workflows` requires `Idempotency-Key`, EDIT ACL, returns 201 on first start and 200 on idempotency replay.
  - `GET /api/dossiers/{id}/workflows` requires VIEW ACL.
  - `POST /api/workflow-tasks/{taskId}/complete` resolves the linked dossier from Flowable variables and requires EDIT ACL.
  - `/api/workflow-type-configs` mutation requires `TENANT_ADMIN` or `WORKFLOW_ADMIN`; reads require authentication.

### Patterns & Best Practices
- Keep domain use-case implementations free of framework annotations; wire them in `WorkflowConfig`.
- Keep schedulers invocable through package-private methods for tests; avoid sleeps in IT.
- Do not expose public outbox enqueue endpoints in this tranche.

## Integration Points
**How do pieces connect?**

- `ManualStartOrchestrator` uses `AttachWorkflowUseCase`, `WorkflowEnginePort`, `IdempotencyStore`, and optional `UpdateDossierStatusUseCase`.
- `FlowableWorkflowEngineAdapter` wraps `RuntimeService`/`TaskService`.
- Flowable delegates call existing use cases under system context.
- `OutboxRelayScheduler` and `ReservedReconcileJob` use the same ports as the manual path.

## Error Handling
**How do we handle failures?**

- Workflow REST errors are mapped to `ProblemDetail` in `WorkflowExceptionHandler`.
- Active workflow collisions map to 409.
- Missing configs/process-linked resources map to 404 where detected by API.
- Start failure after reserve aborts the link with `ORPHAN_START_FAILED`; orphan reconciliation aborts expired unrecoverable reservations with `ORPHAN_TIMEOUT`.
- Outbox failures increment attempts and move to `DEAD` at configured max attempts.

## Performance Considerations
**How do we keep it fast?**

- Indexes cover active link lookup/history and pending outbox polling.
- Flowable async executor is enabled, but smoke completion path remains synchronous.
- Multi-replica locking optimizations (`SKIP LOCKED`) remain deferred.

## Security Notes
**What security measures are in place?**

- Existing BFF session/CSRF applies to mutating endpoints.
- Dossier workflow start/history/complete reuse dossier type ACL (`EDIT`/`VIEW`) with admin bypass through `DossierAccessPolicy`.
- Config mutation is limited to `TENANT_ADMIN`/`WORKFLOW_ADMIN`.
- Client variables cannot override platform-reserved variables.

