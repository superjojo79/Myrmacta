---
phase: requirements
title: Requirements & Problem Understanding
description: Clarify the problem space, gather requirements, and define success criteria
status: reviewed
reviewed: 2026-07-31
updated: 2026-07-31
---

# Requirements & Problem Understanding

## Problem Statement
**What problem are we solving?**

FlowForge a besoin de l’**entité métier centrale** — le dossier — avec types
configurables par tenant, champs dynamiques validés, numérotation unique,
persistance JPA et API REST protégée par session BFF + ACL au niveau type
(décision n° 20). Sans cette tranche, aucune feature aval (workflow, connecteurs,
documents, visualisation) ne peut s’ancrer sur un agrégat réel.

- **Affectés :** gestionnaires de dossiers, admins tenant (configuration des
  types / ACL), développeurs des features aval, ops (schéma PostgreSQL du tenant).
- **Situation actuelle :** socle hexagonal + multi-tenant + authentification BFF
  livrés (phase 9) ; package `domain.dossier` amorcé (`package-info` only) ;
  exclusions autoconfig DataSource / JPA / Liquibase encore actives ; aucune
  table ni API dossier. Analyse : `analysis doc/04-dossiers.md` + décisions
  n° 17, 18, 20 + clarifications lifecycle (tranche B).
- **UX :** pas d’écrans Vue dans cette feature — la SPA (`frontend`) consommera
  les endpoints REST ; formulaires dynamiques dérivés de `fieldDefinitions`.
  Pas de parcours UI à livrer ici ; le contrat d’erreur (`ProblemDetail`) et les
  schémas de champs sont le livrable UX-facing.

## Goals & Objectives
**What do we want to achieve?**

### Primary goals

- Livrer le **domaine pur** `dossier` : `Dossier`, `DossierType`,
  `FieldDefinition` (record), `DossierStatus`, `DossierFieldValidator`,
  `AccessContext`, `DossierTypeAclEntry`, `DossierPermission`,
  `DossierAccessPolicy` — sans annotation JPA (décision n° 17).
- Ports `port/in` + implémentations `usecase/*Impl` : création / lecture dossier,
  mise à jour de statut (transitions), CRUD `DossierType`, grant/revoke ACL.
- Ports `port/out` + adaptateurs JPA : repositories, `SequentialReferenceGenerator`
  (table de séquence atomique), mappers domaine ↔ `*JpaEntity`.
- Activer DataSource / JPA / Liquibase (retrait exclusions) ; changelog
  **tables dossiers uniquement** ; runtime PostgreSQL avec
  `search_path` = `app.tenant.id`.
- Concurrence (TODO n° 26 option 1) : version optimiste sur dossier ; séquence
  `dossier_reference_seq` + `UPDATE … RETURNING` ; format
  `DOS-{TYPE}-{YYYY}-{seq:06d}` ; millésime **UTC** ; `UNIQUE(tenant_id, reference)`.
- API REST minimale ci-dessous + erreurs **RFC 7807 `ProblemDetail`**.
- ACL décision n° 20 complète dès cette tranche :
  - `AccessContext.System` → bypass ;
  - `AccessContext.Service` → **refus** accès dossier (M2M hors scope endpoints) ;
  - `TENANT_ADMIN` / `WORKFLOW_ADMIN` → bypass ACL **lecture/écriture dossiers**
    et lecture types ;
  - sinon `VIEW` / `EDIT` sur le type (`EDIT` ⇒ `VIEW`).
- `POST /api/dossiers` crée en **`DRAFT`** ; `tenantId` = `TenantProperties.id()`
  (jamais pris du body) ; transitions via `PATCH …/status`.
- Tests : unitaires domaine ; IT JPA/API sur **H2 mode PostgreSQL** ; suite
  auth / ArchUnit / Jacoco non régressées.

### Surface API (acceptation)

