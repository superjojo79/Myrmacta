---
phase: implementation
title: Implementation Guide
description: Technical implementation notes, patterns, and code guidelines
status: reviewed
created: 2026-08-10
updated: 2026-08-13
checked: 2026-08-10
reviewed: 2026-08-10
phase4_sync: 2026-08-10
phase5: 2026-08-10
phase6: 2026-08-10
phase7: 2026-08-10
phase9: 2026-08-10
phase9_result: ready
alignment: aligned
---

# Implementation Guide

## Development Setup

- Worktree `.worktrees/feature-visualisation-workflow`
- Branch `feature-visualisation-workflow`
- JDK 25 — `JAVA_HOME=/tmp/jdk/jdk-25.0.4+7`
- Planning T1–T13 done ; phase 7 check **aligned**

## Code Structure

```
domain/workflow/model          — views ; WorkflowTypeConfig+observers ;
                                 WorkflowProcessInstanceMissingException
domain/workflow/port/in|out    — Get* / Reassign ; Visualization + Participant
domain/workflow/usecase        — WorkflowLinkVisualizationUseCaseImpl (authz)
domain/dossier                 — DossierWorkflowLink.startedBy ; AttachWorkflow
infrastructure/flowable        — VisualizationAdapter, ParticipantAdapter,
                                 WorkflowStateEventListener ; OutboxRelay startedBy
infrastructure/web/workflow    — WorkflowLinkVisualizationController ;
                                 ExceptionHandler 409
application/workflow           — ManualStartOrchestrator(startedBy)
infrastructure/persistence     — 007 columns ; element list ; link by piid
infrastructure/config          — WorkflowConfig event listener registration
```

## Phase 7 — Check Implementation (2026-08-10)

### Alignment status: **aligned**

Revue vs requirements/design `reviewed` : **16/16** checkpoints ALIGNED.
Aucun écart bloquant. Pas de retour design requis.

### Checklist design → code

| Id | Exigence | Verdict |
|---|---|---|
| D-SCOPE A | Pas d’UI Vue/bpmn-js | aligned |
| DD-URL / D-ID B | `/api/workflow-links/{linkId}/…` | aligned |
| DD-AUTHZ-LAYER | Authz dans UC | aligned (`WorkflowLinkVisualizationUseCaseImpl`) |
| DD-PART / R3 | startedBy + startUserId + assignees + observers | aligned (`FlowableWorkflowParticipantAdapter`) |
| DD-STARTEDBY-SRC | manuel=`sub` ; outbox=`system:outbox` | aligned |
| DD-DOSSIER-LOAD | Repos directs, pas GetDossierUseCase | aligned |
| R1 | Dossier OK sans PI ; moteur → 409 | aligned |
| D-FAIL A | `deleteReason.startsWith("error")` | aligned |
| DD-SSE / R2 | BROWSER `payload.type=WORKFLOW_STATE_CHANGED` | aligned |
| DD-SSE fail-soft | `isFailOnException=false` | aligned |
| D-REASSIGN-AUTHZ | WA\|TA only + CSRF (web) | aligned |
| D-DENY | 404 link ; 403 non-participant | aligned |
| DD-OBS | `observer_user_ids` + admin DTOs | aligned |
| DD-MIG 007 | observers + started_by + index piid | aligned |
| Listener register | `ProcessEngineConfigurationConfigurer` | aligned |
| Tenant filter | queries Flowable tenant-scoped | aligned |

### Écarts mineurs (non bloquants)

| Item | Sévérité | Notes |
|---|---|---|
| XML via `RepositoryService.getProcessModel` plutôt que `BpmnXMLConverter` | low | Toujours XML BPMN natif ; fidèle à DD-XML |
| Listener extrait `processInstanceId` par réflexion | low | Compat Flowable 8 ; couvert par tests unitaires |
| `setEventListeners(List.of(listener))` | low | Signal listener s’enregistre via `runtimeService.addEventListener` (autre chemin) ; spot-check `FlowableSignalEventListenerTest` vert |

### File-by-file (points clés)

