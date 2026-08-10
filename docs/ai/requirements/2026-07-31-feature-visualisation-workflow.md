---
phase: requirements
title: Requirements & Problem Understanding
description: Clarify the problem space, gather requirements, and define success criteria
status: draft
created: 2026-08-10
updated: 2026-08-10
stakeholder_decisions: 2026-08-10
---

# Requirements & Problem Understanding

## Problem Statement
**What problem are we solving?**

FlowForge orchestre des instances Flowable liées aux dossiers
(`DossierWorkflowLink`, start/history/complete), mais **aucune API de
visualisation runtime** n’expose le diagramme BPMN, l’historique
d’activités, les tâches actives ni la réassignation. Les participants
d’une instance ne peuvent pas inspecter l’état d’exécution ; le futur
Viewer Vue/`bpmn-js` (feature `frontend`) n’a pas de contrat backend.

- **Affectés :** instructeurs / assignés, initiateurs, observateurs
  configurés, `WORKFLOW_ADMIN` / `TENANT_ADMIN`, feature aval `frontend`.
- **Situation actuelle :** `GET /api/dossiers/{id}/workflows` + complete
  task livrés ; packages visualisation absents ; SSE notifications livré
  (canal `BROWSER`) ; `WorkflowTypeConfig` sans observateurs. Analyse :
  `analysis doc/10-visualisation-workflow.md` ; roadmap
  `DEFERRED-FEATURES.md`.
- **UX V1 :** **pas d’écrans Vue / bpmn-js** (**D-SCOPE = A**) — API REST
  + listener SSE uniquement. Erreurs `ProblemDetail` alignées dossiers /
  workflow / notifications.

## Goals & Objectives
**What do we want to achieve?**

### Primary goals

- Livrer le **socle visualisation** hexagonal (**D-SCOPE = A**) :
  ports `WorkflowVisualizationPort` + `WorkflowParticipantPort`, use
  cases lecture + réassign, adaptateur Flowable, API REST sur
  **`/api/workflow-links/{linkId}/…`** (**D-ID = B**).
- **Contrôle d’accès participants** (**D-AUTHZ = B**,
  **D-PARTICIPANT = B**) : initiateur (`startUserId`) + assignés
  (tâches actives **et** historiques) + observateurs configurés ;
  bypass `TENANT_ADMIN` / `WORKFLOW_ADMIN`.
- **Observateurs** (**D-OBS = A**, **D-OBS-ID = A**) : champ
  `observerUserIds` (`List<String>` = OIDC `sub`) sur
  `WorkflowTypeConfig` ; exposé sur le CRUD admin config existant ;
  Liquibase `007`.
- **Surface API** (**D-API = B**) :
  - `GET …/diagram` — XML BPMN + `activeIds` / `completedIds` / `failedIds`
  - `GET …/history` — étapes (timing, durée, assigné)
  - `GET …/tasks` — tâches actives + assigné
  - `POST …/tasks/{taskId}/reassign` — réassignation
  - `GET …/dossier` — `{ dossier, elements, relations: [] }`
    (**D-DOSSIER-PAYLOAD = B**)
- **Réassign** (**D-REASSIGN-AUTHZ = A**) : `WORKFLOW_ADMIN` |
  `TENANT_ADMIN` uniquement (+ CSRF).
- **États HTTP** (**D-DENY = A**, **D-RESERVED = A**) : link inconnu /
  autre tenant → **404** ; non-participant → **403** ; link `RESERVED`
  (pas de `processInstanceId`) → **409**.
- **`failedIds`** (**D-FAIL = A**) : activités historiques dont
  `deleteReason` préfixe `"error"`.
- **SSE état** (**D-SSE = A**) : `FlowableEventListener` sur
  `TASK_ASSIGNED` / `ACTIVITY_COMPLETED` / `PROCESS_COMPLETED` →
  `SendNotificationUseCase` canal `BROWSER`, type
  `WORKFLOW_STATE_CHANGED`, payload `{ linkId, processInstanceId }`
  vers chaque participant (et bypass admins non requis pour le push —
  uniquement les userIds résolus par le port).
- Tests unitaires + MockMvc/IT H2 + ArchUnit ; Jacoco ≥ 80 %.

### Surface API (acceptation)

