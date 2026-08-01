---
phase: testing
title: Testing Strategy
description: Define testing approach, test cases, and quality assurance
status: reviewed
created: 2026-07-31
updated: 2026-08-01
verified: 2026-07-31
reviewed: 2026-08-01
---

# Testing Strategy

## Test Coverage Goals
**What level of testing do we aim for?**

- **Unitaires domaine :** `DossierFieldValidator`, `DossierAccessPolicy`,
  transitions, use cases (create/update type, get type, revoke, create/status).
- **Unitaires infra :** `SequentialReferenceGenerator` (Clock),
  `DossierExceptionHandler` (dont OptimisticLock → 409).
- **IT H2 :** persistence, séquence, `@Version`, MockMvc + OIDC mock.
- **Régression :** auth BFF, Tenant*, workflow, ArchUnit, Jacoco ≥ 80 %.
- **E2E navigateur / PG réel / Testcontainers :** hors scope tranche B.

## Unit Tests
**What individual components need testing?**

### DossierFieldValidator
Fichier :
`src/test/java/com/flowforge/domain/dossier/model/DossierFieldValidatorTest.java`

- [x] Required manquant → erreur
- [x] Type mismatch NUMBER/DATE/BOOLEAN
- [x] SELECT hors options → erreur
- [x] Champ inconnu + `allowUnknownFields=false` → erreur
- [x] Champ inconnu + `allowUnknownFields=true` → OK
- [x] Payload valide → liste vide
- [x] `applyDefaults` si clé absente
- [x] FILE required blank → REQUIRED

### DossierAccessPolicy
Fichier :
`src/test/java/com/flowforge/domain/dossier/DossierAccessPolicyTest.java`

- [x] `AccessContext.System` → autorise view/edit
- [x] `TENANT_ADMIN` / `WORKFLOW_ADMIN` → bypass
- [x] User avec EDIT → view+edit OK
- [x] User avec VIEW → view OK, edit refus
- [x] User sans grant → refus
- [x] `AccessContext.Service` → refus dossier

### UpdateDossierStatus (règles + use case)
Fichiers :
`DossierStatusTransitionsTest.java`, `UpdateDossierStatusUseCaseImplTest.java`

- [x] Chaque transition autorisée du graphe
- [x] Transitions illégales → exception conflit
- [x] `ARCHIVE` terminal
- [x] `expectedVersion` stale → `DossierConflictException`
- [x] `closed*` renseignés sur CLOTURE

### Use cases type / ACL / get
Fichiers :
`UpdateDossierTypeUseCaseImplTest`, `GetDossierTypeUseCaseImplTest`,
`RevokeDossierTypeAccessUseCaseImplTest`, `ListDossierTypesUseCaseImplTest`,
`CreateDossierUseCaseImplTest`, `GrantDossierTypeAccessUseCaseImplTest`

- [x] Update type happy / non-admin / not found
- [x] Get type view / deny / not found
- [x] Revoke admin / non-admin
- [x] List filtré VIEW ; admins voient tout
- [x] Create dossier DRAFT / inactive / validation
- [x] Grant ACL : type existant / type manquant 404 / non-admin

### SequentialReferenceGenerator
Fichier :
`src/test/java/com/flowforge/infrastructure/persistence/dossier/SequentialReferenceGeneratorTest.java`

- [x] Format `DOS-{TYPE}-{YYYY}-{seq:06d}`
- [x] Année dérivée UTC (Clock fixe)

### DossierExceptionHandler
Fichier :
`src/test/java/com/flowforge/infrastructure/web/dossier/DossierExceptionHandlerTest.java`

- [x] OptimisticLock / OLS → 409 ProblemDetail

## Integration Tests
**How do we test component interactions?**

### Persistence H2
Fichier :
`src/test/java/com/flowforge/infrastructure/persistence/dossier/DossierPersistenceTest.java`

- [x] Liquibase + save/load `DossierType` (JSON fieldDefinitions)
- [x] Références séquentielles
- [x] `@Version` stale → optimistic lock failure

