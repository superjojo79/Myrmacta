---
phase: requirements
title: Requirements & Problem Understanding
description: Clarify the problem space, gather requirements, and define success criteria
status: reviewed
reviewed: 2026-07-31
updated: 2026-08-01
---

# Requirements & Problem Understanding

## Problem Statement
**What problem are we solving?**

FlowForge doit isoler les données et le runtime de chaque client (tenant) sans
propagation dynamique de contexte (`ThreadLocal`, résolution de schéma par requête).
Les décisions n° 13 et 14 imposent **une instance Spring Boot dédiée par tenant**, un
schéma PostgreSQL par tenant, et un routage par chemin via reverse-proxy statique.

- **Affectés :** ops (provisioning / déploiement), développeurs des features aval
  (auth, persistence, workflow), et indirectement les utilisateurs finaux via
  l’isolation des données.
- **Situation actuelle :** socle hexagonal livré (`architecture-hexagonale`) avec
  exclusions autoconfig JPA/Security/Flowable ; `StartWorkflowCommand` porte déjà un
  `tenantId` fourni par l’appelant ; aucune config `app.tenant`, aucun validateur
  d’identifiant, aucun package `infrastructure.tenant`. Analyse complète dans
  `docs/specs/02-multi-tenant.md`.
- **UX :** N/A pour cette tranche — fondation technique / ops ; pas d’écran
  d’onboarding tenant.

## Goals & Objectives
**What do we want to achieve?**

### Primary goals

- Introduire `TenantProperties` (`app.tenant.id`) comme **constante de configuration**
  de l’instance (décision n° 13) — propriété **requise** (fail-fast si absente).
- Livrer `TenantIdValidator` + `InvalidTenantIdException` avec la regex
  `^[a-z][a-z0-9_]{2,62}$` (contraintes identifiant PostgreSQL / provisioning).
- Valider `TenantProperties.id()` avec `TenantIdValidator` **au démarrage** de
  l’application (fail-fast si id invalide) — défense locale en plus du provisioning.
- Documenter dans `application.yml` le modèle cible :
  - `app.tenant.id` **actif** (valeur locale par défaut `tenant_dev`) ;
  - `server.servlet.context-path` et `spring.datasource.hikari.connection-init-sql`
    **commentés** dans le yaml (non actifs) — pas de profil Spring obligatoire.
- Documenter le contrat reverse-proxy (chemin `/{tenantId}`, blocs statiques) pour
  les features / ops aval.
- Aligner les fixtures de test workflow `tenant-a` → `tenant_a` (conforme à la regex).

### Secondary goals

- Exposer le pattern « tenant = config, jamais résolu par requête » pour les features
  `authentification`, persistence JPA et `workflow-engine`.
- Préparer le package `infrastructure.tenant` pour le futur `TenantSchemaBootstrap`
  (hors scope ici).

### Non-goals

- PostgreSQL réel, pool Hikari actif, SPI multi-tenant Hibernate.
- `TenantSchemaBootstrap` / Liquibase au démarrage.
- `TenantResolutionFilter` OIDC (feature `authentification`).
- Outillage de provisioning complet (point 31 — création schéma, instance, bloc proxy).
- Scaling horizontal multi-répliques d’un même tenant (chemin d’évolution).
- Flowable `tenantId` natif (feature `workflow-engine`).
- Table `platform.tenant_migration_status` (point 29 — observabilité optionnelle).
- Refactor du domaine workflow pour lire `TenantProperties` (adaptateurs aval).
- Activation de `context-path` par défaut.
- Profil Spring dédié `tenant` pour activer le path (les commentaires yaml suffisent).

## User Stories & Use Cases
**How will users interact with the solution?**

Fondation technique ; « utilisateurs » = contributeurs et features aval.

- En tant que **développeur**, je veux injecter `TenantProperties` afin d’obtenir
  l’identifiant du tenant de **cette** instance sans contexte request-scoped.
- En tant que **outil / ops de provisioning**, je veux valider un `tenantId` via
  `TenantIdValidator` avant création de schéma / génération de config.
- En tant que **mainteneur**, je veux que `context-path` et `search_path` soient
  documentés (commentés) dans la config sans casser les tests du socle hexagonal.
- En tant que **feature `authentification`**, je veux un contrat clair : le claim
  OIDC `tenant_id` devra égaler `TenantProperties.id()` (filtre hors scope ici).
- En tant que **ops**, je veux qu’une instance mal configurée (`app.tenant.id` absent
  ou non conforme à la regex) **refuse de démarrer**.

### Scénarios clés

1. Démarrage local : `app.tenant.id=tenant_dev` lu via `TenantProperties` + validé.
2. `TenantIdValidator.validate("tenant_a")` OK ; `validate("tenant-a")` /
   `validate("")` / `validate(null)` → `InvalidTenantIdException`.
3. `app.tenant.id` absent ou `tenant-a` dans la config → échec au démarrage.
4. Lecture du yaml : blocs cibles `context-path` + `connection-init-sql` **commentés**,
   non appliqués.
5. Tests workflow existants : fixtures `tenant_a` uniquement.

### Edge cases

- Identifiant trop court (`ab`), commençant par un chiffre, ou contenant `-` → rejet
  (validator et/ou démarrage).
- Identifiant à la borne haute (63 caractères valides) → accepté ; 64 → rejet.

## Success Criteria
**How will we know when we're done?**

- [x] `TenantProperties` dans `com.flowforge.infrastructure.config`, bindé sur
      `app.tenant.id` (**requis**), exposé comme bean.
