---
phase: testing
title: Testing Strategy
description: Define testing approach, test cases, and quality assurance
status: draft
created: 2026-08-10
updated: 2026-08-10
design_aligned: 2026-08-10
phase2_sync: 2026-08-10
---

# Testing Strategy

## Test Coverage Goals
**What level of testing do we aim for?**

- **Unitaires domaine :** résolution participants (composition) ; use
  cases get diagram/history/tasks/dossier ; reassign authz ; validation
  `observerUserIds` / `WorkflowTypeConfig`.
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
- [ ] `observerUserIds` défaut `[]` ; copy immuable
- [ ] blank dans la liste → rejet construction / validation
- [ ] dédup à l’écriture admin

### Participant resolution (port/adapter ou helper)
- [ ] union initiateur + assignees actifs/historiques + observers
- [ ] ignore null/blank ; distinct
- [ ] config absente → observers `[]`
- [ ] link sans processInstanceId → pas de crash (liste observers only)

### Get diagram / history / tasks use cases
- [ ] participant OK → délègue port avec processInstanceId + tenantId
- [ ] non-participant → AccessDenied
- [ ] link inconnu → not found
- [ ] sans `processInstanceId` → WorkflowLinkReservedException (409)
- [ ] TA/WA bypass participant check

### Get link dossier use case
- [ ] charge dossier + elements triés ; `relations` toujours vide
- [ ] authz participant|TA/WA ; **autorise** sans `processInstanceId` (R1)

### Reassign use case
- [ ] WA/TA + tâche de l’instance → port.reassign
- [ ] non admin → AccessDenied même si participant
- [ ] assignee blank → 400 / IllegalArgument
- [ ] sans `processInstanceId` → reserved exception
- [ ] tâche autre instance → not found

### FlowableWorkflowVisualizationAdapter
- [ ] bpmnXml non vide ; active/completed peuplés
- [ ] failedIds = deleteReason startsWith `"error"` seulement
- [ ] tenant filter appliqué (pas de fuite cross-tenant)
- [ ] reassign appelle `taskService.setAssignee`

### WorkflowStateEventListener
- [ ] TASK_ASSIGNED / ACTIVITY_COMPLETED / PROCESS_COMPLETED → send
  BROWSER avec `payload.type=WORKFLOW_STATE_CHANGED` + linkId +
  processInstanceId (R2) pour chaque participant
- [ ] autres event types ignorés
- [ ] link introuvable → no-op (pas d’exception fatale)
- [ ] `isFailOnException == false`

### Exception handler
- [ ] WorkflowLinkReservedException → **409** ProblemDetail
- [ ] AccessDenied → **403** ; NotFound → **404**

## Integration Tests

### MockMvc visualisation
- [ ] GET diagram **200** pour participant (assignee ou initiator ou
      observer)
- [ ] GET diagram **403** pour user authentifié hors participants
- [ ] GET diagram **404** link inconnu
- [ ] GET diagram **409** link sans `processInstanceId`
- [ ] GET history / tasks **200** structure JSON
- [ ] GET dossier **200** avec `elements` + `relations: []` **y compris
      RESERVED** (R1)
- [ ] POST reassign **204** en WA ; **403** en DOSSIER_MANAGER participant
- [ ] POST reassign sans CSRF → **403** ; anonyme → **401**
- [ ] TA bypass lecture OK

### MockMvc / persistence config observers
- [ ] create/update `WorkflowTypeConfig` avec `observerUserIds`
- [ ] GET config renvoie la liste
- [ ] Liquibase 007 : colonne `observer_user_ids` présente (SchemaIT)

### Persistence helpers
- [ ] `DossierElementRepository.findByDossierId…` round-trip
- [ ] `DossierWorkflowLinkRepository.findByProcessInstanceId` (si ajouté)

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

## Sources

- Requirements + design `visualisation-workflow` draft (2026-08-10)
- Patterns tests : `WorkflowApiTest`, `Notification*ApiTest`,
  `DossierApiTest`
