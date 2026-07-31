---
phase: implementation
title: Implementation Guide
description: Technical implementation notes, patterns, and code guidelines
status: implemented
updated: 2026-07-31
---

# Implementation Guide

## Development Setup
**How do we get started?**

- JDK **25**, Maven Wrapper : `./mvnw test` / `./mvnw verify`
- Worktree : `.worktrees/feature-authentification`
- Run réel : exporter `OIDC_ISSUER_URI` et `OIDC_CLIENT_SECRET` (requis, sans défaut)
- Tests : stub `OidcClientTestConfiguration` + `src/test/resources/application.properties`
- Dépendance test Boot 4 : `spring-boot-starter-webmvc-test` (`@AutoConfigureMockMvc`)

## Code Structure
**How is the code organized?**

```text
com.flowforge.infrastructure
├── config
│   ├── TenantProperties / TenantConfig      # existants
│   └── SecurityCookieProperties             # app.security.cookie.secure
├── security
│   ├── SecurityConfig                       # BFF @Order(3)
│   ├── ApiAuthenticationEntryPoint
│   ├── CsrfCookieEagerLoadFilter
│   └── oidc.RealmAccessAuthoritiesMapper
├── tenant
│   ├── TenantIdValidator                    # existant
│   └── TenantResolutionFilter               # invalidate + 403
└── web.auth
    ├── AuthController                       # GET /api/auth/me
    └── AuthUserResponse
```

## Key Implementation Details

### SecurityFilterChain BFF `@Order(3)`

- CSRF cookie non-httpOnly + `CsrfTokenRequestAttributeHandler` + eager-load filter
- `oauth2Login` + `RealmAccessAuthoritiesMapper`
- Logout `/api/auth/logout` + `OidcClientInitiatedLogoutSuccessHandler`
- Entry point : `/api/**` → 401 vide ; sinon `/oauth2/authorization/oidc`
- `TenantResolutionFilter` après `SecurityContextHolderFilter`

### YAML

- Exclusions **Security / OAuth2 client retirées** ; JPA / Flowable / Mail conservées
- `server.forward-headers-strategy: framework` actif ; `context-path` commenté
- `registrationId` / provider = **`oidc`**
- `${OIDC_ISSUER_URI}` / `${OIDC_CLIENT_SECRET}` sans défaut

### Tests sans IdP

`com.flowforge.support.OidcClientTestConfiguration` fournit un
`ClientRegistrationRepository` `@Primary` (endpoints locaux factices).
Importé par `@SpringBootTest` concernés + `SpringApplicationBuilder` tenant fail-fast.

### Contrat SPA (feature `frontend`)

```javascript
axios.defaults.withCredentials = true;
axios.defaults.xsrfCookieName = 'XSRF-TOKEN';
axios.defaults.xsrfHeaderName = 'X-XSRF-TOKEN';
// login : /oauth2/authorization/oidc
// GET /api/auth/me → 401 anonyme ; 403 tenant (session déjà invalidée)
```

## Changed files (phase 5)

- `pom.xml` — `spring-boot-starter-webmvc-test`
- `application.yml` — OIDC, forward-headers, cookies, retrait exclusions Security
- Packages `infrastructure.security*`, `web.auth`, `TenantResolutionFilter`
- Tests unitaires + `BffSecurityIT` + stub OIDC + imports Domain/Tenant tests
- Docs planning / testing / implementation

## Design deviations

- Aucune déviation fonctionnelle vs design reviewed.
- Note Boot 4 : `@AutoConfigureMockMvc` dans
  `org.springframework.boot.webmvc.test.autoconfigure` + starter dédié.

## Edge cases handled

- Claim `tenant_id` absent / mismatch → session invalidée + 403
- Rôles déjà préfixés `ROLE_` → pas de double préfixe
- `/me` anonyme → 401 avant contrôleur (entry point)

## Evidence (phase 5)

| Commande | Résultat |
|---|---|
| `./mvnw test` | **50** tests, 0 failure |
| `./mvnw verify` | BUILD SUCCESS ; Jacoco check OK |
