---
phase: implementation
title: Implementation Guide
description: Technical implementation notes, patterns, and code guidelines
status: draft
created: 2026-08-03
updated: 2026-08-03
phase5: 2026-08-03
phase6: 2026-08-03
---

# Implementation Guide

## Development Setup

- Worktree `.worktrees/feature-templates-documents`
- JDK 25 — `JAVA_HOME=/home/ubuntu/java/jdk-25.0.4+7`
- `spring-boot-starter-thymeleaf` déjà au pom (SpringTemplateEngine / SpEL)

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

### Checker

- `SpringTemplateEngine` + `TemplateManager.parseString` (syntaxe only)
- Markup invalide type `<th:block …` non fermé → `InvalidTemplateSyntaxException`

### API

- `/api/document-templates` + path littéral `/resolve` avant `{id}` UUID
- Liste : `htmlContent` vide sauf `?includeContent=true`
- Authz `@PreAuthorize("hasRole('TENANT_ADMIN')")` + CSRF mutateurs

## Changed files (phase 5)

- Domaine models/ports/use cases + `DocumentTemplateSupport`
- Liquibase 004 + master include
- JPA entity/adapter/Spring Data
- Checker, DocumentConfig, web layer
- Tests : Models, Ports, SchemaIT, PersistenceIT, Checker, UseCases, ApiTest

## Design deviations

| Item | Note |
|---|---|
| DocumentTemplateSupport hors `usecase` | ArchUnit `*Impl` naming |
| Checker via SpringTemplateEngine | OGNL absent du classpath Boot 4 |

## Edge cases handled

- blank variant/locale → null
- activate syntax KO → 400, reste DRAFT
- archive auto de l’ACTIVE précédente
- DataIntegrityViolation → 409
- list strip content

## Evidence (T9)

```text
JAVA_HOME=…/jdk-25.0.4+7 ./mvnw verify
→ Tests run: 315, Failures: 0, Errors: 0
→ Jacoco INSTRUCTION 11011/12569 = 87.60%
→ npx ai-devkit lint --feature templates-documents → All checks passed
→ task CLI : unavailable
```

## Non-goals / deferred

- PDF / Flying Saucer / Flowable ServiceTask → `generation-documents`
- `@Version` JPA / pagination / OpenAPI
