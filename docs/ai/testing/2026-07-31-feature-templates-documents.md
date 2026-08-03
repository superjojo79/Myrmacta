---
phase: testing
title: Testing Strategy
description: Define testing approach, test cases, and quality assurance
status: draft
created: 2026-08-03
updated: 2026-08-03
design_aligned: 2026-08-03
phase5_evidence: 2026-08-03
---

# Testing Strategy

## Test Coverage Goals
**What level of testing do we aim for?**

- **Unitaires domaine :** `TemplateResolver` cascade ; activate archive
  précédente ; refus PATCH hors DRAFT ; new-version incrémente ;
  validation blank / taille HTML (5 MiB) ; parse Thymeleaf à l’activate.
- **IT H2 MODE=PostgreSQL :** Liquibase `document_template` + index
  partiel ACTIVE ; MockMvc admin CRUD / activate / archive / resolve ;
  liste `includeContent` ; concurrence activate (contrainte unique).
- **Régression :** dossiers, connecteurs, workflow, auth BFF, ArchUnit,
  Jacoco ≥ 80 %.
- **Hors scope :** Testcontainers PG, Keycloak réel, génération PDF,
  E2E navigateur, sandboxing Thymeleaf avancé.

Alignement requirements success criteria + design DD-*.

## Unit Tests
**What individual components need testing?**

### TemplateResolver / ranked query
- [x] Exact `(key, variant, locale)` → score 1
- [x] Fallback `(key, variant, null)` quand locale absente → score 2
- [x] Fallback `(key, null, locale)` quand variant absent → score 3
- [x] Fallback `(key, null, null)` défaut absolu → score 4
- [x] Aucun ACTIVE → `TemplateNotFoundException`
- [x] Ignore DRAFT / ARCHIVED dans la résolution
- [x] Une seule requête ranked (pas 4 round-trips) — vérif adapter/IT

### TemplateSyntaxChecker
- [x] HTML Thymeleaf syntaxiquement valide → OK
- [x] Markup `th:*` invalide → erreur métier
- [x] Variables métier absentes **ne** font **pas** échouer (syntaxe only)

### Activate / archive / new-version
- [x] Activate DRAFT valide → ACTIVE ; précédente ACTIVE → ARCHIVED
- [x] Activate HTML Thymeleaf invalide → erreur 400 ; reste DRAFT
- [x] Activate non-DRAFT → erreur métier
- [x] Archive ACTIVE → ARCHIVED
- [x] Archive non-ACTIVE → erreur
- [x] New-version depuis ACTIVE/ARCHIVED/DRAFT → nouveau DRAFT version+1
  (contenu cloné)

### Update / create validation
- [x] Create → DRAFT version 1 (ou max+1)
- [x] Patch DRAFT OK ; patch ACTIVE/ARCHIVED → rejet
- [x] `templateKey` / `htmlContent` blank → rejet
- [x] `htmlContent` > 5 MiB → rejet
- [x] Create/PATCH n’exigent pas parse Thymeleaf (seulement activate)

### Mapper domaine ↔ JPA
- [x] Round-trip null variant/locale préservés
- [x] Enum status STRING

## Integration Tests
**What end-to-end flows need testing?**

### MockMvc admin (`DocumentTemplate*ApiTest`)
- [x] POST create 201 + GET detail
- [x] GET list + filtre `templateKey` ; sans `htmlContent` par défaut
- [x] GET list `?includeContent=true` → `htmlContent` présent
- [x] PATCH DRAFT 200 ; PATCH ACTIVE 409
- [x] POST activate 200 ; seconde ACTIVE même combinaison impossible
  (archive auto)
- [x] POST activate HTML invalide → 400
- [x] POST archive 200
- [x] POST new-version 201 DRAFT
- [x] GET resolve cascade 200 / 404
- [x] Non `TENANT_ADMIN` → 403 ; anonyme → 401
- [x] CSRF manquant sur mutateur → 403

### Liquibase / contrainte
- [x] Schema `document_template` présent après migrate
- [x] Insert deux ACTIVE même combinaison → violation unique (si bypass
  use case)

## Test Data & Fixtures

- Templates HTML minimaux Thymeleaf (`th:text`) en fixtures string
- `TenantProperties` test = `tenant_dev` (existant)
- Spring Security Test : `oidcLogin()` + rôle `TENANT_ADMIN`

## Mocks & tools

- Mockito : repository pour unit resolver / use cases
- MockMvc + Security test pour API
- H2 MODE=PostgreSQL + Liquibase (existant)
- ArchUnit règles hexagone inchangées

## Success criteria → scenarios

| Critère requirements | Couverture tests |
|---|---|
| Table + index ACTIVE | Liquibase IT / contrainte |
| Record + adapter + ArchUnit | Unit mapper + `./mvnw verify` |
| Cascade résolution | Unit TemplateResolver + MockMvc resolve |
| Une seule ACTIVE | Activate use case + unique index |
| Surface API admin | MockMvc CRUD / lifecycle |
| 401 / 403 | MockMvc security |
| Jacoco ≥ 80 % | `./mvnw verify` |
