---
phase: requirements
title: Requirements & Problem Understanding
description: Clarify the problem space, gather requirements, and define success criteria
status: draft
created: 2026-08-27
updated: 2026-08-27
feature: serveur-mcp
phase1: 2026-08-27
todo_ref: FILE-ATTENTE.md · analysis doc/15-serveur-mcp.md · analysis doc/16-reporting-sla.md · PR #80
---

# Requirements & Problem Understanding — `serveur-mcp`

## Phase 1 : New Requirement

Phase 1 **en clarification** (2026-08-27). Une décision à la fois.

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

### File de décisions Phase 1 (reste)

Posées **une à la fois**.

| Id | Statut |
|---|---|
| **D-GET-SHAPE** | **posée** — agrégat `get_dossier` et champs `sensitive` |
| D-SPA | en attente (régression FE only vs écran MCP) |
| D-SDK / D-ACTOR | plutôt Phase 3 ; à poser ici s'ils changent le produit |

## Goals & Objectives
**What do we want to achieve?**

### Primary goals

1. Serveur MCP interne : Streamable HTTP **`/api/m2m/mcp`** avec JWT
   M2M `API_SERVICE` (**D-TRANSPORT/ENDPOINT/CREDENTIAL = A**).
2. Tools d'écriture cadrage : `define_dossier_type` (**D-WRITE-GUARD = A**),
   `deploy_workflow` avec **`dryRun` défaut false** (**D-PREVIEW = A**)
   et **upsert config si absente** (**D-DEPLOY-ORDER = A**).
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
- Édition visuelle BPMN via MCP.
- Snapshot `/api/reports/sla`, CSV, settings SLA, inbox remaining :
  **hors MCP** (**D-STATS = A** — summary seulement).
- stdio (**D-TRANSPORT = A**).
- Table journal MCP dédiée (**D-AUDIT = C** — colonnes auteur à la
  place). SPA : pending D-SPA.
- Codegen client, Chart.js, planification d'envoi.

## User Stories & Use Cases
**How will users interact with the solution?**

- En tant qu'**agent interne** (`API_SERVICE`), je veux
  `list_dossier_types` puis `define_dossier_type` pour créer ou
  mettre à jour un schéma (`code` = clé naturelle).
- En tant qu'agent, je veux `list_workflow_types` puis
  `deploy_workflow` (XML + `statusDefinitions`) pour publier une
  version Flowable et la config métier en un appel.
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
- `deploy_workflow` : XML mal formé / clé extraite ≠ clé attendue →
  même rejet que l'upload admin (`BpmnParserPort`).
- `deploy_workflow` clé inconnue → **crée** la config puis déploie
  (**D-DEPLOY-ORDER = A**). L'écran upload admin reste en 404.
- `get_dossier` id inconnu → erreur métier (équivalent 404), pas un
  dump stack.
- `query_sla_summary` cohorte > 10 000 → même
  `SlaCohortTooLargeException` / code `SLA_COHORT_TOO_LARGE`.
- `search_dossiers` size > 100 → erreur (`PageQuery`, **D-PAGE = A**).
  Pas de `from`/`to` (**D-SEARCH = A**).
- Champ `sensitive` : valeur masquée dans `get_dossier` ; l'adaptateur
  ne contourne pas le redactor.

## Success Criteria
**How will we know when we're done?**

1. `tools/list` MCP annonce exactement les tools V1 ci-dessus
   (2 écriture + 7 lecture).
2. Chaque tool d'écriture : test `dryRun=true` (aucun `save`/`deploy`)
   **et** test d'application réelle (**D-PREVIEW = A**).
3. `define_dossier_type` : `API_SERVICE` OK, `WORKFLOW_ADMIN` seul
   refusé (**D-WRITE-GUARD = A**) ; update incompatible **bloqué**
   (**D-SCHEMA-COMPAT = B**, REST PATCH non bloqué) ; purge/ACL/rétention
   toujours TA.
   `deploy_workflow` : XML d'abord, puis upsert config si absente, puis
   deploy (**D-DEPLOY-ORDER = A**). ArchUnit : pas de Spring AI / MCP
   dans `domain`.
4. JWT M2M sur `/api/m2m/mcp` (**D-CREDENTIAL = A**) : happy path +
   rejet mauvais `tenant_id` + sans `API_SERVICE`.
5. `get_dossier` respecte `DossierAccessPolicy` + redaction ; test
   mutant : un champ `sensitive` n'apparaît pas en clair.
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

- A-M2M-PATH : Spring AI (ou le SDK) permet de fixer l'endpoint à
  `/api/m2m/mcp`. Sinon : matcher Security élargi en Phase 3, même JWT.
- A-NO-LLM : on n'active aucun modèle de chat Spring AI.
- A-SYNC : serveur MCP **synchrone** (`type=SYNC`) ; tools courts
  (deploy BPMN = déjà le coût de l'upload admin).

## Questions & Open Items
**What do we still need to clarify?**

| Item | Statut |
|---|---|
| Q1 Transport | **D-TRANSPORT = A**, **D-ENDPOINT = A** (`/api/m2m/mcp`) — 2026-08-27 |
| Q2 Credential | **D-CREDENTIAL = A** (JWT M2M, pas de clé API) — 2026-08-27 |
| Q3 Compatibilité schéma | **D-SCHEMA-COMPAT = B** (blocage MCP si dossiers invalides) — 2026-08-27 |
| Q4 Versionnement Flowable + config | **D-DEPLOY-ORDER = A** — 2026-08-27 |
| Q5 Journal MCP dédié | **D-AUDIT = C** (`createdBy`/`updatedBy` sur types, pas de table MCP) — 2026-08-27 |
| Q6 Backlog écritures métier | Hors V1 (inchangé) |
| Q7 Stats reporting | **D-STATS = A** — actée 2026-08-27 |
| Q8 Pagination search | **D-SEARCH = A**, **D-PAGE = A** (20/100, pas de dates) — 2026-08-27 |
| Pin version Spring AI vs SDK nu | **D-SDK** — plutôt Phase 3 |
| `includeContent` templates | à poser si on ouvre le contenu HTML |

Phase 1 **bloquée** tant que les D-* matérielles ne sont pas votées
(une à une). Pas de passage Phase 2.

## Next phase

Rester en Phase 1 jusqu'à épuisement des questions. Puis revue
Phase 2, puis `dev-design` Phase 3.
