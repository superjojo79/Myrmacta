---
phase: requirements
title: Requirements & Problem Understanding
description: Clarify the problem space, gather requirements, and define success criteria
status: draft
created: 2026-08-13
updated: 2026-08-13
feature: pagination-dossiers
stakeholder_decisions: 2026-08-13
todo_24_slice: list-search-pagination
---

# Requirements & Problem Understanding

## Problem Statement
**What problem are we solving?**

La Supervision FlowForge ne peut aujourd’hui **ouvrir un dossier que par
référence ou UUID**. Il n’existe pas de `GET` liste/pagination sur les
dossiers — écart volontaire de la feature `frontend` (A-LIST / TODO §24).
Sans liste filtrée, l’écran Supervision du mock UI reste incomplet et les
gestionnaires ne peuvent pas parcourir leur portefeuille.

- **Affectés :** `DOSSIER_MANAGER` / `DOSSIER_VIEWER` (parcours Supervision),
  `TENANT_ADMIN` / `WORKFLOW_ADMIN` (vue transverse), frontend pilotage.
- **Situation actuelle :** CRUD dossier minimal + `GET /api/dossiers/{id}` +
  `GET /api/dossiers?ref=` (un seul dossier) ; ACL type (décision n° 20) ;
  `ProblemDetail` déjà en place ; Supervision = formulaire ouvrir-par-ref.
- **Réf. :** TODO §24 (tranche liste/pagination) ;
  `DEFERRED-FEATURES.md` §1.4 / §1.6 ; mock UI Supervision.

## Goals & Objectives
**What do we want to achieve?**

### Primary goals

- Livrer **`GET /api/dossiers`** paginé (offset) avec filtres V1 :
  `status`, `dossierTypeCode`, `reference` (préfixe).
- Filtrer strictement aux types **`VIEW`** (bypass admin workflow/tenant).
- Débloquer **Supervision FE** : liste + filtres + sync URL + clic → détail,
  en **conservant** le raccourci ouvrir-par-ref/UUID.
- Erreurs invalides en **`ProblemDetail` 400** ; pas d’énumération de types
  hors ACL (pages vides 200).

### Secondary goals

- Item de liste **léger** + `DossierType.label` pour colonnes UX.
- Index SQL adaptés au tri `updatedAt,desc` + filtres courants.
- Couverture tests alignée Jacoco / Vitest projet (≥ 80 % zones touchées).

### Non-goals

- OpenAPI généré / client typé (reste TODO §24 partiel).
- Pagination générique des autres ressources (connectors, templates, …).
- Full-text / filtres sur `fields` JSON.
- Rapports & SLA.
- Multi-statut (`status=A,B`) ; cursor pagination.
- Exposition de `fields` (y compris redactés) dans la liste.
- Accès liste pour `API_SERVICE` / M2M.

## Stakeholder decisions (Phase 1)

| Id | Décision | Choix |
|---|---|---|
| **D-SCOPE** | Périmètre V1 | **C** — API + Supervision FE + filtres status/type/reference |
| **D-FILTERS** | Sémantique filtres | **A** — status exact (1 enum) ; `dossierTypeCode` exact ; `reference` **préfixe CI** |
| **D-ITEM** | Payload item | **C** — résumé (`id`, `reference`, `dossierTypeCode`, `status`, `createdAt`, `updatedAt`) + **`label` type** |
| **D-PAGE** | Pagination / tri | **A** — `size` défaut 20, max 100 ; tri supporté **uniquement** `updatedAt,desc` ; envelope `{ content, page, size, totalElements, totalPages }` |
| **D-ACL** | Accès | **A** — humains ; filtre VIEW ; 0 grant / type hors ACL → **200 vide** ; `API_SERVICE` → **403** |
| **D-UX** | Supervision | **C** — liste + filtres + garder ouvrir-par-ref + **sync URL** `?status=&dossierTypeCode=&reference=&page=` |
| **D-VALID** | Params invalides | **A** — `status` invalide / `page < 0` / `size` hors borne / `sort` non supporté → **400** |
| **D-PARAMS** | Noms query | **A** — `page`, `size`, `sort`, `status`, `dossierTypeCode`, `reference` |

