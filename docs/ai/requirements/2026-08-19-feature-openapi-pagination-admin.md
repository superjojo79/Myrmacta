---
phase: requirements
title: Requirements & Problem Understanding
description: Clarify the problem space, gather requirements, and define success criteria
status: reviewed
created: 2026-08-19
updated: 2026-08-19
feature: openapi-pagination-admin
phase1: 2026-08-19
phase2: 2026-08-19
stakeholder_decisions: 2026-08-19
todo_ref: TODO.md §24 · FILE-ATTENTE.md · DEFERRED-FEATURES.md
---

# Requirements & Problem Understanding — `openapi-pagination-admin`

> Reste TODO §24 après pagination dossiers, `ProblemDetail`, PATCH fields et
> éléments. File d’attente produit 2026-08-19 (après `purge-stockage` PR #56).
> Décisions **D-SCOPE … D-CATALOG** actées. Requirements **reviewed** (Phase 2).

## Problem Statement
**What problem are we solving?**

Le contrat HTTP FlowForge n’est **pas** un artefact versionné : pas de
spécification OpenAPI, pas de gate CI de drift, Swagger absent. Les listes
**admin** (types, templates, connecteurs, configs workflow, webhooks de
notification, file de purge) renvoient encore des **tableaux plats** (ou
`{ items, counts }` pour la purge), alors que `GET /api/dossiers` a déjà
une envelope page. Le SPA consomme ces tableaux ; au-delà d’une page
implicite il n’y a ni pager ni contrat unique.

- **Affectés :** `TENANT_ADMIN` / `WORKFLOW_ADMIN` (écrans admin) ;
  gestionnaires (listes déroulantes types / configs workflow) ;
  intégrateurs et agents (contrat) ; le frontend embarqué.
- **Situation actuelle :**
  - `ProblemDetail` RFC 7807 déjà en place (`@RestControllerAdvice`).
  - `GET /api/dossiers` paginé (feature `pagination-dossiers`).
  - Listes admin = `List<>` JSON ; `GET /api/purge-outbox` = `{ items, counts }`
    avec query `limit` (défaut 50).
  - Aucun `springdoc` / `openapi/openapi.yaml`.
  - Pas d’écran SPA pour les webhooks de notification (API seule).
  - **Consommateurs hors admin** des mêmes `GET` listes (tableaux) :
    `CreateDossierView`, `SupervisionView` (filtre type),
    `StartWorkflowDialog`.
- **Réf. :** TODO §24 ; REVUE-PRE-IMPLEMENTATION §24 ; DEFERRED « OpenAPI formel ».

## Goals & Objectives
**What do we want to achieve?**

### Primary goals

- Publier un **OpenAPI 3** runtime (springdoc) **et** un YAML **versionné**
  `openapi/openapi.yaml`, avec **fail-on-diff** en CI (**D-OPENAPI = B**,
  **D-CI = A**).
- Paginer les listes admin manquantes avec **la même envelope** que les
  dossiers (**D-LISTS = A**, **D-PAGE = A**, **D-COMPAT = A**).
- Aligner **tous** les clients SPA de ces `GET` : pagers admin, sync URL,
  **et** catalogues formulaires via concaténation de pages (**D-FE = A**,
  **D-UI = A**, **D-URL = A**, **D-CATALOG = B**).
- Livrer l’écran admin **webhooks de notification** (CRUD dense comme
  Connecteurs) (**D-WEBHOOK-UI = B**, **D-WEBHOOK-UX = A**).

### Secondary goals

- Documenter `ProblemDetail` dans la spec.
- Secrets admin : `writeOnly` + masque runtime inchangé (**D-SECRETS = A**).
- Swagger UI / `/v3/api-docs` : **admins seulement**, **off par défaut**
  (**D-DOCS-ACCESS = B+C**, **D-OPENAPI-DEFAULT = A**).

### Non-goals (actés)

- Codegen FE (`openapi-typescript` / orval) — **follow-up B puis C** (**D-FE**).
- SSE, multipart, webhooks **ingress** (`POST /api/webhooks/**`) dans la spec
  V1 (**D-SPEC = C**).
- Pagination des collections imbriquées / file de tâches / catalogue
  `GET /api/connector-definitions` / ACL type (**D-NESTED = A**).
- Nouveaux widgets ou query `enabled` / `q=` (**D-FILTERS = A**).
- Endpoint jumeau non paginé / `?all=true` (**D-CATALOG ≠ C**).
- Observabilité §29, ERD §25, Testcontainers §28.
- Rapports / SLA ; connecteur `http-rest` SERVICE.
- Restore UI des webhooks / connecteurs soft-deleted.
- Changer les `@PreAuthorize` des listes (rôles **inchangés**).

## Alternatives considérées

| Id | Sujet | Options | Choix |
|---|---|---|---|
| D-SCOPE | Périmètre | OpenAPI+pagination+FE / OpenAPI seul / pagination seule | **A** |
| D-OPENAPI | OpenAPI | Runtime only / runtime+YAML / YAML manuel | **B** |
| D-LISTS | Listes | 5 CRUD + purge / 3 gros volumes / sans petits catalogues | **A** |
| D-COMPAT | Compat | Breaking envelope / dual / chemins `/page` | **A** |
| D-PAGE | Page | Identique dossiers / size défaut 50 / Spring `Page` | **A** |
| D-FE | Client FE | Axios manuel / codegen ciblé / codegen total | **A** (B, C différés) |
| D-SPEC | Couverture spec | Toute surface / listes seules / tout sauf SSE/multipart/ingress | **C** |
| D-PURGE | Shape purge | Envelope+`counts` / counts séparé / garder `items` | **A** |
| D-DOCS-ACCESS | Docs HTTP | Tout authentifié / admins / flag env | **B+C** |
| D-SORT | Tri | Fixe sans `sort` / un tri documenté / multi-champs | **B** |
| D-SORT-FIELDS | Champs de tri | Tableau proposé / tout alpha / tout chrono | **A** |
| D-CI | Gate YAML | Diff / existence / pas de gate | **A** |
| D-UI | Pager | Tous les écrans listes / size=100 / templates+purge seuls | **A** |
| D-URL | URL | `?page=` / mémoire / purge seule | **A** |
| D-NESTED | Nested | Arrays / paginer elements+relations / paginer tasks | **A** |
| D-WEBHOOK-UI | UI webhooks | API only / écran CRUD / reporter pagination API | **B** |
| D-WEBHOOK-UX | Densité UX | Comme connecteurs / + edit / + restore | **A** |
| D-SECRETS | Secrets spec | writeOnly+masque / strip GET / pas d’annotation | **A** |
| D-FILTERS | Filtres | Existants / +enabled / +q= | **A** |
| D-OPENAPI-DEFAULT | Flag runtime | false défaut / profil dev / true hors prod | **A** |
| D-CATALOG | Dropdowns | size=100 tronqué / concaténer les pages / endpoint `all` | **B** |

## User Stories & Use Cases

- En tant que **TENANT_ADMIN**, je parcours types / templates / connecteurs /
  webhooks / file de purge **page par page**, je partage l’URL (`?page=`),
  et je ne vois jamais un secret en clair.
- En tant que **WORKFLOW_ADMIN**, je pagine la liste des configs workflow
  (et j’accède à Swagger si OpenAPI est activé — pas aux écrans
  `TENANT_ADMIN` only).
- En tant que **DOSSIER_MANAGER** (création / Supervision), je vois **tous**
  les types auxquels j’ai accès dans les listes déroulantes, même s’il y en
  a plus de 20 (**D-CATALOG = B**).
- En tant que **intégrateur / CI**, je m’appuie sur `openapi/openapi.yaml` ;
  un drift runtime vs fichier **casse le build**.
- En tant que **ops prod**, Swagger n’est **pas** exposé tant que
  `app.openapi.enabled` n’est pas à `true`.

### Workflows clés

1. Admin ouvre `/admin/templates?page=1` → recharge la page 1.
2. Admin crée un webhook sortant (nom, URL, secret, enabled) → apparaît en
   liste ; le secret n’est plus affiché (`secretConfigured`).
3. CI exporte springdoc et `diff` le YAML commité.
4. Créer un dossier : le client enchaîne les pages de types jusqu’à épuisement.

### Edge cases

- `page < 0`, `size` hors 1..100, `sort` non autorisé → **400** `ProblemDetail`.
- Page vide (filtre sans match, au-delà de `totalPages`) → **200** envelope vide.
- `GET /api/document-templates?includeContent=true` : contenu **uniquement**
  pour la page courante ; filtre `templateKey` inchangé (API ; **pas** de
  widget filtre V1).
- `GET /api/purge-outbox` : `limit` **supprimé** ; `status` conservé en API ;
  `counts` = totaux **globaux** (indépendants de la page / du filtre page).
  UI purge V1 : pas de nouveau sélecteur `status` (D-FILTERS).
- Listes imbriquées dossiers / tasks / `connector-definitions` : **tableaux**.
- Helper catalogue : boucle jusqu’à `content` accumulé = `totalElements` ;
  page vide inattendue → stop (pas de boucle infinie).
- `GET /api/dossier-types` reste utilisable par tout utilisateur authentifié
  autorisé aujourd’hui (ACL types inchangée) — pas seulement l’écran admin.

## Success Criteria

- Les six `GET` listes D-LISTS répondent `{ content, page, size, totalElements, totalPages }`
  (+ `counts` pour purge). Défaut `page=0`, `size=20`.
- Tris autorisés uniquement (défaut = valeur unique si `sort` absent) :

  | Ressource | `sort` |
  |---|---|
  | dossier-types | `code,asc` |
  | document-templates | `updatedAt,desc` |
  | connectors | `name,asc` |
  | workflow-type-configs | `workflowKey,asc` |
  | notification-webhooks | `name,asc` |
  | purge-outbox | `createdAt,desc` |

- `openapi/openapi.yaml` commité ; CI **échoue** si l’export springdoc diffère.
- Spec **complète** hors SSE, multipart, `POST /api/webhooks/**`.
- SPA admin : pagers + sync URL `page` (+ `size` si ≠ 20) sur les vues
  listes existantes + **nouvelle** vue `/admin/notification-webhooks`
  (création, liste, toggle, delete ; secret write-only).
- SPA catalogues : `CreateDossierView`, `SupervisionView`,
  `StartWorkflowDialog` obtiennent l’ensemble via helper multi-pages
  (Vitest : >1 page concaténée).
- Vitest **et** Playwright verts ; mocks `frontend/e2e/**` à jour (envelope,
  plus d’arrays racine sur ces `GET`).
- `./mvnw verify` vert (ArchUnit : springdoc hors `domain`).
- Swagger : 403 pour rôle hors `TENANT_ADMIN`/`WORKFLOW_ADMIN` si enabled ;
  **pas de 200 anonyme** si `app.openapi.enabled=false`.
- Déploiement : **un** artefact Boot (SPA+API) — pas de fenêtre mixed.

## Constraints & Assumptions

- Stack figée : Java 25 / Spring Boot 4.1 / Vue 3 / BFF session ; domaine pur.
- Breaking API **dans la même release** que le SPA (seul client).
- Rôles listes **inchangés** (`@PreAuthorize` existants). `WORKFLOW_ADMIN`
  n’obtient pas les écrans `TENANT_ADMIN` (templates, connecteurs, purge,
  webhooks) via cette feature.
- Index SQL : réutiliser `idx_purge_outbox_status_created_at` ; catalogues
  admin = `ORDER BY` sans nouvel index V1 sauf besoin avéré.
- **Hypothèse nommée :** chemins springdoc par défaut `/v3/api-docs` et
  `/swagger-ui.html` (pas de prefix `/admin`).
- **Hypothèse :** `includeContent` templates reste un query param booléen.
- **Hypothèse (D-URL ∩ D-FILTERS) :** sync URL V1 = `page` + `size` optionnel.
  `templateKey` / `status` restent des query **API** documentées ; **pas**
  de nouveaux contrôles UI ni obligation de les écrire dans l’URL (aucun
  widget filtre existant sur ces écrans).
- **Hypothèse (D-CATALOG impl.) :** le helper demande `size=100` (plafond)
  par aller-retour pour limiter le nombre de requêtes, puis enchaîne les
  `page` tant que `accumulated < totalElements`.
- Pagination SQL offset (pas cursor). Pas de codegen V1.
- Soft-deleted webhooks/connecteurs : liste = comportement API actuel
  (exclus de la liste), inchangé.

## Questions & Open Items

Aucun point produit ouvert après Phase 2.

Follow-ups à noter dans `DEFERRED-FEATURES.md` à la clôture :

1. Codegen FE ciblé (D-FE **B**).
2. Codegen surface complète (D-FE **C**).
3. Spec SSE / multipart / webhooks ingress.
4. Pagination nested (elements, relations, tasks/mine).
5. Filtres `enabled` / recherche `q=` + widgets UI.
6. Cap catalogue > quelques pages (aujourd’hui boucle B, catalogues modestes).

## Phase 2 — revue (2026-08-19)

Validé vs template `docs/ai/requirements/README.md` : problem, goals/non-goals,
stories, success, constraints, open items.

Écarts levés :

- Consommateurs **hors admin** des `GET` types / workflow-configs → **D-CATALOG = B**.
- Contradiction apparente D-FILTERS « URL » vs absence de widgets → hypothèse
  D-URL ∩ D-FILTERS (API oui, URL UI = page/size).
- Typo story WORKFLOW_ADMIN ; critères `./mvnw verify` et déploiement atomique.
- `limit` purge : défaut actuel 50, remplacé par D-PAGE (20).

Pas de retour Phase 1. Suite : Phase 3 `dev-design`.
