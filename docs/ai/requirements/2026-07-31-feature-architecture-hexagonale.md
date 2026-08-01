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

Sans fondation hexagonale stricte, le code métier FlowForge se couple aux frameworks
(Spring, JPA, Flowable, HTTP). Les dérives déjà constatées en revue (entités JPA dans le
domaine, conventions `usecase`/`Impl` non tenues — décisions n° 9 et 17) montrent que la
revue humaine seule ne suffit pas.

- **Affectés :** développeurs et agents qui implémentent les features métier ; mainteneurs
  qui doivent remplacer ou faire évoluer un adaptateur (PDF, Flowable, stockage) sans
  toucher au domaine.
- **Situation actuelle :** analyse technique complète dans `analysis doc/01-architecture-hexagonale.md` ;
  socle Maven mono-module et arborescence de packages déjà amorcés sous `com.monapp`
  (à renommer en `com.flowforge`). Aucune tranche verticale câblée ni preuve exécutable
  du pattern au-delà d’ArchUnit sur packages quasi vides.
- **UX :** N/A — fondation technique, pas d’interface utilisateur.

## Goals & Objectives
**What do we want to achieve?**

### Primary goals

- Isoler le domaine métier de toute technologie (règle de dépendance unidirectionnelle).
- Établir la structure de packages `com.flowforge.domain.*` / `com.flowforge.infrastructure.*`
  (contextes hors workflow = **packages + `package-info` uniquement**, pas de code métier).
- Garantir la règle « domaine pur » et la convention `*Impl` par ArchUnit en CI.
- Livrer une tranche verticale minimale **workflow** : `StartWorkflowUseCase` + ports +
  adaptateurs in-memory + `DomainConfig` Spring (sans Flowable ni PostgreSQL).
- Renommer le package racine Java/tests `com.monapp` → `com.flowforge`.

### Secondary goals

- Documenter le pattern de câblage (javadoc `DomainConfig` + notes dans le doc
  implementation) pour les features suivantes.
- Garder les use cases testables sans contexte Spring (ports mockés ou in-memory).

### Non-goals

- Contrôleurs REST, DTO HTTP et contrat d’API (point 24 — différé).
- `CompleteTaskUseCase` et autres use cases workflow — hors scope (seule la méthode
  modèle `complete()` sert aux tests de règle d’état).
- Intégration Flowable, JPA/PostgreSQL, PDF, MinIO, auth BFF, multi-tenant, frontend.
- Implémentation métier des contextes `dossier`, `document`, `connector`, `notification`
  (arborescence vide autorisée).
- Module Maven `domain` séparé (écarté par la décision n° 27).
- Réécriture de `analysis doc/` pour remplacer `com.monapp` (chore docs ultérieure).

## User Stories & Use Cases
**How will users interact with the solution?**

Cette feature est une fondation technique ; les « utilisateurs » sont les contributeurs
et les features aval.

- En tant que **développeur**, je veux un package `domain` sans dépendance framework afin
  de tester les règles métier sans Spring ni base.
- En tant que **développeur**, je veux des ports entrants/sortants explicites afin de
  brancher un adaptateur (in-memory aujourd’hui, JPA/Flowable demain) sans changer le use case.
- En tant que **mainteneur CI**, je veux qu’ArchUnit échoue si le domaine dépend de Spring,
  JPA, Flowable ou de `infrastructure`.
- En tant que **feature aval** (ex. workflow-engine), je veux réutiliser
  `StartWorkflowUseCase` et remplacer l’adaptateur in-memory par Flowable sans revisiter
  la structure hexa.

### Scénarios clés

1. Appel de `StartWorkflowUseCase.start(cmd)` → instance créée, persistée via port out,
   événement publié via port out (ordre : `save` puis `publish`).
2. Violation ArchUnit (dépendance framework dans `domain`) → build rouge.
3. Remplacement futur des adaptateurs in-memory par JPA/Flowable **dans les mêmes
   packages cibles** sans modification du use case.

### Edge cases

- Transition d’état invalide sur le modèle (`complete()` hors `RUNNING`) → exception domaine.
- Après introduction d’au moins une `*Impl`, `allowEmptyShould(true)` n’est plus requis
  sur la règle de naming `usecase`.

## Success Criteria
**How will we know when we're done?**