- [x] `TenantIdValidator` + `InvalidTenantIdException` dans
      `com.flowforge.infrastructure.tenant`.
- [x] Au démarrage, `TenantProperties.id()` passé dans `TenantIdValidator.validate`
      (échec si invalide).
- [x] Regex `^[a-z][a-z0-9_]{2,62}$` couverte par tests unitaires (cas OK + refus).
- [x] `application.yml` contient `app.tenant.id: tenant_dev` actif ; `context-path` et
      `connection-init-sql` **commentés** (non actifs).
- [x] Doc reverse-proxy (chemin + bloc statique par tenant) dans design et/ou
      implementation.
- [x] Fixtures workflow : plus de `tenant-a` ; usage de `tenant_a` (ou autre id valide).
- [x] `./mvnw test` vert ; ArchUnit inchangé / vert.
- [x] Docs requirements / design / testing alignés sur ce périmètre (tranche A).

**Performance benchmarks :** N/A — pas d’IO métier dans cette tranche.

## Constraints & Assumptions
**What limitations do we need to work within?**

### Technical constraints

- Java 25 LTS · Spring Boot 4.1.x · mono-module Maven (n° 27).
- Exclusions autoconfig JPA / Security / Flowable **conservées** (pas d’activation DB).
- Domaine reste sans dépendance Spring ; validator en **infrastructure** (pas domaine).
- Décisions n° 13 (instance dédiée) et n° 14 (routage par chemin) font foi.

### Business / product

- Isolation forte des données clients ; onboarding = opération de provisioning, pas
  un simple POST applicatif.

### Time / budget / rollout

- Livraison via branche `feature-multi-tenant` / PR ; pas de feature flag.
- Activation progressive : path + datasource dans les features infra suivantes
  (décommenter / activer yaml au moment voulu).

### Assumptions nommées

- `docs/specs/02-multi-tenant.md` fait foi pour le modèle cible complet.
- Le filtre OIDC et le bootstrap Liquibase seront livrés par
  `authentification` / persistence, en réutilisant `TenantProperties` et
  `TenantIdValidator`.
- Les adaptateurs futurs passeront `TenantProperties.id()` dans les commandes
  domaine (ex. `StartWorkflowCommand`) — règle documentée, non enforceée ici.
- Valeur locale par défaut `tenant_dev` (conforme regex) ; les tenants réels sont
  provisionnés hors app.
- Point 31 (outillage provisioning) et point 29 (statut migration plateforme)
  restent différés.
- L’environnement CI/agent peut tourner sous JDK 21 alors que le projet cible 25 —
  risque connu de compile ; les unitaires du validator + smoke config restent la
  preuve principale de cette feature.
- La validation regex au démarrage **complète** (ne remplace pas) la validation au
  provisioning (analyse § validation) — défense en profondeur locale.

## Questions & Open Items
**What do we still need to clarify?**

- Aucune question bloquante — phase 2 requirements **revue et acceptée**.
- **Différé :** filtre OIDC (`authentification`), bootstrap Liquibase / schéma,
  Flowable `tenantId`, outillage provisioning (point 31), observabilité migration
  (point 29), activation `context-path` / datasource.

## Alternatives brainstormées

| Approche | Trade-off | Verdict |
|---|---|---|
| A — Socle config + validator | Preuve du modèle sans JPA/auth | **Retenu** |
| B — Tranche runtime DB | Plus proche prod, active JPA tôt | Écarté pour V1 feature |
| C — Périmètre analyse complet | Mélange multi-tenant / auth / ops | Écarté |
| Path via profil Spring `tenant` | Plus propre pour activer plus tard | Écarté — commentaires yaml suffisent (tranche A) |
| Pas de validation regex au boot | Strictement « provisioning only » | Écarté — fail-fast local retenu |
| Validator en domaine | Pur Java métier | Écarté — contrainte ops/PostgreSQL |

## Clarifications actées

1. **Périmètre :** A — `TenantProperties`, `TenantIdValidator`, config documentée,
   tests validator, doc reverse-proxy ; sans PostgreSQL / Liquibase / filtre OIDC /
   Flowable.
2. **context-path :** `app.tenant.id` actif ; path et `connection-init-sql`
   **commentés** dans `application.yml`, non actifs.
3. **Domaine workflow :** pas de couplage runtime à `TenantProperties` ;
   fixtures `tenant-a` → `tenant_a`.
4. **Packages :** properties dans `infrastructure.config` ; validator +
   exception dans `infrastructure.tenant`.
5. **Binding :** `app.tenant.id` **requis** + validation regex au démarrage
   via `TenantIdValidator`.

## Review notes (phase 2)

| Point | Résolution |
|---|---|
| Conformité template README | Sections présentes + UX / perf / rollout / assumptions |
| `chemin / {tenantId}` (typo espace) | Corrigé → `/{tenantId}` |
| Ambiguïté « profil ou commentaires » | **Commentaires yaml** uniquement |
| Binding fail-fast ouvert | **Acté** — propriété requise |
| Validation regex runtime | **Acté** — au démarrage via validator |
| Alternatives revalidées | Tranche A / path commenté / validator infra / fail-fast boot |
| Design draft | À aligner (même clarifications) puis phase 3 |
| Contradiction domaine vs config | Aucune — domaine inchangé ; config = source de vérité instance |

**Lifecycle tranche A :** phases 1–9 terminées — prêt à merger.
