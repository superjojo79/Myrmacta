---
phase: requirements
title: Requirements & Problem Understanding
description: Clarify the problem space, gather requirements, and define success criteria
status: reviewed
created: 2026-08-22
updated: 2026-08-22
feature: fiabilisation-tests-plateforme
phase1: 2026-08-22
phase2: 2026-08-22
stakeholder_decisions: 2026-08-22
todo_ref: TODO.md §28, §26 (reste), §37 · FILE-ATTENTE.md (priorité 1)
---

# Requirements & Problem Understanding — `fiabilisation-tests-plateforme`

> Requirements **reviewed** (Phase 2), 6 décisions actées, aucun point
> ouvert bloquant.
>
> **Mise à jour 2026-10-02 :** D-TESTCONTAINERS-EXEC et la partie PostgreSQL de D-TESTCONTAINERS sont
> **remplacées** par [zonky-embedded-postgres](2026-10-02-feature-zonky-embedded-postgres.md) (PostgreSQL
> embarqué, sans Docker ; les tests concernés s'exécutent désormais).

## Problem Statement

TODO §28 (« Stratégie de test ») liste plusieurs garanties de fiabilité
documentées par les analysis docs mais jamais rendues exécutables en
test : étanchéité du `search_path` par tenant, dimensionnement des
verrous Flowable en charge, migration Liquibase multi-schéma avec échec
partiel, convention `port/in`/`usecase` (jamais vérifiée par ArchUnit),
et seuils de couverture jamais tranchés.

**Vérifié par lecture de code (2026-08-22), avant de lister ce qui reste
réellement à faire :**

- Les tests tournent aujourd'hui sur **H2 en mode compatibilité
  PostgreSQL** (`spring.datasource.url=jdbc:h2:mem:...MODE=PostgreSQL`),
  jamais sur un vrai PostgreSQL. L'étanchéité réelle du `search_path`
  par tenant et la concurrence multi-pods sur la séquence de référence
  dossier (TODO §26) ne sont donc vérifiables par **aucun test actuel**.
- Les tests du relais outbox (`OutboxRelaySchedulerTest`,
  `OutboxRelayIT`) couvrent déjà largement rejeu/idempotence/crash
  implicite (dédoublonnage par clé métier, échec transitoire, DLQ sur
  collision, épuisement des tentatives) — **pas un vrai trou**, TODO §28
  était en avance sur l'état réel du code.
- TODO §26 (« à décider ») cite deux points **déjà résolus dans le
  code** : verrouillage optimiste `@Version` déjà présent sur
  `Dossier` (`DossierJpaEntity.java:54-56`) ; le bug allégué
  (`Instant` ne supporte pas `%tY`) **n'existe plus** dans
  `SequentialReferenceGenerator.java` (utilise
  `clock.instant().atZone(ZoneOffset.UTC).getYear()`, un `int`, pas de
  conversion `%tY`). Seul le 3ᵉ point (mécanisme de séquence non
  vérifié sous charge multi-pods réelle) reste ouvert.
- `ArchitectureRulesTest` a 3 règles (pureté du domaine ×2, convention
  de nommage `*Impl`) mais **aucune** ne vérifie que les classes
  `..usecase..*Impl` implémentent bien une interface `..port.in..` —
  convention documentée dans `01-architecture-hexagonale.md`, jamais
  exécutable.
- Aucun test dédié à `ResolveDocumentTemplateUseCaseImpl` (résolution en
  cascade des templates, `08-templates-documents.md` §« Résolution en
  cascade ») — trou confirmé.
- Aucun test de dimensionnement de verrous Flowable sous charge, aucun
  test de migration Liquibase multi-schéma avec échec partiel — trous
  confirmés.
- La règle « un seul workflow actif » (`WorkflowAlreadyRunningException`)
  est testée uniquement de façon séquentielle/mockée
  (`AttachWorkflowUseCaseImplTest`, `WorkflowPersistenceTest`), jamais
  sous parallélisme réel (deux threads réservant simultanément) — trou
  confirmé.
- Docker n'est **pas accessible** dans ce bac à sable de développement
  (`/var/run/docker.sock` absent) — GitHub Actions (`ubuntu-latest`) a
  Docker nativement, donc les tests Testcontainers y tourneraient, mais
  ne peuvent pas être exécutés/vérifiés localement dans cette session.

## Goals & Objectives

### Primary goals

- Introduire **Testcontainers PostgreSQL**, adoption **ciblée** (pas de
  remplacement de H2 pour le reste de la suite) : un test prouvant
  l'étanchéité du `search_path` par tenant sous un vrai PostgreSQL.
- Smoke test PostgreSQL réel prouvant que `SequentialReferenceGenerator`
  reste correct sous accès concurrent multi-pods (clôt TODO §26 reste).
- Test de dimensionnement des verrous Flowable sous charge (pas
  nécessairement dépendant de Testcontainers — à confirmer en design).
- Test de migration Liquibase multi-schéma avec échec partiel.
- Règle ArchUnit : `..usecase..*Impl` doit implémenter une interface de
  `..port.in..`.
- Test dédié de résolution en cascade des templates
  (`ResolveDocumentTemplateUseCaseImpl`).
- Test de la règle « un seul workflow actif » sous parallélisme réel
  (threads concurrents).
- TODO §37 : aligner `findClosedForRetention` sur le pattern `tenantId`
  explicite existant.
- Relever les seuils Jacoco : LINE 85%, ajout d'un seuil BRANCH 75%
  (niveau BUNDLE, comme aujourd'hui).
- Corriger TODO.md §26 (narrower : 2 points déjà résolus, 1 point
  reste) et clore §28/§37 après implémentation.

### Secondary goals

- Documenter explicitement, dans le testing doc, quels tests
  Testcontainers n'ont pas de preuve d'exécution locale (contrainte
  d'environnement) vs quels tests ont été réellement exécutés et
  vérifiés dans cette session.

### Non-goals (actés)

- **Pas de remplacement de H2 par Testcontainers pour l'ensemble de la
  suite** — seuls les 2 tests qui en ont structurellement besoin
  (isolation `search_path`, séquence multi-pods) l'utilisent.
- **Pas de changement du mécanisme `@Version`** sur `Dossier` — déjà
  correct, hors périmètre (seule la preuve sous charge de la séquence
  de référence est en périmètre, pas la locking strategy elle-même).
- **Pas de nouveau pipeline CI** — les tests Testcontainers sont écrits
  pour tourner en CI (Docker natif sur `ubuntu-latest`) mais aucune
  modification du workflow GitHub Actions n'est faite dans ce lot (le
  crédit CI est actuellement épuisé, non abordé ici).
- **Pas d'extension du seuil de couverture par fichier/diff** — seuils
  BUNDLE globaux uniquement, pas d'outil de diff-coverage.

## User Stories & Use Cases

1. **En tant que** mainteneur, **je veux** qu'un test prouve que
   `search_path` isole réellement les tenants sous un vrai PostgreSQL,
   **afin de** ne pas découvrir une fuite inter-tenant en production
   suite à une évolution du postulat « une instance = un tenant ».
2. **En tant que** mainteneur, **je veux** un smoke test multi-pods sur
   la séquence de référence dossier, **afin de** avoir la preuve que
   l'incrémentation atomique (`INSERT` puis `UPDATE` transactionnel)
   résiste à la concurrence réelle, pas seulement en théorie.
3. **En tant que** mainteneur, **je veux** que la convention
   `port/in`/`usecase` soit vérifiée automatiquement par ArchUnit,
   **afin de** empêcher une dérive future sans revue manuelle.
4. **En tant que** mainteneur, **je veux** un test dédié de la
   résolution en cascade des templates, **afin de** ne pas casser ce
   comportement documenté sans qu'un test ne le signale.
5. **En tant que** mainteneur, **je veux** que la règle « un seul
   workflow actif » soit prouvée sous parallélisme réel, **afin de**
   avoir confiance dans la garantie au-delà d'un test séquentiel mocké.

### Edge cases

- Testcontainers PostgreSQL : le conteneur doit démarrer/s'arrêter
  proprement sans polluer les autres tests (isolation de schéma par
  test ou réutilisation contrôlée du conteneur — à trancher en design).
- Smoke test séquence multi-pods : doit vérifier l'absence de doublon
  ET l'absence de trou dans la séquence sous accès concurrent réel.
- Migration Liquibase partielle : doit vérifier qu'un tenant en échec
  ne corrompt pas l'état des autres tenants déjà migrés.
- Tests Testcontainers non exécutables dans ce bac à sable : rouge
  confirmé par compilation quand possible (TDD), mais pas d'assertion
  d'exécution verte locale — à documenter explicitement, pas à
  camoufler en « vert » non prouvé.

## Success Criteria

- [ ] Test Testcontainers PostgreSQL prouvant l'isolation `search_path`
      (écrit, compile ; exécution prouvée seulement si Docker devient
      disponible dans cette session, sinon marqué explicitement).
- [ ] Smoke test multi-pods sur `SequentialReferenceGenerator` (même
      contrainte d'exécution).
- [ ] Test de dimensionnement des verrous Flowable sous charge — exécuté
      et vert (ne dépend pas de Testcontainers).
- [ ] Test de migration Liquibase multi-schéma avec échec partiel —
      exécuté et vert (ne dépend pas nécessairement de Testcontainers —
      à confirmer en design selon le mécanisme Liquibase multi-schéma
      actuel).
- [ ] Règle ArchUnit `usecase.*Impl` → `port.in` — exécutée et verte,
      ne casse aucune classe existante.
- [ ] Test dédié `ResolveDocumentTemplateUseCaseImpl` (cascade) —
      exécuté et vert.
- [ ] Test « un seul workflow actif » sous parallélisme réel — exécuté
      et vert.
- [ ] `findClosedForRetention` avec `tenantId` explicite (§37) —
      exécuté et vert, non-régression sur le job de rétention existant.
- [ ] Seuils Jacoco relevés (LINE 85%, BRANCH 75%, BUNDLE) — `./mvnw
      verify` toujours vert après ajout des nouveaux tests.
- [ ] TODO.md §26/§28/§37 mis à jour ; FILE-ATTENTE.md daté après
      implémentation.

## Constraints & Assumptions

**Contraintes stables :**

- Docker inaccessible dans ce bac à sable — tous les tests Testcontainers
  écrits doivent être clairement étiquetés quant à leur statut
  d'exécution réel (pas de fausse preuve de vert).
- H2 reste le datasource par défaut du reste de la suite (986 tests
  existants) — aucune régression de temps de build acceptable en dehors
  des 2 tests Testcontainers ciblés.
- Aucune modification du pipeline CI GitHub Actions dans ce lot.

**Décisions produit (Phase 1+2, toutes actées 2026-08-22) :**

| Id | Sujet | Choix |
|---|---|---|
| **D-SCOPE** | Découpage du travail | Une seule feature, tous les sous-lots en milestones |
| **D-TESTCONTAINERS** | Adoption Testcontainers | Ciblée — uniquement isolation `search_path` + séquence multi-pods |
| **D-TESTCONTAINERS-EXEC** | Preuve d'exécution | Tests écrits (TDD si possible), étiquetés explicitement « non exécutés ici » (contrainte Docker), pas de preuve de vert fabriquée |
| **D-COVERAGE** | Seuils Jacoco | LINE 85%, BRANCH 75%, niveau BUNDLE |
| **D-TENANT-ID-37** | `findClosedForRetention` | Aligné sur le pattern `tenantId` explicite existant |
| **D-ARCHUNIT-RULE** | Règle ArchUnit `usecase`/`port.in` | `..usecase..*Impl` doit implémenter une interface de `..port.in..` |

## Questions & Open Items

### Clos

| Id | Décision | Choix |
|---|---|---|
| **D-SCOPE** | Découpage | Une feature (2026-08-22) |
| **D-TESTCONTAINERS** | Adoption | Ciblée (2026-08-22) |
| **D-TESTCONTAINERS-EXEC** | Preuve d'exécution | Étiquetage explicite, pas de vert fabriqué (2026-08-22) |
| **D-COVERAGE** | Seuils | LINE 85% / BRANCH 75% (2026-08-22) |
| **D-TENANT-ID-37** | §37 | Alignement `tenantId` (2026-08-22) |
| **D-ARCHUNIT-RULE** | Règle ArchUnit | `usecase.*Impl` → `port.in` (2026-08-22) |

### Ouvertes

Aucune décision produit ouverte. Les 4 points techniques listés en
Phase 2 (mécanisme, pas périmètre) ont été tranchés en Phase 3 — voir
`docs/ai/design/2026-08-22-feature-fiabilisation-tests-plateforme.md`,
section « Design Decisions » : `D-FLOWABLE-LOCK-TEST` (H2 + threads
réels), `D-PARALLELISM-TEST` (H2 + threads réels), `D-LIQUIBASE-TEST`
(échec partiel mono-schéma + reprise idempotente — la « migration
multi-schéma » envisagée initialement ne correspond pas à l'architecture
réelle, un schéma par instance), `D-CONTAINER-REUSE` (conteneur
Testcontainers singleton statique partagé).

### Hors file

Remplacement complet de H2 par Testcontainers ; changement du mécanisme
`@Version` ; nouveau pipeline CI ; diff-coverage par fichier.

## Phase 2 — revue close (2026-08-22)

Template README : OK. **Validé :** 6 décisions produit (D-SCOPE,
D-TESTCONTAINERS, D-TESTCONTAINERS-EXEC, D-COVERAGE, D-TENANT-ID-37,
D-ARCHUNIT-RULE) ; contrainte Docker du bac à sable identifiée et
actée avant tout travail d'implémentation (pas découverte en cours de
route) ; 3 corrections factuelles sur l'état du code trouvées et
vérifiées (locking déjà en place, bug `Instant` inexistant, couverture
outbox déjà substantielle) — évitent de refaire un travail déjà fait.

Status : **`reviewed`**. Prochaine phase : `dev-design` (Phase 3).
