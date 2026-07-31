---
phase: requirements
title: Requirements & Problem Understanding
description: Clarify the problem space, gather requirements, and define success criteria
status: draft
created: 2026-07-31
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
  **tables dossiers uniquement** ; `search_path` = `app.tenant.id`.
- Concurrence (TODO n° 26 option 1) : `@Version` sur dossier ; séquence
  `dossier_reference_seq` + `UPDATE … RETURNING` ; format
  `DOS-{TYPE}-{YYYY}-{seq:06d}` ; millésime **UTC** ; `UNIQUE(tenant_id, reference)`.
- API REST minimale (types CRUD dont mise à jour/`active`, dossiers
  create/get/get-by-ref/status, ACL grant/revoke) + erreurs
  **RFC 7807 `ProblemDetail`**.
- ACL décision n° 20 complète dès cette tranche (`System` bypass ;
  `TENANT_ADMIN` / `WORKFLOW_ADMIN` contournent ; sinon VIEW/EDIT sur le type).
- `POST /api/dossiers` crée en **`DRAFT`** ; transitions via
  `PATCH …/status` selon le graphe autorisé.
- Tests : unitaires domaine ; IT JPA/API sur **H2 mode PostgreSQL** ; suite
  auth / ArchUnit / Jacoco non régressées.

### Secondary goals

- Documenter le contrat REST pour `frontend` et les use cases pour
  `workflow-engine` / `connecteurs` (sans les câbler).
- Préparer les packages/éléments futurs (`DossierElement`, relations,
  `DossierWorkflowLink`) en **hors scope code** (pas de tables ni ports).

### Non-goals

- `DossierElement`, `DossierRelation`, `DossierWorkflowLink`, AttachWorkflow,
  Flowable, outbox, batch connecteurs, MinIO.
- Liste paginée / recherche avancée (`GET /api/dossiers` collection).
- `PATCH` des champs dynamiques (hors statut) — lot ultérieur.
- Rétention / purge (TODO 21, 22), `FieldDefinition.sensitive` (TODO 23).
- Schéma consolidé point 25 (hors tables dossiers de cette tranche).
- OpenAPI généré comme contrat formel ; Testcontainers PostgreSQL (option 2
  écartée au profit de H2).
- Écrans Vue / PrimeVue ; validation définitive liste des rôles (TODO 19).

## User Stories & Use Cases
**How will users interact with the solution?**

- En tant que **TENANT_ADMIN**, je crée / consulte un `DossierType` (schéma de
  champs, `workflowKey`, `allowUnknownFields`, `active`) pour configurer le
  tenant sans code.
- En tant que **TENANT_ADMIN**, j’accorde ou révoque `VIEW`/`EDIT` sur un type
  pour un utilisateur.
- En tant que **utilisateur avec EDIT** (ou admin bypass), je crée un dossier
  `DRAFT` avec champs validés et une référence unique générée.
- En tant que **utilisateur avec VIEW**, je lis un dossier par id ou par
  référence.
- En tant que **utilisateur avec EDIT**, je fais avancer le statut selon les
  transitions autorisées.
- En tant que **appel système futur** (`AccessContext.System`), je pourrai
  lire/écrire sans ACL utilisateur (contrat prêt pour Flowable / outbox).
- En tant que **SPA**, je reçois des erreurs `ProblemDetail` actionnables
  (400 champs, 403 ACL, 404, 409 conflit/version/transition).

### Scénarios clés

1. Admin crée type `SINISTRE` avec fieldDefinitions → 201.
2. Grant `EDIT` à user U sur `SINISTRE` → U peut `POST /api/dossiers`.
3. Création avec champs invalides / inconnus → 400 ProblemDetail détaillé.
4. Création OK → statut `DRAFT`, référence `DOS-SINISTRE-{YYYY}-000001`.
5. User sans grant → 403 sur create/get.
6. `PATCH` statut `DRAFT` → `OUVERT` OK ; transition illégale → 409.
7. Mise à jour concurrente (version stale) → 409.
8. Boot + Liquibase H2 : tables présentes ; suite existante verte.

### Edge cases

- Type `active=false` : pas de nouvelle création ; lecture dossiers existants OK.
- `allowUnknownFields=true` : champs hors schéma acceptés.
- `TENANT_ADMIN` / `WORKFLOW_ADMIN` : bypass ACL sur tous les types.
- Référence : unicité `(tenant_id, reference)` ; année UTC.
- DossierType code dupliqué → 409.

## Success Criteria
**How will we know when we're done?**

- [ ] Exclusions DataSource / HibernateJpa / DataJpa / Liquibase retirées ;
      Flowable / Mail restent exclus.
- [ ] Domaine `dossier` pur (ArchUnit) + use cases câblés dans `DomainConfig`
      (ou config dédiée).
