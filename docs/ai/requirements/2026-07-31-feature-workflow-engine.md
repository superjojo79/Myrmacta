---
phase: requirements
title: Requirements & Problem Understanding
description: Clarify the problem space, gather requirements, and define success criteria
status: draft
created: 2026-08-02
updated: 2026-08-02
---

# Requirements & Problem Understanding

## Problem Statement
**What problem are we solving?**

FlowForge a un **socle dossiers** (types, CRUD minimal, ACL type, JPA) et un
**squelette workflow in-memory** (`StartWorkflowUseCase` + stubs repository /
publisher), mais **aucun moteur BPMN réel**, aucun lien durable
dossier↔instance, ni chemin de déclenchement fiable. Sans cette tranche, les
connecteurs, la visualisation et les notifications ne peuvent pas s’ancrer sur
des instances Flowable et un historique d’exécutions.

- **Affectés :** gestionnaires / `WORKFLOW_ADMIN` (démarrage et suivi),
  développeurs des features aval (`connecteurs`, `notifications`,
  `visualisation-workflow`), ops (tables `ACT_*`, outbox, scheduler).
- **Situation actuelle :** exclusions autoconfig Flowable encore actives ;
  `InMemoryWorkflowRepository` / `InMemoryWorkflowEventPublisher` ;
  `WorkflowEventPublisher` typé sur `WorkflowStartedEvent` seulement (pas
  l’interface scellée décision n° 6) ; pas de `DossierWorkflowLink`, pas
  d’outbox. Analyse : `analysis doc/05-workflow-engine.md` +
  `04-dossiers.md` (lien / AttachWorkflow) + décisions n° 6, 7, 11, 13, 16, 17.
- **UX :** pas d’écrans Vue ici — API REST + tâches humaines Flowable
  consommées plus tard par `frontend` / `visualisation-workflow`. Contrat
  d’erreur `ProblemDetail` aligné sur dossiers.

## Goals & Objectives
**What do we want to achieve?**

### Primary goals

- Activer **Flowable 8** (`flowable-spring-boot-starter` déjà en `pom.xml`) :
  retrait des exclusions autoconfig Flowable nécessaires au runtime processus ;
  tables `ACT_*` dans le schéma tenant ; `tenantId` = `TenantProperties.id()`.
- Remplacer le squelette in-memory par un **adaptateur Flowable** pour
  démarrer une instance et compléter une tâche utilisateur.
- Livrer le domaine / infra **lien dossier↔workflow** :
  `DossierWorkflowLink` (record pur), ports
  `AttachWorkflowUseCase` (`reserve` / `reserveOrResume` / `confirmStarted`),
  `GetDossierWorkflowHistoryUseCase`, repository + adaptateur JPA, index unique
  partiel `uq_dossier_workflow_link_active`, mise à jour
  `Dossier.currentWorkflowLinkId`.
- Livrer **`WorkflowTypeConfig`** + `WorkflowStatusDefinition` /
  `StatusOutcome` ; validation des codes de statut métier du lien ; statut
  `initial` à la réservation.
- Livrer l’**outbox transactionnel + relais** (décisions n° 7 / 16) côté
  consommateur workflow : table `outbox_event`, `OutboxStatus`,
  `TriggerOutboxPayload`, scheduler de polling, idempotence `event_id`,
  retry / dead-letter / purge `PROCESSED`.
- **Producteur outbox minimal** pour cette tranche : un chemin **manuel /
  API** et un **producteur de test / seed** capable d’écrire
  `PENDING` sans livrer la feature `connecteurs` (le
  `ConnectorTriggerRuntime` reste hors scope).
- Aligner `WorkflowEvent` sur l’**interface scellée** (décision n° 6) :
  `Started` / `Completed` / `Failed` ; publisher générique ; adaptateur
  in-memory ou no-op acceptable en V1 (notifications = feature séparée).
- Ports : `StartWorkflowUseCase`, `CompleteTaskUseCase`,
  `UpdateWorkflowLinkStatusUseCase` ; delegates Flowable
  `UpdateWorkflowLinkStatusTask` (+ `UpdateDossierStatusTask` si le câblage
  dossier est trivial depuis variables).
