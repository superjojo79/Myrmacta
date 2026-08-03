---
phase: requirements
title: Requirements & Problem Understanding
description: Clarify the problem space, gather requirements, and define success criteria
status: reviewed
reviewed: 2026-08-02
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
- Remplacer le squelette in-memory : la **source de vérité runtime** devient
  Flowable + `DossierWorkflowLink`. `StartWorkflowUseCase` /
  `WorkflowInstance` in-memory sont migrés ou retirés du câblage prod (les
  tests peuvent garder des fakes).
- Livrer le domaine / infra **lien dossier↔workflow** :
  `DossierWorkflowLink` (record pur), ports
  `AttachWorkflowUseCase` (`reserve` / `reserveOrResume` / `confirmStarted`),
  `GetDossierWorkflowHistoryUseCase`, repository + adaptateur JPA, index unique
  partiel `uq_dossier_workflow_link_active`, mise à jour
  `Dossier.currentWorkflowLinkId`.
- Livrer **`WorkflowTypeConfig`** + `WorkflowStatusDefinition` /
  `StatusOutcome` ; validation des codes de statut métier du lien ; statut
  `initial` à la réservation ; **`allowedDossierStatuses[]`**
  (**D-STATUS = option 3**) — le start (API ou relais) refuse si
  `Dossier.status` ∉ liste (400/409 métier) ;
  **`onStartDossierStatus` nullable (**D-DOSSIER-STATUS = option 2**)** —
  si renseigné, après `confirmStarted` appliquer la transition dossier
  (si légale), sinon ne pas toucher `Dossier.status` (BPMN reste source
  habituelle via delegate).
- Livrer l’**outbox transactionnel + relais** (décisions n° 7 / 16) :
  table `outbox_event`, `OutboxStatus`, `TriggerOutboxPayload`, scheduler,
  idempotence `event_id`, retry / dead-letter / purge `PROCESSED`.
- **Démarrage hybride (décision revue D-START = option 3)** :
  - **Manuel / API humaine** : synchrone `reserve → Flowable.start → confirm`
    dans la requête HTTP (feedback immédiat).
  - **Triggers non-humains** (connecteurs, jobs) : **uniquement** via outbox
    + relais — jamais de start Flowable dans la TX producteur.
  - **Producteur outbox V1** : port/repository pour **tests / seed** + contrat
    prêt pour `connecteurs`. **Pas** d’endpoint public d’enqueue.
  - **Réconciliation RESERVED (**D-ORPHAN = option 2**)** : après timeout
    `app.workflow.reserve-timeout` (défaut **5 min**), job de reprise —
    retente `start+confirm` si pas d’instance ; si instance corrélée trouvée →
    `confirmStarted` ; sinon termine le lien (`endedAt`,
    `endReason=ORPHAN_TIMEOUT`) et libère le dossier.
  - **Compensation synchrone (**D-FAIL = option 2**)** : si `Flowable.start`
    échoue dans la requête API après `reserve` → terminer le lien immédiatement
    (`endReason=ORPHAN_START_FAILED` ou équivalent) + ProblemDetail ; D-ORPHAN
    reste le filet si la compensation n’a pas pu être commitée.
- Aligner `WorkflowEvent` sur l’**interface scellée** (décision n° 6) :
  `Started` / `Completed` / `Failed` ; publisher générique ; adaptateur
  in-memory ou no-op acceptable en V1 (notifications = feature séparée).
- Ports : `CompleteTaskUseCase`, `UpdateWorkflowLinkStatusUseCase` ;
  delegate Flowable **`UpdateWorkflowLinkStatusTask` (requis)** ;
  `UpdateDossierStatusTask` **inclus** s’il se limite à appeler le use case
  statut dossier existant depuis variables process (sinon follow-up).
- API REST minimale (session BFF + CSRF) — tableau ci-dessous.
- Tests : unitaires domaine + relais ; IT H2 mode PostgreSQL (Liquibase +
  smoke Flowable) ; non-régression ArchUnit / Jacoco / auth / dossiers.

