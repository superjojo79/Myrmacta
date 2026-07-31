---
phase: implementation
title: Implementation Guide
description: Technical implementation notes, patterns, and code guidelines
status: in-progress
updated: 2026-07-31
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

- Aucune déviation majeure.
- Smoke Spring nommé `DomainConfigTest` (Surefire `*IT` non exécuté par défaut).

## Edge cases handled

- `WorkflowInstance.complete()` hors `RUNNING` → `InvalidStateException`
- Save repository en échec → pas de `publish`

## Follow-ups

- Remplacer in-memory par JPA/Flowable (features aval)
- Retirer exclusions autoconfig progressivement
- Sync lexicale `com.monapp` dans `analysis doc/` (chore)
