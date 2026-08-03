---
phase: testing
title: Testing Strategy
description: Define testing approach, test cases, and quality assurance
status: verified
created: 2026-08-03
updated: 2026-08-03
design_aligned: 2026-08-03
verified: 2026-08-03
phase8: 2026-08-03
---

# Testing Strategy

## Phase 9 — Code Review

**Clos.** Aucun bloqueur ; follow-ups low/nice documentés (DEV-WF-UNUSED,
DEV-SENS-HELPER). Preuve : `./mvnw verify` → 199 / 0 ; Jacoco 90,3 %.
Lifecycle **Complete**.

## Phase 8 — synthèse

**Clos.** Gaps phase 7 comblés (`CreateDossierTypeUseCaseImplTest`,
`MethodSecurityExceptionHandlerTest`, ACL 403 manager, VIEWER sans EDIT).
Preuve fraîche : `./mvnw verify` → **199** tests / 0 fail ; Jacoco
instructions **90,3 %** ; ArchUnit OK.

## Test Coverage Goals
**What level of testing do we aim for?**

- **Unitaires :** `TenantAdminGuard` domaine ; non-régression
  `DossierAccessPolicy` ; construction / défaut `FieldDefinition.sensitive` ;
  use cases admin (refus non-admin via guard domaine) ; advice D-403.
- **IT MockMvc :** `@PreAuthorize` (403 `ProblemDetail` / 2xx admin) sur
  types + ACL + mutateurs workflow config ; **pas** d’exigence d’annotation
  rôle sur start/complete (policy seule) ; round-trip `sensitive`.
- **Régression :** auth BFF, dossiers, workflow, ArchUnit, Jacoco ≥ 80 %.
- **E2E navigateur / IdP réel / masquage UI :** hors scope.

Alignement requirements `reviewed` + design D-403 / D-WF-* : scénarios 1–7.

## Unit Tests
**What individual components need testing?**

### TenantAdminGuard (domaine)
Fichier :
`src/test/java/com/flowforge/domain/dossier/TenantAdminGuardTest.java`

- [x] `TENANT_ADMIN` → OK (pas d’exception)
- [x] `WORKFLOW_ADMIN` seul → `AccessDeniedException`
- [x] `DOSSIER_MANAGER` → deny
- [x] User sans rôles → deny

### FieldDefinition.sensitive
Fichiers :
`src/test/java/com/flowforge/domain/dossier/model/FieldDefinitionTest.java`,
`DossierFieldValidatorTest.java`

- [x] Constructeur / record : `sensitive=true` conservé
- [x] Défaut `false` quand non spécifié (factory / overload / compact)
- [x] `null` coercé → `false`
- [x] `DossierFieldValidator` ignore le flag (fixture avec sensitive OK)

### Use cases admin (guard domaine)
Fichiers :
`CreateDossierTypeUseCaseImplTest`, `UpdateDossierTypeUseCaseImplTest`,
`GrantDossierTypeAccessUseCaseImplTest`, `RevokeDossierTypeAccessUseCaseImplTest`

- [x] `CreateDossierType` non-admin → deny (via guard)
- [x] `UpdateDossierType` non-admin → deny
- [x] `Grant` / `Revoke` non-admin → deny
- [x] Admin happy path inchangé (régression)

### MethodSecurityExceptionHandler (D-403)
Fichier :
`src/test/java/com/flowforge/infrastructure/web/security/MethodSecurityExceptionHandlerTest.java`

- [x] Spring `AccessDeniedException` → 403 ProblemDetail
- [x] `AuthorizationDeniedException` message blank → detail défaut

### DossierAccessPolicy (régression)
Fichier :
`src/test/java/com/flowforge/domain/dossier/DossierAccessPolicyTest.java`

- [x] Bypass TENANT_ADMIN / WORKFLOW_ADMIN
- [x] Service refus
- [x] VIEW vs EDIT

## Integration Tests
**How do we test component interactions?**

### Method security MockMvc
Fichiers :
`src/test/java/com/flowforge/infrastructure/web/dossier/DossierApiTest.java`,
`…/workflow/WorkflowApiTest.java`

- [x] `POST /api/dossier-types` en `DOSSIER_MANAGER` → **403** ProblemDetail
- [x] `POST /api/dossier-types` en `WORKFLOW_ADMIN` → **403** (pas TENANT_ADMIN)
- [x] `POST /api/dossier-types` en `TENANT_ADMIN` → **201** (ou 2xx)
- [x] `POST …/acl` sans TENANT_ADMIN → **403** ProblemDetail
- [x] Mutateur workflow type-config sans admin/workflow-admin → **403**
- [x] Mutateur workflow type-config en `WORKFLOW_ADMIN` → autorisé
- [x] `DOSSIER_VIEWER` authentifié `PATCH …/status` sans EDIT → **403** policy
- [x] Session absente `/api/**` → **401** (régression BFF)

### sensitive bout-en-bout
Fichier : `DossierApiTest.java`

- [x] Create type avec champ `sensitive: true` → GET renvoie `true`
- [x] Create type sans clé `sensitive` → GET renvoie `false`
- [x] GET dossier avec champ sensible → **valeur en clair** (pas de redaction)

### Persistence JSON
Fichier :
`src/test/java/com/flowforge/infrastructure/persistence/dossier/DossierPersistenceTest.java`

- [x] Save/load `DossierType` avec `sensitive` via JPA/H2

## End-to-End Tests
**What user flows need validation?**

- [x] Hors scope navigateur
- [x] Régression suite `./mvnw verify` (auth + dossiers + workflow)

## Test Data
**What data do we use for testing?**

- OIDC mock / `oidcLogin()` avec autorités `ROLE_*` ciblées
- Payloads type avec et sans `sensitive`
- Fixtures ACL VIEW/EDIT existantes réutilisées

## Test Reporting & Coverage
**How do we verify and communicate test results?**

- Commande : `./mvnw verify`
- **Preuve phase 8 (2026-08-03) :** 199 tests / 0 fail / 0 error ; Jacoco
  instructions **90,3 %** (5989/6636) ; ArchUnit OK
- Gaps acceptables : branches Spring Security internes ; code hors feature

## Manual Testing
**What requires human validation?**

- Smoke IdP réel (cinq rôles realm) — hors CI, follow-up ops
- Vérif visuelle SPA — N/A (pas de frontend)

## Performance Testing
**How do we validate performance?**

- N/A pour cette tranche

## Bug Tracking
**How do we manage issues?**

- Écarts design → doc implementation + planning `[!]`
- Régressions 401/403 → bloquant avant phase 9
- Phase 8 : aucun bug bloquant découvert
