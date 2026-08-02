---
phase: planning
title: Project Planning & Task Breakdown
description: Break down work into actionable tasks and estimate timeline
status: implemented
created: 2026-08-02
updated: 2026-08-02
---

# Project Planning & Task Breakdown

## Phase 5 — synthèse

**14/14 tâches done.** Flowable + link + outbox + API livrés. Suite :
`./mvnw test` → 147 tests, 0 fail ; Jacoco ~86 % ; legacy in-memory
supprimé (DD-STUBS). Traçage `task` CLI indisponible.

Prochaine phase lifecycle : **7 Check Implementation** puis 8/9.

## Milestones
**What are the major checkpoints?**

- [x] **M1 — Domaine & ports** : link, config étendue, events sealed, outbox/idempotency ports, DD-STUBS préparé
- [x] **M2 — Persistence & Flowable** : Liquibase (link/outbox/config/idempotency), JPA, activation Flowable, smoke BPMN
- [x] **M3 — Use cases & jobs** : Attach, ManualStartOrchestrator (DD-TX), complete, relais, orphan, delegates
- [x] **M4 — API REST** : start+Idempotency-Key, history, complete, workflow-type-configs
- [x] **M5 — Preuves & docs** : IT complètes, régressions, doc implementation

## Task Breakdown
**What specific work needs to be done?**

Statuts : `[ ]` not started · `[~]` in progress · `[x]` done · `[!]` blocked

### M1 — Domaine & ports

- [x] **T1. `DossierWorkflowLink` + exceptions**
  - **Outcome :** record pur ; `WorkflowAlreadyRunningException` ;
    `AbortReservedReason` (`ORPHAN_START_FAILED`, `ORPHAN_TIMEOUT`, …) ;
    helpers RESERVED/ACTIVE/TERMINATED (dérivés colonnes).
  - **Depends on :** —
  - **Validation :** compile ; ArchUnit domaine pur.
  - **Tests liés :** Unit Attach (T9) ; glossaire A6.

- [x] **T2. `WorkflowTypeConfig` + validation**
  - **Outcome :** `statusDefinitions` (un `initial`) ;
    `allowedDossierStatuses` non vide ; `onStartDossierStatus?` ;
    `StatusOutcome` ; reject unknown status / liste vide.
  - **Depends on :** —
  - **Validation :** unit validation.
  - **Tests liés :** Unit WorkflowTypeConfig / UpdateStatus ; D-STATUS /
    D-DOSSIER-STATUS.

- [x] **T3. `WorkflowEvent` sealed (+ préparation DD-STUBS)**
  - **Outcome :** `Started` / `Completed` / `Failed` ; publisher générique.
    **Ne pas** casser le boot : retirer `StartWorkflowUseCase` /
    `WorkflowInstance` / InMemory* **au plus tard dans T11** quand
    `WorkflowEnginePort` + nouveaux beans remplacent `DomainConfig`
    (DD-STUBS opt. 1). Jusque-là : introduire sealed events sans supprimer
    le legacy si les tests `DomainConfigTest` / StartWorkflow bloquent.
  - **Depends on :** —
  - **Validation :** unit switch exhaustif.
  - **Tests liés :** Unit WorkflowEvent sealed.

- [x] **T4. Outbox + idempotency ports + properties**
  - **Outcome :** `OutboxStatus`, `TriggerOutboxPayload`, `OutboxRepository` ;
    `IdempotencyStore` (port) ; `@ConfigurationProperties`
    `app.workflow.outbox.*` + `app.workflow.reserve-timeout` (DD-OUTBOX /
    D-ORPHAN défauts).
  - **Depends on :** —
  - **Validation :** compile ; bind yaml test.
  - **Tests liés :** Unit relais (T10) ; Unit idempotency (T11).

- [x] **T5. Ports in/out (interfaces)**
  - **Outcome :** `AttachWorkflowUseCase` (reserve / reserveOrResume /
    confirmStarted / abortReserved) ; `GetDossierWorkflowHistoryUseCase` ;
    `CompleteTaskUseCase` ; `UpdateWorkflowLinkStatusUseCase` ;
    ports CRUD `WorkflowTypeConfig` ; **`WorkflowEnginePort`**
    (start with businessKey, completeTask, findByBusinessKey) — **pas** de
    nouveau `StartWorkflowUseCase` legacy.
  - **Depends on :** T1–T4
  - **Validation :** compile.
  - **Tests liés :** —

### M2 — Persistence & Flowable

- [x] **T6. Liquibase : link + outbox + config + idempotency**
  - **Outcome :** `dossier_workflow_link` + index partiel actif ;
    `workflow_type_config` (jsonb statuses + allowedStatuses +
    on_start_dossier_status) ; `outbox_event` + index (status, created_at) ;
    **`workflow_idempotency`** PK (tenant_id, dossier_id, idempotency_key).
  - **Depends on :** T1, T2, T4
  - **Validation :** IT schema H2.
  - **Tests liés :** IT Persistence.

- [x] **T7. JPA entities + adapters**
  - **Outcome :** adapters link / config / outbox / idempotency ;
    MAJ `dossier.current_workflow_link_id` à confirm ; clear quand lien
    terminé (actif seulement).
  - **Depends on :** T5, T6
  - **Validation :** IT round-trip + contrainte unique + idempotency save/get.
  - **Tests liés :** IT Persistence.

- [x] **T8. Activer Flowable + DD-SMOKE BPMN**
  - **Outcome :** exclusions Flowable process retirées autant que nécessaire ;
    yaml async executor ; `processes/smoke-process.bpmn20.xml`
    (`smokeProcess` : userTask → delegate statut terminal → end) ; boot IT.
  - **Depends on :** T6
  - **Validation :** contexte Spring + RuntimeService start IT.
  - **Tests liés :** IT Flowable smoke.

