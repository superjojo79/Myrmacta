---
phase: requirements
title: Requirements & Problem Understanding
description: Clarify the problem space, gather requirements, and define success criteria
status: draft
created: 2026-08-03
updated: 2026-08-03
---

# Requirements & Problem Understanding

## Problem Statement
**What problem are we solving?**

FlowForge a des packages `domain/document` et `infrastructure/*/document`
vides. Aucun stockage versionné de templates Thymeleaf, aucune résolution
multi-variants / locale, aucune API d’administration. Sans cette tranche, la
feature aval `generation-documents` (PDF) ne peut pas fusionner un modèle HTML
avec des données dossier / workflow.

- **Affectés :** `TENANT_ADMIN` (config templates — analyse `12`), auteurs de
  contenus documentaires, feature `generation-documents`, futurs écrans
  `frontend`.
- **Situation actuelle :** placeholders `package-info` uniquement ; Liquibase
  s’arrête à `003-connector-tables` ; analyse :
  `analysis doc/08-templates-documents.md` + décisions n° 13, 17, 18 ;
  authz templates = `TENANT_ADMIN` (`12-autorisation-conformite.md`).
- **UX :** pas d’écrans Vue — API REST admin + résolution. Erreurs
  `ProblemDetail` alignées dossiers / connecteurs / workflow.

## Goals & Objectives
**What do we want to achieve?**

### Primary goals

- Livrer le **socle templates** hexagonal (**D-SCOPE = 1**) :
  `DocumentTemplate` record domaine (décision n° 17) vs
  `DocumentTemplateJpaEntity`, port `DocumentTemplateRepository`, use cases
  admin + `TemplateResolver`, Liquibase `004-document-template-tables`.
- **Modèle** (**D-MODEL = 1**) : triplet de résolution
  `(templateKey, variantKey, locale)` avec `variantKey` / `locale` optionnels
  (`null` = défaut — **D-NULL = 1**) ; champs `label`, `version`,
  `TemplateStatus`, `htmlContent`, audit `createdAt` / `updatedAt` /
  `updatedBy`, `tenantId` via `TenantProperties` (décision n° 13).
- **Cycle de vie** (**D-VERSION = 1**) :
  `DRAFT → ACTIVE → ARCHIVED` ; **une seule** version `ACTIVE` par
  `(tenantId, templateKey, variantKey, locale)` ; passage en `ACTIVE`
  archive automatiquement la précédente ; contenu `ACTIVE` / `ARCHIVED`
  **immuable** ; `DRAFT` éditable in-place (**D-EDIT = 1**).
- **Résolution en cascade** (**D-RESOLVE = 1**) :
  1. `(key, variant, locale)` → 2. `(key, variant, null)` →
  3. `(key, null, locale)` → 4. `(key, null, null)` sinon
  `TemplateNotFoundException`.
- **Admin CRUD** (**D-ADMIN = 1**) : `TENANT_ADMIN` seul + CSRF BFF ;
  method security `@PreAuthorize`.
- **API resolve** (**D-RESOLVE-API = 1**) : endpoint admin de résolution
  (ops / smoke) ; le consommateur principal reste le use case interne pour
  `generation-documents`.
- Validation minimale (**D-VALIDATE = 1**) : `templateKey` et `htmlContent`
  non blank ; taille max `htmlContent` (**1 MiB**) ; pas de compile
  Thymeleaf obligatoire en V1.
- Tests unitaires + MockMvc/IT H2 ; ArchUnit ; Jacoco ≥ 80 %.

### Surface API (acceptation)

