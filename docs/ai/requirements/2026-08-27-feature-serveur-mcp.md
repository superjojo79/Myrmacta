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

Phase 1 **draft** (2026-08-27). Agent cloud : pas d'`AskUserQuestion` ;
les D-* ci-dessous sont des **recommandations de cadrage** (analyse
[15-serveur-mcp.md](../../../analysis%20doc/15-serveur-mcp.md) + code
vérifié) à valider en Phase 2.

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

**D-APPROACH = A.**

## Stakeholder decisions

### Actées en cadrage (PR #78) — conservées

Voir tableau ci-dessus. Non renégociées en Phase 1.

### Recommandations Phase 1 (à confirmer en Phase 2)

| Id | Décision | Pourquoi | Source |
|---|---|---|---|
| **D-STATS = A** | Tool lecture **`query_sla_summary`** dans **cette** V1, qui appelle `GetSlaSummaryUseCase` (`groupBy=dossierTypeCode\|status`, période `from`/`to`). Pas le snapshot maquette, pas de CSV. | Le séquencement `reporting-sla` → `serveur-mcp` n'avait d'autre objet que de débloquer le point stats. Le summary est le contrat BFF déjà prévu pour le MCP (design `reporting-sla`). | Analyse § dépendance + design reporting |
| **D-TRANSPORT = A** | Streamable HTTP (WebMVC, pas WebFlux). **Pas** stdio en V1. | Le BFF cookie ne s'applique pas au M2M ; l'app est servlet. | Analyse Q1, reco HTTP |
| **D-ENDPOINT = A** | Chemin **`/api/m2m/mcp`** (sous le `securityMatcher` M2M existant). | Réutilise JWT `API_SERVICE` + `M2mTenantValidationFilter` (`tenant_id` = `app.tenant.id`). Évite une 4ᵉ chaîne Security. | `SecurityConfig` Order 1 |
| **D-CREDENTIAL = A** | JWT M2M existant (`issuer-uri` ou HMAC local `app.security.m2m.jwt-secret`). **Pas** de clé API applicative nouvelle. | Décision n° 14 déjà livrée ; une API key dupliquerait le secret HMAC. | Analyse Q2, `M2mSecurityProperties` |
| **D-ACTOR = A** | Tools construisent `AccessContext.User(jwt.sub \| client_id, {API_SERVICE})`. **Interdit** `AccessContext.system(...)` et `AccessContext.Service` pour ces tools. | `DossierAccessPolicy` / `SlaReportAccess` rejettent `Service` ; `system` est réservé Flowable. | Analyse authz + code |
| **D-POLICY-AS = A** | `API_SERVICE` = lecture **tenant-wide** (comme `WORKFLOW_ADMIN`) dans `DossierAccessPolicy` (VIEW) et `SlaReportAccess`. Écriture config : `Create`/`UpdateDossierTypeUseCase` acceptent `TENANT_ADMIN` **ou** `API_SERVICE` (nouvelle méthode de garde, **sans** élargir rétention / purge / ACL). REST `@PreAuthorize` **inchangé**. | Sinon `get_dossier` / `query_sla_summary` 403 (ACL vide, D-AUTHZ reporting = C refuse le service). | Écart code vs cadrage |
| **D-SCHEMA-COMPAT = A** | `define_dossier_type` a le **même** comportement que POST/PATCH admin : pas de garde de compatibilité ascendante supplémentaire. | `UpdateDossierTypeUseCaseImpl` écrase déjà le schéma. Inventer un blocage MCP-only divergerait de l'UI. | Analyse Q3 + code |
| **D-DEPLOY-ORDER = A** | `deploy_workflow` : upsert `WorkflowTypeConfig` (`SaveWorkflowTypeConfigUseCase`) **puis** `DeployBpmnProcessUseCase`. Création de config autorisée si la clé n'existe pas. Redéploiement = nouvelle version Flowable (instances en cours inchangées). `statusDefinitions` = singleton non versionné (comme le PATCH REST actuel). | Comble l'écart « config obligatoire avant deploy ». | Analyse Q4 + `DeployBpmnProcessUseCaseImpl` |
| **D-AUDIT = B** | Pas de table journal MCP. `createdBy`/`updatedBy` = `sub` du JWT ; log ECS à l'adaptateur (`tool`, `actor`, succès/échec). | Suffisant pour un usage interne V1 ; une table dédiée est un BC à part. | Analyse Q5 |
| **D-PREVIEW = A** | Paramètre optionnel `dryRun` (défaut `false`) sur les 2 tools d'écriture : validation + payload prévu, **sans** persister ni déployer. | Rayon d'action d'un agent IA (analyse risques). | Analyse risques |
| **D-SEARCH = A** | `search_dossiers` reprend `SearchDossiersQuery` tel quel : `status`, `dossierTypeCode`, `referencePrefix`, `page`/`size`. **Pas** de filtre `createdAt`/`closedAt` nouveau (les dates restent sur `query_sla_summary`). | La recherche livrée n'a pas de plage de dates ; ne pas élargir `pagination-dossiers` dans cette feature. | Analyse Q8 + `SearchDossiersQuery` |
| **D-PAGE = A** | Bornes `PageQuery` : défaut 20, max 100. | Contrat pagination déjà acté. | Analyse Q8 |
| **D-SDK = A** | Starter **Spring AI 2.x** `spring-ai-starter-mcp-server-webmvc` (Streamable HTTP). Si le BOM tire un client LLM inutile / casse Jacoco, **repli** SDK MCP officiel câblé à la main (Phase 3 tranche). Tools = classes infra qui appellent `port.in` — **pas** de `@McpTool` dans `domain`. | Boot 4.1 / Java 25 aligné Spring AI 2 ; hexagone : SDK = infra. | Docs Spring AI 2.0 |
| **D-SPA = A** | **Zéro** changement SPA / contrat REST consommé par le SPA. Playwright = régression uniquement. | Canal M2M distinct. | Conventions FE |
| **D-GET-SHAPE = A** | `get_dossier` agrège `GetDossierUseCase` + `ListDossierElementsUseCase` + `GetDossierWorkflowHistoryUseCase` + `ListDossierRelationsUseCase`. Champs sensibles déjà rédigés par `DossierFieldRedactor`. L'adaptateur **ne lit pas** `Dossier.fields()` (allowlist ArchUnit). | Analyse point 3. | ArchUnit D-LOG-GUARD |

