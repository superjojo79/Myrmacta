---
phase: requirements
title: Requirements & Problem Understanding
description: Clarify the problem space, gather requirements, and define success criteria
status: reviewed
created: 2026-08-13
updated: 2026-08-13
feature: pagination-dossiers
stakeholder_decisions: 2026-08-13
phase2: 2026-08-13
phase2_stakeholder_confirm: 2026-08-13
phase3_sync: 2026-08-13
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
  `DEFERRED-FEATURES.md` §1.4 / §1.6 ; mock UI Supervision ;
  requirements `frontend` (A-LIST).

## Goals & Objectives
**What do we want to achieve?**

### Primary goals

- Livrer **`GET /api/dossiers`** paginé (offset) avec filtres V1 :
  `status`, `dossierTypeCode`, `reference` (préfixe).
- Filtrer strictement aux types **`VIEW`** (bypass `TENANT_ADMIN` /
  `WORKFLOW_ADMIN`).
- Débloquer **Supervision FE** : liste + filtres + sync URL + clic → détail,
  en **conservant** le raccourci ouvrir-par-ref/UUID.
- Erreurs invalides en **`ProblemDetail` 400** ; pas d’énumération de types
  hors ACL (pages vides 200).

### Secondary goals

- Item de liste **léger** : champs dossier de pilotage + `dossierTypeLabel`.
- Index SQL adaptés au tri `updatedAt,desc` + filtres courants.
- Couverture tests alignée Jacoco / Vitest projet (≥ 80 % zones touchées).
- Mettre à jour `DEFERRED-FEATURES.md` / note TODO §24 pour la tranche liste.

### Non-goals

- OpenAPI généré / client typé (reste TODO §24 partiel).
- Pagination générique des autres ressources (connectors, templates, …).
- Full-text / filtres sur `fields` JSON.
- Rapports & SLA.
- Multi-statut (`status=A,B`) ; cursor pagination.
- Exposition de `fields` (y compris redactés) dans la liste.
- Accès liste pour `API_SERVICE` / M2M (`AccessContext.Service`).
- PATCH fields dossier, éléments, relations (hors cette feature).

## Stakeholder decisions

| Id | Décision | Choix |
|---|---|---|
| **D-SCOPE** | Périmètre V1 | **C** — API + Supervision FE + filtres status/type/reference |
| **D-FILTERS** | Sémantique filtres | **A** — status exact (1 enum) ; `dossierTypeCode` exact ; `reference` **préfixe CI** |
| **D-ITEM** | Payload item | **C′** (Phase 2) — résumé : `id`, `reference`, `label` (**dossier**), `dossierTypeCode`, `dossierTypeLabel`, `status`, `createdAt`, `updatedAt` — **pas** de `fields` |
| **D-PAGE** | Pagination / tri | **A** — `size` défaut **20**, plage **1..100** ; tri supporté **uniquement** `updatedAt,desc` ; envelope `{ content, page, size, totalElements, totalPages }` |
| **D-ACL** | Accès | **A** — humains ; filtre VIEW ; 0 grant / type hors ACL → **200 vide** ; `API_SERVICE` / Service → **403** |
| **D-UX** | Supervision | **C** — liste + filtres + garder ouvrir-par-ref + **sync URL** `?status=&dossierTypeCode=&reference=&page=` (+ `size` optionnel) |
| **D-VALID** | Params invalides | **A** — `status` invalide / `page < 0` / `size` hors **1..100** / `sort` non supporté → **400** |
| **D-PARAMS** | Noms query | **A** — `page`, `size`, `sort`, `status`, `dossierTypeCode`, `reference` |
| **D-ROUTE** | Routage `ref` | **Phase 2** — si query `ref` présente → get-by-ref **existant** (prioritaire) ; sinon → liste paginée |

### Alternatives revalidées (Phase 2)

| Sujet | Retenu | Écarté |
|---|---|---|
| Pagination | Offset page/size | Cursor (complexité FE, volume V1 OK) |
| Item liste | Résumé + labels | Dossier complet + redactor (lourd, risque `fields`) |
| Hors ACL | 200 vide | 403 (énumération / UX brutale) |
| Nom filtre ref | `reference` | Réutiliser `ref` (casserait get unique) |

### Compatibilité avec l’existant

| Param | Usage | Endpoint |
|---|---|---|
| `ref` | **égalité exacte**, un dossier | `GET /api/dossiers?ref=` (existant, inchangé) |
| `reference` | **préfixe** filtre liste | `GET /api/dossiers` liste (nouveau) |

### Surface API (acceptation)

| Méthode | Path | Authz / notes |
|---|---|---|
| GET | `/api/dossiers` *(sans `ref`)* | Humain authentifié ; résultat ∩ VIEW ; envelope page |
| GET | `/api/dossiers?ref=` | Inchangé — un `DossierResponse` complet (redaction) |
| GET | `/api/dossiers/{id}` | Inchangé |