| Méthode | Path | Authz / notes |
|---|---|---|
| GET | `/api/document-templates` | `TENANT_ADMIN` — liste (filtre optionnel `templateKey`) ; toutes versions / statuts |
| GET | `/api/document-templates/{id}` | `TENANT_ADMIN` — détail |
| POST | `/api/document-templates` | `TENANT_ADMIN` — créer en `DRAFT` (version = max+1 ou 1) |
| PATCH | `/api/document-templates/{id}` | `TENANT_ADMIN` — éditer `label` / `htmlContent` **si DRAFT** ; sinon 409 |
| POST | `/api/document-templates/{id}/activate` | `TENANT_ADMIN` — `DRAFT`→`ACTIVE` ; archive l’ACTIVE précédente |
| POST | `/api/document-templates/{id}/archive` | `TENANT_ADMIN` — `ACTIVE`→`ARCHIVED` (sans activer un autre) |
| POST | `/api/document-templates/{id}/new-version` | `TENANT_ADMIN` — clone contenu → nouveau `DRAFT` (version+1) |
| GET | `/api/document-templates/resolve` | `TENANT_ADMIN` — query `templateKey` + opt. `variantKey` / `locale` → ACTIVE résolu |

Mutateurs BFF : CSRF. Anonyme → 401. Erreurs : `ProblemDetail`.
Pas de soft-delete séparé : `ARCHIVED` suffit (**D-DELETE = 1**).

### Secondary goals

- Index unique partiel PostgreSQL `uq_template_active` (analyse § cycle de vie).
- Documenter le contrat de résolution pour `generation-documents`.
- Audit `updatedBy` depuis le principal OIDC quand disponible.

### Non-goals

- Génération PDF / Flying Saucer / jsoup / `PdfRenderer` /
  `GenerateDocumentUseCase` / `DocumentStoragePort` →
  **`generation-documents`**.
- `DocumentGenerationTask` / ServiceTask Flowable / variables d’exécution →
  **`generation-documents`** (**D-FLOWABLE = différé**).
- Preview HTML+PDF hybride → **`generation-documents`** (**D-PREVIEW =
  différé**).
- UI Vue / OpenAPI formel / pagination avancée (TODO §24 partiel —
  liste filtrée V1).
- Validation compile Thymeleaf stricte ; sandboxing expressions ;
  upload fichiers template.
- Multi-tenant cross-schema ; templates plateforme partagés.
- Métriques Micrometer riches (TODO §29) ; Testcontainers PG.

## Alternatives considérées

| Approache | Trade-off | Verdict |
|---|---|---|
| **A — Templates-first** (CRUD + resolve, sans PDF) | Débloque génération ensuite ; surface bornée | **Retenue** (**D-SCOPE**) |
| B — Templates + PDF dans la même tranche | Couple stockage et rendu ; trop large | Écartée |
| C — Templates fichiers classpath seulement | Pas de versionning / variants tenant | Écartée |
| Soft-delete vs ARCHIVED | Soft-delete redondant avec statut | **ARCHIVED** (**D-DELETE**) |
| Resolve HTTP vs interne seul | HTTP utile smoke/admin ; risque surface | **HTTP admin** (**D-RESOLVE-API**) |

## User Stories & Use Cases

- En tant que **TENANT_ADMIN**, je crée un template `contrat` / variant
  `premium` / locale `fr` en `DRAFT` pour préparer un contenu Thymeleaf.
- En tant que **TENANT_ADMIN**, j’édite le HTML d’un `DRAFT`, puis je
  **active** : l’ancienne `ACTIVE` passe `ARCHIVED`.
- En tant que **TENANT_ADMIN**, je crée une **new-version** depuis une
  `ACTIVE` pour itérer sans casser la génération en cours.
- En tant que **TENANT_ADMIN**, je **résous** `(contrat, premium, nl)` et
  obtiens le fallback cascade si la combinaison exacte n’existe pas.
- En tant que **système** (`generation-documents`, futur), j’appelle
  `TemplateResolver.resolve(...)` pour obtenir le HTML `ACTIVE`.
- **Edge :** activer un second `DRAFT` alors qu’une `ACTIVE` existe →
  archive automatique ; éditer une `ACTIVE` → 409 ; resolve sans aucun
  fallback → 404 `TemplateNotFound` ; `templateKey` blank → 400.

## Success Criteria
**How will we know when we're done?**

- [ ] Table `document_template` Liquibase + index unique partiel ACTIVE
- [ ] Record domaine + adapter JPA (décision n° 17) ; ArchUnit OK
- [ ] Cascade de résolution couverte par tests (TODO §28 partiel)
- [ ] Une seule ACTIVE par combinaison après activate concurrent-safe
  (contrainte SQL + gestion conflit)
