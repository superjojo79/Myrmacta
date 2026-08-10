---
phase: planning
title: Project Planning & Task Breakdown
description: Break down work into actionable tasks and estimate timeline
status: ready
created: 2026-08-10
updated: 2026-08-10
design_aligned: 2026-08-10
phase3_sync: 2026-08-10
phase4: 2026-08-10
sequencing: layered
---

# Project Planning & Task Breakdown

Plan **phase 4** — dérivé des docs `requirements` / `design` (`reviewed`)
et `testing` (`draft`, scénarios `- [ ]`). Aucune tâche d’implémentation
démarrée.

Traçage `task` CLI : **indisponible** (`npx ai-devkit@latest task list
--json` → `unknown command 'task'`).

## Milestones
**What are the major checkpoints?**

- [x] **M1 — Domaine** : models, ports, use cases lecture + reassign
- [x] **M2 — Persistance & wiring startedBy** : Liquibase 007, JPA, repos,
      AttachWorkflow + orchestrator/outbox
- [x] **M3 — Adapters Flowable** : visualization + participants
- [x] **M4 — HTTP** : controller viz + admin observers + 409 handler
- [x] **M5 — SSE** : `WorkflowStateEventListener`
- [ ] **M6 — Preuves & docs** : verify, Jacoco ≥ 80 %, sync docs

## Task Breakdown
**What specific work needs to be done?**

Statuts : `[ ]` not started · `[~]` in progress · `[x]` done · `[!]` blocked

### M1 — Domaine

- [x] **T1. Models + ports out**
  - **Outcome :** `WorkflowDiagramView`, `ActivityHistoryEntry`,
    `WorkflowTaskView` ; `WorkflowVisualizationPort` ;
    `WorkflowParticipantPort` ; `WorkflowProcessInstanceMissingException`
    (ou équivalent → 409) ; reuse access-denied / not-found ;
    `WorkflowTypeConfig.observerUserIds` ; `DossierWorkflowLink.startedBy`.
  - **Deps :** aucune.
  - **Validation :** unit modèles ; ArchUnit `domain.workflow` sans
    dépendances web/mail.
  - **Tests :** WorkflowTypeConfig observers ; DossierWorkflowLink
    startedBy ; prépare use cases.

- [x] **T2. Use cases lecture (authz in UC)**
  - **Outcome :** Get diagram / history / tasks / dossier-link ;
    charge link → authz participant|TA/WA (DD-AUTHZ-LAYER) ;
    PI absent → 409 moteur, **200** dossier (R1) ; dossier via
    `DossierRepository` + elements (DD-DOSSIER-LOAD) ; `relations: []`.
  - **Deps :** T1 (ports mockés en test).
  - **Validation :** Get*UseCaseTest (403/404/409 + dossier sans PI +
    observateur).
  - **Tests :** Get diagram/history/tasks ; Get link dossier.

- [x] **T3. Use case reassign**
  - **Outcome :** WA|TA only ; `assigneeId` non blank ; tâche appartient
    à l’instance du link ; PI requis ; délègue
    `WorkflowVisualizationPort.reassignTask`.
  - **Deps :** T1.
  - **Validation :** ReassignWorkflowTaskUseCaseTest.
  - **Tests :** Reassign use case.

### M2 — Persistance & startedBy

- [x] **T4. Liquibase 007 + JPA**
  - **Outcome :** changelog
    `007-workflow-viz-columns.yaml` inclus au master ;
    `workflow_type_config.observer_user_ids` JSON NOT NULL défaut `[]` ;
    `dossier_workflow_link.started_by` VARCHAR ; mapping entities ;
    admin DTOs `observerUserIds` ; `WorkflowLinkResponse.startedBy`.
  - **Deps :** T1.
  - **Validation :** SchemaIT colonnes ; round-trip config + link.
  - **Tests :** Liquibase / WorkflowTypeConfig + link persistence ;
    MockMvc/persistence config observers (partiel jusqu’à T10).

- [x] **T5. Repo helpers**
  - **Outcome :**
    `DossierElementRepository.findByDossierIdOrderByAddedAtAsc` ;
    `DossierWorkflowLinkRepository.findByProcessInstanceId(tenantId, piid)`
    (+ index si besoin).
  - **Deps :** T4.
  - **Validation :** persistence IT round-trip.
  - **Tests :** Persistence helpers.

- [x] **T6. AttachWorkflow + producteurs `startedBy`**
  - **Outcome :**
    `reserve` / `reserveOrResume(..., String startedBy)` ;
    resume **ne remplace pas** `startedBy` ;
    `ManualStartOrchestrator` passe OIDC `sub` (signature start étendue
    ou contexte) ;
    `OutboxRelayScheduler` passe `"system:outbox"` ;
    tous appelants / tests mis à jour.
  - **Deps :** T4.
  - **Validation :** AttachWorkflowUseCaseImplTest ; tests orchestrator /
    relay mis à jour verts.
  - **Tests :** AttachWorkflow startedBy ; ManualStart / outbox wiring.

### M3 — Flowable adapters

- [x] **T7. FlowableWorkflowVisualizationAdapter**
  - **Outcome :** XML natif (`BpmnXMLConverter`) ; active / completed /
    failedIds (`deleteReason.startsWith("error")`) ; list tasks ;
    `setAssignee` ; filtres `processInstanceTenantId` ; 404 si instance
    absente.
  - **Deps :** T1.
  - **Validation :** FlowableWorkflowVisualizationAdapterTest (services
    mockés).
  - **Tests :** adapter visualization (testing §).

