---
phase: requirements
title: Requirements & Problem Understanding
description: Clarify the problem space, gather requirements, and define success criteria
status: reviewed
feature: mcp-ecritures-metier
lot: A3
---

# Requirements & Problem Understanding — `mcp-ecritures-metier`

> Origine : lot **A3** ([FILE-ATTENTE.md](../../../FILE-ATTENTE.md) Vague A, 2026-09-08) ;
> backlog déjà cadré dans [15-serveur-mcp.md](../../../analysis%20doc/15-serveur-mcp.md)
> § *Backlog — extensions futures envisageables*. Extension précédente du canal MCP :
> [extension-mcp-flux-metier](2026-09-06-feature-extension-mcp-flux-metier.md) (PR #111, 16 tools).
>
> Mode workspace : **no-worktree** (VM cloud isolée). Branche
> `cursor/mcp-ecritures-metier-6efe`. Traçage `ai-devkit task` **indisponible**
> (`error: unknown command 'task'`).
>
> Phases 1–3 **rejouées** le 2026-09-08 après constat : les `D-*` avaient été
> actées sans question (violation `dev-requirements` / `dev-design`). Cadrage
> FILE-ATTENTE inchangé (4 tools, un cycle, ports REST, pas de SPA /
> `SecurityConfig` / ACL / purge). Toute décision d'image Docker, de store
> partagé ou d'`context-path` est **hors lot** (A1).

## Problem Statement

Le serveur MCP interne (`POST /api/m2m/mcp`) permet aujourd'hui de **définir** un flux
métier (type de dossier, BPMN, connecteurs, questions de tâche, lecture dossiers/SLA)
mais **pas de l'opérer** : un agent ne peut ni créer un dossier, ni y ajouter une note
ou un lien, ni démarrer le workflow, ni produire un document. Ces quatre écritures
existent déjà en REST et étaient volontairement hors V1 MCP (« risque modéré »,
données métier plutôt que configuration).

Sans elles, l'agent MCP s'arrête à la configuration et un opérateur humain doit
reprendre le parcours métier dans le SPA. Le lot A3 débloque ce parcours **sans
nouvelle surface HTTP, sans SPA, sans toucher à `SecurityConfig`**.

**Constat de code (Phase 1, pas supposé) :**

- `CreateDossierUseCase` / `AddDossierElementUseCase` portent déjà l'ACL
  (`DossierAccessPolicy`) et la validation (`DossierFieldValidator`).
- Le démarrage manuel REST n'appelle **pas** `AttachWorkflowUseCase` seul : il passe
  par `ManualStartOrchestrator` (`DossierWorkflowController`) — réserve, start
  Flowable, confirm, idempotence.
- `GenerateDocumentUseCase.generate(...)` rend un PDF ; il **n'attache pas** au
  dossier. L'attache `PRODUCED_DOC` est le chemin BPMN
  (`DocumentGenerationTask` + `AttachGeneratedDocumentTask`).
- `POST /api/dossiers/{id}/elements` refuse `PRODUCED_DOC` / `ATTACHMENT` (upload
  dédié). Le canal MCP ne doit pas inventer un multipart.
- `DocumentGenerationRateLimiter` est **package-private** dans
  `infrastructure.web.document` — le partager imposerait d'écrire hors de la
  surface exclusive A3 (`infrastructure/mcp/**`). Non retenu.

## Goals & Objectives

**Objectifs principaux**

- Ajouter **4 tools MCP d'écriture métier** dans **un seul cycle** (pas 4 PRs) :
  `create_dossier`, `add_dossier_element`, `start_workflow`, `generate_document`.
- Réutiliser les use cases / orchestrateurs REST existants — **zéro** nouvelle
  règle métier dans le domaine, **zéro** modification de
  `CreateDossierUseCase` / `CreateDossierBatchUseCase` (couture partagée avec le
  futur import batch, interdite en parallèle de A3).
- Même acteur que les 16 tools déjà livrés : JWT M2M → `AccessContext.User` avec
  `API_SERVICE` via `McpAccessContextFactory` — jamais `AccessContext.system`.

**Objectifs secondaires**

- `dryRun=true` sur les 4 tools (contrat déjà établi par `define_dossier_type`,
  `deploy_workflow`, `define_connector_instance`).
- `generate_document` est **centré dossier** : rend, stocke, attache un
  `PRODUCED_DOC` (pas de PDF Base64 dans la réponse MCP).
- Compteur tools : 16 → **20**. `tools/list` et `GETTING-STARTED.md` §12 à jour.

**Non-goals**

- ACL `grant`/`revoke`, purge, mise à jour du statut d'un `DossierWorkflowLink`
  (exclusions explicites de [15-serveur-mcp.md](../../../analysis%20doc/15-serveur-mcp.md)).
- Upload binaire (`ATTACHMENT`) via MCP.
- `frontend/**`, admin connecteurs, Compose, `SecurityConfig`, golden OpenAPI
  (le contrat MCP n'y figure pas).
- Kafka / import batch / ERP (Vague B).
- Nouveau credential, nouveau matcher Spring Security, nouveau transport MCP.

## User Stories & Use Cases

1. En tant qu'agent MCP interne (`API_SERVICE`), je crée un dossier (`dossierTypeCode`,
   `label`, `fields`) pour qu'il apparaisse ensuite via `get_dossier` / `search_dossiers`.
2. J'ajoute une `NOTE`, un `LINK` ou une `INFORMATION` à un dossier existant, sans
   upload de fichier.
3. Je démarre le workflow d'un dossier (équivalent `POST /api/dossiers/{id}/workflows`)
   avec une `idempotencyKey`, en respectant un seul workflow actif.
4. Je génère un PDF pour un dossier à partir d'un template associé au type, et
   l'élément `PRODUCED_DOC` est visible sur le dossier.
5. En `dryRun=true`, je valide les mêmes gardes sans persister / sans démarrer
   Flowable / sans écrire le store.

## Success Criteria

- `tools/list` annonce exactement les 16 tools existants **plus** les 4 nouveaux.
- Chaque tool délègue au port/orchestrateur REST correspondant ; les gardes ACL,
  validation champs, concurrence workflow et types d'éléments sont celles du domaine
  déjà livré (pas de copie).
- `create_dossier` : type inactif → même exception domaine ; champs invalides →
  `DossierFieldValidationException` ; `createdBy` = sujet JWT.
- `add_dossier_element` : `PRODUCED_DOC` / `ATTACHMENT` rejetés comme le REST ;
  `NOTE` exige `metadata.text` ; `LINK` exige une URI absolue.
- `start_workflow` : `Idempotency-Key` obligatoire (paramètre tool) ; replay
  renvoyé sans second start ; clés réservées (`dossierId`, `tenantId`, …) rejetées.
- `generate_document` : template absent de `DossierType.documentTemplateKeys` →
  refus ; réponse = vue élément (`DossierElementView`, **sans** `storageRef`) ;
  `dryRun` ne touche ni store ni éléments.
- `./mvnw verify` vert (Jacoco LINE ≥ 85 % / BRANCH ≥ 75 % tenus). Feature
  backend-only : Vitest en régression seulement, Playwright non exigé (zéro fichier
  `frontend/**`, zéro contrat SPA).
- `GETTING-STARTED.md` §12 : 16 → 20 tools.

## Constraints & Assumptions

| ID | Contrainte / hypothèse |
|---|---|
| C-SURFACE | Écriture exclusive : `src/main/java/com/flowforge/infrastructure/mcp/**` + tests MCP + docs. |
| C-ARCHUNIT | Ne pas appeler `Dossier.fields()` depuis une nouvelle classe — passer par `DossierMcpView` (allowlist). |
| C-HEXAGONE | `infrastructure.mcp` peut dépendre de `application.workflow.ManualStartOrchestrator` (comme le contrôleur REST). |
| A-ACTOR | Réutilise `McpAccessContextFactory` tel quel (risque M2M « tout ou rien » déjà assumé, L1 / TODO §56). |
| A-ERRORS | Le SDK MCP convertit une exception de tool en `isError=true` (déjà prouvé, `McpEndpointApiTest`). |
| A-TENANT | Tenant = `TenantProperties.id` de l'instance, jamais un argument tool. |

## Décisions actées (Phase 1–2)

| ID | Question | Décision | Pourquoi |
|---|---|---|---|
| **D-WRITE-ONE-CYCLE** | 4 tools / 4 PRs ? | **Un cycle, une PR** | FILE-ATTENTE A3 : même enregistrement tools / tests MCP. |
| **D-REUSE-PORTS** | Nouvelle logique domaine ? | **Non** — adaptateurs minces | Import batch (Vague B) partage `CreateDossierUseCase` ; ne pas le modifier. |
| **D-DRY-RUN** | dryRun sur les écritures métier ? | **C — Oui, les 4** (confirmé utilisateur 2026-09-08) | Contrat MCP d'écriture déjà livré. |
| **D-ELEMENT-TYPES** | Quels types d'éléments ? | **NOTE, LINK, INFORMATION** uniquement | Analyse 15 + parité `DossierController.addElement`. |
| **D-START-ORCH** | `AttachWorkflowUseCase` seul ? | **`ManualStartOrchestrator`** | C'est le chemin REST réel (idempotence + confirm + abort). |
| **D-GENERATE-ATTACH** | Renvoyer le PDF ? | **C — Store + attache `PRODUCED_DOC`** (confirmé utilisateur 2026-09-08) | MCP mal adapté au binaire ; `DossierElementView` sans `storageRef` (DD-ELEMENTS-DTO). |
| **D-GENERATE-TEMPLATE** | N'importe quel template du tenant ? | **C — Uniquement si le type du dossier le liste** (confirmé utilisateur 2026-09-08) | Plus strict que le bypass TA/WA REST ; l'agent agit *sur ce dossier*. |
| **D-GENERATE-VARS** | Variables du template ? | **C — Champs du dossier** (`DossierMcpView.fields`) **+ overlay optionnel** (confirmé utilisateur 2026-09-08) | Équivalent d'un POST `/generate` alimenté par le dossier. Overlay soumis à `WorkflowVariables.assertNoReservedKeys`. |
| **D-RATE-LIMIT** | Partager le rate limiter REST ? | **A — Non** (confirmé utilisateur 2026-09-08) | Classe package-private hors surface A3 ; client M2M de confiance (L1). |
| **D-NO-OPENAPI** | Régénérer le golden ? | **A — Non** (confirmé utilisateur 2026-09-08) | MCP n'est pas un contrat OpenAPI SPA. |

## Questions & Open Items

Phases 1–3 rejeu **clos** (2026-09-08). Confirmé utilisateur, aligné sur la PR #118
(aucun écart code) : D-GENERATE-ATTACH = C, D-GENERATE-TEMPLATE = C,
D-GENERATE-VARS = C, D-DRY-RUN = C, D-RATE-LIMIT = A, D-NO-OPENAPI = A,
D-START-ORCH = C, D-DRY-RUN-START = B, D-FIELDS-READ = B. Cadrage FILE-ATTENTE
non relancé : D-WRITE-ONE-CYCLE, D-REUSE-PORTS, D-ELEMENT-TYPES.

Aucun point matériel ouvert.

## Amendement post-implémentation (2026-09-09, revue adversariale)

**D-RATE-LIMIT reversée** : la décision « A — Non » (confirmée 2026-09-08) laissait
`generate_document` sans aucune limite, alors que l'équivalent REST est plafonné à 30/min —
identifié en revue adversariale comme un risque de déni de service (A3-M1) si le JWT M2M venait à
fuiter. `DocumentGenerationRateLimiter` a été rendue publique et réutilisée depuis le tool MCP
(même compteur que le REST). Le motif d'origine (classe package-private, hors surface exclusive
A3) est toujours vrai mais n'a plus été jugé suffisant face au risque.

**A-ACTOR / risque M2M « tout ou rien » (TODO §56)** : toujours assumé par défaut, mais
`McpAccessContextFactory` accepte maintenant une allowlist optionnelle
(`M2mSecurityProperties#trustedClientIds`, vide par défaut = comportement inchangé) pour fermer le
risque avant l'introduction d'un second client M2M (A3-M2). Voir TODO.md §56 et §60.
