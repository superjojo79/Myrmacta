---
phase: implementation
title: Implementation Guide
description: Technical implementation notes, patterns, and code guidelines
status: checked
checked: 2026-07-31
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
- JSON via Hibernate `SqlTypes.JSON` ; colonnes Liquibase `json` (H2-friendly)
- `@Version` sur `DossierJpaEntity`
- Références : `SequentialReferenceGenerator` (JdbcTemplate) —
  insert + ignore duplicate → update +1 → select ; format
  `DOS-{TYPE}-{YYYY}-{seq:06d}` UTC
- `ddl-auto: none` ; runtime `SET search_path TO ${app.tenant.id}, public`

### Authz

- Gate `TENANT_ADMIN` : `TenantAdminGuard` (web) + défense dans use cases admin
- ACL type : `DossierAccessPolicy` (`EDIT` ⇒ `VIEW` ; System bypass ; Service refus)
- `AccessContextFactory` : OIDC `sub` + rôles sans préfixe `ROLE_`
- ACL grant body : `{ "subjectUserId", "permission" }`

### API

| Méthode | Path |
|---|---|
| POST/PATCH/GET | `/api/dossier-types` (+ `/{code}`) |
| POST/DELETE | `/api/dossier-types/{code}/acl` |
| POST/GET/PATCH | `/api/dossiers`, `?ref=`, `/{id}/status` |

Erreurs : `DossierExceptionHandler` → RFC 7807 `ProblemDetail`
(400 `errors[]`, 403, 404, 409 dont OptimisticLock /
`ObjectOptimisticLockingFailureException`).

### Écarts H2 vs PostgreSQL

- Compteur de référence portable (pas de `RETURNING` / `ON CONFLICT`) pour H2
- Schema via `INIT=CREATE SCHEMA…SET SCHEMA` en tests (pas de search_path Hikari)
- DBs H2 isolées par `${random.uuid}` pour paralléliser les contextes Spring

## Verification Evidence

```text
./mvnw -B verify   # phase 7
Tests run: 116, Failures: 0, Errors: 0
Jacoco: All coverage checks have been met (ligne ~83 %)
```

## Design deviations

| Sévérité | Écart | Traitement |
|---|---|---|
| High | OptimisticLock → 409 manquant | **Corrigé** en phase 7 |
| Med | ACL JSON `userId` vs `subjectUserId` | **Corrigé** → `subjectUserId` |
| Med | Index `(tenant_id, dossier_type_code)` + CHECK permission absents Liquibase | Follow-up bas (unicité/ACL déjà OK) |
| Low | JSON `json` vs design `jsonb` | Accepté (compat H2) |
| Low | Seq sans `RETURNING` | Accepté (portable, transactionnel) |

## Phase 7 — Check Implementation notes

| Exigence design/requirements | Statut |
|---|---|
| Domaine pur + records + validator + policy + transitions | OK |
| Ports / `*Impl` / ArchUnit | OK |
| Create DRAFT + tenantId instance + label + inactive 409 | OK |
| Référence UTC format | OK |
| JPA + Liquibase 4 tables + exclusions Flowable/Mail | OK |
| Surface API complète | OK |
| TenantAdminGuard sans method security | OK |
| ProblemDetail 400/403/404/409 (+ OLS) | OK |
| expectedVersion + `@Version` | OK |

Aucun gap bloquant restant pour la tranche B.

## Follow-ups

- Changelog Liquibase : index secondaire + CHECK permission (nice-to-have)
- Smoke PG réel (hors CI)
- Lots suivants : éléments, relations, workflow links, PATCH fields, pagination

**Prochaine phase :** `dev-testing` (phase 8).
