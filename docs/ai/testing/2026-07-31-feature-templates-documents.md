---
phase: testing
title: Testing Strategy
description: Define testing approach, test cases, and quality assurance
status: draft
created: 2026-08-03
updated: 2026-08-03
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

### TemplateResolver
- [ ] Exact `(key, variant, locale)` → hit
- [ ] Fallback `(key, variant, null)` quand locale absente
- [ ] Fallback `(key, null, locale)` quand variant absent
- [ ] Fallback `(key, null, null)` défaut absolu
- [ ] Aucun ACTIVE → `TemplateNotFoundException`
- [ ] Ignore DRAFT / ARCHIVED dans la résolution

### Activate / archive / new-version
- [ ] Activate DRAFT valide → ACTIVE ; précédente ACTIVE → ARCHIVED
- [ ] Activate HTML Thymeleaf invalide → erreur 400 ; reste DRAFT
- [ ] Activate non-DRAFT → erreur métier
- [ ] Archive ACTIVE → ARCHIVED
- [ ] Archive non-ACTIVE → erreur
- [ ] New-version depuis ACTIVE/ARCHIVED/DRAFT → nouveau DRAFT version+1
  (contenu cloné)

### Update / create validation
- [ ] Create → DRAFT version 1 (ou max+1)
- [ ] Patch DRAFT OK ; patch ACTIVE/ARCHIVED → rejet
- [ ] `templateKey` / `htmlContent` blank → rejet
- [ ] `htmlContent` > 5 MiB → rejet
- [ ] Create/PATCH n’exigent pas parse Thymeleaf (seulement activate)

### Mapper domaine ↔ JPA
- [ ] Round-trip null variant/locale préservés
- [ ] Enum status STRING

## Integration Tests
**What end-to-end flows need testing?**

### MockMvc admin (`DocumentTemplate*ApiTest`)
- [ ] POST create 201 + GET detail
- [ ] GET list + filtre `templateKey` ; sans `htmlContent` par défaut
- [ ] GET list `?includeContent=true` → `htmlContent` présent
- [ ] PATCH DRAFT 200 ; PATCH ACTIVE 409
- [ ] POST activate 200 ; seconde ACTIVE même combinaison impossible
  (archive auto)
- [ ] POST activate HTML invalide → 400
- [ ] POST archive 200
- [ ] POST new-version 201 DRAFT
- [ ] GET resolve cascade 200 / 404
- [ ] Non `TENANT_ADMIN` → 403 ; anonyme → 401
- [ ] CSRF manquant sur mutateur → 403

### Liquibase / contrainte
- [ ] Schema `document_template` présent après migrate
- [ ] Insert deux ACTIVE même combinaison → violation unique (si bypass
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
