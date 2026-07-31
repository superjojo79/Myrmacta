---
phase: testing
title: Testing Strategy
description: Define testing approach, test cases, and quality assurance
status: draft
updated: 2026-07-31
---

# Testing Strategy

## Test Coverage Goals
**What level of testing do we aim for?**

- **Unitaires / web :** AuthController, mapper rôles, TenantResolutionFilter,
  entry point 401, propriétés cookie.
- **Security MockMvc :** login mock OIDC (`oidcLogin()`), `/me`, CSRF, logout
  config, 403 tenant mismatch — **sans** Keycloak réel.
- **Régression :** workflow + `DomainConfigTest` + Tenant* + ArchUnit.
- **E2E navigateur / IdP réel :** hors scope.
- **Jacoco :** seuil bundle ≥ 80 % (`./mvnw verify`).

Alignement success criteria requirements : BFF testable sans IdP ; garde tenant ;
`./mvnw test` / `verify` verts.

## Unit Tests
**What individual components need testing?**

### RealmAccessAuthoritiesMapper
- [ ] Mappe `realm_access.roles` → `ROLE_*`
- [ ] Ignore rôles déjà préfixés / absents / claim manquant (liste vide)
- [ ] Préserve autres autorités non rôles si applicable

### TenantResolutionFilter
- [ ] Skip si non authentifié
- [ ] 403 si authentifié OIDC sans `tenant_id` / blank
- [ ] 403 si `tenant_id` ≠ `TenantProperties.id()`
- [ ] Continue si `tenant_id` égal à l’instance

### SecurityCookieProperties
- [ ] Défaut `secure=true`
- [ ] Override `false` via propriété

### ApiAuthenticationEntryPoint
- [ ] `/api/...` → 401
- [ ] chemin hors API → délègue redirect (ou comportement login)

## Integration Tests (MockMvc + mock OIDC)
**How do we test component interactions?**

Fichiers cibles (indicatif) :
`AuthControllerTest`, `BffSecurityIT` / `TenantResolutionFilterIT`

- [ ] `GET /api/auth/me` anonyme → **401** (pas 302 vers IdP)
- [ ] `oidcLogin()` avec claims name/email/roles + `tenant_id=tenant_dev` → **200**
      body `roles` sans préfixe `ROLE_`
- [ ] `oidcLogin()` `tenant_id=other` → **403**
- [ ] `oidcLogin()` sans claim `tenant_id` → **403**
- [ ] POST mutant sans CSRF → **403** ; avec CSRF token → pas 403 CSRF
- [ ] Logout URL `/api/auth/logout` configurée (smoke)
- [ ] Contexte Spring boot avec yaml test / mock issuer — exclusions Security retirées
- [ ] `DomainConfigTest` + suite Tenant* + workflow **non régressés**

## End-to-End Tests
**What user flows need validation?**

- [ ] N/A — pas de parcours navigateur + Keycloak réel dans cette feature

## Architecture Tests (ArchUnit)
Fichier : `ArchitectureRulesTest.java`

- [ ] Domaine ne dépend pas de Spring Security / OAuth2 / `infrastructure.security`
- [ ] Pas de fuite Security dans `domain.*`

## Test Data
**What data do we use for testing?**

| Fixture | Valeur | Usage |
|---|---|---|
| Tenant instance | `tenant_dev` | yaml + match claim |
| Claim OK | `tenant_id=tenant_dev` | `/me` 200 |
| Claim KO | `tenant_id=tenant_a` / absent | 403 |
| Rôles realm | `WORKFLOW_ADMIN`, `DOSSIER_VIEWER` | mapper + `/me.roles` |
| registrationId | `oidc` | chemins OAuth2 |
| Cookie secure test | `false` si besoin MockMvc | propriétés |

Stratégie IdP : **`SecurityMockMvcRequestPostProcessors.oidcLogin()`** et/ou
UserService mock — **pas** Testcontainers Keycloak.

## Test Reporting & Coverage
**How do we verify and communicate test results?**

```bash
export JAVA_HOME=…/jdk-25   # si besoin
./mvnw test
./mvnw verify
```

Rapport : `target/site/jacoco/index.html`

## Manual Testing
**What requires human validation?**

- [ ] Relire yaml : exclusions Security/OAuth2 retirées ; Flowable/JPA encore exclus
- [ ] `forward-headers-strategy: framework` présent ; `context-path` commenté
- [ ] Doc contrat SPA : `/oauth2/authorization/oidc`, CSRF Axios, cookies
- [ ] Smoke local optionnel avec Keycloak réel (hors CI)

## Performance Testing
**How do we validate performance?**

- N/A — pas de charge IdP dans cette tranche.

## Bug Tracking
**How do we manage issues?**

- Redirect HTML sur `/api/**` anonyme = P0.
- Absence de garde `tenant_id` = P0.
- Régression ArchUnit / Jacoco / suite existante = bloquant PR.
- `registrationId=keycloak` résiduel dans code/docs feature = bug doc/contrat.

## Phase 8 notes

- À remplir après exécution des tests (evidence fraîche).
