---
phase: planning
title: Project Planning & Task Breakdown
description: Break down work into actionable tasks and estimate timeline
status: drafted
created: 2026-08-02
updated: 2026-08-02
---

<!-- Aligned with design reviewed (DD-TX…DD-STUBS) -->

# Project Planning & Task Breakdown

## Milestones
**What are the major checkpoints?**

- [ ] **M1 — Domaine & ports** : link, config, events sealed, outbox model, exceptions
- [ ] **M2 — Persistence & Flowable** : Liquibase, JPA, retrait exclusions, smoke BPMN
- [ ] **M3 — Use cases & relais** : AttachWorkflow, start/complete, outbox relay, delegates
- [ ] **M4 — API REST** : controllers, authz, ProblemDetail
- [ ] **M5 — Preuves & docs** : IT H2, régressions, doc implementation

## Task Breakdown
**What specific work needs to be done?**

Statuts : `[ ]` not started · `[~]` in progress · `[x]` done · `[!]` blocked

### M1 — Domaine & ports

- [ ] **T1. `DossierWorkflowLink` + exceptions métier**
  - **Outcome :** record domaine ; `WorkflowAlreadyRunningException` ; helpers
    ended/active / abortReserved reasons.
  - **Depends on :** —
  - **Validation :** compile ; ArchUnit pur.
  - **Tests liés :** Unit Attach (T9).

- [ ] **T2. `WorkflowTypeConfig` + `StatusOutcome` + validation codes**
  - **Outcome :** records/enums ; un `initial` ; `allowedDossierStatuses` non
    vide ; `onStartDossierStatus?` ; reject unknown status.
  - **Depends on :** —
  - **Validation :** unit validation.
  - **Tests liés :** Unit WorkflowTypeConfig / UpdateStatus.

- [ ] **T3. `WorkflowEvent` sealed + publisher générique + DD-STUBS**
  - **Outcome :** Started/Completed/Failed ; retirer câblage et modèle
    in-memory legacy (`StartWorkflowUseCase` / `WorkflowInstance` /
    InMemory*) hors tests.
  - **Depends on :** —
  - **Validation :** unit switch exhaustif ; DomainConfig sans stubs prod.
  - **Tests liés :** Unit WorkflowEvent sealed ; compile DomainConfig.

- [ ] **T4. Modèle outbox + ports + idempotency + config étendue**
  - **Outcome :** `OutboxStatus`, `TriggerOutboxPayload`, `OutboxRepository` ;
    `IdempotencyStore` ; `WorkflowTypeConfig.allowedDossierStatuses` +
    `onStartDossierStatus` ; properties `app.workflow.outbox.*` +
    `reserve-timeout`.
  - **Depends on :** —
  - **Validation :** compile.
  - **Tests liés :** Unit relais (T10) ; Unit start idempotency.

- [ ] **T5. Ports in workflow / dossier (interfaces)**
  - **Outcome :** `AttachWorkflowUseCase`, `GetDossierWorkflowHistoryUseCase`,
    `CompleteTaskUseCase`, `UpdateWorkflowLinkStatusUseCase`,
    ports config ; ajuster `StartWorkflowUseCase` si besoin du moteur réel.
  - **Depends on :** T1–T4
  - **Validation :** compile.
  - **Tests liés :** —

### M2 — Persistence & Flowable

- [ ] **T6. Liquibase : link + outbox + workflow_type_config**
  - **Outcome :** changesets + index partiel actif ; colonnes alignées design.
  - **Depends on :** T1, T2, T4
  - **Validation :** IT schema H2.
  - **Tests liés :** IT Persistence.

- [ ] **T7. JPA entities + adapters (link, config, outbox)**
  - **Outcome :** adapters bi-directionnels ; `currentWorkflowLinkId` MAJ ;
    retirer câblage prod `InMemoryWorkflowRepository` si remplacé.
  - **Depends on :** T5, T6
  - **Validation :** IT round-trip + contrainte unique.
  - **Tests liés :** IT Persistence.

- [ ] **T8. Activer Flowable + smoke BPMN classpath**
  - **Outcome :** exclusions Flowable retirées (garder ce qui doit l’être) ;
    yaml async executor ; process smoke ; boot IT vert.
  - **Depends on :** T6
  - **Validation :** contexte Spring + start RuntimeService en IT.
  - **Tests liés :** IT Flowable smoke.

