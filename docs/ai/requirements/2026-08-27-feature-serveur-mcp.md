---
phase: requirements
title: Requirements & Problem Understanding
description: Clarify the problem space, gather requirements, and define success criteria
status: reviewed
created: 2026-08-27
updated: 2026-08-27
feature: serveur-mcp
phase1: 2026-08-27
phase1_clarification: close
phase2: 2026-08-27
todo_ref: FILE-ATTENTE.md · analysis doc/15-serveur-mcp.md · analysis doc/16-reporting-sla.md · PR #80
---

# Requirements & Problem Understanding — `serveur-mcp`

## Phase 2 : Review Requirements

Phase 2 **reviewed** (2026-08-27). Template README OK (problem, goals,
stories, success, constraints, open items). Décisions Phase 1
conservées. **D-TYPE-FIELDS = A** voté. Les autres trous Phase 2 sont
des **A-*** (pas de nouveaux D-* stakeholder) : parité REST / cadrage
déjà acté, demandée explicitement pour enchaîner Phase 3.

Workspace : `cursor/serveur-mcp-e12d`, no-worktree. Task tracing
indisponible (`unknown command 'task'`). Lint feature OK ; branche
`feature-serveur-mcp` = MISS attendu.

Écarts du jet design Phase 1 (D-AUDIT B, D-SCHEMA-COMPAT A, ordre
deploy) **réécrits en Phase 3**.

## Phase 1 : New Requirement

Phase 1 **clarification close** (2026-08-27). Toutes les D-* produit
sont votées, reportées en Phase 3, ou nommées comme conséquence.

Workspace : branche `cursor/serveur-mcp-e12d`, no-worktree (VM cloud,
préfixe de branche imposé). Task tracing indisponible
(`npx ai-devkit@latest task list` → `unknown command 'task'`).
Lint feature : docs créés ce jour. Check branche `feature-serveur-mcp`
= MISS attendu.

### Décisions déjà actées hors de cette phase

