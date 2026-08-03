---
phase: testing
title: Testing Strategy
description: Define testing approach, test cases, and quality assurance
status: verified
created: 2026-08-03
updated: 2026-08-03
design_aligned: 2026-08-03
phase5_evidence: 2026-08-03
phase8: 2026-08-03
verified: 2026-08-03
---

# Testing Strategy

## Test Coverage Goals
**What level of testing do we aim for?**

- **Unitaires domaine :** `TemplateResolver` cascade ; activate archive
  précédente ; refus PATCH hors DRAFT ; new-version incrémente ;
  validation blank / taille HTML (5 MiB) ; parse Thymeleaf à l’activate.
- **IT H2 MODE=PostgreSQL :** Liquibase `document_template` + index
  partiel ACTIVE ; MockMvc admin CRUD / activate / archive / resolve ;
  liste `includeContent` ; contrainte unique ACTIVE / version.
- **Régression :** dossiers, connecteurs, workflow, auth BFF, ArchUnit,
  Jacoco ≥ 80 %.
- **Hors scope :** Testcontainers PG, Keycloak réel, génération PDF,
  E2E navigateur, sandboxing Thymeleaf avancé, stress concurrence
  multi-thread (couvert par contrainte SQL unique).

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
- [x] Normalisation blank variant/locale → null (`DocumentTemplateUseCasesTest`)

### TemplateSyntaxChecker
- [x] HTML Thymeleaf syntaxiquement valide → OK
- [x] Markup `th:*` invalide → erreur métier
- [x] Variables métier absentes **ne** font **pas** échouer (syntaxe only)

### Activate / archive / new-version
- [x] Activate DRAFT valide → ACTIVE ; précédente ACTIVE → ARCHIVED
- [x] Activate sans ACTIVE précédente → 1 seul save ACTIVE
- [x] Activate HTML Thymeleaf invalide → erreur 400 ; reste DRAFT
- [x] Activate non-DRAFT → erreur métier
- [x] Archive ACTIVE → ARCHIVED
- [x] Archive non-ACTIVE → erreur
- [x] New-version depuis ACTIVE/ARCHIVED/DRAFT → nouveau DRAFT version+1
  (contenu cloné)

### Update / create validation
- [x] Create → DRAFT version 1 (ou max+1)
- [x] Patch DRAFT OK ; patch ACTIVE/ARCHIVED → rejet
- [x] Patch partiel (label seul / html seul)
- [x] `templateKey` / `htmlContent` blank → rejet
- [x] `htmlContent` > 5 MiB → rejet
- [x] Create/PATCH n’exigent pas parse Thymeleaf (seulement activate)

### Mapper domaine ↔ JPA
- [x] Round-trip null variant/locale préservés
- [x] Enum status STRING

## Integration Tests
**What end-to-end flows need testing?**

### MockMvc admin (`DocumentTemplateApiTest`)
- [x] POST create 201 + GET detail
- [x] GET list + filtre `templateKey` ; sans `htmlContent` par défaut
- [x] GET list `?includeContent=true` → `htmlContent` présent
- [x] PATCH DRAFT 200 ; PATCH ACTIVE 409
- [x] POST create HTML Thymeleaf invalide → 201 (pas de parse)
- [x] POST activate 200 ; seconde activate même id → 409
- [x] POST activate HTML invalide → 400
- [x] POST archive 200 ; archive non-ACTIVE → 409
- [x] POST new-version 201 DRAFT
- [x] GET resolve exact + cascade fallback défaut 200 / 404
- [x] GET `/{id}` inconnu → 404
- [x] create blank `templateKey` → 400
- [x] Non `TENANT_ADMIN` → 403 ; anonyme → 401
- [x] CSRF manquant sur mutateur → 403

### Liquibase / contrainte
- [x] Schema `document_template` présent après migrate
- [x] Insert deux ACTIVE même combinaison → violation unique
- [x] Insert doublon `(combinaison, version)` → violation unique
- [x] Activate IT archive ACTIVE précédente (null defaults) en TX

## Test files

| Suite | Path |
|---|---|
| Models | `src/test/java/com/flowforge/domain/document/model/DocumentTemplateModelsTest.java` |
| Ports | `src/test/java/com/flowforge/domain/document/port/DocumentTemplatePortsContractTest.java` |
| Use cases | `src/test/java/com/flowforge/domain/document/usecase/DocumentTemplateUseCasesTest.java` |
| Checker | `src/test/java/com/flowforge/infrastructure/document/ThymeleafTemplateSyntaxCheckerTest.java` |
| Mapper | `src/test/java/com/flowforge/infrastructure/persistence/document/DocumentTemplateMapperTest.java` |
| Schema IT | `src/test/java/com/flowforge/infrastructure/persistence/document/DocumentTemplateSchemaIT.java` |
| Persistence IT | `src/test/java/com/flowforge/infrastructure/persistence/document/DocumentTemplatePersistenceIT.java` |
| API MockMvc | `src/test/java/com/flowforge/infrastructure/web/document/DocumentTemplateApiTest.java` |

## Test Data & Fixtures

- Templates HTML minimaux Thymeleaf (`th:text`) en fixtures string
- Invalid markup fiable : `"<th:block th:each=\"x : ${y}\""`
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
| Table + index ACTIVE / version | `DocumentTemplateSchemaIT` |
| Record + adapter + ArchUnit | Unit mapper + `./mvnw verify` |
| Cascade résolution | PersistenceIT + MockMvc resolve fallback |
| Une seule ACTIVE | Activate use case/IT + unique index |
| Surface API admin | `DocumentTemplateApiTest` |
| 401 / 403 / CSRF | MockMvc security |
| Jacoco ≥ 80 % | `./mvnw verify` |

## Evidence (phase 8)

```text
JAVA_HOME=…/jdk-25.0.4+7 ./mvnw -q verify
→ exit 0
→ Surefire: tests=328, failures=0, errors=0, skipped=0
→ Jacoco INSTRUCTION 11044/12593 = 87.70%
→ Packages document : usecase 96.7%, web 97.6%, persistence 97.6%, checker 100%
→ Targeted: ApiTest 5, UseCases 14, SchemaIT 4, PersistenceIT 4, Checker 3 — OK
→ npx ai-devkit lint --feature templates-documents → All checks passed
→ task CLI : unavailable
```