| Méthode | Path | Authz |
|---|---|---|
| POST | `/api/dossier-types` | `TENANT_ADMIN` uniquement |
| PATCH | `/api/dossier-types/{code}` | `TENANT_ADMIN` (label, workflowKey, fields, allowUnknownFields, active) |
| GET | `/api/dossier-types` | authentifié ; filtré aux types `VIEW` (bypass admin → tous) |
| GET | `/api/dossier-types/{code}` | `VIEW` ou bypass |
| POST | `/api/dossiers` | `EDIT` sur le type (ou bypass) |
| GET | `/api/dossiers/{id}` | `VIEW` (ou bypass) |
| GET | `/api/dossiers?ref=` | `VIEW` (ou bypass) |
| PATCH | `/api/dossiers/{id}/status` | `EDIT` ; body `{ "status", "expectedVersion"? }` |
| POST | `/api/dossier-types/{code}/acl` | `TENANT_ADMIN` |
| DELETE | `/api/dossier-types/{code}/acl/{userId}` | `TENANT_ADMIN` |

Mutateurs : CSRF BFF. Anonyme `/api/**` → 401.

### Secondary goals

- Documenter le contrat REST pour `frontend` et les ports/use cases pour
  `workflow-engine` / `connecteurs` (sans les câbler).
- Conserver `currentWorkflowLinkId` nullable sur `Dossier` (toujours `null` en B)
  pour compatibilité modèle analyse.

### Non-goals

- `DossierElement`, `DossierRelation`, `DossierWorkflowLink`, AttachWorkflow,
  Flowable, outbox, batch connecteurs, MinIO / upload fichiers.
- Scaffolding de packages/ports pour éléments / relations / links (aucun code
  skeleton au-delà du `package-info` existant).
- Liste paginée / recherche avancée (`GET /api/dossiers` collection).
- `PATCH` des champs dynamiques du dossier (hors statut).
- Rétention / purge (TODO 21, 22), `FieldDefinition.sensitive` (TODO 23).
- Schéma consolidé point 25 (hors tables dossiers de cette tranche).
- OpenAPI généré comme contrat formel ; Testcontainers PostgreSQL.
- Écrans Vue / PrimeVue ; validation définitive liste des rôles (TODO 19).
- Administration des types par `WORKFLOW_ADMIN` ou `DOSSIER_MANAGER`
  (`TENANT_ADMIN` seul).

## User Stories & Use Cases
**How will users interact with the solution?**

- En tant que **TENANT_ADMIN**, je crée / mets à jour / consulte un `DossierType`
  (schéma de champs, `workflowKey`, `allowUnknownFields`, `active`) pour
  configurer le tenant sans code.
- En tant que **TENANT_ADMIN**, j’accorde ou révoque `VIEW`/`EDIT` sur un type
  pour un utilisateur (re-grant idempotent → 204).
- En tant que **utilisateur avec EDIT** (ou bypass admin), je crée un dossier
  `DRAFT` avec `dossierTypeCode`, `label` (requis), `fields` validés et une
  référence unique générée.
- En tant que **utilisateur avec VIEW**, je lis un dossier par id ou par
  référence ; je liste les types visibles.
- En tant que **utilisateur avec EDIT**, je fais avancer le statut selon les
  transitions autorisées, avec contrôle de version optionnel.
- En tant que **appel système futur** (`AccessContext.System`), je pourrai
  lire/écrire sans ACL utilisateur (contrat prêt pour Flowable / outbox).
- En tant que **SPA**, je reçois des erreurs `ProblemDetail` actionnables
  (400 champs, 403 ACL, 404, 409 conflit/version/transition/unicité).

### Scénarios clés

1. Admin crée type `SINISTRE` avec fieldDefinitions → 201.
2. Admin PATCH type (`active`, fields…) → 200.
3. Grant `EDIT` à user U sur `SINISTRE` → U peut `POST /api/dossiers`.
4. Création avec champs invalides / inconnus → 400 ProblemDetail `errors[]`.
5. Création OK → statut `DRAFT`, référence `DOS-SINISTRE-{YYYY}-000001`,
   `tenantId` = instance.
6. User sans grant → 403 sur create/get ; `WORKFLOW_ADMIN` bypass OK.
7. `PATCH` statut `DRAFT` → `OUVERT` OK ; transition illégale → 409.
8. Mise à jour concurrente (`expectedVersion` stale) → 409.
9. Type `active=false` → create dossier refusé (409 ou 400 métier) ; get existant OK.
10. Boot + Liquibase H2 : tables présentes ; suite existante verte.

### Edge cases

- `allowUnknownFields=true` : champs hors schéma acceptés.
- Type de champ `FILE` : valeur **string** (réf. future storage) — pas d’upload
  dans cette tranche ; validation = non-blank si required.