### M3 — Use cases & relais

- [ ] **T9. `AttachWorkflowUseCaseImpl` (+ history + abortReserved)**
  - **Outcome :** reserve / reserveOrResume / confirmStarted / abortReserved /
    getHistory ; statut initial ; sequence ; collision ; allowedStatuses.
  - **Depends on :** T5, T7, T2
  - **Validation :** unit + IT.
  - **Tests liés :** Unit AttachWorkflow ; IT Persistence.

- [ ] **T10. OutboxRelayScheduler + ReservedReconcileJob + purge**
  - **Outcome :** relais retry/DLQ ; D-ORPHAN (businessKey=linkId) ; purge
    PROCESSED.
  - **Depends on :** T4, T7, T8, T9
  - **Validation :** unit + IT relais/orphan.
  - **Tests liés :** Unit OutboxRelay ; IT Relais ; Unit/IT orphan.

- [ ] **T11. ManualStartOrchestrator + Complete + delegates (DD-TX)**
  - **Outcome :** orchestrateur sync D-FAIL/D-VARS/D-DOSSIER-STATUS/D-IDEM ;
    `WorkflowEnginePort` Flowable ; `CompleteTaskUseCaseImpl` ;
    `UpdateWorkflowLinkStatusTask` (+ `UpdateDossierStatusTask` mince) ;
    events sealed.
  - **Depends on :** T3, T8, T9, T4
  - **Validation :** IT smoke start/complete + compensation.
  - **Tests liés :** IT Flowable smoke ; Unit UpdateStatus ; Unit orchestrator.

### M4 — API REST

- [ ] **T12. Controllers dossier workflows + tasks + configs**
  - **Outcome :** surface API requirements (POST/GET workflows, complete
    task EDIT, POST/PATCH/GET workflow-type-configs) ; CSRF/session ;
    ACL type ; ProblemDetail 409 collision ; pas d’enqueue outbox public.
  - **Depends on :** T9, T11, T2
  - **Validation :** MockMvc OIDC.
  - **Tests liés :** IT API MockMvc.

### M5 — Preuves & docs

- [ ] **T13. Suite tests complète + régressions**
  - **Outcome :** tous scénarios testing doc cochables ; Jacoco ≥ 80 % ;
    ArchUnit ; dossiers/auth verts.
  - **Depends on :** T8–T12
  - **Validation :** `./mvnw test` (ou équivalent projet).
  - **Tests liés :** tous.

- [ ] **T14. Doc implementation + follow-ups DEFERRED**
  - **Outcome :** notes d’impl ; non-goals connecteurs/UI/visu rappelés ;
    chemins config outbox.
  - **Depends on :** T13
  - **Validation :** relecture.
  - **Tests liés :** —

## Dependencies
**What needs to happen in what order?**

```text
T1–T5 (domaine) → T6–T8 (infra Flowable) → T9–T11 (usecases/relais) → T12 (API) → T13–T14
```

- Bloqué par feature `connecteurs` : **non** — producteur outbox seed/API suffit.
- Suites dossiers doivent rester vertes (partage schéma / ACL).

## Risks & Mitigation

| Risque | Mitigation |
|---|---|
| Ordre init Liquibase vs Flowable Boot 4 | IT boot tôt (T8) ; ajuster config si collision |
| Index partiel H2 vs PG | MODE=PostgreSQL ; doc smoke PG différé |
| Périmètre trop large (UI Flowable, connecteurs) | Non-goals stricts requirements |
| Flaky scheduler tests | Invocation directe du relais |

## Resources Needed

- Analyse `05` / `04`, décisions 6/7/11/13/16/17/20/27
- Worktree `.worktrees/feature-workflow-engine`
- Maven Wrapper + H2 test

## Traceability testing → tasks

| Scénario testing | Tasks |
|---|---|
| Attach reserve/resume/collision | T9, T1 |
| Config / statut / outcome | T2, T11 |
| Outbox relay policies | T10, T4 |
| Persistence + index | T6, T7 |
| Flowable smoke | T8, T11 |
| API MockMvc | T12 |
| Events sealed | T3 |
| Régression Jacoco/ArchUnit | T13 |
