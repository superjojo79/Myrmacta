---
phase: testing
title: Testing Strategy
description: Define testing approach, test cases, and quality assurance
status: verified
created: 2026-08-03
updated: 2026-08-03
design_aligned: 2026-08-03
verified: 2026-08-03
---

# Testing Strategy

## Test Coverage Goals
**What level of testing do we aim for?**

- **Unitaires :** `TenantAdminGuard` domaine ; non-régression
  `DossierAccessPolicy` ; construction / défaut `FieldDefinition.sensitive` ;
  use cases admin (refus non-admin via guard domaine).
- **IT MockMvc :** `@PreAuthorize` (403 `ProblemDetail` / 2xx admin) sur
  types + ACL + mutateurs workflow config ; **pas** d’exigence d’annotation
  rôle sur start/complete (policy seule) ; round-trip `sensitive`.
- **Régression :** auth BFF, dossiers, workflow, ArchUnit, Jacoco ≥ 80 %.
- **E2E navigateur / IdP réel / masquage UI :** hors scope.

Alignement requirements `reviewed` + design D-403 / D-WF-* : scénarios 1–7.

## Unit Tests
**What individual components need testing?**

### TenantAdminGuard (domaine)
- [x] `TENANT_ADMIN` → OK (pas d’exception)
- [x] `WORKFLOW_ADMIN` seul → `AccessDeniedException`
- [x] `DOSSIER_MANAGER` → deny
- [x] User sans rôles → deny

### FieldDefinition.sensitive
- [x] Constructeur / record : `sensitive=true` conservé
- [x] Défaut `false` quand non spécifié (factory / overload / compact)
- [x] `DossierFieldValidator` ignore le flag (fixture avec sensitive OK)

### Use cases admin (guard domaine)
- [x] `CreateDossierType` non-admin → deny (via guard)
- [x] `UpdateDossierType` non-admin → deny
- [x] `Grant` / `Revoke` non-admin → deny
- [x] Admin happy path inchangé (régression)

### DossierAccessPolicy (régression)
- [x] Bypass TENANT_ADMIN / WORKFLOW_ADMIN
- [x] Service refus
- [x] VIEW vs EDIT

## Integration Tests
**How do we test component interactions?**

### Method security MockMvc
- [x] `POST /api/dossier-types` en `DOSSIER_MANAGER` → **403** ProblemDetail
- [x] `POST /api/dossier-types` en `WORKFLOW_ADMIN` → **403** (pas TENANT_ADMIN)
- [x] `POST /api/dossier-types` en `TENANT_ADMIN` → **201** (ou 2xx)
- [x] `POST …/acl` sans TENANT_ADMIN → **403** ProblemDetail
- [x] Mutateur workflow type-config sans admin/workflow-admin → **403**
- [x] Mutateur workflow type-config en `WORKFLOW_ADMIN` → autorisé
- [x] `DOSSIER_VIEWER` authentifié `PATCH …/status` sans EDIT → **403** policy
- [x] Session absente `/api/**` → **401** (régression BFF)

### sensitive bout-en-bout
- [x] Create type avec champ `sensitive: true` → GET renvoie `true`
- [x] Create type sans clé `sensitive` → GET renvoie `false`
- [x] GET dossier avec champ sensible → **valeur en clair** (pas de redaction)

### Persistence JSON
- [x] Save/load `DossierType` avec `sensitive` via JPA/H2 (IT persistence ou
  API)

## End-to-End Tests
**What user flows need validation?**

- [x] Hors scope navigateur
- [x] Régression suite `./mvnw test` (auth + dossiers + workflow)

## Test Data
**What data do we use for testing?**

- OIDC mock / `oidcLogin()` avec autorités `ROLE_*` ciblées
- Payloads type avec et sans `sensitive`
- Fixtures ACL VIEW/EDIT existantes réutilisées

## Test Reporting & Coverage
**How do we verify and communicate test results?**

- Commande : `./mvnw test` (Jacoco déjà configuré, seuil ≥ 80 %)
- Preuve phase 8 : sortie surefire + rapport Jacoco
- Gaps acceptables : code mort guard infra une fois supprimé ; branches
  Spring Security internes

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
