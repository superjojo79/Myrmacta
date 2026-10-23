---
phase: requirements
title: Requirements & Problem Understanding
description: Clarify the problem space, gather requirements, and define success criteria
status: reviewed
created: 2026-08-21
updated: 2026-08-21
feature: contrat-api-client
phase1: 2026-08-21
phase2: 2026-08-21
stakeholder_decisions: 2026-08-21
todo_ref: TODO.md §24 (reste) · FILE-ATTENTE.md (priorité 1 après `durcissement-donnees-sensibles`)
---

# Requirements & Problem Understanding — `contrat-api-client`

> Reste TODO §24 après `pagination-dossiers`, `patch-dossier-fields`,
> `elements-dossier` et `openapi-pagination-admin` (PR #58, qui a
> explicitement mis la pagination des listes imbriquées et la spec des
> endpoints `@Hidden` en non-goal — **D-NESTED = A** — pour un lot séparé).
> Requirements **reviewed** (Phase 2), 4 décisions actées, aucun point
> ouvert bloquant.

## Problem Statement
**What problem are we solving?**

Le golden `openapi/openapi.yaml` (livré par `openapi-pagination-admin`,
PR #58) documente déjà les 6 listes **admin** avec pagination, mais deux
trous subsistent dans le contrat HTTP :

1. **6 listes non-admin renvoient encore des tableaux plats** (`List<T>`
   JSON sans pagination ni tri contrôlé), alors que le même envelope
   `PageResponse<T>` existe déjà et est utilisé ailleurs dans le code.
   Un client (SPA ou futur intégrateur) n'a aucune garantie sur la taille
   de ces réponses.
2. **3 endpoints restent `@Hidden`** dans le golden OpenAPI (SSE,
   upload multipart, ingress webhook) — décision de périmètre assumée par
   `openapi-pagination-admin` (**D-SPEC = C**), pas une limitation
   technique de springdoc 3.1.0 (aucune trace d'un tel blocage dans le
   code ou les docs).

- **Affectés :** SPA embarqué (consommateur actuel de ces endpoints) ;
  tout futur intégrateur/agent lisant `openapi/openapi.yaml` comme contrat
  de référence ; `TENANT_ADMIN` (catalogue connecteurs) ;
  `DOSSIER_MANAGER`/`WORKFLOW_ADMIN` (file de tâches, historique workflow).
- **Situation actuelle (vérifiée par lecture de code, 2026-08-21) :**
  - `GET /api/dossiers/{id}/elements` → `List<DossierElementView>`
    (`DossierController.listElements`).
  - `GET /api/workflow-tasks/mine` → `List<MyWorkflowTaskResponse>`
    (`WorkflowTaskController.mine`) — déjà bornée en interne à
    `ListMyTasksUseCaseImpl.DEFAULT_LIMIT = 100`, mais ce plafond n'est ni
    documenté ni piloté par le client.
  - `GET /api/connector-definitions` → `List<ConnectorDefinitionResponse>`
    (`ConnectorDefinitionController.list`) — catalogue **statique en
    mémoire** (`ConnectorRegistry`, construit une fois au démarrage depuis
    les beans `ConnectorDefinition` injectés), borné par le nombre de
    types de connecteurs codés, pas par tenant.
  - `GET /api/dossiers/{id}/workflows` → `List<WorkflowLinkResponse>`
    (`DossierWorkflowController.history`).
  - `GET /api/workflow-links/{linkId}/history` → `List<ActivityHistoryEntry>`
    (`WorkflowLinkVisualizationController.history`).
  - `GET /api/workflow-links/{linkId}/tasks` → `List<WorkflowTaskView>`
    (`WorkflowLinkVisualizationController.tasks`).
  - `GET /api/dossiers/{id}/relations` → `List<DossierRelationResponse>` —
    **volontairement non paginée**, décision déjà actée par
    `relations-dossiers` (2026-08-14) : « volume attendu faible par
    dossier — pas de pagination sur V1 » (`docs/ai/design/2026-08-14-feature-relations-dossiers.md`).
  - `GET /api/notifications/stream` (SSE, `NotificationSseController`),
    `POST /api/dossiers/{id}/elements/attachment` (multipart,
    `DossierElementUploadController`), `POST /api/webhooks/{instanceId}`
    (`WebhookController`) : tous les trois `@Hidden`, absents de
    `openapi/openapi.yaml`.
  - Format d'erreur : **déjà normalisé** — 7 `@RestControllerAdvice`
    basés sur `ProblemDetail` couvrent tout le backend (dossier, workflow,
    notification, sécurité, génération document, template document,
    connecteur). Aucun gap trouvé — pas de travail requis sur ce point.

## Goals & Objectives
**What do we want to achieve?**

### Primary goals

- Migrer les **6 listes non-admin identifiées** vers l'envelope
  `PageResponse<T>` existant (`page`, `size`, `totalElements`,
  `totalPages`), avec tri contrôlé par allow-list unique par ressource
  (pattern `PageQueryParser` + `AdminListSorts`, à étendre) :
  `elements`, `workflow-tasks/mine`, `connector-definitions`,
  `dossiers/{id}/workflows`, `workflow-links/{linkId}/history`,
  `workflow-links/{linkId}/tasks`.
- Documenter intégralement dans `openapi/openapi.yaml` (golden, fail-on-diff
  CI déjà en place) les 3 opérations actuellement `@Hidden` : SSE
  (`text/event-stream` documenté via `@ApiResponse`), multipart (schema
  `binary` standard, déjà supporté nativement par springdoc), webhook
  ingress (schema JSON libre — `object` sans propriétés fixes, cohérent
  avec un payload qui dépend du connecteur appelant).

### Secondary goals

- Documenter dans `docs/specs/` ou en javadoc les allow-lists de tri
  retenues par ressource, pour rester cohérent avec le pattern déjà en
  place sur les listes admin.

### Non-goals (actés)

- **Codegen client** (typescript-axios ou équivalent) — TODO §24 le
  marque explicitement « hors V1 » ; aucune nouvelle justification pour
  revenir dessus. La feature couvre le contrat serveur, pas sa
  consommation outillée côté client.
- **Pagination de `GET /api/dossiers/{id}/relations`** — décision déjà
  actée par `relations-dossiers` (2026-08-14), pas de preuve nouvelle de
  croissance du volume par dossier pour la revoir.
- Nouveau format d'erreur — `ProblemDetail` déjà uniforme sur les 7
  tranches, rien à faire.
- Schéma de payload strict/typé pour le webhook ingress — le payload
  dépend du connecteur, un schema JSON libre suffit (pas de contrat de
  champ par champ).
- Rate limiting, authentification, ou toute autre préoccupation déjà
  couverte par `durcissement-donnees-sensibles`/§30.
- Changer le mécanisme de tri libre — chaque ressource garde un
  allow-list à une seule valeur (même contrainte que les listes admin
  déjà migrées), pas de tri multi-champs.

## Alternatives considérées

| Sujet | Id | Approche | Trade-off | Verdict |
|---|---|---|---|---|
| Codegen | A | **Exclure, respecter TODO** | Le SPA continue à consommer les endpoints à la main | **Retenu** |
| Codegen | B | Inclure un client typescript-axios généré | Revient sur une décision déjà actée sans nouvelle justification | Écarté |
| Relations | A | **Respecter la décision existante (pas de pagination)** | Incohérence apparente avec les 6 autres listes migrées, mais justifiée par le volume réel | **Retenu** |
| Relations | B | Paginer par cohérence systématique | Contredit une décision produit déjà actée sans preuve de besoin | Écarté |
| Périmètre pagination | A | Exactement les 3 listes citées par TODO §24 | Périmètre minimal, mais laisse 3 listes au même profil non traitées | Écarté |
| Périmètre pagination | B | **Les 6 listes trouvées, cohérence totale** | Périmètre plus large, mais ferme le sujet une fois pour toutes et évite un 7ᵉ lot futur pour les 3 listes oubliées | **Retenu** |
| Périmètre pagination | C | Seulement `workflow-tasks/mine` (seule liste vraiment cross-dossier) | Périmètre minimal viable, mais laisse `elements`/`connector-definitions` (explicitement cités par TODO §24) non traités | Écarté |
| Endpoints `@Hidden` | A | **Documentation complète, retirer `@Hidden` partout** | SSE/multipart déjà supportés nativement par springdoc ; webhook = schema libre | **Retenu** |
| Endpoints `@Hidden` | B | Documenter SSE/multipart, laisser webhook ingress `@Hidden` | Le payload variable rendrait un schema générique peu utile — mais laisse un troisième trou dans le golden | Écarté |

## User Stories & Use Cases

1. **En tant que** SPA (ou futur intégrateur), **je veux** que
   `GET /api/dossiers/{id}/elements`, `GET /api/workflow-tasks/mine`,
   `GET /api/connector-definitions`, `GET /api/dossiers/{id}/workflows`,
   `GET /api/workflow-links/{linkId}/history` et
   `GET /api/workflow-links/{linkId}/tasks` renvoient une envelope
   `PageResponse` avec `page`/`size` pilotables, **afin de** ne jamais
   recevoir une réponse de taille imprévisible.
2. **En tant que** `DOSSIER_MANAGER`, **je veux** que ma file de tâches
   (`workflow-tasks/mine`) reste utilisable même si elle contient plus de
   100 tâches, **afin de** voir toutes mes tâches via pagination plutôt
   qu'une troncature silencieuse.
3. **En tant que** développeur lisant `openapi/openapi.yaml`, **je veux**
   voir les 3 opérations SSE/multipart/webhook documentées avec leur type
   de contenu réel, **afin de** avoir un contrat complet sans trou
   `@Hidden` non justifié techniquement.
4. **En tant que** développeur, **je veux** que
   `GET /api/dossiers/{id}/relations` reste inchangée (non paginée),
   **afin de** ne pas introduire une rupture de contrat sur un endpoint
   dont le volume ne le justifie pas.

### Edge cases

- `connector-definitions` : catalogue déjà entièrement en mémoire — la
  pagination s'applique sur la liste déjà chargée (slice en mémoire), pas
  sur une requête DB ; à documenter en design comme différence de
  mécanisme vs les listes JPA déjà paginées.
- `workflow-tasks/mine` : le plafond interne `DEFAULT_LIMIT = 100` de
  `ListMyTasksUseCaseImpl` doit être réconcilié avec le nouveau contrat
  `page`/`size` — à trancher en design (garder un plafond dur au-delà
  duquel même la pagination ne va pas chercher plus loin, ou le retirer
  au profit du seul contrat de pagination).
- `workflow-links/{linkId}/history` et `.../tasks` : sources potentiellement
  Flowable (historique/tâches du moteur) — à vérifier en design si une
  pagination native existe côté `HistoryService`/`TaskService` Flowable ou
  si un slice en mémoire est nécessaire comme pour `connector-definitions`.
- Tri : chaque ressource migrée garde un allow-list à une seule valeur de
  tri (même contrainte que le pattern admin existant) — pas de tri libre
  multi-champs.
- OpenAPI golden : les 6 nouvelles envelopes + les 3 opérations non
  `@Hidden` doivent apparaître dans `openapi/openapi.yaml` après
  régénération (`-Dopenapi.update=true`), `OpenApiContractIT` vert sans
  régénération ensuite.

## Success Criteria
**How will we know when we're done?**

- [ ] Les 6 endpoints listés migrent vers `PageResponse<T>` avec
      `page`/`size`/tri contrôlé, réponse `200` inchangée en structure pour
      les clients qui ignorent les nouveaux champs d'envelope (pas de
      breaking change de type sur le contenu de chaque item).