| Méthode | Path | Authz / notes |
|---|---|---|
| GET | `/api/workflow-links/{linkId}/diagram` | Participant ou TA/WA ; **409** si RESERVED |
| GET | `/api/workflow-links/{linkId}/history` | Idem |
| GET | `/api/workflow-links/{linkId}/tasks` | Idem |
| GET | `/api/workflow-links/{linkId}/dossier` | Idem ; `relations` toujours `[]` V1 |
| POST | `/api/workflow-links/{linkId}/tasks/{taskId}/reassign` | `WORKFLOW_ADMIN` \| `TENANT_ADMIN` + CSRF ; body `{ "assigneeId" }` ; **409** si RESERVED |

Déjà livré (hors scope nouveau, non régresser) :
`GET|POST /api/dossiers/{id}/workflows`,
`POST /api/workflow-tasks/{taskId}/complete`.

Extension admin existante :

| Méthode | Path | Notes |
|---|---|---|
| POST/PUT/PATCH (existant) | `/api/workflow-type-configs…` | Accepte / renvoie `observerUserIds` (défaut `[]`) |

Anonyme → **401**. Erreurs : `ProblemDetail`.

### Contrats de réponse

**Diagram** — `WorkflowDiagramView` :

```json
{
  "bpmnXml": "<?xml …>",
  "activeIds": ["taskA"],
  "completedIds": ["startEvent"],
  "failedIds": ["serviceTaskX"]
}
```

**History** — liste `ActivityHistoryEntry` :

```json
[{
  "activityId": "taskA",
  "name": "Instruct",
  "assignee": "user-sub",
  "startTime": "…",
  "endTime": null,
  "durationMs": null
}]
```

**Tasks** — tâches actives Flowable (id, name, assignee, createTime,
taskDefinitionKey).

**Reassign** — **204** No Content.

**Dossier** :

```json
{
  "dossier": { /* Dossier DTO existant */ },
  "elements": [ /* DossierElement[] */ ],
  "relations": []
}
```

### Secondary goals

- Préparer le contrat pour le futur `WorkflowViewer` (`frontend`) sans
  livrer de code Vue.
- Logs structurés minimaux (`tenantId`, `linkId`, `processInstanceId`).
- Documenter follow-up incidents Flowable (au-delà de D-FAIL=A).

### Non-goals (explicitly out of scope)

- UI Vue 3 / `bpmn-js` / overlays CSS (feature `frontend`) (**D-SCOPE**).
- Flowable UI / Cockpit embarqué.
- Auto-refresh SPA sans action utilisateur (bannière côté client via SSE).
- Incidents Flowable avancés / dead-letter jobs UI.
- Relations entre dossiers réelles (`relations` reste `[]`).
- Observateurs par rôles realm ou par instance (D-OBS / D-OBS-ID).
- Authz lecture via seul `DossierAccessGuard` (écarté, D-AUTHZ=B).
- OpenAPI formel ; observabilité Micrometer complète (§29).

## Alternatives considérées

| Approche | Trade-off | Verdict |
|---|---|---|
| Backend only vs + Viewer vs contrat UI | Dépendance `frontend` | **Backend only** (D-SCOPE A) |
| Authz dossier vs participants vs rôles seuls | Fidélité analyse | **Participants** (D-AUTHZ B) |
| Observateurs type vs instance vs différés | Modèle manquant | **Sur WorkflowTypeConfig** (D-OBS A) |
| Observateurs userIds vs rôles | Scalabilité | **userIds sub** (D-OBS-ID A) |
| Lecture seule vs + reassign | Surface mutation | **+ reassign WA** (D-API B) |
| URL processInstanceId vs linkId | RESERVED | **linkId** (D-ID B) |
| 403/404 aligné vs opaque | Cohérence | **Aligné dossiers** (D-DENY A) |
| RESERVED 409 vs 404 vs vide | Clarté | **409** (D-RESERVED A) |
| failedIds heuristique vs incidents vs vide | Précision | **Heuristique** (D-FAIL A) |
| SSE listener vs différé | Temps réel | **Listener** (D-SSE A) |

## User Stories & Use Cases

1. **En tant que** assigné / initiateur / observateur, **je veux** lire le
   diagramme et l’historique d’une instance, **afin de** comprendre où en
   est le traitement.
2. **En tant que** `WORKFLOW_ADMIN`, **je veux** réassigner une tâche
   active, **afin de** débloquer une instruction.
