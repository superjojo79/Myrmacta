---
phase: requirements
title: Requirements & Problem Understanding
description: Clarify the problem space, gather requirements, and define success criteria
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
  (à renommer). Aucune tranche verticale câblée ni preuve exécutable du pattern au-delà
  d’ArchUnit sur packages vides.

## Goals & Objectives
**What do we want to achieve?**

### Primary goals

- Isoler le domaine métier de toute technologie (règle de dépendance unidirectionnelle).
- Établir la structure de packages `com.flowforge.domain.*` / `com.flowforge.infrastructure.*`.
- Garantir la règle « domaine pur » et la convention `*Impl` par ArchUnit en CI.
- Livrer une tranche verticale minimale **workflow** : `StartWorkflowUseCase` + ports +
  adaptateurs in-memory + `DomainConfig` Spring (sans Flowable ni PostgreSQL).
- Renommer le package racine `com.monapp` → `com.flowforge`.

### Secondary goals

- Documenter le pattern de câblage pour les features suivantes (dossiers, documents, etc.).
- Garder les use cases testables sans contexte Spring (ports mockés ou in-memory).

### Non-goals

- Contrôleurs REST, DTO HTTP et contrat d’API (point 24 — différé).
- Intégration Flowable, JPA/PostgreSQL, PDF, MinIO, auth BFF, multi-tenant, frontend.
- Implémentation complète des cinq contextes métier (`dossier`, `document`, `connector`,
  `notification`, au-delà du stub workflow).
- Module Maven `domain` séparé (écarté par la décision n° 27).

## User Stories & Use Cases
**How will users interact with the solution?**

Cette feature est une fondation technique ; les « utilisateurs » sont surtout les
contributeurs et les features aval.

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
   événement publié via port out.
2. Violation ArchUnit (annotation Spring dans `domain`) → build rouge.
3. Remplacement futur de l’adaptateur in-memory par un adaptateur Flowable/JPA sans
   modification du use case.

### Edge cases

- Transition d’état invalide sur le modèle workflow (ex. `complete()` hors `RUNNING`) →
  exception domaine.
- Absence de classes `usecase` concrètes : règles ArchUnit avec `allowEmptyShould` retirée
  dès qu’au moins une `*Impl` existe.

## Success Criteria
**How will we know when we're done?**

- [ ] Package racine `com.flowforge` partout (sources, tests, ArchUnit).
- [ ] Structure `domain/{workflow,dossier,document,connector,notification}` et
      `infrastructure/{web,persistence,flowable,pdf,storage,notification,connector}` présente.
- [ ] Tranche workflow : ports in/out, `StartWorkflowUseCaseImpl`, modèle minimal
      (`WorkflowInstance` / statut), adaptateurs in-memory, `DomainConfig`.
- [ ] Tests unitaires du use case **sans** `@SpringBootTest` (ports mockés ou in-memory).
- [ ] `ArchitectureRulesTest` vert : domaine sans Spring/JPA/Flowable ; domaine ↛ infrastructure ;
      classes dans `usecase` suffixées `Impl`.
- [ ] `./mvnw test` vert.
- [ ] Docs requirements / design / testing alignés sur ce périmètre.

## Constraints & Assumptions
**What limitations do we need to work within?**

### Technical constraints

- Java 25 LTS · Spring Boot 4.1.x · build mono-module Maven (décision n° 27).
- Domaine en `record` + `@Builder` Lombok ; entités JPA distinctes (décision n° 17) —
  les JPA ne font pas partie de cette feature.
- ArchUnit = garantie unique de la règle de dépendance (pas de module `domain` séparé).

### Business / product

- Feature fondation : pas de valeur utilisateur finale directe ; bloque le démarrage
  sain des features métier.

### Assumptions nommées

- `analysis doc/01-architecture-hexagonale.md` fait foi pour les règles et le découpage,
  **sauf** le rename `com.monapp` → `com.flowforge`.
- Les features suivantes (dossiers, workflow-engine, etc.) remplacent les adaptateurs
  in-memory sans changer les ports.
- Point 24 (DTO REST) reste hors scope jusqu’à l’ajout d’adaptateurs web.

## Questions & Open Items
**What do we still need to clarify?**

- Aucune question bloquante pour clôturer la phase requirements.
- **Différé :** contrat d’API / DTO (point 24) — rouvrir à l’ajout du premier controller.
- **Différé :** contenu exact des modèles des autres contextes — porté par leurs features.
