---
phase: implementation
title: Implementation Guide
description: Technical implementation notes, patterns, and code guidelines
status: implemented
updated: 2026-07-31
---

# Implementation Guide

## Development Setup
**How do we get started?**

- JDK **25**, Maven Wrapper : `./mvnw test` / `./mvnw verify`
- Worktree : `.worktrees/feature-dossiers`
- Runtime PG : `DB_URL`, `DB_USERNAME`, `DB_PASSWORD`, `OIDC_*` ; schéma
  `app.tenant.id` **pré-existant**
- Tests : H2 `MODE=PostgreSQL` + schema `tenant_dev` (INIT) ;
  `OidcClientTestConfiguration`

## Code Structure
**How is the code organized?**

```text
com.flowforge.domain.dossier
├── model/                 # records, validator, transitions, exceptions
├── DossierAccessPolicy
├── port/in|out
└── usecase/*Impl

com.flowforge.infrastructure
├── config/DossierConfig   # beans policy + use cases
├── persistence/dossier/   # JPA entities, adapters, SequentialReferenceGenerator
└── web/dossier/           # controllers, DTOs, guards, ProblemDetail advice
```

## Key Implementation Details

### Persistence

- Liquibase : `db/changelog/db.changelog-master.yaml` → `001-dossier-tables`
- JSON via Hibernate `SqlTypes.JSON` ; colonnes Liquibase compatibles H2
- `@Version` sur `DossierJpaEntity`
- Références : `SequentialReferenceGenerator` (JdbcTemplate) —
  insert + ignore duplicate → update +1 → select ; format
  `DOS-{TYPE}-{YYYY}-{seq:06d}` UTC
- `ddl-auto: none` ; runtime `SET search_path TO ${app.tenant.id}, public`

### Authz

- Gate `TENANT_ADMIN` : `TenantAdminGuard` (web) + défense dans use cases admin
- ACL type : `DossierAccessPolicy` (`EDIT` ⇒ `VIEW` ; System bypass ; Service refus)
- `AccessContextFactory` : OIDC `sub` + rôles sans préfixe `ROLE_`

### API

| Méthode | Path |
|---|---|
| POST/PATCH/GET | `/api/dossier-types` (+ `/{code}`) |
| POST/DELETE | `/api/dossier-types/{code}/acl` |
| POST/GET/PATCH | `/api/dossiers`, `?ref=`, `/{id}/status` |

Erreurs : `DossierExceptionHandler` → RFC 7807 `ProblemDetail`
(400 `errors[]`, 403, 404, 409).

### Écarts H2 vs PostgreSQL

- Compteur de référence portable (pas de `RETURNING` / `ON CONFLICT`) pour H2
- Schema via `INIT=CREATE SCHEMA…SET SCHEMA` en tests (pas de search_path Hikari)
- DBs H2 isolées par `${random.uuid}` pour paralléliser les contextes Spring

## Verification Evidence

```text
./mvnw -B verify
Tests run: 113, Failures: 0, Errors: 0
Jacoco: All coverage checks have been met (ligne ~83 %)
```

## Design deviations

- Aucune déviation fonctionnelle vs design reviewed
- Seq SQL volontairement portable (design citait `RETURNING` PG) — comportement
  équivalent atomique en transaction

## Follow-ups

- Smoke PG réel (hors CI)
- Lots suivants : éléments, relations, workflow links, PATCH fields, pagination