- [ ] `GET /api/dossiers/{id}/relations` reste un `List<>` inchangé.
- [ ] `openapi/openapi.yaml` documente les 6 endpoints paginés et les 3
      opérations SSE/multipart/webhook, golden régénéré et `OpenApiContractIT`
      vert.
- [ ] Aucune régression sur les appels SPA existants à ces 6 endpoints
      (mocks `frontend/e2e/**` et composants consommateurs mis à jour si
      la forme de réponse change côté SPA).
- [ ] `./mvnw verify` vert (ArchUnit inclus) ; Vitest **et** Playwright
      verts si le SPA consomme un des 6 endpoints migrés (à confirmer en
      design lequel des 6 est réellement appelé par le SPA aujourd'hui).
- [ ] TODO §24 clos (plus de « reste ouvert ») ; FILE-ATTENTE et
      DEFERRED-FEATURES mis à jour après implémentation.

## Constraints & Assumptions

**Contraintes stables :**

- Réutiliser `PageResponse<T>`/`PageQueryParser`/`PageQuery`/`PageResult`
  existants — pas de nouvel envelope de pagination.
- Domaine pur : la pagination reste un concept infra/web, pas de fuite
  dans les use cases au-delà de ce qui existe déjà pour les listes admin.
- Pas de nouvelle dépendance (springdoc gère déjà multipart/SSE
  nativement).

**Décisions produit (Phase 1+2, toutes actées 2026-08-21) :**

| Id | Sujet | Choix |
|---|---|---|
| **D-CODEGEN** | Client généré | Exclu — hors V1, TODO déjà tranché |
| **D-RELATIONS** | Pagination relations | Non — respecte la décision `relations-dossiers` existante |
| **D-PAGINATION-SCOPE** | Périmètre pagination | Les 6 listes trouvées (au-delà des 3 citées par TODO §24) |
| **D-HIDDEN-SPEC** | Endpoints `@Hidden` | Documentation complète des 3 (SSE, multipart, webhook), retrait de `@Hidden` |

## Questions & Open Items

### Clos

| Id | Décision | Choix |
|---|---|---|
| **D-CODEGEN** | Codegen client | Exclu (2026-08-21) |
| **D-RELATIONS** | Pagination relations | Non, décision existante respectée (2026-08-21) |
| **D-PAGINATION-SCOPE** | Périmètre pagination | 6 listes (2026-08-21) |
| **D-HIDDEN-SPEC** | Endpoints `@Hidden` | Documentation complète (2026-08-21) |

### Ouvertes

Aucune décision produit ouverte. Points techniques à trancher en design
(mécanisme, pas périmètre) :
- Mécanisme de pagination pour `connector-definitions` (slice en mémoire)
  vs les 5 autres listes (à vérifier si backées par JPA ou Flowable).
- Sort du plafond interne `DEFAULT_LIMIT = 100` de `ListMyTasksUseCaseImpl`
  face au nouveau contrat `page`/`size`.
- Disponibilité d'une pagination native Flowable pour
  `workflow-links/{linkId}/history` et `.../tasks` si ces sources sont
  bien Flowable-backed (à confirmer).

### Hors file

Codegen client, pagination des relations, nouveau format d'erreur.

## Phase 2 — revue close (2026-08-21)

Template README : OK.

**Validé :** 4 décisions produit (D-CODEGEN, D-RELATIONS,
D-PAGINATION-SCOPE, D-HIDDEN-SPEC) ; contradiction FILE-ATTENTE/TODO sur
le codegen résolue en faveur de TODO ; non-goals ; stories ; critères de
succès ; alternatives par sous-sujet ; vérification que le format
d'erreur normalisé est déjà complet (pas de travail requis).

Status : **`reviewed`**. Prochaine phase : `dev-design` (Phase 3).
