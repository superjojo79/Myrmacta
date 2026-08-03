---
phase: implementation
title: Implementation Guide
description: Technical implementation notes, patterns, and code guidelines
status: reviewed
created: 2026-08-03
updated: 2026-08-03
phase5: 2026-08-03
phase6: 2026-08-03
phase7: 2026-08-03
checked: 2026-08-03
phase7_result: aligned
phase9: 2026-08-03
reviewed: 2026-08-03
---

# Implementation Guide

## Development Setup

- Worktree `.worktrees/feature-templates-documents`
- JDK 25 — `JAVA_HOME=/home/ubuntu/java/jdk-25.0.4+7`
- `spring-boot-starter-thymeleaf` déjà au pom (`SpringTemplateEngine` / SpEL)

## Code Structure

```
domain/document/model|port|usecase/ + DocumentTemplateSupport
infrastructure/persistence/document/   JPA + ranked native query
infrastructure/document/               ThymeleafTemplateSyntaxChecker
infrastructure/web/document/           Controller + ExceptionHandler + DTOs
infrastructure/config/DocumentConfig.java
db/changelog/changes/004-document-template-tables.yaml
```

## Implementation Notes

### Persistence

- Table `document_template` ; PG index partiel ACTIVE + unique version (COALESCE)
- H2 : colonnes générées `active_*` / `ver_*` (pattern 002/003)
- `findBestActive` : native SQL ranked scores 1–4, `FETCH FIRST 1 ROW ONLY`
- `findActiveExact` : JPQL null-safe (`IS NULL` pairings) — pas de query dérivée Spring Data

### Activate (DD-ACTIVATE-TX)

- Domaine : checker → archive ACTIVE exacte → save ACTIVE
- Infra : bean `ActivateDocumentTemplateUseCase` wrappé dans `TransactionTemplate`
  (`DocumentConfig`) pour atomicité archive+activate

### Checker

- `SpringTemplateEngine` + `TemplateManager.parseString` (syntaxe only)
- Markup invalide type `<th:block …` non fermé → `InvalidTemplateSyntaxException`

### API

- `/api/document-templates` + path littéral `/resolve` avant `{id}` UUID
- Liste : `htmlContent` vide sauf `?includeContent=true`
- Authz `@PreAuthorize("hasRole('TENANT_ADMIN')")` + CSRF mutateurs
- Pas d’endpoint DELETE

## Changed files (phase 5)

- Domaine models/ports/use cases + `DocumentTemplateSupport`
- Liquibase 004 + master include
- JPA entity/adapter/Spring Data
- Checker, DocumentConfig, web layer
- Tests : Models, Ports, SchemaIT, PersistenceIT, Checker, UseCases, ApiTest

## Phase 7 fixes

| Fix | Fichiers |
|---|---|
| TX activate (DD-ACTIVATE-TX) | `DocumentConfig` (`TransactionTemplate`) |
| `findActiveExact` null-safe | `SpringDataDocumentTemplateRepository`, adapter |
| IT activate + null defaults | `DocumentTemplatePersistenceIT` |

## Design deviations (acceptées, sévérité basse)

| Item | Note |
|---|---|
| `DocumentTemplateSupport` hors `usecase` | ArchUnit `*Impl` naming — helpers partagés |
| Checker via `SpringTemplateEngine` | OGNL absent du classpath Boot 4 ; parse syntaxe OK |
| Nom `ResolveDocumentTemplateUseCase` vs design `TemplateResolver` | Même port in / comportement ranked ; alias doc only |
| TX via décorateur config, pas `@Transactional` sur `*Impl` | Domaine pur ; atomicité garantie au wiring Spring |

## Requirements / design coverage

| Décision | Impl |
|---|---|
| D-SCOPE CRUD + resolve | Use cases + controller |
| D-MODEL / D-NULL / A7 | Record + blank→null |
| D-VERSION / D-MULTI-DRAFT / D-EDIT | Lifecycle use cases |
| D-RESOLVE cascade | `findBestActive` ranked 1–4 |
| D-RESOLVE-API / PATH | `GET /resolve` littéral |
| D-ADMIN | `@PreAuthorize` TENANT_ADMIN |
| D-DELETE ARCHIVED | Pas soft-delete / DELETE HTTP |
| D-UQ | Liquibase PG partial + H2 generated |
| D-VALIDATE parse activate | `TemplateSyntaxChecker` sur activate |
| D-LIST includeContent | Query param + strip |
| A1 5 MiB | `DocumentConfig.MAX_HTML_BYTES` |
| DD-ACTIVATE-TX | `TransactionTemplate` wrapper |
| DD-NO-OPTLOCK | Pas de `@Version` JPA |
| DD-THYMELEAF-SCOPE | parseString, pas de vars métier |

## Edge cases handled

- blank variant/locale → null
- activate syntax KO → 400, reste DRAFT
- archive auto de l’ACTIVE précédente (y compris null/null) en TX
- DataIntegrityViolation → 409 sanitizé (pas de SQL brut)
- list strip content
- resolve 404 si aucun ACTIVE

## Phase 9 — Code review

### Checklist

| Critère | Verdict |
|---|---|
| Design match (lifecycle, ranked resolve, dual UQ, checker, TX) | OK |
| Security (TENANT_ADMIN, CSRF, tenant hors body) | OK |
| Logic gaps bloquants | Aucun |
| Integration (Liquibase 004, ArchUnit, BFF) | OK |
| Tests / Jacoco ≥ 80 % | OK |
| Docs AI à jour | OK |

### Findings

| Sévérité | Item | Disposition |
|---|---|---|
| Blocking | — | Aucun |
| Important | 409 DataIntegrity fuyait le SQL | **Corrigé** — messages stables (pattern connecteurs) |
| Important | `requestTenantId` param ignoré (tenant injecté) | Follow-up — pas d’IDOR (controller = `TenantProperties`) |
| Important | parse Thymeleaf dans la TX activate | Follow-up — atomicité OK ; optimiser hors TX plus tard |
| Nice | liste renvoie `htmlContent: ""` | Accepté V1 |
| Nice | beans `*Impl` vs interface activate | Accepté V1 |

### Evidence (phase 9)

```text
JAVA_HOME=…/jdk-25.0.4+7 ./mvnw -q verify
→ exit 0
→ Surefire: tests=331, failures=0, errors=0, skipped=0
→ Jacoco INSTRUCTION 11080/12635 = 87.69%
→ DocumentTemplateExceptionHandlerTest: 3 tests
→ npx ai-devkit lint --feature templates-documents → All checks passed
→ task CLI : unavailable
→ Verdict: ready to merge
```

## Follow-ups (non-bloquants)

- Assert `requestTenantId == tenantId` (ou retirer le paramètre redondant)
- Parse Thymeleaf hors `TransactionTemplate`, TX uniquement archive+activate
- `@Version` / ETag si besoin concurrence éditoriale
- PDF / Flying Saucer / Flowable ServiceTask → `generation-documents`
- OpenAPI / pagination
- Stress activate multi-thread (hors V1 ; unique SQL couvre la race)

## Non-goals / deferred

- PDF / Flying Saucer / Flowable ServiceTask → `generation-documents`
- `@Version` JPA / pagination / OpenAPI