- API REST minimale (session BFF + CSRF) :
  démarrage manuel sur dossier, historique des liens, complétion de tâche,
  CRUD / lecture `WorkflowTypeConfig` pour admin.
- Tests : unitaires domaine + relais (idempotence, collision, dead-letter) ;
  IT H2 mode PostgreSQL (Liquibase outbox + links + smoke Flowable) ;
  non-régression ArchUnit / Jacoco / auth / dossiers.

### Surface API (acceptation)

| Méthode | Path | Authz |
|---|---|---|
| POST | `/api/dossiers/{id}/workflows` | `EDIT` type (ou bypass admin) — démarre via reserve→Flowable→confirm |
| GET | `/api/dossiers/{id}/workflows` | `VIEW` (ou bypass) — historique trié `sequence` |
| POST | `/api/workflow-tasks/{taskId}/complete` | authentifié ; body variables ; contrôle accès dossier lié |
| POST | `/api/workflow-type-configs` | `TENANT_ADMIN` ou `WORKFLOW_ADMIN` |
| GET | `/api/workflow-type-configs` | authentifié (admins : tous ; sinon lecture) |
| GET | `/api/workflow-type-configs/{workflowKey}` | idem |

Mutateurs : CSRF BFF. Anonyme `/api/**` → 401. Erreurs : `ProblemDetail`
(dont collision workflow actif → 409).

### Secondary goals

- Documenter le contrat outbox / `TriggerOutboxPayload` pour `connecteurs`
  (sans implémenter les webhooks).
- Garder un **processus BPMN de smoke** versionné (classpath) pour IT /
  démo locale.
- Préserver le chemin d’évolution CDC/Debezium (doc only).

### Non-goals

- Feature `connecteurs` (HMAC, `ConnectorInstance`, DEK/KEK,
  `ConnectorTriggerRuntime` complet).
- Flowable UI Designer / Cockpit embarqués en prod.
- `visualisation-workflow`, `notifications` (listeners métier riches),
  `frontend`.
- `DossierElement` / `DossierRelation` / MinIO.
- Scaling multi-répliques d’un tenant (`FOR UPDATE SKIP LOCKED`, séparation
  API/worker) — documenté, non implémenté sauf config Flowable par défaut.
- Testcontainers PostgreSQL / tests de charge verrous Flowable (TODO 28).
- OpenAPI formel ; MDC / métriques Micrometer outbox (TODO 29) au-delà d’un
  compteur/log minimal acceptable.
- Chaînes sécu M2M / webhooks (follow-ups auth).

## Alternatives considérées

| Approche | Trade-off | Verdict |
|---|---|---|
| **A — Flowable réel + outbox + `DossierWorkflowLink` (cette tranche)** | Plus large, débloque connecteurs et historique | **Retenue** (alignée décisions 7/16 + DEFERRED) |
| B — Liens + API manuelle seulement, stubs Flowable | Rapide mais ne retire pas l’exclusion ni ne prouve le moteur | Écartée |
| C — Démarrage synchrone sans outbox | Simple, réouvre perte au crash (décision 7) | Écartée |

## User Stories & Use Cases
**How will users interact with the solution?**

- En tant que **utilisateur EDIT** (ou bypass), je démarre un workflow BPMN
  sur un dossier existant ; un `DossierWorkflowLink` est réservé puis confirmé
  avec `processInstanceId`.
- En tant que **utilisateur VIEW**, je consulte l’historique des exécutions
  d’un dossier (`sequence`, statuts métier, dates).
- En tant que **utilisateur habilité**, je complète une tâche humaine
  Flowable avec des variables.
- En tant que **TENANT_ADMIN / WORKFLOW_ADMIN**, je configure les statuts
  métier d’un `workflowKey` (`WorkflowTypeConfig`).
- En tant que **relais outbox**, je consomme les `PENDING`, démarre Flowable
  de façon idempotente, marque `PROCESSED` ou `DEAD`.
- En tant que **ops / CI**, je démarre l’app avec Flowable actif sur le schéma
  tenant sans exclusion bloquante, smoke process OK.

### Edge cases

