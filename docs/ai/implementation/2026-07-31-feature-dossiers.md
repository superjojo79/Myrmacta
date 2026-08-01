---
phase: implementation
title: Implementation Guide
description: Technical implementation notes, patterns, and code guidelines
status: reviewed
checked: 2026-07-31
reviewed: 2026-08-01
updated: 2026-08-01
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
- `DossierRepository.save` retourne le `Dossier` post-flush (`saveAndFlush`) pour
  exposer la `version` incrémentée aux clients
- Références : `SequentialReferenceGenerator` (JdbcTemplate) —
  insert + ignore duplicate → update +1 → select ; format
  `DOS-{TYPE}-{YYYY}-{seq:06d}` UTC
- `ddl-auto: none` ; runtime `SET search_path TO ${app.tenant.id}, public`

### Authz

- Gate `TENANT_ADMIN` : `TenantAdminGuard` (web) + défense dans use cases admin
- ACL type : `DossierAccessPolicy` (`EDIT` ⇒ `VIEW` ; System bypass ; Service refus)
- `AccessContextFactory` : OIDC `sub` + rôles sans préfixe `ROLE_`
- ACL grant body : `{ "subjectUserId", "permission" }`
- Grant ACL : type inexistant → 404 (vérif `DossierTypeRepository`)

### API

| Méthode | Path |
|---|---|
| POST/PATCH/GET | `/api/dossier-types` (+ `/{code}`) |
| POST/DELETE | `/api/dossier-types/{code}/acl` |
| POST/GET/PATCH | `/api/dossiers`, `?ref=`, `/{id}/status` |

Erreurs : `DossierExceptionHandler` → RFC 7807 `ProblemDetail`
(400 `errors[]`, 403, 404, 409 dont OptimisticLock /
`ObjectOptimisticLockingFailureException` / `DataIntegrityViolationException`
avec message sanitizé).

### Écarts H2 vs PostgreSQL

- Compteur de référence portable (pas de `RETURNING` / `ON CONFLICT`) pour H2
- Schema via `INIT=CREATE SCHEMA…SET SCHEMA` en tests (pas de search_path Hikari)
- DBs H2 isolées par `${random.uuid}` pour paralléliser les contextes Spring

## Verification Evidence

```text
./mvnw -B verify   # phase 9
Tests run: 139, Failures: 0, Errors: 0
Jacoco: All coverage checks have been met (ligne ~95 %)
```

## Design deviations

| Sévérité | Écart | Traitement |
|---|---|---|
| High | OptimisticLock → 409 manquant | **Corrigé** en phase 7 |
| High | `save` void → `version` stale dans réponse API | **Corrigé** phase 9 (`Dossier` + `saveAndFlush`) |
| Med | ACL JSON `userId` vs `subjectUserId` | **Corrigé** → `subjectUserId` |
| Med | Grant ACL sans vérif type | **Corrigé** phase 9 → 404 |
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

## Review notes (phase 9)

| Point | Verdict |
|---|---|
| Design match | PASS |
| Logic gaps | PASS (version post-save + grant type 404) |
| Security | PASS (`TENANT_ADMIN` gate, ACL, CSRF BFF, messages 409 sanitizés) |
| Integration | PASS (Liquibase H2, API MockMvc, ArchUnit) |
| Tests | PASS — **139** tests ; Jacoco lignes **~95 %** |
| Docs | PASS (deploy drafted ; monitoring deferred / point 29) |
| Bloquants | **Aucun** |
| Prêt à merger | **OUI** |

### Findings phase 9

1. **Corrigé (bloquant) :** `DossierRepository.save` renvoyait `void` ; les use cases
   retournaient l’agrégat pré-persist → `version` API stale → faux 409 client.
   Fix : `save` → `Dossier` + `saveAndFlush` dans l’adapter JPA.
2. **Corrigé :** grant ACL sur type inconnu → 404.
3. **Corrigé :** `DataIntegrityViolationException` → 409 message générique ;
   rollback Liquibase explicite sur `dossier_reference_seq`.
4. **Suivi bas :** index Liquibase `(tenant_id, dossier_type_code)` + CHECK
   permission ; `Long version` / `Persistable` pour `isNew()` ; smoke PG réel.

## Evidence (phase 9)

| Commande | Résultat |
|---|---|
| `npx ai-devkit@latest lint --feature dossiers` | All checks passed |
| `./mvnw -B verify` | **139** tests, 0 failure ; Jacoco check OK (~95 % lignes) |
| Revue holistique | Aucun bloquant ; prêt à merger |

## Follow-ups

- Changelog Liquibase : index secondaire + CHECK permission (nice-to-have)
- Smoke PG réel (hors CI)
- Lots suivants : éléments, relations, workflow links, PATCH fields, pagination
- Observabilité dossiers (TODO 29) — feature monitoring

**Lifecycle tranche B :** prêt à merger (PR).