3. **En tant que** `TENANT_ADMIN`, **je veux** configurer
   `observerUserIds` sur un type de workflow, **afin que** des lecteurs
   non assignés suivent les runs.
4. **En tant qu’** utilisateur connecté participant, **je veux** recevoir
   un push SSE `WORKFLOW_STATE_CHANGED`, **afin de** savoir qu’un refresh
   diagramme est nécessaire (future SPA).
5. **En tant que** client API, **je veux** le panneau dossier lié
   (`dossier` + `elements`), **afin d’** afficher le contexte à côté du
   diagramme sans appels multiples.

### Edge cases

- `linkId` inconnu ou tenant différent → **404**.
- Utilisateur authentifié non participant (hors TA/WA) → **403**.
- Link `RESERVED` (pas de `processInstanceId`) → **409** sur tous les
  endpoints viz / reassign.
- Réassign : tâche absente / autre instance → **404** ; assignee blank →
  **400** ; non WA/TA → **403** ; sans CSRF → **403**.
- Process terminé : diagram OK (historique) ; `tasks` → `[]` ; reassign →
  **404** (pas de tâche active).
- `observerUserIds` null à l’écriture → traité comme `[]` ; valeurs blank
  rejetées (**400**).
- Listener SSE : échec notif **non fatale** pour Flowable
  (`isFailOnException = false`).
- Liste éléments : ajouter lecture par `dossierId` si absente du port
  `DossierElementRepository` (gap actuel).

## Success Criteria
**How will we know when we're done?**

- [ ] `WorkflowVisualizationPort` + adapter Flowable exposent diagram /
      history / tasks / reassign (tenant-safe).
- [ ] `WorkflowParticipantPort` résout initiateur + assignés +
      `observerUserIds` ; bypass TA/WA en couche HTTP/use case.
- [ ] `WorkflowTypeConfig` persiste `observerUserIds` (Liquibase 007) ;
      CRUD admin les accepte / renvoie.
- [ ] Endpoints `/api/workflow-links/{linkId}/…` : authz + 401/403/404/409
      selon D-DENY / D-RESERVED.
- [ ] `GET …/dossier` renvoie dossier + elements + `relations: []`.
- [ ] `WorkflowStateEventListener` envoie `WORKFLOW_STATE_CHANGED`
      (BROWSER) aux participants.
- [ ] `./mvnw verify` vert ; Jacoco instructions ≥ 80 % ; ArchUnit OK.
- [ ] `npx ai-devkit lint --feature visualisation-workflow` OK (branche
      feature).

## Constraints & Assumptions

- Java 25 · Spring Boot 4.x · Flowable 8 · hexagone mono-module ·
  H2 MODE=PostgreSQL IT.
- Tenant = instance dédiée / `TenantProperties` (décision n° 13).
- XML BPMN natif Flowable — **aucune conversion** de format.
- Réutiliser `SendNotificationUseCase` / canal `BROWSER` existants.
- **A1** — `instanceId` dans l’analyse = `processInstanceId` Flowable ;
  l’URL publique utilise `linkId` (D-ID B) ; le payload SSE porte les deux.
- **A2** — Initiateur = `HistoricProcessInstance.getStartUserId()` (peut
  être null → ignoré).
- **A3** — Assignés = union distincte des assignees tâches runtime +
  historic (null ignorés).
- **A4** — Observateurs lus depuis `WorkflowTypeConfig` du
  `processDefinitionKey` du link (même tenant).
- **A5** — `DossierElementRepository` gagnera
  `findByDossierIdOrderByAddedAt(UUID)` (ou équivalent) pour le panneau.
- **A6** — Jacoco ≥ 80 % projet ; pas de Testcontainers Flowable UI.
- **A7** — Traçage `task` CLI indisponible (`unknown command 'task'`).

## Questions & Open Items

- Aucune question bloquante restante après décisions stakeholder
  2026-08-10 (D-SCOPE … D-NONGOALS).
- Follow-ups documentés : incidents Flowable riches ; relations dossier ;
  Viewer Vue dans `frontend`.

## Sources

- `analysis doc/10-visualisation-workflow.md`
- `analysis doc/12-autorisation-conformite.md` (réassign WA)
- `analysis doc/04-dossiers.md` (panneau dossier)
- `DEFERRED-FEATURES.md`
- Décisions stakeholder 2026-08-10 (session lifecycle)