JSON item liste (contrat) :

```json
{
  "id": "…",
  "reference": "DOS-FAC-2026-000042",
  "label": "Facture Acme",
  "dossierTypeCode": "FAC",
  "dossierTypeLabel": "Facture",
  "status": "OUVERT",
  "createdAt": "2026-08-01T10:00:00Z",
  "updatedAt": "2026-08-12T15:30:00Z"
}
```

`dossierTypeLabel` = `DossierType.label` ; si type absent → `""` (pas 500).
Ne pas confondre avec `label` du **dossier**.

## User Stories & Use Cases
**How will users interact with the solution?**

1. **En tant que** gestionnaire, **je veux** voir une page de dossiers que je
   peux consulter, **afin de** piloter mon activité sans connaître chaque ref.
2. **En tant que** viewer, **je veux** filtrer par statut / type / préfixe de
   référence, **afin de** retrouver rapidement un sous-ensemble.
3. **En tant que** admin tenant / workflow, **je veux** lister tous les
   dossiers du tenant (bypass ACL), **afin de** superviser.
4. **En tant qu’** utilisateur, **je veux** partager l’URL Supervision filtrée,
   **afin de** retrouver le même contexte après refresh.
5. **En tant qu’** utilisateur, **je veux** toujours ouvrir directement par
   ref/UUID, **afin de** garder le raccourci actuel.

### Edge cases

- Aucun grant VIEW → page vide 200 (pas 403).
- Filtre `dossierTypeCode` hors ACL → page vide 200 (même si le type existe).
- `status=NOPE` / `page=-1` / `size=0` / `size=101` / `sort=reference,asc` → 400.
- `reference` vide ou blanc → traité comme filtre absent.
- Page au-delà de `totalPages` → `content` vide, totaux cohérents.
- `?ref=` présent (seul ou avec d’autres params) → **get-by-ref**, pas liste.
- Token / contexte `API_SERVICE` ou `AccessContext.Service` → 403.
- Types inactifs : dossiers toujours listables si VIEW/bypass ; pas de filtre
  `active` dédié V1.

## Success Criteria
**How will we know when we're done?**

- [ ] `GET /api/dossiers` (sans `ref`) renvoie l’envelope paginée décidée.
- [ ] Filtres D-FILTERS + validation D-VALID respectés (tests MockMvc).
- [ ] ACL D-ACL : VIEW / bypass admin / 403 service / pages vides.
- [ ] Item = résumé D-ITEM (incl. `label` + `dossierTypeLabel`) ; **pas** de `fields`.
- [ ] Supervision : tableau paginé, filtres, sync URL, clic → `/dossiers/{id}`,
      formulaire ouvrir-par-ref conservé.
- [ ] Régression : `GET /api/dossiers?ref=` inchangé (shape + authz).
- [ ] `./mvnw test` (zones touchées) + `frontend` Vitest Supervision OK.
- [ ] Docs AI + `DEFERRED-FEATURES` / TODO §24 mis à jour (tranche liste).

## Constraints & Assumptions
**What limitations do we need to work within?**

- Hexagone : use case + port out ; pas de JPA dans le domaine.
- Instance dédiée par tenant (décision n° 13) ; `tenantId` = `TenantProperties`.
- Session BFF + CSRF inchangés (GET liste idempotent, pas de CSRF requis).
- H2 mode PostgreSQL pour IT (comme dossiers) ; pas Testcontainers dans cette
  feature.
- Chaîne M2M `client_credentials` (décision n° 14) n’est pas le client cible ;
  défense en profondeur 403 si Service / `ROLE_API_SERVICE`.
- **Assumption A1 :** volume V1 compatible offset + `COUNT(*)` par requête.
- **Assumption A2 :** indexes `(updated_at DESC)` (+ aides type/status)
  suffisent ; pas de search engine.
- **Assumption A3 :** `dossierTypeLabel` via join/batch ; type soft-missing → `""`.
- **Assumption A4 :** sync URL Supervision — `size` optionnel (défaut 20 si
  absent) ; `sort` non exposé dans l’URL V1 (toujours `updatedAt,desc`).

## Questions & Open Items
**What do we still need to clarify?**

### Clos Phase 2

| Id | Clarification |
|---|---|
| R1 | Nom JSON du label type = **`dossierTypeLabel`** (évite collision avec `Dossier.label`) |
| R2 | Inclure **`label` dossier** dans l’item liste (pilotage UX) |
| R3 | `size` ∈ **1..100** ; `size=0` → 400 |
| R4 | Priorité routage **`ref` > liste** |
| R5 | `size` optionnel dans l’URL FE ; `sort` pas dans l’URL V1 |

### Reporté hors feature

- OpenAPI formel, pagination autres ressources, recherche `fields`,
  rate-limit spécifique liste, Testcontainers PG.