- Second workflow actif sur le même dossier →
  `WorkflowAlreadyRunningException` / 409 / dead-letter si via outbox.
- Rejeu outbox : reprendre lien `RESERVED` ; no-op si déjà `ACTIVE` avec
  `processInstanceId`.
- Statut métier inconnu pour le `workflowKey` → rejet.
- Échecs répétés relais → `DEAD` après plafond `attempts`.
- Process definition absente → échec relais / API avec message actionnable.

## Success Criteria
**How will we know when we're done?**

- Exclusions Flowable retirées autant que nécessaire ; app démarre ; tables
  `ACT_*` + `outbox_event` + `dossier_workflow_link` + `workflow_type_config`
  créées (Liquibase / Flowable) sur H2 mode PostgreSQL en IT.
- `POST /api/dossiers/{id}/workflows` démarre une instance réelle ; GET
  historique renvoie le lien ; complete task avance le process smoke.
- Relais : scénarios unitaires/IT pour happy path, retry, dead-letter,
  collision, idempotence rejeu.
- Stubs in-memory workflow retirés du câblage production (tests peuvent
  garder des fakes ciblés).
- ArchUnit hexagonale + Jacoco ≥ 80 % non régressés ; suite dossiers / auth
  verte.
- Docs design/testing/planning alignés ; non-goals explicites pour
  connecteurs / UI Flowable / visualisation.

## Constraints & Assumptions
**What limitations do we need to work within?**

- Instance **mono-tenant** (décision n° 13) : `TenantProperties.id()` partout ;
  pas de `TenantContext` request-scoped pour le relais / delegates.
- Domaine pur sans JPA (décision n° 17) ; convention `port/in` + `usecase/*Impl`.
- Outbox option B (décision n° 16) : producteur connecteurs **hors scope** ;
  cette tranche livre le **schéma + relais + producteur API/manuel** et un
  hook/port prêt pour connecteurs.
- IT sur H2 MODE=PostgreSQL (comme dossiers) ; PG réel hors CI.
- Jackson 3 défaut Flowable 8 — rester sur le défaut sauf blocage prouvé
  (`flowable.variable-json-mapper=jackson2` en repli documenté).
- Ordre Liquibase / Flowable sous Boot 4 à valider au premier démarrage IT
  (vigilance décision n° 27).

### Assumptions (acceptées)

- A1 — Un seul process BPMN de smoke suffit pour l’acceptation IT.
- A2 — `WORKFLOW_ADMIN` peut administrer `WorkflowTypeConfig` (avec
  `TENANT_ADMIN`) ; liste définitive des rôles = TODO 19, hors blocage.
- A3 — Le démarrage manuel API peut démarrer Flowable **dans la requête**
  (reserve→start→confirm) ; l’outbox reste le chemin durable pour les
  triggers asynchrones (connecteurs futurs + éventuel enqueue manuel
  optionnel si utile aux tests).
- A4 — Pas de grant ACL par instance de workflow (décision n° 20) : l’accès
  suit l’ACL du `DossierType`.

## Questions & Open Items
**What do we still need to clarify?**

| # | Item | Disposition |
|---|---|---|
| Q1 | Embarquer Flowable UI en local ? | **Différé** — non-goal V1 ; designer externe / fichiers BPMN |
| Q2 | Enqueue manuel via outbox vs start synchrone API | **A3** — synchrone API ; outbox pour async |
| Q3 | Plafonds `attempts` / rétention purge outbox | Valeurs config par défaut documentées en design (`app.workflow.outbox.*`) ; réglage ops plus tard |
| Q4 | Testcontainers / charge verrous | **Différé** TODO 28 |
| Q5 | Métriques Micrometer âge outbox | **Différé** TODO 29 ; logs structurés min. dans la tranche |

## Sources

- `analysis doc/05-workflow-engine.md`, `04-dossiers.md` (lien / AttachWorkflow)
- Décisions TODO n° 6, 7, 11, 13, 16, 17, 20, 27
- `DEFERRED-FEATURES.md` §2 ordre 1 `workflow-engine`
- Code existant : `domain.workflow.*`, stubs
  `infrastructure.persistence.workflow` / `infrastructure.flowable`