### Surface API (acceptation)

| Méthode | Path | Authz |
|---|---|---|
| POST | `/api/dossiers/{id}/workflows` | **D-AUTHZ = option 3** : `EDIT` sur le type (ou bypass `TENANT_ADMIN` / `WORKFLOW_ADMIN`) — reserve→Flowable→confirm **synchrone** ; permission dédiée `START_WORKFLOW` éventuelle = TODO 19 (hors V1). **D-IDEM = option 2** : header `Idempotency-Key` (ou équivalent) requis pour les mutateurs start — même clé + même dossier → même lien sans second start ; clé différente pendant actif → 409 |
| GET | `/api/dossiers/{id}/workflows` | `VIEW` (ou bypass) — historique trié `sequence` |
| POST | `/api/workflow-tasks/{taskId}/complete` | `EDIT` sur le type du dossier lié (ou bypass) ; body variables |
| POST | `/api/workflow-type-configs` | `TENANT_ADMIN` ou `WORKFLOW_ADMIN` |
| PATCH | `/api/workflow-type-configs/{workflowKey}` | `TENANT_ADMIN` ou `WORKFLOW_ADMIN` |
| GET | `/api/workflow-type-configs` | authentifié — liste lisible par tout utilisateur authentifié |
| GET | `/api/workflow-type-configs/{workflowKey}` | authentifié |

**Body `POST …/workflows` :** `processDefinitionKey` (requis) + `variables`
(optionnel, objet JSON). La clé doit référencer un `WorkflowTypeConfig`
existant (**D-KEY = option 2**). `DossierType.workflowKey` = **hint UI
uniquement** — aucune égalité imposée côté serveur.

**Variables (**D-VARS = option 2**)** : map libre sauf clés **réservées
refusées** dans le body/payload client — `dossierId`,
`dossierWorkflowLinkId`, `tenantId` → 400 si présentes. La plateforme les
injecte après `reserve` / avant `start`. Pas de JSON Schema start en V1.

Mutateurs : CSRF BFF. Anonyme `/api/**` → 401. Erreurs : `ProblemDetail`
(collision workflow actif → 409 ; statut métier inconnu → 400 ;
process/config introuvable → 404).

### Secondary goals

- Documenter le contrat outbox / `TriggerOutboxPayload` pour `connecteurs`
  (sans implémenter les webhooks).
- Un **processus BPMN de smoke** versionné (classpath) pour IT / démo locale.
- Préserver le chemin d’évolution CDC/Debezium (doc only).

### Non-goals

- Feature `connecteurs` (HMAC, `ConnectorInstance`, DEK/KEK,
  `ConnectorTriggerRuntime` complet) et **tout endpoint public d’enqueue
  outbox**.
- Flowable UI Designer / Cockpit embarqués.
- `visualisation-workflow` (liste des tâches, diagrammes), listeners
  `notifications` riches, `frontend`.
- `GET` collection des tâches Flowable (complete uniquement par `taskId`).
- Chaînage automatique `startChainedWorkflowTask` (ServiceTask N→N+1).
- `DossierElement` / `DossierRelation` / MinIO.
- Scaling multi-répliques d’un tenant (`FOR UPDATE SKIP LOCKED`, séparation
  API/worker) — config async Flowable par défaut seulement.
- Testcontainers PostgreSQL / tests de charge verrous Flowable (TODO 28).
- OpenAPI formel ; métriques Micrometer âge outbox (TODO 29) au-delà de logs
  structurés minimaux.
- Chaînes sécu M2M / webhooks (follow-ups auth).

## Alternatives considérées

