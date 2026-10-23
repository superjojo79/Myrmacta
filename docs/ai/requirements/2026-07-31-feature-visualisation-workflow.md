---
phase: requirements
title: Requirements & Problem Understanding
description: Clarify the problem space, gather requirements, and define success criteria
status: reviewed
created: 2026-08-10
updated: 2026-08-10
stakeholder_decisions: 2026-08-10
phase2: 2026-08-10
phase2_stakeholder_confirm: 2026-08-10
phase3_sync: 2026-08-10
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
  `docs/specs/10-visualisation-workflow.md` ; roadmap
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
  **D-PARTICIPANT = B**, **R3**) : `startedBy` du link (toujours) +
  initiateur Flowable (`startUserId`) + assignés (tâches actives **et**
  historiques) + observateurs configurés ; bypass `TENANT_ADMIN` /
  `WORKFLOW_ADMIN`.
- **`startedBy` sur `DossierWorkflowLink`** (**R3** / DD-STARTEDBY) :
  colonne persistée à la réserve ; manuel = OIDC `sub` ; outbox =
  `"system:outbox"` ; signatures `reserve` / `reserveOrResume` étendues.
- **Observateurs** (**D-OBS = A**, **D-OBS-ID = A**) : champ
  `observerUserIds` (`List<String>` = OIDC `sub`) sur
  `WorkflowTypeConfig` ; exposé sur
  `POST|PATCH /api/workflow-type-configs` (**TA|WA**, authz admin
  existante) ; Liquibase `007` (observers + `started_by`).
- **Surface API** (**D-API = B**) :
  - `GET …/diagram` — XML BPMN + `activeIds` / `completedIds` / `failedIds`
  - `GET …/history` — étapes (timing, durée, assigné)
  - `GET …/tasks` — tâches actives + assigné
  - `POST …/tasks/{taskId}/reassign` — réassignation
  - `GET …/dossier` — `{ dossier, elements, relations: [] }`
    (**D-DOSSIER-PAYLOAD = B**)
- **Réassign** (**D-REASSIGN-AUTHZ = A**) : `WORKFLOW_ADMIN` |
  `TENANT_ADMIN` uniquement (+ CSRF).
- **États HTTP** (**D-DENY = A**, **D-RESERVED** raffiné **R1**) :
  link inconnu / autre tenant → **404** ; non-participant → **403** ;
  `processInstanceId` absent → **409** sur diagram / history / tasks /
  reassign (**pas** sur `GET …/dossier`).
- **`failedIds`** (**D-FAIL = A**) : activités historiques dont
  `deleteReason` préfixe `"error"` (sensible à la casse).
- **SSE état** (**D-SSE = A**, **R2**) : `FlowableEventListener` sur
  `TASK_ASSIGNED` / `ACTIVITY_COMPLETED` / `PROCESS_COMPLETED` →
  `SendNotificationUseCase` canal `BROWSER` ; le discriminant
  `type=WORKFLOW_STATE_CHANGED` vit **dans** `NotificationEvent.payload`
  (pas de champ `type` sur l’événement domaine) avec `linkId` +
  `processInstanceId` ; fan-out aux userIds du port participants
  uniquement (pas de push automatique TA/WA hors liste).
- Tests unitaires + MockMvc/IT H2 + ArchUnit ; Jacoco ≥ 80 %.

### Surface API (acceptation)

| Méthode | Path | Authz / notes |
|---|---|---|
| GET | `/api/workflow-links/{linkId}/diagram` | Participant ou TA/WA ; **409** si pas de `processInstanceId` |
| GET | `/api/workflow-links/{linkId}/history` | Idem |
| GET | `/api/workflow-links/{linkId}/tasks` | Idem |
| GET | `/api/workflow-links/{linkId}/dossier` | Participant ou TA/WA ; **200** même sans `processInstanceId` (RESERVED/abort) ; `relations: []` |
| POST | `/api/workflow-links/{linkId}/tasks/{taskId}/reassign` | `WORKFLOW_ADMIN` \| `TENANT_ADMIN` + CSRF ; body `{ "assigneeId" }` ; **409** si pas de PI |

Déjà livré (hors scope nouveau, non régresser) :
`GET|POST /api/dossiers/{id}/workflows`,
`POST /api/workflow-tasks/{taskId}/complete`.

Extension admin existante :

| Méthode | Path | Authz / notes |
|---|---|---|
| POST | `/api/workflow-type-configs` | `TENANT_ADMIN` \| `WORKFLOW_ADMIN` + CSRF ; `observerUserIds?` (défaut `[]`) |
| PATCH | `/api/workflow-type-configs/{workflowKey}` | Idem |
| GET | `/api/workflow-type-configs` / `/{workflowKey}` | Authentifié ; réponse inclut `observerUserIds` |

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

**Tasks** — tâches actives Flowable (`taskId`, `name`, `assignee`,
`createTime`, `taskDefinitionKey`).