- `AccessContext.Service` : refus systématique accès dossier.
- Référence : unicité `(tenant_id, reference)` ; année UTC.
- DossierType code dupliqué → 409.
- Grant ACL dupliqué → 204 no-op (idempotent).

### Transitions de statut (acceptation)

```
DRAFT → OUVERT
OUVERT → EN_COURS | CLOTURE
EN_COURS → SUSPENDU | CLOTURE
SUSPENDU → EN_COURS | CLOTURE
CLOTURE → ARCHIVE
ARCHIVE → (terminal)
```

## Success Criteria
**How will we know when we're done?**

- [ ] Exclusions DataSource / HibernateJpa / DataJpa / Liquibase retirées ;
      Flowable / Mail restent exclus.
- [ ] Domaine `dossier` pur (ArchUnit) + use cases câblés dans `DomainConfig`
      (ou config dédiée).
- [ ] Liquibase crée : `dossier_type`, `dossier`, `dossier_type_acl`,
      `dossier_reference_seq` (+ index/contraintes actés).
- [ ] Générateur de référence atomique + format documenté ; millésime UTC.
- [ ] Version optimiste sur dossier ; 409 si conflit.
- [ ] `DossierFieldValidator` + validation create ; 400 ProblemDetail.
- [ ] `DossierAccessPolicy` + table ACL + grant/revoke ; 403 si refus ;
      `EDIT` ⇒ `VIEW` ; `Service` refus ; admins bypass dossiers.
- [ ] Surface API (tableau ci-dessus) opérationnelle sous session BFF + CSRF ;
      admin types = `TENANT_ADMIN` seul.
- [ ] Création en `DRAFT` ; `tenantId` depuis `TenantProperties` ; transitions
      statut validées.
- [ ] Tests H2 (PostgreSQL mode) + unitaires ; `./mvnw test` / `verify` verts ;
      Jacoco ≥ 80 %.
- [ ] Docs requirements / design / testing / planning alignés sur tranche B.

**Performance benchmarks :** N/A pour la tranche — CRUD simple ; pas de cible
latence formelle. Scaling horizontal couvert par séquence SQL atomique +
version optimiste.

## Constraints & Assumptions
**What limitations do we need to work within?**

### Technical constraints

- Java 25 · Spring Boot 4.1 · Spring Data JPA · PostgreSQL (prod) · Liquibase ·
  Spring Security BFF déjà livré.
- Instance dédiée par tenant (décision n° 13) : `app.tenant.id` constant ;
  `SET search_path TO ${app.tenant.id}, public` en runtime PG.
- Domaine sans Spring / JPA ; entités `*JpaEntity` en
  `infrastructure.persistence.dossier`.
- Convention `port/in` + `usecase/*Impl` (ArchUnit).
- Tests IT : **H2 mode PostgreSQL** (pas Testcontainers dans cette tranche).

### Business / product

- Rôles de référence (`TENANT_ADMIN`, `WORKFLOW_ADMIN`, `DOSSIER_MANAGER`,
  `DOSSIER_VIEWER`, …) inchangés — point 19 non rouvert.
- ACL au **type**, pas à l’instance (décision n° 20).
- Un seul workflow actif / dossier = hors scope (pas de table link).
- `workflowKey` est stocké sur le type dès B (prérequis workflow) mais **aucun**
  démarrage BPMN n’est déclenché.

### Time / budget / rollout

- Branche `feature-dossiers` / PR ; pas de feature flag.
- Datasource réelle fournie par env ; H2 pour CI/tests.

### Assumptions nommées

- `analysis doc/04-dossiers.md` + `12-autorisation-conformite.md` font foi sauf
  écarts de périmètre tranche B et clarifications actées ci-dessous.
- Auth BFF + `TenantProperties` + claim `tenant_id` déjà opérationnels.
- H2 mode PostgreSQL suffit pour JSON (équivalent jsonb), `UPDATE … RETURNING`
  et contraintes uniques de cette tranche ; écarts SQL documentés si découverts.
  Sur H2, le `search_path` PG peut être simulé (schéma dédié ou default) sans
  changer le modèle logique.
- `FieldDefinition` est un **record** (alignement décision n° 17 ; l’extrait
  `@Value` de l’analyse est corrigé).
- Point 25 (ERD consolidé) reste ouvert hors tables de cette feature.
- Frontend consommera CSRF + cookie comme documenté par `authentification`.
- `subjectUserId` ACL = subject OIDC (`sub`) de l’utilisateur cible.