- **UC** — `requireReadableLink` (participant\|admin) ; `requireProcessInstance` → missing exception ; dossier sans PI ; reassign admin + `assertTaskOnInstance`.
- **Controller** — mince ; délègue UC ; reassign 204.
- **Adapters** — diagram états + failedIds ; history ; tasks ; reassign `setAssignee` ; participants union.
- **SSE** — events filtrés ; fan-out notif ; catch RuntimeException ; no-op si link absent.
- **Attach/start** — `startedBy` requis ; resume ne recrée pas (conserve link existant).

## Deviations from Design

Voir écarts mineurs ci-dessus. Aucune déviation fonctionnelle vs décisions D-*/DD-*.

## Verification Evidence (phase 5, re-spot-check phase 7)

```text
JAVA_HOME=/tmp/jdk/jdk-25.0.4+7 ./mvnw verify
→ Tests run: 530, Failures: 0, Errors: 0, Skipped: 0
→ Jacoco instructions ≈ 87 %

Spot-check phase 7:
./mvnw -Dtest=FlowableSignalEventListenerTest,WorkflowLinkVisualizationUseCaseImplTest,WorkflowStateEventListenerTest,FlowableWorkflowParticipantAdapterTest test
→ Tests run: 18, Failures: 0, Errors: 0
```

## Follow-ups (hors scope V1)

- Viewer Vue / bpmn-js (`frontend`)
- Relations dossier réelles
- Incidents Flowable avancés
- Remplacer réflexion listener par typage `FlowableEngineEvent` si API stable

**Phase 7 close** (2026-08-10) — implementation `checked` / `aligned`.

## Phase 9 — Code Review (2026-08-10)

**Verdict :** prêt à merger (PR #34). Aucun blocker résiduel ; follow-ups
importants corrigés ou acceptés V1.

### Checklist finale

| Critère | Statut |
|---|---|
| Design match (D-* / DD-*) | OK |
| Pas de trou logique bloquant | OK |
| Sécurité (authz UC, CSRF, tenant Flowable) | OK après durcissement |
| Points d’intégration Flowable / SSE / Liquibase | OK |
| Tests couvrent les changements | OK (phase 8 + correctifs 9) |
| Docs à jour | OK |

### Findings phase 9

| Sévérité | ID | Issue | Action |
|---|---|---|---|
| Important | REV-REASSIGN | `assertTask` + `reassign` en deux appels (TOCTOU / 500) | **Corrigé** — `reassignTask(piId, taskId, assignee, tenant)` atomique |
| Important | REV-TENANT-ACT | Historic activities sans `activityTenantId` | **Corrigé** — filtre sur diagram + history |
| Important | REV-ROLLBACK | Liquibase 007 sans `rollback:` | **Corrigé** — dropColumn / dropIndex |
| Important | REV-SSE-TX | Listener hors `COMMITTED` | Accepté V1 — follow-up |
| Nice | REV-REFLECT | Extraction PI par réflexion | Accepté V1 (déjà phase 7) |
| Nice | REV-LISTENERS | `setEventListeners` remplace la liste | Accepté V1 — signal via autre chemin |
| Nice | REV-DTO | Records domaine exposés HTTP | Accepté V1 (design) |

### Correctifs phase 9

- Port / adapter : `reassignTask(processInstanceId, …)` valide instance+tenant puis `setAssignee`
- `activityTenantId(tenantId)` sur queries historic activities
- Rollbacks Liquibase 007 (`observer_user_ids`, `started_by`, index piid)

### Evidence (phase 9)

```text
JAVA_HOME=/tmp/jdk/jdk-25.0.4+7 ./mvnw -q verify
→ Surefire 540 tests, 0 failures/errors/skips
→ Jacoco instructions 88 % (≥ 80 %)
→ ArchUnit OK
→ npx ai-devkit lint --feature visualisation-workflow → All checks passed
```

### Follow-ups post-merge

- SSE sur transaction `COMMITTED` si API Flowable stable
- Cast `FlowableEngineEvent` à la place de la réflexion
- Pagination history ; DTOs HTTP dédiés ; append listeners

**Lifecycle :** phase 9 terminée — prêt à merger.