## Goals & Objectives
**What do we want to achieve?**

### Primary goals

1. Serveur MCP interne sur `/api/m2m/mcp`, authentifié `API_SERVICE`.
2. Tools d'écriture : `define_dossier_type`, `deploy_workflow` (`dryRun`
   optionnel), via use cases existants (+ upsert config avant deploy).
3. Tools de lecture : `get_dossier`, `get_dossier_type`,
   `list_dossier_types`, `list_workflow_types`, `search_dossiers`,
   `list_document_templates`, `query_sla_summary`.
4. Un serveur par instance tenant ; pas de `ThreadLocal` tenant.

### Secondary goals

- Logs ECS des appels tools (acteur = JWT `sub`).
- `dryRun` sur les écritures.
- README / getting-started : comment pointer un client MCP interne.

### Non-goals (explicitly out of scope)

- Consommation par un tenant ou un système tiers.
- stdio.
- `create_dossier`, `add_dossier_element`, `start_workflow`,
  `generate_document` (backlog analyse).
- ACL, purge, mise à jour directe d'un `DossierWorkflowLink`.
- Snapshot `/api/reports/sla`, CSV, settings SLA, inbox
  `slaRemainingHours` (déjà SPA ; MCP = summary seulement).
- Nouvelle table d'audit MCP.
- Édition visuelle BPMN via MCP.
- Changement des `@PreAuthorize` REST / du SPA.
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
- `define_dossier_type` sur un `code` inconnu → create ; connu → update.
- `deploy_workflow` : XML mal formé / clé extraite ≠ clé attendue →
  même rejet que l'upload admin (`BpmnParserPort`).
- `deploy_workflow` clé inconnue → **crée** la config puis déploie
  (**D-DEPLOY-ORDER**), contrairement à l'écran upload qui 404.
- `get_dossier` id inconnu → erreur métier (équivalent 404), pas un
  dump stack.
- `query_sla_summary` cohorte > 10 000 → même
  `SlaCohortTooLargeException` / code `SLA_COHORT_TOO_LARGE`.
- `search_dossiers` size > 100 → erreur de validation (PageQuery).
- Champ `sensitive` : valeur masquée dans `get_dossier` ; l'adaptateur
  ne contourne pas le redactor.

## Success Criteria
**How will we know when we're done?**

1. `tools/list` MCP annonce exactement les tools V1 ci-dessus
   (2 écriture + 7 lecture).
2. Chaque tool d'écriture a un test `dryRun` (aucun `save` / `deploy`)
   et un test d'application réelle.
3. `define_dossier_type` et `deploy_workflow` passent par les use
   cases domaine (ArchUnit : `domain` n'importe pas Spring AI / MCP).
4. JWT M2M : happy path + rejet mauvais tenant + rejet sans rôle.
5. `get_dossier` respecte `DossierAccessPolicy` + redaction ; test
   mutant : un champ `sensitive` n'apparaît pas en clair.
6. `query_sla_summary` : `API_SERVICE` non 403 ; `groupBy` invalide
   rejeté ; cap 10k conservé.
7. `./mvnw verify` vert (Jacoco BUNDLE tenus) ; frontend
   `npm run test` + `npm run test:e2e` **régression** (0 spec MCP,
   0 mock SPA cassé).
8. README : URL `/api/m2m/mcp`, rôle, exemple d'en-tête `Authorization`.

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
- Cloud agent : D-* = recommandations, Phase 2 les confirme.

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
| Q1 Transport | **D-TRANSPORT / D-ENDPOINT** — Phase 2 |
| Q2 Credential | **D-CREDENTIAL** — Phase 2 |
| Q3 Compatibilité schéma | **D-SCHEMA-COMPAT** — Phase 2 |
| Q4 Versionnement Flowable + config | **D-DEPLOY-ORDER** — Phase 2 |
| Q5 Journal MCP dédié | **D-AUDIT = B** — Phase 2 |
| Q6 Backlog écritures métier | Hors V1 (inchangé) |
| Q7 Stats reporting | **D-STATS = A** (prérequis livré) — Phase 2 |
| Q8 Pagination search | **D-SEARCH / D-PAGE** — Phase 2 |
| Pin version Spring AI vs SDK nu | **D-SDK** — Phase 3 (preuve BOM) |
| `includeContent` templates | défaut `false` ; pas d'ouverture V1 |

Aucun item bloquant pour rédiger le design de travail ; Phase 2 doit
entériner les D-* avant implémentation.

## Next phase

`dev-requirements` Phase 2 (review) puis `dev-design` Phase 3.
Le planning (Phase 4) n'est **pas** commencé.