- [x] Package racine `com.flowforge` partout (sources, tests, ArchUnit) — plus de `com.monapp`.
- [x] Arborescence `domain/{workflow,dossier,document,connector,notification}` et
      `infrastructure/{web,persistence,flowable,pdf,storage,notification,connector,config}`
      présente (hors workflow : `package-info` suffit).
- [x] Tranche workflow livrée :
  - ports `port.in` / `port.out`
  - `StartWorkflowUseCaseImpl` + modèle minimal (`WorkflowInstance`, `WorkflowStatus`,
    `StartWorkflowCommand`, `WorkflowStartedEvent`)
  - `InMemoryWorkflowRepository` dans `infrastructure.persistence.workflow`
  - `InMemoryWorkflowEventPublisher` dans `infrastructure.flowable`
  - `DomainConfig` dans `infrastructure.config`
- [x] Tests unitaires du use case et du modèle **sans** `@SpringBootTest`.
- [x] `ArchitectureRulesTest` vert (3 règles) sur `com.flowforge`.
- [x] `./mvnw test` vert.
- [x] Pattern de câblage documenté (javadoc `DomainConfig` et/ou
      `docs/ai/implementation/2026-07-31-feature-architecture-hexagonale.md`).
- [x] Docs requirements / design / testing alignés sur ce périmètre.

**Performance benchmarks :** N/A (pas d’IO métier) — critère qualitatif : tests unitaires
domaine sans contexte Spring.

## Constraints & Assumptions
**What limitations do we need to work within?**

### Technical constraints

- Java 25 LTS · Spring Boot 4.1.x · build mono-module Maven (décision n° 27).
- Domaine en `record` + `@Builder` Lombok ; entités JPA distinctes (décision n° 17) —
  les JPA ne font pas partie de cette feature.
- ArchUnit = garantie unique de la règle de dépendance (pas de module `domain` séparé).
- Boot 4.1 : `spring-boot-starter-aspectj` (pas `starter-aop`) ; `spring-retry` versionné
  explicitement.

### Business / product

- Feature fondation : pas de valeur utilisateur finale directe ; bloque le démarrage
  sain des features métier.

### Time / budget / rollout

- Pas de contrainte calendaire imposée.
- Livraison via la branche `feature-architecture-hexagonale` / PR existante ; pas de
  feature flag (changement structurel).

### Assumptions nommées

- `analysis doc/01-architecture-hexagonale.md` fait foi pour les règles et le découpage
  package **logique** ; le code utilise `com.flowforge` (rename acté). La synchro textuelle
  `com.monapp` → `com.flowforge` dans `analysis doc/` est une chore docs séparée.
- Les adaptateurs in-memory occupent les **packages cibles** des futurs adaptateurs réels
  (`persistence.workflow`, `flowable`) pour un remplacement localisé.
- Les features suivantes remplacent les adaptateurs in-memory sans changer les ports.
- Point 24 (DTO REST) reste hors scope jusqu’à l’ajout d’adaptateurs web.
- Un test de contexte Spring pour `DomainConfig` est **souhaitable** mais non bloquant
  si les unitaires + ArchUnit + compilation suffisent (voir doc testing).

## Questions & Open Items
**What do we still need to clarify?**

- Aucune question bloquante — phase 2 requirements **revue et acceptée**.
- **Différé :** contrat d’API / DTO (point 24).
- **Différé :** modèles complets des autres contextes (leurs features).
- **Différé :** mise à jour lexicale `com.monapp` dans `analysis doc/`.
- **Lifecycle :** phases 1–9 terminées — prêt à merger.

## Review notes (phase 2)

| Point | Résolution |
|---|---|
| Packages hors workflow vs non-goal « pas d’impl » | Clarifié : arborescence + `package-info` seulement |
| Emplacement in-memory / `DomainConfig` | Figé dans les success criteria |
| `complete()` vs `CompleteTaskUseCase` | Use case hors scope ; méthode modèle pour tests d’état |
| UX / perf / rollout manquants au template | Ajoutés (N/A ou PR sans feature flag) |
| Divergence `analysis doc` `com.monapp` | Chore docs différée (assumption) |
| Alternatives revalidées | Socle seul / full 5 contextes / REST / Flowable réel — toujours écartés |
