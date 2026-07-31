---
phase: implementation
title: Implementation Guide
description: Technical implementation notes, patterns, and code guidelines
status: verified
checked: 2026-07-31
reviewed: 2026-07-31
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
│   ├── SecurityConfig                       # BFF @Order(3) + FilterRegistrationBean
│   ├── ApiAuthenticationEntryPoint
│   ├── CsrfCookieEagerLoadFilter
│   └── oidc.RealmAccessAuthoritiesMapper
├── tenant
│   ├── TenantIdValidator                    # existant
│   └── TenantResolutionFilter               # Security chain only
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
- `FilterRegistrationBean` **disabled** pour `TenantResolutionFilter` (évite double
  enregistrement servlet + Security)

### Mapping rôles

`RealmAccessAuthoritiesMapper` lit `realm_access.roles` sur l’**ID token**
uniquement. Provisioning IdP : mapper Keycloak vers l’ID token (pas seulement
l’access token).

`/me.roles` : autorités `ROLE_*` uniquement (préfixe retiré).

### YAML

- Exclusions **Security / OAuth2 client retirées** ; JPA / Flowable / Mail conservées
- `server.forward-headers-strategy: framework` actif ; `context-path` commenté
- `registrationId` / provider = **`oidc`**
- `${OIDC_ISSUER_URI}` / `${OIDC_CLIENT_SECRET}` sans défaut

### Tests sans IdP

`OidcClientTestConfiguration` — `ClientRegistrationRepository` `@Primary`.

### Contrat SPA (feature `frontend`)

```javascript
axios.defaults.withCredentials = true;
axios.defaults.xsrfCookieName = 'XSRF-TOKEN';
axios.defaults.xsrfHeaderName = 'X-XSRF-TOKEN';
// login : /oauth2/authorization/oidc
// GET /api/auth/me → 401 anonyme ; 403 tenant (session déjà invalidée)
```

## Design deviations

| Écart | Sévérité | Résolution |
|---|---|---|
| `/me.roles` incluait `SCOPE_*` | Mineur | Corrigé (phase 7) |
| Pas de `permitAll` OAuth2 explicite | Mineur | Accepté — short-circuit `oauth2Login` |
| Double filtre servlet `TenantResolutionFilter` | Important | **Corrigé phase 9** — `FilterRegistrationBean` disabled |
| Boot 4 MockMvc | Doc | Starter `webmvc-test` |

## Edge cases handled

- Claim `tenant_id` absent / blank / mismatch → session invalidée + 403
- Rôles déjà préfixés `ROLE_` → pas de double préfixe (mapper)
- `/me` anonyme → 401 ; pas de `SCOPE_*` dans JSON

## Review notes (phase 9)

| Point | Verdict |
|---|---|
| Design match | PASS |
| Logic gaps | PASS |
| Security | PASS (filtre servlet désactivé) |
| Integration | PASS |
| Tests | PASS — 66+ tests ; Jacoco ≥ 98 % lignes |
| Docs | PASS (deploy/monitoring templates hors chemin critique) |
| Bloquants | **Aucun** |
| Prêt à merger | **OUI** |

### Findings phase 9

1. **Corrigé :** `TenantResolutionFilter` n’est plus enregistré comme filtre servlet
   global (`FilterRegistrationBean#setEnabled(false)` + test).
2. **Suivi ops :** s’assurer que Keycloak mappe `realm_access.roles` dans l’**ID token**.
3. **Nice-to-have :** IT MockMvc exerçant le mapper via claim `realm_access` (aujourd’hui
   couvert unitairement ; `oidcLogin()` bypasse le flux login).

## Evidence (phase 9)

| Commande | Résultat |
|---|---|
| `./mvnw clean verify` | **66+** tests, 0 failure ; Jacoco check OK |
| Lint feature | OK |
| Revue holistique | Aucun bloquant ; prêt à merger |

## Follow-ups (hors tranche)

- Chaînes `@Order(1)` M2M et `@Order(2)` webhooks
- Feature `frontend` (Pinia / Axios)
- Feature `autorisation-conformite` (`@PreAuthorize`)
- Checklist IdP : claim `tenant_id` + `realm_access` sur ID token
- Smoke local Keycloak réel (ops)

**Lifecycle tranche A+ :** prêt à merger (PR).