- [x] **T8. FlowableWorkflowParticipantAdapter**
  - **Outcome :** union `startedBy` + startUserId + assignees +
    `observerUserIds` (A2–A4, R3) ; distinct ; blanks ignorés ; OK sans PI.
  - **Deps :** T1, T4, T5.
  - **Validation :** participant resolution tests.
  - **Tests :** Participant resolution (testing §).

### M4 — HTTP

- [x] **T9. WorkflowLinkVisualizationController + handler**
  - **Outcome :** 5 endpoints sous `/api/workflow-links/{linkId}` ;
    CSRF reassign ; mapping 409/403/404 ; beans config wiring UC +
    adapters ; controller mince (délègue UC).
  - **Deps :** T2, T3, T5, T7, T8.
  - **Validation :** MockMvc visualisation (tous cas testing §).
  - **Tests :** WorkflowLinkVisualizationApiTest ; Exception handler.

- [x] **T10. Admin `observerUserIds` sur WorkflowTypeConfig**
  - **Outcome :** POST/PATCH/GET acceptent/renvoient la liste ; blank →
    400 ; défaut `[]` ; authz TA|WA inchangée.
  - **Deps :** T4.
  - **Validation :** MockMvc create/update/get observers.
  - **Tests :** MockMvc / persistence config observers.

### M5 — SSE

- [x] **T11. WorkflowStateEventListener**
  - **Outcome :** events
    `TASK_ASSIGNED` / `ACTIVITY_COMPLETED` / `PROCESS_COMPLETED` ;
    resolve link via `findByProcessInstanceId` ; fan-out
    `SendNotificationUseCase` BROWSER
    `payload.type=WORKFLOW_STATE_CHANGED` (R2) ; fail-soft ;
    enregistrement engine config.
  - **Deps :** T5, T8.
  - **Validation :** WorkflowStateEventListenerTest.
  - **Tests :** WorkflowStateEventListener (testing §).

### M6 — Preuves & docs

- [ ] **T12. `./mvnw verify` + Jacoco ≥ 80 %**
  - **Deps :** T1–T11.
  - **Validation :** exit 0 ; Jacoco instructions ; ArchUnit ; pas de
    régression workflow/notifications/dossiers.
  - **Tests :** Coverage targets.

- [ ] **T13. Docs sync phase 5–8**
  - **Outcome :** cocher scénarios testing ; planning statuses ;
    implementation notes / deviations.
  - **Deps :** T12.
  - **Validation :** `npx ai-devkit lint --feature visualisation-workflow`.

## Traceability testing → tasks

| Scénario testing | Tâche(s) |
|---|---|
| WorkflowTypeConfig + observers (modèle) | T1, T4, T10 |
| Participant resolution | T8, T2 |
| AttachWorkflow startedBy | T6 |
| Get diagram / history / tasks UC | T2 |
| Get link dossier UC | T2, T5 |
| Reassign UC | T3 |
| FlowableWorkflowVisualizationAdapter | T7 |
| WorkflowStateEventListener | T11 |
| Exception handler 409/403/404 | T9 |
| MockMvc visualisation | T9 |
| MockMvc / persistence config observers | T4, T10 |
| Persistence helpers | T5 |
| Jacoco ≥ 80 % + ArchUnit + régression | T12–T13 |

**Couverture :** chaque section scénario du testing doc a ≥ 1 tâche.

## Dependencies
**What needs to happen in what order?**

```text
T1 → T2 / T3
T1 → T4 → T5 → T8 → T11
T4 → T6
T4 → T10
T1 → T7
T2 + T3 + T5 + T7 + T8 → T9 → T12 → T13
T6 ∥ T7 (après T4 / T1)
```

External : feature `frontend` (Viewer) hors plan ; pas de Cockpit Flowable.

## Risks & Mitigation

| Risque | Mitigation |
|---|---|
| IT Flowable flaky / lourde | Unit adapters mock services ; MockMvc `@MockBean` ports si besoin |
| Régression `reserve` signatures | T6 met à jour tous appelants + tests existants |
| Double ACL dossier | DD-DOSSIER-LOAD — ne pas appeler `GetDossierUseCase` |
| `system:outbox` dans participants | OK liste ; aucun sub humain ne matche (A11) |
| Fan-out SSE N outbox | Fail-soft listener ; coût accepté V1 |
| Scope creep UI bpmn-js | Non-goal D-SCOPE — ne pas implémenter |

## Resources Needed

- Worktree `.worktrees/feature-visualisation-workflow` (actif).
- Patterns : `WorkflowApiTest`, `DossierApiTest`,
  `NotificationOutboxRelay` / SSE, `AttachWorkflowUseCaseImpl`,
  `ManualStartOrchestrator`, `OutboxRelayScheduler`.
- JDK 25 pour `./mvnw verify` (pom `java.version=25`).

## Progress summary

Phase 5 implementation update (2026-08-10) — **T1–T11 done** :
domain contracts, persistence helpers, `startedBy` producers, Flowable
visualization/participant adapters, REST visualization API, observer DTOs,
409 handler, and `WorkflowStateEventListener`.
Remaining : **T12** full `./mvnw verify` + coverage gate, **T13** final docs
sync after full verification. Task CLI indisponible.
