---
phase: implementation
title: Implementation Guide
description: Technical implementation notes, patterns, and code guidelines
status: verified
updated: 2026-07-31
checked: 2026-07-31
---

# Implementation Guide

## Development Setup
**How do we get started?**

- JDK **25**, Maven Wrapper : `./mvnw test`
- Worktree : `.worktrees/feature-architecture-hexagonale`
- Aucune base / Keycloak / Flowable requis pour cette feature (exclusions autoconfig)

## Code Structure
**How is the code organized?**

```text
com.flowforge
├── FlowForgeApplication
├── domain.workflow
│   ├── model          # records + InvalidStateException
│   ├── port.in/out
│   └── usecase        # StartWorkflowUseCaseImpl
└── infrastructure
    ├── config         # DomainConfig
    ├── persistence.workflow  # InMemoryWorkflowRepository
    └── flowable              # InMemoryWorkflowEventPublisher
```

Autres contextes (`dossier`, …) : `package-info` seulement.

## Key Implementation Details

### DomainConfig pattern

`infrastructure.config.DomainConfig` déclare des `@Bean` :
`WorkflowRepository`, `WorkflowEventPublisher`, `StartWorkflowUseCase` (interface).
Le domaine reste sans annotation Spring.

### Autoconfig temporaire

`application.yml` exclut JDBC/JPA/Liquibase/Security/OAuth2/Mail et les
auto-config Flowable 8. À retirer feature par feature.

### Ordre use case

`create` → `save` → `publish` (pas de publish si save échoue).

## Changed files (phase 5)

- Rename `com.monapp` → `com.flowforge`
- Domaine workflow + ports + `StartWorkflowUseCaseImpl`
- Adaptateurs in-memory + `DomainConfig`
- Tests unitaires + `DomainConfigTest` + ArchUnit
- `application.yml` exclusions

## Design deviations

| Écart | Sévérité | Notes |
|---|---|---|
| Pas de `@Builder` Lombok sur les `record`s | Basse | Design : fabrique `create` = chemin nominal ; `@Builder` seulement autorisé |
| Smoke nommé `DomainConfigTest` (pas `*IT`) | Basse | Surefire n’exécute pas `*IT` par défaut |
| `allowEmptyShould(true)` gardé sur 2 règles domaine ArchUnit | Basse | Règle `usecase` sans empty-should ; les deux autres restent permissives sans impact |

Aucune déviation fonctionnelle vs design / requirements.

## Edge cases handled

- `WorkflowInstance.complete()` hors `RUNNING` → `InvalidStateException`
- Save repository en échec → pas de `publish`

## Phase 7 — Check Implementation

**Evidence :** `./mvnw test` → Tests run: 13, Failures: 0, BUILD SUCCESS (2026-07-31).
Lint feature OK. Aucun `com.monapp` résiduel. Domaine sans Spring/JPA/Flowable.

### Couverture design ↔ code

| Élément design | Statut |
|---|---|
| Package `com.flowforge` + arbre | OK |
| Modèle workflow (`record`s, exception, fabrique, `complete`) | OK |
| Ports in/out + `StartWorkflowUseCaseImpl` | OK |
| In-memory repo/publisher packages cibles | OK |
| `DomainConfig` beans | OK |
| Exclusions autoconfig | OK |
| ArchUnit 3 règles | OK |
| Tests sans Spring (modèle + use case) | OK |
| Doc pattern câblage | OK (javadoc + ce fichier) |

### Missing / follow-ups

- Remplacer in-memory par JPA/Flowable (features aval)
- Retirer exclusions autoconfig progressivement
- Sync lexicale `com.monapp` dans `analysis doc/` (chore)
- Jacoco / seuils (phase 8 optionnelle)

**Verdict phase 7 :** implementation **alignée** — prêt pour phase 8 (`dev-testing`) puis phase 9 (`dev-review`).