### M3 — Use cases & jobs

- [x] **T9. `AttachWorkflowUseCaseImpl` + history**
  - **Outcome :** reserve (allowedStatuses + initial + sequence) ;
    reserveOrResume ; confirmStarted ; abortReserved ; getHistory ;
    collision → exception.
  - **Depends on :** T5, T7, T2
  - **Validation :** unit + IT.
  - **Tests liés :** Unit AttachWorkflow ; IT Persistence.

- [x] **T10. OutboxRelay + ReservedReconcileJob + purge**
  - **Outcome :** relais DD-OUTBOX (retry / DEAD / idempotence rejeu) ;
    orphan DD-BKEY/D-ORPHAN ; purge PROCESSED 7d.
  - **Depends on :** T4, T7, T8, T9, `WorkflowEnginePort` (T11 peut fournir
    l’adaptateur en parallèle contrôlée — sinon stub port en unit T10).
  - **Validation :** unit + IT invoke directe (pas de sleep flaky).
  - **Tests liés :** Unit OutboxRelay ; IT Relais ; Unit/IT orphan.

- [x] **T11. ManualStartOrchestrator + Complete + delegates + DD-STUBS**
  - **Outcome :** orchestrateur DD-TX / D-FAIL / D-VARS / D-IDEM /
    D-DOSSIER-STATUS ; adaptateur Flowable `WorkflowEnginePort` ;
    `CompleteTaskUseCaseImpl` ; `UpdateWorkflowLinkStatusTask` +
    `UpdateDossierStatusTask` mince ; publisher sealed ; **suppression
    legacy in-memory** de `DomainConfig` (DD-STUBS).
  - **Depends on :** T3, T8, T9, T4, T7
  - **Validation :** IT smoke start/complete/compensate ; DomainConfig sans
    InMemory*.
  - **Tests liés :** Unit orchestrator ; IT Flowable smoke ; Unit UpdateStatus.

### M4 — API REST

- [x] **T12. Controllers + authz + Idempotency-Key**
  - **Outcome :** POST/GET `/api/dossiers/{id}/workflows` (EDIT/VIEW,
    header Idempotency-Key **requis** sur POST) ; POST
    `/api/workflow-tasks/{taskId}/complete` (EDIT) ; POST/PATCH/GET
    `/api/workflow-type-configs` ; ProblemDetail 400/403/404/409 ; CSRF ;
    pas d’enqueue outbox.
  - **Depends on :** T9, T11, T2
  - **Validation :** MockMvc OIDC.
  - **Tests liés :** IT API MockMvc (+ Idempotency-Key / reserved vars).

### M5 — Preuves & docs

- [x] **T13. Suite tests + régressions**
  - **Outcome :** scenarios testing doc verts ; Jacoco ≥ 80 % ; ArchUnit ;
    dossiers/auth non régressés.
  - **Depends on :** T8–T12
  - **Validation :** `./mvnw test` (ou `verify` projet).
  - **Tests liés :** tous.

- [x] **T14. Doc implementation + follow-ups**
  - **Outcome :** notes d’impl (TX, businessKey, yaml) ; rappel non-goals
    connecteurs/UI/visu ; pointer DEFERRED.
  - **Depends on :** T13
  - **Validation :** relecture.
  - **Tests liés :** —

## Dependencies
**What needs to happen in what order?**

```text
T1,T2,T4 (domaine) → T5 → T6 → T7,T8
                ↘ T3 (events ; DD-STUBS finalisé dans T11)
T7,T8,T5 → T9 → T10∥T11 → T12 → T13 → T14
```

- **Next 3 tasks (phase 5) :** T1 → T2 → T4 (puis T5).
- Risque sequencing : ne pas supprimer legacy DomainConfig avant T11.
- Pas de dépendance `connecteurs`.

## Risks & Mitigation

| Risque | Mitigation |
|---|---|
| Liquibase vs schema Flowable Boot 4 | T8 tôt ; ajuster init si collision |
| Index partiel H2 | MODE=PostgreSQL ; doc écart si besoin |
| DomainConfig cassé mi-parcours | DD-STUBS final uniquement en T11 |
| Flaky `@Scheduled` | invoke directe relais/orphan en tests |
| Périmètre UI/connecteurs | non-goals requirements |

## Resources Needed

- Docs requirements/design/testing `reviewed`/`draft`
- Analyse `05` / `04` ; décisions 6/7/11/13/16/17/20/27
- Worktree `.worktrees/feature-workflow-engine` ; `./mvnw`

## Traceability testing → tasks

| Scénario testing | Tasks |
|---|---|
| Attach reserve/resume/collision/abort | T1, T9 |
| Config / allowedStatuses / onStart / outcome | T2, T9, T11 |
| Outbox relay policies | T4, T10 |
| D-ORPHAN reconcile | T10, T8 (BKEY) |
| ManualStart DD-TX / D-FAIL / D-IDEM / D-VARS | T4, T11, T12 |
| Persistence + index + idempotency table | T6, T7 |
| Flowable smoke + delegate | T8, T11 |
| Complete task + reserved keys | T11, T12 |
| API MockMvc + Idempotency-Key | T12 |
| Events sealed + DD-STUBS DomainConfig | T3, T11, T13 |
| Régression Jacoco/ArchUnit/dossiers/auth | T13 |

**Couverture :** chaque section de
`docs/ai/testing/2026-07-31-feature-workflow-engine.md` mappe ≥ 1 tâche.