### API MockMvc + OIDC mock
Fichier :
`src/test/java/com/flowforge/infrastructure/web/dossier/DossierApiTest.java`

- [x] Anonyme `POST /api/dossiers` → 401
- [x] `TENANT_ADMIN` crée type → 201 ; PATCH type → 200
- [x] `WORKFLOW_ADMIN` crée type → 403
- [x] Grant EDIT + create → 201 `DRAFT` + `tenantId` instance
- [x] Type `active=false` → 409
- [x] DefaultValue injecté à la création
- [x] Champs invalides → 400 `errors[]`
- [x] Sans `label` → 400
- [x] Get sans VIEW → 403 ; avec VIEW → 200 ; `WORKFLOW_ADMIN` bypass
- [x] Get by `ref` → 200
- [x] PATCH status légal → 200 + `version` incrémentée ; illégal → 409 ; version stale → 409
- [x] Type code dupliqué → 409 (intégrité)
- [x] Grant ACL type inconnu → 404
- [x] Revoke ACL → refus ; re-grant idempotent 204
- [x] CSRF manquant → 403
- [x] Suite BFF non régressée (`BffSecurityTest`)

## End-to-End Tests
**What user flows need validation?**

- [x] N/A navigateur — couvert en MockMvc
- [ ] Smoke manuel optionnel PG réel (hors CI)

## Architecture Tests (ArchUnit)
Fichier : `src/test/java/com/flowforge/ArchitectureRulesTest.java`

- [x] Domaine ↛ Spring / JPA / infrastructure
- [x] Convention `usecase/*Impl`

## Test Data
**What data do we use for testing?**

| Fixture | Valeur | Usage |
|---|---|---|
| Tenant | `tenant_dev` | yaml + schema H2 |
| Admin | `TENANT_ADMIN` | types / ACL |
| Manager | grant EDIT | create/status |
| Viewer | grant VIEW | get only |
| H2 | `MODE=PostgreSQL` + schema `tenant_dev` | IT |

## Test Reporting & Coverage
**How do we verify and communicate test results?**

```bash
export JAVA_HOME=…/jdk-25
./mvnw -B verify
```

### Evidence phase 8 (2026-07-31)

| Commande | Résultat |
|---|---|
| `./mvnw -B verify` | **134** tests, 0 failure, BUILD SUCCESS |
| Jacoco check | OK (seuil lignes 80 %) |
| Jacoco lignes (bundle) | **~93 %** |
| Classes dossier &lt; 50 % lignes | **aucune** |

### Evidence phase 9 (2026-08-01)

| Commande | Résultat |
|---|---|
| `npx ai-devkit@latest lint --feature dossiers` | All checks passed |
| `./mvnw -B verify` | **139** tests, 0 failure, BUILD SUCCESS |
| Jacoco check | OK |
| Jacoco lignes (bundle) | **~95 %** |

Rapport : `target/site/jacoco/index.html`

Gaps acceptés (&lt; 80 % locaux, non bloquants) : accessors d’exceptions,
quelques branches `DossierTypeController` / validator (couverts ailleurs en IT).

## Manual Testing
**What requires human validation?**

- [ ] Optionnel : PG réel + création type/dossier via HTTP
- UI/UX : N/A

## Performance Testing
**How do we validate performance?**

- Hors scope tranche B.

## Bug Tracking
**How do we manage issues?**

- Régression auth / ArchUnit / Jacoco = bloqueur merge.
- Écarts H2 vs PG documentés en implementation.

## Phase 8 notes

- Gaps fermés : Update/Get/Revoke type use cases, SequentialReferenceGenerator
  (Clock), ExceptionHandler OLS, API PATCH type / defaults / invalid fields /
  VIEW / WORKFLOW_ADMIN bypass / revoke+re-grant, persistence `@Version`.

## Phase 9 notes

- Bloquant corrigé : `version` API après PATCH status (save + flush).
- Couverture ajoutée : grant type manquant, duplicate type 409, version
  post-transition.
- Aucun défaut de design restant pour la tranche B.
- **Lifecycle :** prêt à merger.