## Questions & Open Items
**What do we still need to clarify?**

- Aucune question bloquante pour la phase 3 (revue design).
- **Différé :** éléments, relations, workflow links, Flowable, outbox, batch,
  MinIO/upload, pagination liste, PATCH fields dossier, rétention (21),
  purge (22), `sensitive` (23), OpenAPI formel, Testcontainers PG, point 19,
  ERD consolidé (25).

## Alternatives brainstormées

| Approche | Trade-off | Verdict |
|---|---|---|
| A — Domaine + in-memory only | Rapide, pas de vraie persistence | Écarté |
| B — Domaine + JPA + API minimale | Active persistence utile ; scope maîtrisé | **Retenu** |
| C — Surface analyse complète | Dépend workflow/connecteurs/MinIO | Écarté |
| Séquence PG dynamique / in-memory | Fragile ou non multi-pod | Écarté — table seq + UPDATE RETURNING |
| ACL no-op / sans AccessContext | Dette sécu / casse API plus tard | Écarté — ACL complète |
| API large (liste + PATCH fields) | Ouvre point 24 élargi | Écarté — surface option 1 |
| Testcontainers PG | Fidèle, CI lourde | Écarté — H2 mode PG |
| Création directe `OUVERT` | Mélange création / soumission | Écarté — `DRAFT` |
| Admin types élargi à WORKFLOW_ADMIN | Dilue responsabilité config tenant | Écarté — TENANT_ADMIN seul |
| Upload FILE en B | Tire MinIO | Écarté — string ref only |

## Clarifications actées

1. **Périmètre :** tranche **B** — domaine + JPA/Liquibase + CRUD
   `DossierType`/`Dossier` + validation champs + référence + ACL + API listée.
2. **TODO 26 :** version optimiste ; table `dossier_reference_seq` ; format
   `DOS-{TYPE}-{YYYY}-{seq:06d}` ; UTC ; `UNIQUE(tenant_id, reference)`.
3. **ACL :** `AccessContext` + policy + `dossier_type_acl` + grant/revoke ;
   `EDIT` ⇒ `VIEW` ; `Service` refus ; System/admins bypass dossiers.
4. **API :** tableau endpoints + `ProblemDetail` ; pas de liste dossiers ni
   `/workflows` ; `PATCH` type inclus pour `active`/schéma.
5. **Persistence/tests :** PG+Liquibase runtime ; IT H2 mode PostgreSQL.
6. **Statut / admin :** création `DRAFT` ; `tenantId` = `TenantProperties.id()` ;
   `label` requis à la création ; admin types + ACL = `TENANT_ADMIN` seul ;
   create dossier exige `EDIT` (ou bypass).
7. **FILE :** type de champ autorisé ; valeur string ; pas d’upload.
8. **Reports :** TODO 21/22/23, éléments/relations/links, Flowable, OpenAPI,
   Testcontainers, pagination, PATCH fields dossier.

## Review notes (phase 2)

| Point | Résolution |
|---|---|
| Conformité template README | Sections template + UX, perf, rollout, assumptions, alternatives |
| Cohérence surface API vs design | Tableau endpoints ajouté ; `PATCH` types explicite |
| `tenantId` à la création | Toujours `TenantProperties.id()` — jamais body |
| Admin types vs bypass ACL | `TENANT_ADMIN` seul pour admin types/ACL ; `WORKFLOW_ADMIN` bypass dossiers/lecture types seulement |
| `AccessContext.Service` | Refus accès dossier (aligné décision 20 / doc 12) |
| `EDIT` ⇒ `VIEW` | Acté success criteria + stories |
| Champ `FILE` vs non-goal MinIO | String ref only ; pas d’upload |
| Payload create dossier | `dossierTypeCode` + `label` requis + `fields` |
| `expectedVersion` | Optionnel sur PATCH status ; stale → 409 |
| Type inactif | Bloque nouvelles créations ; lecture existante OK |
| Secondary goal « packages futurs » | Clarifié : pas de skeleton éléments/relations/links |
| Questions bloquantes | Aucune |
| Design / testing drafts | Alignement mineur design (table endpoints) ; revue design = phase 3 |

**Prochaine phase :** `dev-design` (phase 3 — revue design contre requirements).