### Compatibilité avec l’existant

| Param | Usage | Endpoint |
|---|---|---|
| `ref` | **égalité exacte**, un dossier | `GET /api/dossiers?ref=` (existant, inchangé) |
| `reference` | **préfixe** filtre liste | `GET /api/dossiers` liste (nouveau) |

Si `ref` est présent → comportement get-by-ref inchangé. Sinon → liste paginée.

## User Stories & Use Cases
**How will users interact with the solution?**

1. **En tant que** gestionnaire, **je veux** voir une page de dossiers que je
   peux consulter, **afin de** piloter mon activité sans connaître chaque ref.
2. **En tant que** viewer, **je veux** filtrer par statut / type / préfixe de
   référence, **afin de** retrouver rapidement un sous-ensemble.
3. **En tant que** admin tenant, **je veux** lister tous les dossiers du
   tenant, **afin de** superviser sans grants ACL individuels.
4. **En tant qu’** utilisateur, **je veux** partager l’URL Supervision filtrée,
   **afin de** retrouver le même contexte après refresh.
5. **En tant qu’** utilisateur, **je veux** toujours ouvrir directement par
   ref/UUID, **afin de** garder le raccourci actuel.

### Edge cases

- Aucun grant VIEW → page vide 200 (pas 403).
- Filtre `dossierTypeCode` hors ACL → page vide 200.
- `status=NOPE` / `page=-1` / `size=101` / `sort=reference,asc` → 400.
- `reference` vide ou blanc → traité comme filtre absent.
- Page au-delà de `totalPages` → `content` vide, totaux cohérents.
- Coexistence `?ref=` (détail) vs liste sans `ref`.

## Success Criteria
**How will we know when we're done?**

- [ ] `GET /api/dossiers` (sans `ref`) renvoie l’envelope paginée décidée.
- [ ] Filtres D-FILTERS + validation D-VALID respectés (tests MockMvc).
- [ ] ACL D-ACL : VIEW / bypass admin / 403 service / pages vides.
- [ ] Item = résumé + `label` type ; **pas** de `fields`.
- [ ] Supervision : tableau paginé, filtres, sync URL, clic → `/dossiers/{id}`,
      formulaire ouvrir-par-ref conservé.
- [ ] `./mvnw test` (zones touchées) + `frontend` Vitest Supervision OK ;
      pas de régression `?ref=`.
- [ ] Docs AI + `DEFERRED-FEATURES` / TODO §24 mis à jour (tranche liste).

## Constraints & Assumptions
**What limitations do we need to work within?**

- Hexagone : use case + port out ; pas de JPA dans le domaine.
- Instance dédiée par tenant (décision n° 13) ; `tenantId` = `TenantProperties`.
- Session BFF + CSRF inchangés (GET liste idempotent, pas de CSRF requis).
- H2 mode PostgreSQL pour IT (comme dossiers) ; pas Testcontainers dans cette
  feature.
- **Assumption A1 :** volume V1 compatible offset + `COUNT(*)` par requête.
- **Assumption A2 :** un index `(updated_at DESC)` (+ aides type/reference)
  suffit ; pas de search engine.
- **Assumption A3 :** `DossierType.label` lu en join/batch ; type soft-missing
  → `label` null/empty sans 500.

## Questions & Open Items
**What do we still need to clarify?**

- Aucune question produit bloquante Phase 1 — décisions D-* actées.
- Reporté hors feature : OpenAPI formel, pagination autres ressources,
  recherche `fields`, rate-limit spécifique liste (réutiliser stack globale si
  besoin ops plus tard).