Cadrage analyse 2026-08-26 (PR #78) :

| Id | Décision |
|---|---|
| Consommateur | Outillage **interne** (agents IA type Claude Code). Pas d'exposition tenants / tiers en V1. |
| Portée d'écriture | `define_dossier_type` + `deploy_workflow` uniquement. |
| Sens de « dossier » | **Type** (`DossierType` + `FieldDefinition`), pas d'instance `Dossier`. |
| Granularité BPMN | Un tool = XML + upsert `WorkflowTypeConfig`. |
| Intégration | Adaptateur `infrastructure.mcp` dans le monolithe. Un serveur MCP par instance = un tenant (décision n° 13). |
| Authentification | Rôle **`API_SERVICE`** existant, pas de nouveau rôle. |

Prérequis reporting/SLA : **livré** ([PR #80](https://github.com/superjojo79/FlowForge/pull/80),
2026-08-26). Les deux lots (comptages/durées + seuils) sont dans `main`.

## Problem Statement
**What problem are we solving?**

FlowForge n'expose aujourd'hui ses capacités qu'via le SPA (BFF/session)
et l'API REST consommée par ce SPA. Un agent IA (Claude Code ou autre
client MCP) ne peut pas configurer un type de dossier, déployer un
BPMN, ni consulter un dossier / un résumé SLA **sans** rejouer le
parcours navigateur.

- **Affectés :** l'équipe FlowForge (outillage interne), pas les
  utilisateurs métier du tenant en V1.
- **Situation actuelle :**
  - Pas de package `infrastructure.mcp`.
  - Chaîne M2M existante : JWT `API_SERVICE` sur `/api/m2m/**` seulement
    (`M2mConnectorExecuteController`).
  - Use cases cibles déjà livrés (`CreateDossierTypeUseCase`,
    `DeployBpmnProcessUseCase`, `GetDossierUseCase`,
    `SearchDossiersUseCase`, `GetSlaSummaryUseCase`, …) — **sans**
    façade MCP.
  - `DeployBpmnProcessUseCase` **exige** un `WorkflowTypeConfig`
    préexistant (rejet `UnknownWorkflowKeyException`) : le contrat MCP
    « un appel = XML + config » n'existe pas encore.
  - `CreateDossierTypeUseCase` / `UpdateDossierTypeUseCase` exigent
    `TENANT_ADMIN` (`TenantAdminGuard`) ; `DossierAccessPolicy` et
    `SlaReportAccess` **refusent** `AccessContext.Service` et ne
    connaissent pas `API_SERVICE` comme lecteur tenant-wide.
- **Réf. :** [15-serveur-mcp.md](../../../analysis%20doc/15-serveur-mcp.md),
  décision n° 13, chaîne M2M (décision n° 14), `reporting-sla` PR #80.

## Brainstorm — approaches

| | Approche | Trade-off |
|---|---|---|
| **A (retenue)** | Adaptateur `infrastructure.mcp` dans le monolithe, transport Streamable HTTP, tools → `port.in` existants. | Réutilise hexagone + tenant d'instance ; pas de second déploiement. |
| B | Sidecar MCP qui appelle l'API REST. | Double auth, duplication de DTOs, trop de surface HTTP. |
| C | Serveur stdio lancé à côté de l'agent. | Incompatible avec « une instance = un tenant » (l'agent n'a pas le process Boot). |

**D-APPROACH** : proposition A (alignée cadrage PR #78, pas re-votée ici).

## Stakeholder decisions

### Actées en cadrage (PR #78) — conservées

Voir tableau ci-dessus. Non renégociées en Phase 1.

### Actées en Phase 1

| Id | Décision | Vote |
|---|---|---|
| **D-STATS = A** | Tool lecture **`query_sla_summary`** dans **cette** V1 → `GetSlaSummaryUseCase` (`groupBy=dossierTypeCode\|status`, `from`/`to`). Pas le snapshot maquette, pas le CSV, pas d'agrégat ad hoc. | Stakeholder 2026-08-27 |
| **D-POLICY-AS = A** | Un `AccessContext.User` avec rôle `API_SERVICE` a une lecture **tenant-wide** (comme `WORKFLOW_ADMIN`) : `DossierAccessPolicy` VIEW + `SlaReportAccess` unrestricted. REST `@PreAuthorize` **inchangé**. Pas de `AccessContext.system` / `Service` depuis MCP. Champs `sensitive` : masqués (redactor) — **D-GET-SHAPE**. | Stakeholder 2026-08-27 |
| **D-WRITE-GUARD = A** | Create/Update `DossierType` : `TENANT_ADMIN` **ou** `API_SERVICE` (nouvelle méthode de garde, **sans** élargir `requireTenantAdmin` : purge, ACL, rétention, settings SLA restent TA). REST `@PreAuthorize` des types **inchangé** (TA only). | Stakeholder 2026-08-27 |
| **D-TRANSPORT = A** | Streamable HTTP WebMVC seulement. **Pas** de stdio en V1. | Stakeholder 2026-08-27 |
| **D-ENDPOINT = A** | Chemin **`/api/m2m/mcp`** sur le port HTTP de l'instance (matcher M2M existant). Si le starter MCP refuse ce path, Phase 3 élargit le matcher — **même JWT**, pas de 4ᵉ chaîne par défaut. | Stakeholder 2026-08-27 |
| **D-CREDENTIAL = A** | JWT M2M existant seulement (`Authorization: Bearer`). Prod = `issuer-uri` OIDC ; local/test = HMAC `app.security.m2m.jwt-secret`. **Pas** de clé API MCP. Même client que `/api/m2m/connectors/**`. | Stakeholder 2026-08-27 |
| **D-PREVIEW = A** | Paramètre optionnel **`dryRun`** (défaut **`false`**) sur `define_dossier_type` et `deploy_workflow`. `true` = valider + renvoyer le payload prévu, **sans** `save` ni `deploy`. | Stakeholder 2026-08-27 |
| **D-DEPLOY-ORDER = A** | `deploy_workflow` : valider/parser le XML **puis** upsert `WorkflowTypeConfig` (création si la clé n'existe pas) **puis** `DeployBpmnProcessUseCase`. Redéploiement = nouvelle version Flowable (instances en cours inchangées). `statusDefinitions` = singleton non versionné (comme le PATCH REST). L'UI admin upload reste en 404 si config absente. | Stakeholder 2026-08-27 |
| **D-SCHEMA-COMPAT = B** | `define_dossier_type` en **update** : **bloquer** si au moins un dossier existant du type échouerait `DossierFieldValidator` avec le nouveau schéma (compte dans l'erreur). Create (code nouveau) : pas de check. `dryRun=true` signale la même incompatibilité **sans** écrire. PATCH REST admin **inchangé** (pas de garde). | Stakeholder 2026-08-27 |
| **D-AUDIT = C** | Colonnes **`createdBy` / `updatedBy`** sur `DossierType` et `WorkflowTypeConfig` (JWT `sub` côté MCP ; principal OIDC côté REST admin — mêmes colonnes, tous les writers). **Pas** de table journal MCP. Lectures non auditées en base. `dryRun` ne stamp pas. Migration Liquibase. | Stakeholder 2026-08-27 |
| **D-SEARCH = A** | `search_dossiers` = `SearchDossiersQuery` tel quel : `status`, `dossierTypeCode`, `referencePrefix`, `page`/`size`. **Pas** de filtre dates (fenêtres = `query_sla_summary`). | Stakeholder 2026-08-27 |
| **D-PAGE = A** | Bornes existantes `PageQuery` : défaut **20**, max **100** (search, list types, templates, workflow types). | Conséquence **D-SEARCH = A** |
| **D-GET-SHAPE = A** | `get_dossier` : agrégat dossier **rédigé** + éléments + historique `DossierWorkflowLink` + relations. `id` **xor** `reference`. `API_SERVICE` ≠ TA → champs `sensitive` **masqués** (`DossierFieldRedactor`). L'adaptateur **n'appelle pas** `Dossier.fields()`. | Stakeholder 2026-08-27 |
| **D-SPA = A** | **Zéro** changement SPA / contrat REST consommé par le SPA. Getting-started + README (URL, rôle, `Authorization: Bearer`). Playwright = **régression** de la suite actuelle (0 spec MCP). | Stakeholder 2026-08-27 |
| **D-ACTOR = A** | Tools : `AccessContext.User(jwt.sub \| client_id, {API_SERVICE})`. Interdit `system(...)` et `Service` depuis MCP. | Conséquence **D-POLICY-AS = A** |

### Actées en Phase 2

| Id | Décision | Vote |
|---|---|---|
| **D-TYPE-FIELDS = A** | `define_dossier_type` écrit **seulement le schéma** : `code`, `label`, `workflowKey`, `active`, `allowUnknownFields`, `fieldDefinitions` (+ `dryRun`). **Pas** `retentionDays` / `retentionNever` / `slaTargetHours`. Create : défauts `DossierType.inherit` (`retentionDays=null`, `retentionNever=false`, `slaTargetHours=null`). Update : ces trois champs **conservés**. S'ils sont présents dans l'appel → **erreur** (pas d'ignore silencieux). Purge, ACL, PUT `TenantSlaSettings` hors MCP. REST PATCH types **inchangé** (TA peut toujours poser rétention / cible SLA). | Stakeholder 2026-08-27 |

Hypothèses nommées Phase 2 (conséquences, pas des votes) :

| Id | Hypothèse |
|---|---|
| **A-WF-REST-PARITY** | `deploy_workflow` écrit la **même** config que REST `SaveWorkflowTypeConfig` : `label`, `statusDefinitions`, `allowedDossierStatuses` (obligatoire), `onStartDossierStatus?`, `observerUserIds?`, plus XML UTF-8, `processDefinitionKey`, `dryRun`. Create et update = remplacement complet de la config (comme le body REST). Découle du cadrage « un tool = XML + upsert `WorkflowTypeConfig` » + **D-DEPLOY-ORDER** « comme le PATCH REST ». |
| **A-SCHEMA-FULL** | Schéma MCP **exigé en entier** à chaque create/update (`label`, `workflowKey`, `active`, `allowUnknownFields`, `fieldDefinitions`). Pas d'omit-means-keep REST PATCH sur ces champs. |
| **A-TPL-FORCE-FALSE** | `includeContent=true` **rejeté**. Le tool n'expose jamais le HTML. |
| **A-GET-COMPLETE** | `get_dossier` pagine en interne (size 100) éléments et historique jusqu'à épuisement — pas de tools `list_elements` / `list_history` V1. Relations : liste complète existante. |
| **A-LIST-PARITY** | `list_dossier_types` / `list_workflow_types` = mêmes pages que REST (actifs **et** inactifs). |

### Conséquences / report Phase 3 (plus de vote Phase 1)

| Id | Statut |
|---|---|
| **D-SDK** | **Phase 3** — pin Spring AI 2.x WebMVC vs SDK MCP officiel si BOM trop lourd. Pas de `ChatClient`. Tools = infra → `port.in`. |
| **A-TPL-META** | Renforcé **A-TPL-FORCE-FALSE**. |
| **A-WF-REST-PARITY** | Voir Phase 2. |

## Goals & Objectives
**What do we want to achieve?**

### Primary goals

1. Serveur MCP interne : Streamable HTTP **`/api/m2m/mcp`** avec JWT
   M2M `API_SERVICE` (**D-TRANSPORT/ENDPOINT/CREDENTIAL = A**).
2. Tools d'écriture cadrage : `define_dossier_type` (**D-WRITE-GUARD = A**,
   schéma seul **D-TYPE-FIELDS = A**), `deploy_workflow` avec
   **`dryRun` défaut false** (**D-PREVIEW = A**) et **upsert config
   si absente** (**D-DEPLOY-ORDER = A**).
3. Tools de lecture cadrage : `get_dossier`, `get_dossier_type`,
   `list_dossier_types`, `list_workflow_types`, `search_dossiers`,
   `list_document_templates`, **`query_sla_summary` (D-STATS = A)**.
4. Un serveur par instance tenant ; pas de `ThreadLocal` tenant.

### Secondary goals

- `createdBy` / `updatedBy` sur types dossier et workflow (**D-AUDIT = C**).
- `dryRun` défaut `false` sur les 2 tools d'écriture (**D-PREVIEW = A**).
- README / getting-started : comment pointer un client MCP interne.

### Non-goals (explicitly out of scope)

- Consommation par un tenant ou un système tiers (cadrage PR #78).
- `create_dossier`, `add_dossier_element`, `start_workflow`,
  `generate_document` (backlog analyse).
- ACL, purge, mise à jour directe d'un `DossierWorkflowLink`.
- Écriture MCP de `retentionDays` / `retentionNever` /
  `slaTargetHours` (**D-TYPE-FIELDS = A**) ; settings SLA tenant
  (PUT `TenantSlaSettings`) hors MCP.
- Édition visuelle BPMN via MCP.
- Snapshot `/api/reports/sla`, CSV, settings SLA, inbox remaining :
  **hors MCP** (**D-STATS = A** — summary seulement).
- stdio (**D-TRANSPORT = A**).
- Table journal MCP dédiée (**D-AUDIT = C**).
- Écran SPA MCP (**D-SPA = A**).
- Codegen client, Chart.js, planification d'envoi.

## User Stories & Use Cases
**How will users interact with the solution?**

- En tant qu'**agent interne** (`API_SERVICE`), je veux
  `list_dossier_types` puis `define_dossier_type` pour créer ou
  mettre à jour un **schéma** (`code` = clé naturelle) sans toucher
  rétention ni cible SLA (**D-TYPE-FIELDS = A**).
- En tant qu'agent, je veux `list_workflow_types` puis
  `deploy_workflow` (XML + config métier complète, **A-WF-REST-PARITY**)
  pour publier une version Flowable et upsert la config en un appel.
- En tant qu'agent, je veux `dryRun=true` pour valider un XML / un
  schéma **sans** écrire.
- En tant qu'agent, je veux `get_dossier` par `id` **ou** `reference`
  (éléments, historique, relations, champs rédigés).
- En tant qu'agent, je veux `search_dossiers` paginé (mêmes filtres
  que l'API liste).
- En tant qu'agent, je veux `query_sla_summary` avec la même fenêtre
  et le même `groupBy` que `GET /api/reports/sla/summary`.
- En tant qu'agent, je veux `list_document_templates` (métadonnées,
  **sans** corps HTML par défaut — `includeContent=false`).

### Edge cases

- JWT sans `API_SERVICE` / `tenant_id` ≠ instance → 401/403 (filtre M2M
  existant).
- `define_dossier_type` sur un `code` inconnu → create ; connu → update
  **sauf** incompatibilité (**D-SCHEMA-COMPAT = B**) : au moins un
  dossier du type échouerait la validation → erreur + compte, pas de
  save. `dryRun=true` : même diagnostic sans write. PATCH REST inchangé.
  Présence de `retentionDays` / `retentionNever` / `slaTargetHours`
  dans l'appel → erreur (**D-TYPE-FIELDS = A**), y compris en
  `dryRun`. Create n'écrit pas de cible SLA (null jusqu'à un PATCH
  REST TA).
- `deploy_workflow` : XML mal formé / clé extraite ≠ `processDefinitionKey`
  attendu → même famille d'erreur que l'upload admin / draft
  (`BpmnParserPort`, `InvalidBpmnException`).
- `deploy_workflow` : `allowedDossierStatuses` vide ou absent → rejet
  invariant existant (**A-WF-REST-PARITY**).
- `deploy_workflow` clé inconnue → **crée** la config puis déploie
  (**D-DEPLOY-ORDER = A**). L'écran upload admin reste en 404.
- `get_dossier` : les deux de `id`/`reference` ou aucun → erreur ;
  id/référence inconnus → erreur métier (pas un dump). Champ
  `sensitive` masqué (**D-GET-SHAPE = A**) ; l'adaptateur ne contourne
  pas le redactor.
- `query_sla_summary` cohorte > 10 000 → même
  `SlaCohortTooLargeException` / code `SLA_COHORT_TOO_LARGE`.
- `search_dossiers` size > 100 → erreur (`PageQuery`, **D-PAGE = A**).
  Pas de `from`/`to` (**D-SEARCH = A**).

## Success Criteria
**How will we know when we're done?**

1. `tools/list` MCP annonce exactement les tools V1 ci-dessus
   (2 écriture + 7 lecture).
2. Chaque tool d'écriture : test `dryRun=true` (aucun `save`/`deploy`)
   **et** test d'application réelle (**D-PREVIEW = A**).
3. `define_dossier_type` : `API_SERVICE` OK, `WORKFLOW_ADMIN` seul
   refusé (**D-WRITE-GUARD = A**) ; schéma seul (**D-TYPE-FIELDS = A**)
   — create sans rétention/SLA, update les conserve, champs interdits
   → erreur ; update incompatible **bloqué**
   (**D-SCHEMA-COMPAT = B**, REST PATCH non bloqué) ; purge/ACL/rétention
   toujours TA.
   `deploy_workflow` : XML d'abord, puis upsert config si absente, puis
   deploy (**D-DEPLOY-ORDER = A**). ArchUnit : pas de Spring AI / MCP
   dans `domain`.
4. JWT M2M sur `/api/m2m/mcp` (**D-CREDENTIAL = A**) : happy path +
   rejet mauvais `tenant_id` + sans `API_SERVICE`.
5. `get_dossier` agrège 4 use cases (**D-GET-SHAPE = A**) ; `id` xor
   `reference` ; mutant : champ `sensitive` absent en clair ;
   ArchUnit : tool MCP hors allowlist `Dossier.fields()`.
6. `query_sla_summary` : `API_SERVICE` non 403 (**D-POLICY-AS = A**) ;
   `groupBy` invalide rejeté ; cap 10k conservé. REST `/api/reports/**`
   reste sans `API_SERVICE` au `@PreAuthorize`.
7. `./mvnw verify` vert (Jacoco BUNDLE tenus) ; frontend
   `npm run test` + `npm run test:e2e` **régression** (0 spec MCP,
   0 mock SPA cassé).
8. README : URL `/api/m2m/mcp`, rôle, exemple `Authorization: Bearer`.
9. `DossierType` / `WorkflowTypeConfig` portent `createdBy`/`updatedBy`
   (**D-AUDIT = C**) ; MCP `dryRun` ne stamp pas ; REST admin types et
   workflow-configs stampent aussi le principal OIDC.

## Constraints & Assumptions
**What limitations do we need to work within?**

- Hexagone : `infrastructure` → `domain.port.in` seulement.
- Une instance = un tenant ; MCP hébergé dans la même JVM.
- Stack figée Java 25 / Boot 4.1 / Maven mono-module.
- Secrets : JWT M2M déjà fail-fast hors `local`/`test`.
- Validation BPMN : **réutiliser** `BpmnParserPort` (pas de parseur
  XML parallèle, pas d'entités externes).
- `WorkflowTypeConfig` : exactement un statut `initial` ; listes
  `allowedDossierStatuses` non vides (invariants existants).

### Assumptions

- A-TPL-META / **A-TPL-FORCE-FALSE** : métadonnées seulement ; `true` rejeté.
- A-NO-LLM : on n'active aucun modèle de chat Spring AI.
- A-SYNC : serveur MCP **synchrone** (`type=SYNC`) ; tools courts
  (deploy BPMN = déjà le coût de l'upload admin).
- A-M2M-PATH : si le starter refuse `/api/m2m/mcp`, Phase 3 élargit le
  matcher — même JWT (**D-ENDPOINT = A**).
- A-TYPE-INHERIT : create MCP = `DossierType.inherit` pour rétention /
  SLA (**D-TYPE-FIELDS = A**).
- **A-WF-REST-PARITY** / **A-SCHEMA-FULL** / **A-GET-COMPLETE** /
  **A-LIST-PARITY** : voir tableau Phase 2.

## Questions & Open Items
**What do we still need to clarify?**

| Item | Statut |
|---|---|
| Q1 Transport | **D-TRANSPORT = A**, **D-ENDPOINT = A** |
| Q2 Credential | **D-CREDENTIAL = A** |
| Q3 Compatibilité schéma | **D-SCHEMA-COMPAT = B** |
| Q4 Deploy + config | **D-DEPLOY-ORDER = A** |
| Q5 Audit | **D-AUDIT = C** |
| Q6 Backlog écritures métier | Hors V1 |
| Q7 Stats | **D-STATS = A** |
| Q8 Search | **D-SEARCH = A**, **D-PAGE = A** |
| D-SDK | **Phase 3** (choix technique) |
| `includeContent` templates | **A-TPL-FORCE-FALSE** |
| **D-TYPE-FIELDS** | **A** — schéma seul |
| Payload `deploy_workflow` | **A-WF-REST-PARITY** (hypothèse nommée) |

Phase 2 **reviewed**. Phase 3 design **reviewed**. Phase 4 **planned**.

## Next phase

`dev-implementation` Phase 5, TDD **T1.1** (`DossierAccessPolicy` +
`API_SERVICE`).
