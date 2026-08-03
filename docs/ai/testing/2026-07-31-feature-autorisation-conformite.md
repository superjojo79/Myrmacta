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

- **Unitaires :** `TenantAdminGuard` domaine ; non-régression
  `DossierAccessPolicy` ; construction / défaut `FieldDefinition.sensitive` ;
  use cases admin (refus non-admin via guard domaine).
- **IT MockMvc :** `@PreAuthorize` (403 rôle insuffisant / 2xx admin) sur
  types + ACL + mutateurs workflow config ; round-trip `sensitive` sur type.
- **Régression :** auth BFF, dossiers, workflow, ArchUnit, Jacoco ≥ 80 %.
- **E2E navigateur / IdP réel / masquage UI :** hors scope.

Alignement requirements : scénarios clés 1–6 + edge 403 authentifié.

## Unit Tests
**What individual components need testing?**

### TenantAdminGuard (domaine)
- [ ] `TENANT_ADMIN` → OK (pas d’exception)
- [ ] `WORKFLOW_ADMIN` seul → `AccessDeniedException`
- [ ] `DOSSIER_MANAGER` → deny
- [ ] User sans rôles → deny

### FieldDefinition.sensitive
- [ ] Constructeur / record : `sensitive=true` conservé
- [ ] Défaut `false` quand non spécifié (factory / overload / compact)
- [ ] `DossierFieldValidator` ignore le flag (fixture avec sensitive OK)

### Use cases admin (guard domaine)
- [ ] `CreateDossierType` non-admin → deny (via guard)
- [ ] `UpdateDossierType` non-admin → deny
- [ ] `Grant` / `Revoke` non-admin → deny
- [ ] Admin happy path inchangé (régression)

### DossierAccessPolicy (régression)
- [ ] Bypass TENANT_ADMIN / WORKFLOW_ADMIN
- [ ] Service refus
- [ ] VIEW vs EDIT

## Integration Tests
**How do we test component interactions?**

### Method security MockMvc
- [ ] `POST /api/dossier-types` en `DOSSIER_MANAGER` → **403**
- [ ] `POST /api/dossier-types` en `TENANT_ADMIN` → **201** (ou 2xx)
- [ ] `POST …/acl` sans TENANT_ADMIN → **403**
- [ ] Mutateur workflow type-config sans admin/workflow-admin → **403**
- [ ] Mutateur workflow type-config en `WORKFLOW_ADMIN` → autorisé (si matrice)
- [ ] Session absente `/api/**` → **401** (régression BFF)

### sensitive bout-en-bout
- [ ] Create type avec champ `sensitive: true` → GET renvoie `true`
- [ ] Create type sans clé `sensitive` → GET renvoie `false`
- [ ] GET dossier avec champ sensible → **valeur en clair** (pas de redaction)

### Persistence JSON
- [ ] Save/load `DossierType` avec `sensitive` via JPA/H2 (IT persistence ou
  API)

## End-to-End Tests
**What user flows need validation?**

- [ ] Hors scope navigateur
- [ ] Régression suite `./mvnw test` (auth + dossiers + workflow)

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
