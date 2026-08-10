---
phase: planning
title: Project Planning & Task Breakdown
description: Break down work into actionable tasks and estimate timeline
status: draft
created: 2026-08-10
updated: 2026-08-10
design_aligned: 2026-08-10
sequencing: layered
---

# Project Planning & Task Breakdown

Plan **phase 4 (initial)** — dérivé des docs `requirements` / `design` /
`testing` (`draft`). Aucune tâche d’implémentation démarrée.

Traçage `task` CLI : **indisponible** (`npx ai-devkit@latest task list
--json` → `unknown command 'task'`).

## Milestones
**What are the major checkpoints?**

- [ ] **M1 — Domaine** : models views, ports, use cases lecture + reassign,
      `WorkflowTypeConfig.observerUserIds`
- [ ] **M2 — Persistance** : Liquibase 007, repos (elements by dossier,
      link by processInstanceId), JPA config
- [ ] **M3 — Flowable adapters** : visualization + participants
- [ ] **M4 — HTTP** : controller viz + ProblemDetail 409 + admin observers
- [ ] **M5 — SSE listener** : `WorkflowStateEventListener`
- [ ] **M6 — Preuves & docs** : verify, Jacoco ≥ 80 %, sync docs

## Task Breakdown
**What specific work needs to be done?**

Statuts : `[ ]` not started · `[~]` in progress · `[x]` done · `[!]` blocked

### M1 — Domaine

- [ ] **T1. Models + ports out**
  - **Outcome :** `WorkflowDiagramView`, `ActivityHistoryEntry`,
    `WorkflowTaskView` ; `WorkflowVisualizationPort` ;
    `WorkflowParticipantPort` ; exceptions reserved / reuse not-found &
    access-denied ; `WorkflowTypeConfig` + `observerUserIds`.
  - **Deps :** aucune.
  - **Validation :** tests modèles observers ; ArchUnit package.
  - **Tests :** WorkflowTypeConfig observers ; prépare use cases.

- [ ] **T2. Use cases lecture**
  - **Outcome :** Get diagram / history / tasks / dossier link ; load
    link → si PI absent : 409 sur moteur, **200** sur dossier (R1) ;
    participant|TA/WA → délègue ports ; `relations: []`.
  - **Deps :** T1.
  - **Validation :** use case unit tests (403/404/409 paths + dossier
    sans PI).
  - **Tests :** Get* use cases (testing §).

- [ ] **T3. Use case reassign**
  - **Outcome :** WA|TA only ; validate assigneeId ; task belongs to
    instance ; délègue `reassignTask`.
  - **Deps :** T1.
  - **Validation :** ReassignWorkflowTaskUseCaseTest.
  - **Tests :** Reassign use case.

### M2 — Persistance

- [ ] **T4. Liquibase 007 + JPA config observers**
  - **Outcome :** colonne `observer_user_ids` JSON NOT NULL default `[]` ;
    mapping entity/domain ; admin DTOs étendus.
  - **Deps :** T1.
  - **Validation :** SchemaIT ; round-trip config.
  - **Tests :** Liquibase / WorkflowTypeConfig persistence.

- [ ] **T5. Repo helpers**
  - **Outcome :** `DossierElementRepository.findByDossierId…` ;
    `DossierWorkflowLinkRepository.findByProcessInstanceId` (+ tenant).
  - **Deps :** T4 (ou parallèle si pas de dépendance colonne).
  - **Validation :** persistence IT.
  - **Tests :** Persistence helpers (testing §).

### M3 — Flowable adapters

- [ ] **T6. FlowableWorkflowVisualizationAdapter**
  - **Outcome :** XML natif, active/completed/failedIds (D-FAIL), tasks,
    reassign `setAssignee`, filtre tenant.
  - **Deps :** T1.
  - **Validation :** adapter unit tests (testing § adapter).
  - **Tests :** FlowableWorkflowVisualizationAdapterTest.

- [ ] **T7. FlowableWorkflowParticipantAdapter**
  - **Outcome :** initiateur + assignees + observers config (A2–A4).
  - **Deps :** T1, T4/T5 (config + link).
  - **Validation :** participant resolution tests.
  - **Tests :** Participant adapter / helper.

### M4 — HTTP

- [ ] **T8. WorkflowLinkVisualizationController + handler**
  - **Outcome :** 5 endpoints ; authz ; CSRF reassign ; 409 mapping ;
    wiring beans.
  - **Deps :** T2, T3, T6, T7.
  - **Validation :** MockMvc IT (testing § MockMvc).
  - **Tests :** WorkflowLinkVisualizationApiTest ;
    ExceptionHandlerTest.

- [ ] **T9. Admin WorkflowTypeConfig observerUserIds**
  - **Outcome :** request/response JSON ; validation blank ; défaut `[]`.
  - **Deps :** T4.
  - **Validation :** MockMvc create/update/get.
  - **Tests :** WorkflowTypeConfig API extension.

### M5 — SSE

- [ ] **T10. WorkflowStateEventListener**
  - **Outcome :** filter events ; resolve linkId ; fan-out
    `SendNotificationUseCase` BROWSER avec
    `payload.type=WORKFLOW_STATE_CHANGED` (R2) ; fail-soft.
  - **Deps :** T7, T5.
  - **Validation :** listener unit tests.
  - **Tests :** WorkflowStateEventListenerTest.

### M6 — Preuves

- [ ] **T11. Verify + docs sync**
  - **Outcome :** `./mvnw verify` vert ; Jacoco ≥ 80 % ; scenarios
    testing `[x]` ; implementation/planning status synced.
  - **Deps :** T1–T10.
  - **Validation :** verify evidence in testing doc.
  - **Tests :** suite complète.

## Dependencies
**What needs to happen in what order?**

```text
T1 → T2 / T3
T1 → T4 → T9
T1 → T6
T4/T5 → T7 → T10
T2+T3+T6+T7 → T8 → T11
```

- Dépend de features livrées : `workflow-engine`, `notifications`,
  `dossiers` / `generation-documents` (éléments).
- Feature aval : `frontend` (Viewer) — hors tâches.

## Risks & Mitigation

| Risque | Mitigation |
|---|---|
| IT Flowable lourdes / flaky | Préférer unit adapter + MockMvc `@MockBean` port si coûteux |
| `startUserId` null en prod | Documenté A2 ; observers + assignees suffisent |
| Fan-out SSE N outbox | Accepté V1 ; fail-soft listener |
| Régression admin config | Tests API config existants + extension |

## Summary

Plan initial prêt pour phase 5 après revue requirements (phase 2) et
design (phase 3). Première implémentation suggérée : **T1 → T4 → T6**.