**Reassign** — **204** No Content.

**Dossier** :

```json
{
  "dossier": { /* Dossier DTO existant */ },
  "elements": [ /* DossierElement[] */ ],
  "relations": []
}
```

**SSE `notification` event** (forme outbox BROWSER existante) :

```json
{
  "id": "<outbox-uuid>",
  "at": "<ISO-8601>",
  "payload": {
    "type": "WORKFLOW_STATE_CHANGED",
    "linkId": "<uuid>",
    "processInstanceId": "<flowable-id>"
  }
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
- Cross-check ACL type dossier en plus des participants (V1 = participants
  only pour lecture viz).
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
| RESERVED 409 vs 404 vs vide | Clarté | **409 si pas de PI** (D-RESERVED / R1) |
| GET dossier aussi 409 vs 200 sans PI | Panneau utile dès réserve | **200 sans PI** (R1) |
| failedIds heuristique vs incidents vs vide | Précision | **Heuristique** (D-FAIL A) |
| SSE listener vs différé | Temps réel | **Listener** (D-SSE A) |
| `type` champ événement vs dans payload | Contrat notif existant | **Dans payload** (R2) |

## User Stories & Use Cases

1. **En tant que** assigné / initiateur / observateur, **je veux** lire le
   diagramme et l’historique d’une instance, **afin de** comprendre où en
   est le traitement.
2. **En tant que** `WORKFLOW_ADMIN`, **je veux** réassigner une tâche
   active, **afin de** débloquer une instruction.
3. **En tant que** `TENANT_ADMIN` ou `WORKFLOW_ADMIN`, **je veux**
   configurer `observerUserIds` sur un type de workflow, **afin que** des
   lecteurs non assignés suivent les runs.
4. **En tant qu’** utilisateur connecté participant, **je veux** recevoir
   un push SSE dont `payload.type = WORKFLOW_STATE_CHANGED`, **afin de**
   savoir qu’un refresh diagramme est nécessaire (future SPA).
5. **En tant que** client API, **je veux** le panneau dossier lié
   (`dossier` + `elements`) y compris pendant `RESERVED`, **afin d’**
   afficher le contexte avant le démarrage Flowable.

### Edge cases

- `linkId` inconnu ou tenant différent → **404**.
- Utilisateur authentifié non participant (hors TA/WA) → **403**.
- `processInstanceId` null (RESERVED **ou** abort avant start) → **409**
  sur diagram / history / tasks / reassign ; **200** sur `GET …/dossier`.
- Instance Flowable introuvable malgré PI présent → **404** (détail
  ProblemDetail).
- Réassign : tâche absente / autre instance → **404** ; assignee blank →
  **400** ; non WA/TA → **403** ; sans CSRF → **403**.
- Process terminé : diagram OK (historique) ; `tasks` → `[]` ; reassign →
  **404** (pas de tâche active).
- `observerUserIds` null à l’écriture → traité comme `[]` ; valeurs blank
  rejetées (**400**) ; dédup order-preserving.
- Listener SSE : échec notif **non fatale** pour Flowable
  (`isFailOnException = false`) ; link inconnu pour un event → no-op.
- Liste éléments : ajouter lecture par `dossierId` si absente du port
  `DossierElementRepository` (gap actuel — A5).

## Success Criteria
**How will we know when we're done?**

- [ ] `WorkflowVisualizationPort` + adapter Flowable exposent diagram /
      history / tasks / reassign (tenant-safe).
- [ ] `WorkflowParticipantPort` résout `startedBy` + initiateur +
      assignés + `observerUserIds` ; bypass TA/WA dans les use cases.
- [ ] `DossierWorkflowLink.startedBy` persisté (Liquibase 007) ; reserve
      manuel / outbox renseignent la valeur (R3).
- [ ] `WorkflowTypeConfig` persiste `observerUserIds` (Liquibase 007) ;
      `POST|PATCH /api/workflow-type-configs` les acceptent / renvoient
      (authz TA|WA).
- [ ] Endpoints `/api/workflow-links/{linkId}/…` : authz + 401/403/404/409
      selon D-DENY / R1 (dossier jamais 409 pour absence de PI).
- [ ] `GET …/dossier` renvoie dossier + elements + `relations: []`.
- [ ] `WorkflowStateEventListener` envoie BROWSER avec
      `payload.type=WORKFLOW_STATE_CHANGED` aux participants.
- [ ] `./mvnw verify` vert ; Jacoco instructions ≥ 80 % ; ArchUnit OK.
- [ ] `npx ai-devkit lint --feature visualisation-workflow` OK (branche
      feature).

## Constraints & Assumptions

- Java 25 · Spring Boot 4.x · Flowable 8 · hexagone mono-module ·
  H2 MODE=PostgreSQL IT.
- Tenant = instance dédiée / `TenantProperties` (décision n° 13).
- XML BPMN natif Flowable — **aucune conversion** de format.
- Réutiliser `SendNotificationUseCase` / canal `BROWSER` existants
  (snapshot outbox `browser(userId, payload)` inchangé).
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
- **A8** — Discriminant SSE = `payload.type` (contrat
  `NotificationEvent` sans champ type dédié) — **R2**.
- **A9** — Préfixe `failedIds` : `deleteReason != null &&
  deleteReason.startsWith("error")` (casse exacte).
- **A10** — Authz lecture viz = participants **ou** TA/WA ; **pas** de
  second filtre `DossierAccessGuard` / `GetDossierUseCase` V1 — le panneau
  charge `DossierRepository` + éléments directement après authz
  participant (DD-DOSSIER-LOAD A).
- **A11** — `startedBy` : toujours participant (DD-STARTEDBY-PART A) ;
  valeur `"system:outbox"` n’accorde de droit à aucun user OIDC humain.
- **A12** — Authz lecture/réassign appliquée **dans les use cases**
  (DD-AUTHZ-LAYER A), pas seulement un filter HTTP.

## Questions & Open Items

| # | Item | Disposition |
|---|---|---|
| Q1 | Périmètre UI | **Clos** — D-SCOPE A |
| Q2 | Modèle authz | **Clos** — D-AUTHZ B / D-PARTICIPANT B |
| Q3 | Observateurs | **Clos** — D-OBS A / D-OBS-ID A |
| Q4 | Surface API | **Clos** — D-API B / D-ID B |
| Q5 | SSE | **Clos** — D-SSE A + R2 (type dans payload) |
| Q6 | Réassign authz | **Clos** — D-REASSIGN-AUTHZ A |
| Q7 | 403/404 | **Clos** — D-DENY A |
| Q8 | Link sans PI | **Clos** — D-RESERVED + **R1** (409 engine ; dossier 200) |
| Q9 | failedIds | **Clos** — D-FAIL A / A9 |
| Q10 | Payload dossier | **Clos** — D-DOSSIER-PAYLOAD B |
| Q11 | Non-goals | **Clos** — D-NONGOALS A |
| Q12 | Admin observers path/authz | **Clos** — phase 2 (TA\|WA, paths réels) |
| Q13 | ACL dossier supplémentaire | **Clos** — A10 (non V1) |
| Q14 | Accès RESERVED sans PI | **Clos** — R3 `startedBy` |
| Q15 | Couche authz / load dossier | **Clos** — phase 3 (A12, DD-DOSSIER-LOAD) |

### Décisions actées

| Id | Sujet | Choix |
|---|---|---|
| D-SCOPE | Tranche | Backend only (pas de Vue/bpmn-js) |
| D-AUTHZ | Lecture | `WorkflowParticipantPort` + bypass TA/WA |
| D-PARTICIPANT | Composition | `startedBy` + initiateur + assignés + observateurs |
| D-OBS | Stockage observateurs | `WorkflowTypeConfig` |
| D-OBS-ID | Identité | `observerUserIds` = OIDC `sub` |
| D-API | Endpoints | diagram, history, tasks, dossier + reassign |
| D-SSE | Push | Listener → BROWSER `WORKFLOW_STATE_CHANGED` |
| D-REASSIGN-AUTHZ | Mutation | WA \| TA + CSRF |
| D-ID | Clé URL | `/api/workflow-links/{linkId}` |
| D-DENY | Refus | 404 absent ; 403 non autorisé |
| D-RESERVED | Sans PI | 409 sur endpoints moteur |
| D-FAIL | Erreurs activité | `deleteReason` préfixe `error` |
| D-DOSSIER-PAYLOAD | Panneau | dossier + elements + `relations: []` |
| D-NONGOALS | Hors scope | Paquet UI / cockpit / relations / rôles |
| R1 | GET dossier sans PI | **200** (pas 409) |
| R2 | Forme SSE type | Dans `payload.type` |
| R3 | Starter link | `startedBy` persisté ; toujours participant |

**Phase 1 close** (2026-08-10) — D-SCOPE…D-NONGOALS.  
**Phase 2 close** (2026-08-10) — statut `reviewed` ; R1–R2 + A8–A10 ;
chemins admin `workflow-type-configs` précisés ; questions Q1–Q13 closes.  
**Phase 3 sync** (2026-08-10) — R3 + A11–A12 ; design `reviewed`.

Follow-ups non bloquants : incidents Flowable riches ; relations dossier ;
Viewer Vue (`frontend`).

## Sources

- `docs/specs/10-visualisation-workflow.md`
- `docs/specs/12-autorisation-conformite.md` (réassign WA)
- `docs/specs/04-dossiers.md` (panneau dossier)
- `DEFERRED-FEATURES.md`
- Code : `WorkflowTypeConfigController`, `NotificationEvent` /
  `NotificationOutboxRelay` (forme SSE)
- Décisions stakeholder 2026-08-10 (phases 1–2)