| Approche | Trade-off | Verdict |
|---|---|---|
| **A — Flowable réel + outbox + `DossierWorkflowLink`** | Plus large, débloque connecteurs et historique | **Retenue** (décisions 7/16 + DEFERRED) |
| B — Liens + API manuelle seulement, stubs Flowable | Rapide mais ne prouve pas le moteur | Écartée |
| C — Démarrage synchrone sans outbox | Réouvre perte au crash (décision 7) | Écartée |
| D — Forcer l’API manuelle via outbox aussi | UX plus lente / complexité sans gain V1 | Écartée |
| **D-START — Hybride (opt. 3)** : sync humain + outbox async + réconciliation RESERVED | UX + durabilité homogène pour non-humain | **Retenue** (revue phase 2) |

## User Stories & Use Cases
**How will users interact with the solution?**

- En tant que **utilisateur EDIT** (ou bypass), je démarre un workflow BPMN
  sur un dossier existant ; un `DossierWorkflowLink` est réservé puis confirmé
  avec `processInstanceId`.
- En tant que **utilisateur VIEW**, je consulte l’historique des exécutions
  d’un dossier (`sequence`, statuts métier, dates).
- En tant que **utilisateur EDIT** (ou bypass), je complète une tâche humaine
  Flowable avec des variables.
- En tant que **TENANT_ADMIN / WORKFLOW_ADMIN**, je crée / mets à jour les
  statuts métier d’un `workflowKey` (`WorkflowTypeConfig`).
- En tant que **utilisateur authentifié**, je lis les configs de workflow
  (pour UI / clients).
- En tant que **relais outbox**, je consomme les `PENDING` (écrits par seed /
  futurs connecteurs), démarre Flowable de façon idempotente, marque
  `PROCESSED` ou `DEAD`.
- En tant que **ops / CI**, je démarre l’app avec Flowable actif sur le schéma
  tenant, smoke process OK.

### Scénarios clés

1. Admin crée `WorkflowTypeConfig` pour `smokeProcess` (statut initial +
   terminal) → 201.
2. User EDIT : `POST /api/dossiers/{id}/workflows` avec
   `processDefinitionKey=smokeProcess` → 200, lien avec `processInstanceId`,
   `currentWorkflowLinkId` posé.
3. User VIEW : `GET …/workflows` → historique `sequence` croissante.
4. Second `POST` pendant lien actif → 409 ProblemDetail.
5. Complete task smoke → process avance ; delegate peut poser statut métier
   terminal → `endedAt` renseigné.
6. Seed outbox `PENDING` → relais démarre instance + `PROCESSED` ; rejeu →
   idempotent.
7. Collision via outbox (autre workflow actif) → `DEAD`.
8. `processDefinitionKey` sans config / process inconnu → 404 (API) ou
   retry/DEAD (relais).
9. Boot IT H2 : tables métier + `ACT_*` ; suites dossiers/auth vertes.

### Edge cases & glossaire lien

États techniques du lien (**pas** un enum séparé — dérivés des colonnes) :

| État parlé | Signification |
|---|---|
| **RESERVED** | `ended_at IS NULL` et `process_instance_id IS NULL` |
| **ACTIVE** | `ended_at IS NULL` et `process_instance_id IS NOT NULL` |
| **TERMINATED** | `ended_at IS NOT NULL` (outcome terminal) |

- Second workflow actif → `WorkflowAlreadyRunningException` / 409 ; via outbox
  → `DEAD`.
- Échec `start` synchrone après reserve → compensation immédiate du lien
  (**D-FAIL**) ; pas de 200 avec lien RESERVED.
- Rejeu outbox : reprendre **RESERVED** même dossier+process ; si **ACTIVE**
  avec `processInstanceId` → `markProcessed` sans second start.
- Statut métier inconnu pour le `workflowKey` → 400.
- Échecs répétés relais → `DEAD` après `app.workflow.outbox.max-attempts`.
- `AccessContext.System` pour relais / delegates (bypass ACL) ; `Service`
  reste hors endpoints humains.

## Success Criteria
**How will we know when we're done?**

- [x] Exclusions Flowable retirées autant que nécessaire ; app démarre ; tables
      `ACT_*` + `outbox_event` + `dossier_workflow_link` +
      `workflow_type_config` présentes en IT H2 mode PostgreSQL.