- [ ] Liquibase crée : `dossier_type`, `dossier`, `dossier_type_acl`,
      `dossier_reference_seq` (+ index/contraintes actés).
- [ ] Générateur de référence atomique + format documenté ; millésime UTC.
- [ ] `@Version` / champ `version` sur dossier ; 409 si conflit.
- [ ] `DossierFieldValidator` + validation create ; 400 ProblemDetail.
- [ ] `DossierAccessPolicy` + table ACL + grant/revoke ; 403 si refus.
- [ ] API surface option 1 opérationnelle sous session BFF + CSRF.
- [ ] Création en `DRAFT` ; transitions statut validées.
- [ ] Tests H2 (PostgreSQL mode) + unitaires ; `./mvnw test` / `verify` verts ;
      Jacoco ≥ 80 %.
- [ ] Docs requirements / design / testing / planning alignés sur tranche B.

**Performance benchmarks :** N/A pour la tranche — CRUD simple ; pas de cible
latence formelle. Scaling horizontal couvert par séquence SQL atomique +
`@Version`.

## Constraints & Assumptions
**What limitations do we need to work within?**

### Technical constraints

- Java 25 · Spring Boot 4.1 · Spring Data JPA · PostgreSQL (prod) · Liquibase ·
  Spring Security BFF déjà livré.
- Instance dédiée par tenant (décision n° 13) : `app.tenant.id` constant ;
  `SET search_path TO ${app.tenant.id}, public`.
- Domaine sans Spring / JPA ; entités `*JpaEntity` en
  `infrastructure.persistence.dossier`.
- Convention `port/in` + `usecase/*Impl` (ArchUnit).
- Tests IT : **H2 mode PostgreSQL** (pas Testcontainers dans cette tranche).

### Business / product

- Rôles de référence (`TENANT_ADMIN`, `WORKFLOW_ADMIN`, `DOSSIER_MANAGER`,
  `DOSSIER_VIEWER`, …) inchangés — point 19 non rouvert.
- ACL au **type**, pas à l’instance (décision n° 20).
- Un seul workflow actif / dossier = hors scope (pas de table link).

### Time / budget / rollout

- Branche `feature-dossiers` / PR ; pas de feature flag.
- Datasource réelle fournie par env ; H2 pour CI/tests.

### Assumptions nommées

- `analysis doc/04-dossiers.md` + `12-autorisation-conformite.md` font foi sauf
  écarts de périmètre tranche B et clarifications actées ci-dessous.
- Auth BFF + `TenantProperties` + claim `tenant_id` déjà opérationnels.
- H2 mode PostgreSQL suffit pour JSONB-as-JSON, `UPDATE … RETURNING` et
  contraintes uniques de cette tranche (écarts SQL acceptés / surveillés).
- `FieldDefinition` est un **record** (alignement décision n° 17 ; l’extrait
  `@Value` de l’analyse est corrigé).
- Point 25 (ERD consolidé) reste ouvert hors tables de cette feature.
- Frontend consommera CSRF + cookie comme documenté par `authentification`.

## Questions & Open Items
**What do we still need to clarify?**

- Aucune question bloquante pour design / testing / planning de la tranche B.
- **Différé :** éléments, relations, workflow links, Flowable, outbox, batch,
  MinIO, pagination liste, PATCH fields, rétention (21), purge (22),
  `sensitive` (23), OpenAPI formel, Testcontainers PG, point 19, ERD consolidé (25).

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

## Clarifications actées

1. **Périmètre :** tranche **B** — domaine + JPA/Liquibase + CRUD
   `DossierType`/`Dossier` + validation champs + référence + ACL + API option 1.
2. **TODO 26 :** `@Version` ; table `dossier_reference_seq` ; format
   `DOS-{TYPE}-{YYYY}-{seq:06d}` ; UTC ; `UNIQUE(tenant_id, reference)`.
3. **ACL :** option 1 — `AccessContext` + policy + `dossier_type_acl` +
   grant/revoke dès B.
4. **API :** option 1 — types, dossiers (create/get/get-by-ref/status), ACL ;
   `ProblemDetail` ; pas de liste paginée ni `/workflows`.
5. **Persistence/tests :** option 2 — PG+Liquibase en runtime ; IT sur H2
   mode PostgreSQL.
6. **Statut / admin :** création en `DRAFT` ; transitions via PATCH ; admin
   types + ACL réservés à `TENANT_ADMIN` ; create dossier exige `EDIT` (ou bypass).
7. **Reports :** TODO 21/22/23, éléments/relations/links, Flowable, OpenAPI,
   Testcontainers, pagination.

## Review notes (phase 2)

*(à compléter lors de la revue requirements)*

**Prochaine phase :** `dev-requirements` phase 2 (revue requirements), puis
`dev-design` phase 3.
