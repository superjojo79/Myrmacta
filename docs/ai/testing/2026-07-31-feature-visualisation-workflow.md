---
phase: testing
title: Testing Strategy
description: Define testing approach, test cases, and quality assurance
status: verified
created: 2026-08-10
updated: 2026-08-10
design_aligned: 2026-08-10
phase2_sync: 2026-08-10
phase3_sync: 2026-08-10
phase4_sync: 2026-08-10
phase7_sync: 2026-08-10
---

# Testing Strategy

## Test Coverage Goals
**What level of testing do we aim for?**

- **Unitaires domaine :** résolution participants (`startedBy` + …) ;
  use cases get diagram/history/tasks/dossier ; reassign authz ;
  validation `observerUserIds` ; AttachWorkflow `startedBy`.
- **Unitaires infra :** adapter visualisation (XML, active/completed/
  failedIds) ; adapter participants ; listener SSE (events filtrés,
  fan-out, fail-soft) ; mapping 409/403/404.
- **IT H2 + MockMvc :** endpoints `/api/workflow-links/{id}/…` ;
  extension admin `observerUserIds` ; Liquibase 007.
- **Régression :** ArchUnit (domain sans Flowable API engine dans
  use cases si applicable) ; Jacoco ≥ 80 % ; suites workflow /
  notifications existantes vertes.
- **Hors scope :** UI bpmn-js, Flowable Cockpit, SMTP/SSE multi-pod,
  Testcontainers PostgreSQL réel.

## Unit Tests

### WorkflowTypeConfig + observers
- [x] `observerUserIds` défaut `[]` ; copy immuable
- [x] blank dans la liste → rejet construction / validation
- [x] dédup à l’écriture admin

### Participant resolution (port/adapter ou helper)
- [x] union `startedBy` + initiateur + assignees + observers
- [x] `startedBy` toujours inclus même avec PI
- [x] `"system:outbox"` présent dans la liste mais ne matche pas un sub humain
- [x] ignore null/blank ; distinct
- [x] config absente → observers `[]`
- [x] link sans processInstanceId → `startedBy` + observers (pas de crash)

### AttachWorkflow startedBy
- [x] reserve persiste `startedBy`
- [x] reserveOrResume sur link existant **ne remplace pas** `startedBy`
- [x] ManualStart / outbox passent les valeurs attendues (tests wiring)

### Get diagram / history / tasks use cases
- [x] participant OK → délègue port avec processInstanceId + tenantId
- [x] non-participant → AccessDenied
- [x] link inconnu → not found
- [x] sans `processInstanceId` → WorkflowProcessInstanceMissingException (409)
- [x] TA/WA bypass participant check

### Get link dossier use case
- [x] charge dossier + elements triés ; `relations` toujours vide
- [x] authz participant|TA/WA ; **autorise** sans `processInstanceId` (R1)

### Reassign use case
- [x] WA/TA + tâche de l’instance → port.reassign
- [x] non admin → AccessDenied même si participant
- [x] assignee blank → 400 / IllegalArgument
- [x] sans `processInstanceId` → reserved exception
- [x] tâche autre instance → not found

### FlowableWorkflowVisualizationAdapter
- [x] bpmnXml non vide ; active/completed peuplés
- [x] failedIds = deleteReason startsWith `"error"` seulement
- [x] tenant filter appliqué (pas de fuite cross-tenant)
- [x] reassign appelle `taskService.setAssignee`

### WorkflowStateEventListener
- [x] TASK_ASSIGNED / ACTIVITY_COMPLETED / PROCESS_COMPLETED → send
  BROWSER avec `payload.type=WORKFLOW_STATE_CHANGED` + linkId +
  processInstanceId (R2) pour chaque participant
- [x] autres event types ignorés
- [x] link introuvable → no-op (pas d’exception fatale)
- [x] `isFailOnException == false`

### Exception handler
- [x] WorkflowProcessInstanceMissingException → **409** ProblemDetail
- [x] AccessDenied → **403** ; NotFound → **404**

## Integration Tests

### MockMvc visualisation
- [x] GET diagram **200** pour participant (assignee ou initiator ou
      observer)
- [x] GET diagram **403** pour user authentifié hors participants
- [x] GET diagram **404** link inconnu
- [x] GET diagram **409** link sans `processInstanceId`
- [x] GET history / tasks **200** structure JSON
- [x] GET dossier **200** avec `elements` + `relations: []` **y compris
      RESERVED** (R1) pour `startedBy` (sans ACL type dossier)
- [x] GET dossier **200** pour observateur sans ACL type (A10)
- [x] POST reassign **204** en WA ; **403** en DOSSIER_MANAGER participant
- [x] POST reassign sans CSRF → **403** ; anonyme → **401**
- [x] TA bypass lecture OK

### MockMvc / persistence config observers
- [x] create/update `WorkflowTypeConfig` avec `observerUserIds`
- [x] GET config renvoie la liste
- [x] Liquibase 007 : colonne `observer_user_ids` présente (SchemaIT)

### Persistence helpers
- [x] `DossierElementRepository.findByDossierId…` round-trip
- [x] `DossierWorkflowLinkRepository.findByProcessInstanceId` (si ajouté)

## Mocks & Fixtures

- Process BPMN de test minimal déjà utilisé par workflow IT (réutiliser).
- Users MockMvc : `oidcLogin` avec rôles TA / WA / DOSSIER_MANAGER /
  user sans rôle + `sub` contrôlé.
- Stub/fake Flowable services en unitaire adapter ; IT sur moteur H2
  Flowable existant si les suites workflow le permettent, sinon mock
  port + IT HTTP avec `@MockBean` port (préférer IT réel quand
  low-cost).

## Coverage Targets

| Zone | Cible |
|---|---|
| Jacoco instructions projet | ≥ 80 % |
| ArchUnit | OK |
| Scénarios ci-dessus | tous `[x]` en phase 8 |

## Verification Evidence

- Targeted workflow command:
  `./mvnw -q test -Dtest='*Workflow*Test,*Workflow*IT,*Attach*,*Visualization*,*Participant*,*WorkflowLink*,*WorkflowState*'`
  → 68 tests, 0 failures/errors/skips.
- Full verification: `./mvnw -q verify` → 530 surefire tests, 0
  failures/errors/skips ; ArchUnit and Jacoco verify phase completed.

## Sources

- Requirements + design `visualisation-workflow` `reviewed` (2026-08-10)
- Planning phase 4 `ready` — traçabilité scénarios → T1–T13
- Patterns tests : `WorkflowApiTest`, `Notification*ApiTest`,
  `DossierApiTest`
