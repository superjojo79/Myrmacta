---
phase: requirements
title: Requirements & Problem Understanding
description: Clarify the problem space, gather requirements, and define success criteria
status: draft
created: 2026-08-19
updated: 2026-08-19
feature: openapi-pagination-admin
phase1: 2026-08-19
stakeholder_decisions: 2026-08-19
todo_ref: TODO.md §24 · FILE-ATTENTE.md · DEFERRED-FEATURES.md
---

# Requirements & Problem Understanding — `openapi-pagination-admin`

> Reste TODO §24 après pagination dossiers, `ProblemDetail`, PATCH fields et
> éléments. File d’attente produit 2026-08-19 (après `purge-stockage` PR #56).
> Décisions **D-SCOPE … D-OPENAPI-DEFAULT** actées. Requirements **draft**
> (Phase 2 review à enchaîner).

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
  intégrateurs et agents (contrat) ; le frontend embarqué.
- **Situation actuelle :**
  - `ProblemDetail` RFC 7807 déjà en place (`@RestControllerAdvice`).
  - `GET /api/dossiers` paginé (feature `pagination-dossiers`).
  - Listes admin = `List<>` JSON ; `GET /api/purge-outbox` = `{ items, counts, limit }`.
  - Aucun `springdoc` / `openapi/openapi.yaml`.
  - Pas d’écran SPA pour les webhooks de notification (API seule).
- **Réf. :** TODO §24 ; REVUE-PRE-IMPLEMENTATION §24 ; DEFERRED « OpenAPI formel ».

## Goals & Objectives
**What do we want to achieve?**

### Primary goals

- Publier un **OpenAPI 3** runtime (springdoc) **et** un YAML **versionné**
  `openapi/openapi.yaml`, avec **fail-on-diff** en CI (**D-OPENAPI = B**,
  **D-CI = A**).
- Paginer les listes admin manquantes avec **la même envelope** que les
  dossiers (**D-LISTS = A**, **D-PAGE = A**, **D-COMPAT = A**).
- Aligner le SPA : clients axios, pagers, sync URL, mocks Playwright
  (**D-FE = A**, **D-UI = A**, **D-URL = A**).
- Livrer l’écran admin **webhooks de notification** (CRUD dense comme
  Connecteurs) (**D-WEBHOOK-UI = B**, **D-WEBHOOK-UX = A**).

### Secondary goals

- Documenter `ProblemDetail` dans la spec.
- Secrets admin : `writeOnly` + masque runtime inchangé (**D-SECRETS = A**).
- Swagger UI / `/v3/api-docs` : **admins seulement**, **off par défaut**
  (**D-DOCS-ACCESS = B+C**, **D-OPENAPI-DEFAULT = A**).

### Non-goals (actés)

- Codegen FE (`openapi-typescript` / orval) — **follow-up B puis C** (**D-FE**).
- SSE, multipart, webhooks **ingress** dans la spec V1 (**D-SPEC = C**).
- Pagination des collections imbriquées / file de tâches / catalogue
  `connector-definitions` / ACL type (**D-NESTED = A**).
- Nouveaux filtres `enabled` / `q=` (**D-FILTERS = A**).
- Observabilité §29, ERD §25, Testcontainers §28.
- Rapports / SLA ; connecteur `http-rest` SERVICE.
- Restore UI des webhooks / connecteurs soft-deleted.

## Alternatives considérées

| Sujet | Options | Choix |
|---|---|---|
| Périmètre | OpenAPI+pagination+FE / OpenAPI seul / pagination seule | **A** D-SCOPE |
| OpenAPI | Runtime only / runtime+YAML / YAML manuel | **B** D-OPENAPI |
| Listes | 5 CRUD + purge / 3 gros volumes / sans petits catalogues | **A** D-LISTS |
| Compat | Breaking envelope / dual / chemins `/page` | **A** D-COMPAT |
| Page | Identique dossiers / size défaut 50 / Spring `Page` | **A** D-PAGE |
| FE | Axios manuel / codegen ciblé / codegen total | **A** (+ B, C différés) |
| Spec | Toute surface / listes seules / tout sauf SSE/multipart/ingress | **C** D-SPEC |
| Purge | Envelope+`counts` / counts séparé / garder `items` | **A** D-PURGE |
| Docs HTTP | Tout authentifié / admins / flag env | **B+C** |
| Tri | Fixe sans `sort` / un tri documenté / multi-champs | **B** + champs **A** |
| CI | Diff YAML / existence / pas de gate | **A** D-CI |
| UI pager | Tous les écrans / size=100 / templates+purge seuls | **A** D-UI |
| URL | `?page=` / mémoire / purge seule | **A** D-URL |
| Webhooks UI | API only / écran CRUD / reporter pagination API | **B** + UX **A** |

## User Stories & Use Cases

- En tant que **TENANT_ADMIN**, je parcours types / templates / connecteurs /
  webhooks / file de purge **page par page**, je partage l’URL (`?page=`),
  et je ne vois jamais un secret en clair.
- En tant que **WORKFLOW_ADMIN**, je paginé la liste des configs workflow
  (et j’accède à Swagger si OpenAPI est activé).
- En tant que **intégrateur / CI**, je m’appuie sur `openapi/openapi.yaml` ;
  un drift runtime vs fichier **casse le build**.
- En tant que **ops prod**, Swagger n’est **pas** exposé tant que
  `app.openapi.enabled` n’est pas à `true`.

### Edge cases

- `page < 0`, `size` hors 1..100, `sort` non autorisé → **400** `ProblemDetail`.
- Page vide (filtre sans match, au-delà de `totalPages`) → **200** envelope vide.
- `GET /api/document-templates?includeContent=true` : contenu **uniquement**
  pour la page courante ; filtre `templateKey` inchangé.
- `GET /api/purge-outbox` : `limit` **supprimé** ; `status` conservé ;
  `counts` = totaux **globaux** (pas seulement la page).
- Listes imbriquées dossiers / tasks : **tableaux** toujours.

## Success Criteria

- Les six `GET` listes D-LISTS répondent `{ content, page, size, totalElements, totalPages }`
  (+ `counts` pour purge). Défaut `page=0`, `size=20`.
- Tris autorisés uniquement (défaut = valeur unique) :

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
- SPA : pagers + sync URL sur les vues admin existantes + **nouvelle** vue
  `/admin/notification-webhooks` (création, liste, toggle, delete ; secret write-only).
- Vitest **et** Playwright verts ; mocks `frontend/e2e/**` à jour.
- Swagger : 401/403 hors `TENANT_ADMIN`/`WORKFLOW_ADMIN` ; 404/403 si
  `app.openapi.enabled=false` (pas d’UI anonyme).

## Constraints & Assumptions

- Stack figée : Java 25 / Spring Boot 4.1 / Vue 3 / BFF session ; domaine pur.
- Breaking API **dans la même release** que le SPA (seul client).
- Rôles listes **inchangés** (`@PreAuthorize` existants).
- Index SQL : réutiliser `idx_purge_outbox_status_created_at` ; catalogues
  admin = `ORDER BY` sans nouvel index V1 sauf besoin avéré.
- **Hypothèse nommée :** chemins springdoc par défaut `/v3/api-docs` et
  `/swagger-ui.html` (pas de prefix `/admin`).
- **Hypothèse :** `includeContent` templates reste un query param booléen.
- Pagination SQL offset (pas cursor). Pas de codegen V1.

## Questions & Open Items

Aucun point produit ouvert. Phase 2 = revue de ce document.

Follow-ups à noter dans `DEFERRED-FEATURES.md` à la clôture :

1. Codegen FE ciblé (D-FE **B**).
2. Codegen surface complète (D-FE **C**).
3. Spec SSE / multipart / webhooks ingress.
4. Pagination nested (elements, relations, tasks/mine).
5. Filtres `enabled` / recherche `q=`.