- [x] Surface API (tableau) opérationnelle sous session BFF + CSRF ;
      collision → 409 ; complete task exige `EDIT`.
- [x] Relais : happy path, retry, dead-letter, collision, idempotence rejeu
      couverts par tests (couverture partielle branches — follow-up tests OK).
- [x] Stubs in-memory workflow hors câblage production (classes legacy retirées).
- [x] Delegate `UpdateWorkflowLinkStatusTask` validé (smoke ou unit/IT).
- [x] ArchUnit hexagonale + Jacoco ≥ 80 % ; suites dossiers / auth vertes.
- [x] Docs design / testing / planning alignés sur cette revue ; non-goals
      connecteurs / UI Flowable / visualisation explicites.

**Performance benchmarks :** N/A pour la tranche — smoke process + CRUD
config ; pas de cible latence formelle. Tuning async / multi-répliques hors
V1 (décision n° 11 documentée).

## Constraints & Assumptions
**What limitations do we need to work within?**

### Technical constraints

- Java 25 · Spring Boot 4.1 · Flowable 8.0.x · Liquibase · BFF déjà livré.
- Instance **mono-tenant** (décision n° 13) : `TenantProperties.id()` partout ;
  pas de `TenantContext` request-scoped pour le relais / delegates.
- Domaine pur sans JPA (décision n° 17) ; convention `port/in` + `usecase/*Impl`.
- Outbox option B (décision n° 16) : producteur connecteurs **hors scope**.
- IT : **H2 MODE=PostgreSQL** ; PG réel hors CI.
- Jackson 3 défaut Flowable 8 — repli `flowable.variable-json-mapper=jackson2`
  seulement si blocage prouvé.
- Ordre Liquibase / Flowable sous Boot 4 à valider au premier démarrage IT
  (vigilance décision n° 27).

### Business / product

- Rôles de référence inchangés — TODO 19 non rouvert ; A2 pour admin config.
- ACL au **type**, pas à l’instance process (décision n° 20).
- Un seul workflow non terminé par dossier (index partiel).

### Time / rollout

- Branche `feature-workflow-engine` / PR ; pas de feature flag.
- Rollout = merge + déploiement instance tenant (ops hors docs).

### Assumptions (acceptées)

- A1 — Un seul process BPMN de smoke suffit pour l’acceptation IT.
- A2 — `WORKFLOW_ADMIN` et `TENANT_ADMIN` administrent `WorkflowTypeConfig`.
- A3 / **D-START** — Hybride option 3 : manuel HTTP **synchrone** ; outbox =
  seul chemin pour triggers non-humains (+ seed/tests) ; **sans** API enqueue
  publique ; réconciliation des RESERVED orphelins **obligatoire**.
- A4 / **D-AUTHZ** — option 3 : start manuel = `EDIT` type (bypass admins) en
  V1 ; raffinement permission `START_WORKFLOW` = TODO 19, hors tranche.
  History = `VIEW` ; complete task = `EDIT` (même base ACL type).
- A5 / **D-KEY** — option 2 : `processDefinitionKey` **requis** ; doit exister
  en `WorkflowTypeConfig` ; **pas** de contrainte vs `DossierType.workflowKey`
  (hint UI seulement).
- A6 — « RESERVED / ACTIVE / TERMINATED » = glossaire dérivé des colonnes,
  pas un champ `state` supplémentaire.
- A7 / **D-STATUS** — option 3 : éligibilité start = `Dossier.status` ∈
  `WorkflowTypeConfig.allowedDossierStatuses` ; liste obligatoire non vide à
  la création de config ; défaut smoke recommandé
  `{OUVERT, EN_COURS, SUSPENDU}` (figé en design).
- A8 / **D-ORPHAN** — option 2 : réconciliation expire+reprise ; défaut timeout
  5 min ; corrélation instance / annulation ORPHAN_TIMEOUT.
