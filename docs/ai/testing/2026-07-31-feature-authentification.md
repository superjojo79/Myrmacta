---
phase: testing
title: Testing Strategy
description: Define testing approach, test cases, and quality assurance
status: verified
verified: 2026-07-31
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
- [x] Mappe `realm_access.roles` → `ROLE_*`
- [x] Ignore rôles déjà préfixés / absents / claim manquant (liste vide)
- [x] Préserve autres autorités non rôles si applicable

### TenantResolutionFilter
- [x] Skip si non authentifié
- [x] 403 + **session invalidée** si authentifié OIDC sans `tenant_id` / blank
- [x] 403 + **session invalidée** si `tenant_id` ≠ `TenantProperties.id()`
- [x] Continue si `tenant_id` égal à l’instance (session intacte)

### SecurityCookieProperties
- [x] Défaut `secure=true`
- [x] Override `false` via propriété

### ApiAuthenticationEntryPoint
- [x] `/api/...` → 401
- [x] chemin hors API → délègue redirect (ou comportement login)

### AuthController
- [x] `null` → 401
- [x] strip `ROLE_` + profil name/email/roles

## Integration Tests (MockMvc + mock OIDC)
**How do we test component interactions?**

Fichiers : `BffSecurityIT`, stub `OidcClientTestConfiguration`

- [x] `GET /api/auth/me` anonyme → **401** body vide (pas 302 vers IdP)
- [x] `oidcLogin()` avec claims name/email/roles + `tenant_id=tenant_dev` → **200**
      body `roles` sans préfixe `ROLE_`
- [x] `oidcLogin()` `tenant_id=other` → **403** body vide + session invalidée
      (appel suivant `/me` → 401)
- [x] `oidcLogin()` sans claim `tenant_id` → **403** + session invalidée
- [x] POST mutant sans CSRF → **403** ; avec CSRF token → pas 403 CSRF
- [x] Logout URL `/api/auth/logout` configurée (smoke)
- [x] Contexte Spring : stub `ClientRegistrationRepository` (pas de discovery HTTP)
- [x] `DomainConfigTest` + suite Tenant* + workflow **non régressés**

## End-to-End Tests
**What user flows need validation?**

- [x] N/A — pas de parcours navigateur + Keycloak réel dans cette feature

## Architecture Tests (ArchUnit)
Fichier : `ArchitectureRulesTest.java`

- [x] Domaine ne dépend pas de Spring Security / OAuth2 / `infrastructure.security`
      (règle `org.springframework..` + `infrastructure`)
- [x] Pas de fuite Security dans `domain.*`

## Test Data
**What data do we use for testing?**

| Fixture | Valeur | Usage |
|---|---|---|
| Tenant instance | `tenant_dev` | yaml + match claim |
| Claim OK | `tenant_id=tenant_dev` | `/me` 200 |
| Claim KO | `tenant_id=tenant_a` / absent | 403 |
| Rôles realm | `WORKFLOW_ADMIN`, `DOSSIER_VIEWER` | mapper + `/me.roles` |
| registrationId | `oidc` | chemins OAuth2 |
| Cookie secure test | `false` | `application.properties` test |

Stratégie IdP : **`oidcLogin()`** + stub repository — **pas** Testcontainers Keycloak.

## Test Reporting & Coverage
**How do we verify and communicate test results?**

```bash
export JAVA_HOME=…/jdk-25
./mvnw test
./mvnw verify
```

### Evidence phase 5 / 8 (2026-07-31)

| Commande | Résultat |
|---|---|
| `./mvnw test` | **50** tests, 0 failure, BUILD SUCCESS |
| `./mvnw verify` | Jacoco check OK |
| Jacoco lignes (bundle) | **≥ 80 %** (check OK ; ~84 % avant AuthControllerTest) |

Rapport : `target/site/jacoco/index.html`

## Manual Testing
**What requires human validation?**

- [x] Relire yaml : exclusions Security/OAuth2 retirées ; Flowable/JPA encore exclus
- [x] `forward-headers-strategy: framework` présent ; `context-path` commenté
- [x] Doc contrat SPA : `/oauth2/authorization/oidc`, CSRF Axios, cookies
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

- Couverture bundle ≥ 80 % atteinte (84.4 %).
- Boot 4 : dépendance `spring-boot-starter-webmvc-test` documentée.
