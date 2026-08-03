---
phase: implementation
title: Implementation Guide
description: Technical implementation notes, patterns, and code guidelines
status: reviewed
checked: 2026-08-02
reviewed: 2026-08-03
created: 2026-08-02
updated: 2026-08-03
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
- Prod publisher = `NoOpWorkflowEventPublisher` ; `InMemoryWorkflowEventPublisher` confined to `src/test` (DD-STUBS).

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
- Adapter `@Transactional` on repository writes commits reserve before Flowable.start (DD-TX critical constraint). Prefer a future `TransactionTemplate` wrapper if TX2 bundling (confirm + idempotency + onStart) must be atomic.

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
- Outbox/idempotency purge methods exist on ports/adapters but have no scheduled caller yet (follow-up).

## Security Notes
**What security measures are in place?**

- Existing BFF session/CSRF applies to mutating endpoints.
- Dossier workflow start/history/complete reuse dossier type ACL (`EDIT`/`VIEW`) with admin bypass through `DossierAccessPolicy`.
- Config mutation is limited to `TENANT_ADMIN`/`WORKFLOW_ADMIN`.
- Client variables cannot override platform-reserved variables.

## Phase 7 — Check Implementation

**Verdict :** aligné design/requirements — **pas d’écart majeur**. Suites : phase 8 (tests manquants) puis 9.

### Matrice d’alignement

| Décision | Attendu | Implémenté | Sévérité écart |
|---|---|---|---|
| D-START | Hybrid sync + outbox, pas d’enqueue public | `ManualStartOrchestrator` + `OutboxRelayScheduler` ; pas d’API enqueue | OK |
| D-KEY | `processDefinitionKey` → config existante | `AttachWorkflowUseCaseImpl.reserve` | OK |
| D-AUTHZ | EDIT start/complete ; admin config | Controllers + `DossierAccessPolicy` | OK |
| D-STATUS | `allowedDossierStatuses[]` | Validé à `reserve` | OK |
| D-ORPHAN | resume / retry / `ORPHAN_TIMEOUT` ; timeout 5m | `ReservedReconcileJob` + `WorkflowProperties` | OK (poll = timeout) |
| D-IDEM | Header requis ; table dédiée | Header + `workflow_idempotency` | OK |
| D-VARS | Clés réservées refusées + injectées | `WorkflowVariables` start/complete/outbox | OK |
| D-DOSSIER-STATUS | `onStartDossierStatus?` après confirm | `applyOnStartStatusIfConfigured` | OK |
| D-FAIL | Compensate immédiat si start KO | `abortReserved(ORPHAN_START_FAILED)` | OK |
| DD-TX | reserve commit → start hors TX → confirm/idem | Ordre respecté ; TX2 non atomique (saves séparés) | **Medium** |
| DD-BKEY | `businessKey = linkId` | `FlowableWorkflowEngineAdapter.start` | OK |
| DD-IDEM | Table `workflow_idempotency` | Liquibase + `JpaIdempotencyStoreAdapter` | OK |
| DD-OUTBOX | batch 20 / max 5 / poll 1s / retention 7d | Defaults yaml + properties ; **purge non schedulée** | **Low** |
| DD-SMOKE | userTask → delegate terminal → end | `smoke-process.bpmn20.xml` | OK |
| DD-STUBS | Legacy hors prod / test-only | Legacy retiré ; publisher in-memory → `src/test` | OK |

### Écarts & follow-ups

| ID | Sévérité | Description | Action |
|---|---|---|---|
| DEV-TX2 | Medium | TX2 (`confirmStarted` + `idempotency.remember` + `onStart`) n’est pas une seule TX applicative ; crash intermédiaire peut laisser ACTIVE sans ligne idempotency | Follow-up phase 8/impl : `TransactionTemplate` infra autour TX2 |
| DEV-PURGE | Low | `purgeProcessedOlderThanDays` / `purgeExpired` non appelés par un job | Follow-up ops/scheduler |
| DEV-TRIG | Low | `triggerRef = "manual:" + idempotencyKey` (pas `userSubject`) | Acceptable V1 ; documenter si audit nécessite subject |
| DEV-TESTS | Low→phase 8 | Gaps testing doc : 401/403 API, collision 409, branches outbox/orphan unitaires, mutation config non-admin | **Clos phase 8** |
| DEV-RESUME | — (fix) | `reserveOrResume` ne reprenait pas un lien ACTIVE même process → rejeu outbox marquait DEAD au lieu de PROCESSED | **Corrigé phase 8** |
| DEV-RELAY-DUP | Blocking→fix | Relais retry après start OK / confirm KO pouvait re-`start` Flowable | **Corrigé phase 9** |

### Fichiers clés revus

- `ManualStartOrchestrator`, `AttachWorkflowUseCaseImpl`
- `OutboxRelayScheduler`, `ReservedReconcileJob`
- `DossierWorkflowController`, `WorkflowTaskController`, `WorkflowTypeConfigController`
- `FlowableWorkflowEngineAdapter`, `WorkflowConfig`, `002-workflow-tables.yaml`, smoke BPMN

### Preuves phase 7

- `npx ai-devkit@latest lint --feature workflow-engine` → OK
- `JAVA_HOME=/usr/lib/jvm/java-25-openjdk-amd64 ./mvnw test` →
  **156 tests, 0 failures, 0 errors** ; Jacoco instructions **86,2 %**
- `check-status workflow-engine` → **Phase 8 (Write Tests)**

## Phase 9 — Code Review

**Verdict :** prêt à merge. Bloquant corrigé ; follow-ups non bloquants documentés.

### Checklist finale

| Critère | Statut |
|---|---|
| Design match (D-* / DD-*) | OK (TX2 / purge follow-ups connus) |
| Pas de trou logique bloquant | OK après DEV-RELAY-DUP |
| Sécurité (ACL, CSRF, D-VARS) | OK |
| Points d’intégration Flowable/outbox | OK |
| Tests couvrent les changements | OK (173 / 0 fail, Jacoco 89,6 %) |
| Docs à jour | OK |

### Findings phase 9

| Sévérité | ID | Issue | Action |
|---|---|---|---|
| Blocking | DEV-RELAY-DUP | Relais pouvait créer une 2ᵉ instance Flowable | **Corrigé** : `findProcessInstanceIdByBusinessKey` avant start |
| Important | DEV-TX2 | TX2 confirm+idempotency non atomique | Follow-up post-merge (`TransactionTemplate`) |
| Important | DEV-PURGE | Retention outbox / TTL idempotency non schedulées | Follow-up ops |
| Important | DEV-ORPHAN-VARS | Retry orphan sans variables client | Acceptable V1 ; outbox reste source de vérité |
| Nice | DEV-LOC | `Location` pointait vers GET-by-id inexistant | **Corrigé** → collection history |
| Nice | DEV-LOCK | `lockNextPending` sans `SKIP LOCKED` | Non-goal multi-réplique V1 |
| Nice | DEV-RECONCILE-POLL | Cadence orphan = `reserve-timeout` | Follow-up propriété dédiée |

### Preuves phase 9

- `JAVA_HOME=/usr/lib/jvm/java-25-openjdk-amd64 ./mvnw test` →
  **173 tests, 0 fail** ; Jacoco instructions **89,6 %**
- `check-status workflow-engine` → **Complete**