- A9 / **D-IDEM** — option 2 : `Idempotency-Key` sur `POST …/workflows` ;
  persistance de la clé liée au lien créé ; TTL aligné ou ≥ reserve-timeout
  (détail design).
- A10 / **D-VARS** — option 2 : variables libres ; clés réservées
  `dossierId` / `dossierWorkflowLinkId` / `tenantId` interdites au client ;
  injection plateforme.
- A11 / **D-DOSSIER-STATUS** — option 2 : `onStartDossierStatus` optionnel sur
  config ; start ne mute le dossier que si défini + transition légale.
- A12 / **D-FAIL** — option 2 : compensation immédiate du RESERVED si start
  sync échoue ; D-ORPHAN = filet.

## Questions & Open Items
**What do we still need to clarify?**

| # | Item | Disposition |
|---|---|---|
| Q1 | Embarquer Flowable UI en local ? | **Différé** — non-goal |
| Q2 | Méthode de start (sync / outbox / hybride) | **Clos** — D-START option 3 |
| Q3 | Défauts `max-attempts` / rétention purge | **Clos pour V1** — valeurs dans design (`app.workflow.outbox.*`) ; ops peut surcharger plus tard |
| Q4 | Testcontainers / charge verrous | **Différé** TODO 28 |
| Q5 | Métriques Micrometer âge outbox | **Différé** TODO 29 |
| Q6 | Lier `processDefinitionKey` à `DossierType.workflowKey` ? | **Clos** — D-KEY option 2 |
| Q7 | Chaînage N→N+1 / liste tâches | **Différé** — non-goals |
| Q8 | Qui peut POST start ? | **Clos** — D-AUTHZ option 3 (EDIT V1) |
| Q9 | Statuts dossier autorisant le start ? | **Clos** — D-STATUS option 3 (par config) |
| Q10 | RESERVED orphelins | **Clos** — D-ORPHAN option 2 (reprise, T=5 min) |
| Q11 | Idempotence POST start | **Clos** — D-IDEM option 2 (`Idempotency-Key`) |
| Q12 | Variables au start | **Clos** — D-VARS option 2 (clés réservées) |
| Q13 | Start change-t-il Dossier.status ? | **Clos** — D-DOSSIER-STATUS option 2 |
| Q14 | Échec start sync après reserve | **Clos** — D-FAIL option 2 |

### Décisions start actées (phase 2 interactive)

| Id | Sujet | Choix |
|---|---|---|
| D-START | Chemins de démarrage | Hybride : sync humain + outbox non-humain |
| D-KEY | Clé process | Libre si `WorkflowTypeConfig` existe |
| D-AUTHZ | Qui démarre | `EDIT` V1 (+ TODO 19) |
| D-STATUS | Statuts dossier | `allowedDossierStatuses[]` sur config |
| D-ORPHAN | RESERVED orphelins | Reprise puis timeout 5 min |
| D-IDEM | Retry client | `Idempotency-Key` |
| D-VARS | Variables | Libres ; clés réservées interdites |
| D-DOSSIER-STATUS | Statut dossier au start | `onStartDossierStatus` optionnel |
| D-FAIL | Échec start sync | Compensation immédiate + filet orphan |

**Phase 2 close** (confirmée 2026-08-02) — statut `reviewed`.

**Phase 3 (design)** : persistance idempotency-key, corrélation orphan,
frontières TX start, défauts outbox, BPMN smoke, ordre Liquibase/Flowable,
mapping stubs→adaptateurs.

## Sources

- `analysis doc/05-workflow-engine.md`, `04-dossiers.md` (lien / AttachWorkflow)
- Décisions TODO n° 6, 7, 11, 13, 16, 17, 20, 27
- `DEFERRED-FEATURES.md` §2 ordre 1 `workflow-engine`
- Code existant : `domain.workflow.*`, stubs
  `infrastructure.persistence.workflow` / `infrastructure.flowable`
- Mémoire projet : périmètre tranche workflow-engine (phase 1)