- [ ] Surface API admin ci-dessus via MockMvc + `TENANT_ADMIN`
- [ ] Non-admin → 403 ; anonyme → 401
- [ ] `./mvnw verify` vert ; Jacoco INSTRUCTION ≥ 80 %
- [ ] Docs feature alignées ; `npx ai-devkit lint --feature templates-documents`

## Constraints & Assumptions
**What limitations do we need to work within?**

- Java 25 · Spring Boot 4.x · hexagone mono-module · H2 MODE=PostgreSQL IT.
- Tenant = constante instance (`TenantProperties`, décision n° 13).
- Pas de MinIO / stockage objet dans cette tranche (DEFERRED §0).
- Contenu = HTML Thymeleaf TEXT en base (analyse § stratégie).
- **A1** — Taille max HTML 1 MiB (aligné webhook connecteurs).
- **A2** — `version` entier monotone par combinaison ; géré applicativement.
- **A3** — Pas de hard delete V1.
- **A4** — Liste sans pagination cursor V1 (filtre `templateKey` suffit).
- **A5** — `updatedBy` = subject OIDC ou `"system"` si absent.
- **A6** — Unicité logique ACTIVE via index partiel + `COALESCE` null→`''`.

## Questions & Open Items

| # | Item | Disposition |
|---|---|---|
| Q1 | Scope PDF / Flowable dans cette feature ? | **Clos** — D-SCOPE 1 (différé génération) |
| Q2 | Qui administre les templates ? | **Clos** — D-ADMIN 1 (`TENANT_ADMIN`) |
| Q3 | Soft-delete ? | **Clos** — D-DELETE 1 (`ARCHIVED`) |
| Q4 | Endpoint resolve HTTP ? | **Clos** — D-RESOLVE-API 1 |
| Q5 | Édition ACTIVE ? | **Clos** — D-EDIT 1 (immuable ; new-version) |
| Q6 | Null vs empty variant/locale | **Clos** — D-NULL 1 (`null` + COALESCE) |
| Q7 | Validation Thymeleaf compile | **Clos** — D-VALIDATE 1 (non-blank + taille) |
| Q8 | Preview HTML/PDF | **Différé** — D-PREVIEW / génération |
| Q9 | Pagination liste | **Différé** — TODO §24 ; A4 |
| Q10 | Bornes exactes taille HTML | **Clos** — A1 = 1 MiB |

### Décisions actées

| Id | Sujet | Choix |
|---|---|---|
| D-SCOPE | Tranche | Templates CRUD + resolve ; PDF hors scope |
| D-MODEL | Domaine | Record + JpaEntity (décision n° 17) |
| D-VERSION | Cycle de vie | DRAFT → ACTIVE → ARCHIVED ; 1 ACTIVE |
| D-EDIT | Mutabilité | DRAFT editable ; ACTIVE/ARCHIVED immuables |
| D-NULL | Défauts | `null` variant/locale = défaut |
| D-RESOLVE | Cascade | 4 niveaux analyse |
| D-RESOLVE-API | HTTP resolve | GET admin `TENANT_ADMIN` |
| D-ADMIN | Authz | `TENANT_ADMIN` seul |
| D-DELETE | Suppression | Statut `ARCHIVED` (pas soft-delete) |
| D-VALIDATE | Contenu | Non-blank + max 1 MiB |
| D-PREVIEW | Preview | Différé génération |
| D-FLOWABLE | ServiceTask | Différé génération |

## Sources

- `analysis doc/08-templates-documents.md`, `07-generation-documents.md`,
  `12-autorisation-conformite.md`, `01-architecture-hexagonale.md`
- Décisions TODO n° 13, 17, 18 ; TODO §24 / §28 (partiels)
- `DEFERRED-FEATURES.md` — ordre feature `templates-documents`
- Features livrées : `connecteurs`, `dossiers`, `autorisation-conformite`
- Mémoire : aucune entrée préalable (recherche vide)
