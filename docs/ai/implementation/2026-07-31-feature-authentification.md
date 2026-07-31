---
phase: implementation
title: Implementation Guide
description: Technical implementation notes, patterns, and code guidelines
status: verified
checked: 2026-07-31
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
- `authorizeHttpRequests` : `anyRequest().authenticated()` — les chemins
  `/oauth2/**` / callback sont pris en charge par les filtres `oauth2Login`
  (pas de `permitAll` explicite)

### Mapping rôles

`RealmAccessAuthoritiesMapper` lit `realm_access.roles` sur l’**ID token**
uniquement (`OidcUserAuthority.getIdToken()`). Le provisioning IdP doit y
placer le claim (pas seulement l’access token).

`/me.roles` ne conserve que les autorités `ROLE_*` (préfixe retiré) — les
`SCOPE_*` sont exclus du JSON.

### YAML

- Exclusions **Security / OAuth2 client retirées** ; JPA / Flowable / Mail conservées
- `server.forward-headers-strategy: framework` actif ; `context-path` commenté
- `registrationId` / provider = **`oidc`**
- `${OIDC_ISSUER_URI}` / `${OIDC_CLIENT_SECRET}` sans défaut

### Tests sans IdP

`com.flowforge.support.OidcClientTestConfiguration` fournit un
`ClientRegistrationRepository` `@Primary` (endpoints locaux factices).

### Contrat SPA (feature `frontend`)

```javascript
axios.defaults.withCredentials = true;
axios.defaults.xsrfCookieName = 'XSRF-TOKEN';
axios.defaults.xsrfHeaderName = 'X-XSRF-TOKEN';
// login : /oauth2/authorization/oidc
// GET /api/auth/me → 401 anonyme ; 403 tenant (session déjà invalidée)
```

## Changed files (phase 5 + correctifs phase 7)

- `pom.xml` — `spring-boot-starter-webmvc-test`
- `application.yml` — OIDC, forward-headers, cookies, retrait exclusions Security
- Packages `infrastructure.security*`, `web.auth`, `TenantResolutionFilter`
- Tests unitaires + `BffSecurityTest` + stub OIDC
- Phase 7 : filtre `/me` sur `ROLE_*` seulement ; test `tenant_id` blank

## Design deviations

| Écart | Sévérité | Résolution phase 7 |
|---|---|---|
| `/me.roles` incluait `SCOPE_*` | Mineur | **Corrigé** — filtre `ROLE_*` avant strip |
| Pas de `permitAll` OAuth2 explicite | Mineur | Accepté — short-circuit `oauth2Login` documenté |
| Boot 4 MockMvc package | Doc | Starter `webmvc-test` |

## Edge cases handled

- Claim `tenant_id` absent / blank / mismatch → session invalidée + 403
- Rôles déjà préfixés `ROLE_` → pas de double préfixe (mapper)
- `/me` anonyme → 401 avant contrôleur (entry point)
- `/me` n’expose pas `SCOPE_*`

## Requirements coverage (phase 7)

| Exigence | Statut |
|---|---|
| Retrait exclusions Security/OAuth2 | OK |
| Chaîne BFF CSRF + oauth2Login + logout | OK |
| `/api/auth/me` + 401 `/api/**` | OK |
| `registrationId=oidc` | OK |
| Mapper `realm_access` → `ROLE_*` ; JSON sans préfixe | OK |
| `TenantResolutionFilter` 403 + invalidate | OK |
| `forward-headers` ; cookie secure configurable | OK |
| Tests sans IdP ; ArchUnit ; Jacoco | OK |
| Non-goals M2M / webhooks / SPA / RS | Respectés |

## Evidence (phase 7)

| Commande / revue | Résultat |
|---|---|
| Revue design ↔ code | Aligné ; écarts mineurs traités / documentés |
| `./mvnw clean verify` | **58** tests, 0 failure ; Jacoco check OK |
| Lint feature | OK |
| Note Surefire | `BffSecurityTest` (pas `*IT` — exclu par défaut Surefire) |

## Follow-ups (hors tranche)

- Chaînes `@Order(1)` M2M et `@Order(2)` webhooks
- Feature `frontend` (Pinia / Axios)
- Feature `autorisation-conformite` (`@PreAuthorize`)
- Smoke local Keycloak réel (ops)

**Prochaine phase :** `dev-testing` (phase 8) si revalidation formelle, sinon `dev-review` (phase 9).
