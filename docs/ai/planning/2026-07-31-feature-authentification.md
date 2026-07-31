---
phase: planning
title: Project Planning & Task Breakdown
description: Break down work into actionable tasks and estimate timeline
status: implemented
created: 2026-07-31
updated: 2026-07-31
---

# Project Planning & Task Breakdown

## Milestones
**What are the major checkpoints?**

- [x] **M1 — Config & propriétés** : yaml OIDC/`forward-headers`/cookies ; `SecurityCookieProperties`
- [x] **M2 — Pièces security (TDD)** : mapper rôles, entry point 401, `TenantResolutionFilter`
- [x] **M3 — BFF câblé** : `SecurityConfig` `@Order(3)`, `AuthController`, retrait exclusions
- [x] **M4 — Harness tests + IT** : stub `ClientRegistrationRepository`, MockMvc OIDC
- [x] **M5 — Preuves & docs** : suite / ArchUnit / Jacoco ; doc implementation + contrat SPA

## Task Breakdown
**What specific work needs to be done?**

Statuts : `[ ]` not started · `[~]` in progress · `[x]` done · `[!]` blocked

### M1 — Config & propriétés

- [x] **T1. YAML sécurité + OIDC client `oidc`**
  - **Outcome :** forward-headers actif ; cookies SameSite=Lax + secure configurable ;
    registration `oidc` ; issuer/secret requis ; exclusions Security retirées avec T7.
  - **Depends on :** —
  - **Validation :** yaml relu ; JPA/Flowable exclus.
  - **Tests liés :** Manual yaml.

- [x] **T2. `SecurityCookieProperties` + tests**
  - **Outcome :** `@ConfigurationProperties` + défaut `secure=true`.
  - **Depends on :** T1
  - **Validation :** `SecurityCookiePropertiesTest` vert.
  - **Tests liés :** Unit — SecurityCookieProperties.

### M2 — Pièces security (TDD)

- [x] **T3. `RealmAccessAuthoritiesMapper` + tests**
  - **Outcome :** `realm_access.roles` → `ROLE_*`.
  - **Depends on :** —
  - **Validation :** 4 tests mapper verts.
  - **Tests liés :** Unit — RealmAccessAuthoritiesMapper.

- [x] **T4. `ApiAuthenticationEntryPoint` + tests**
  - **Outcome :** `/api/**` → 401 vide ; hors API → délégué.
  - **Depends on :** —
  - **Validation :** 2 tests entry point verts.
  - **Tests liés :** Unit — ApiAuthenticationEntryPoint.

- [x] **T5. `TenantResolutionFilter` + tests**
  - **Outcome :** mismatch/absent → invalidate + 403.
  - **Depends on :** —
  - **Validation :** 4 tests filtre verts.
  - **Tests liés :** Unit — TenantResolutionFilter.

### M3 — BFF câblé

- [x] **T6. `AuthController` + `AuthUserResponse`**
  - **Outcome :** `GET /api/auth/me` ; rôles sans `ROLE_`.
  - **Depends on :** —
  - **Validation :** `AuthControllerTest` + IT `/me`.
  - **Tests liés :** Unit AuthController ; IT `/me`.

- [x] **T7. `SecurityConfig` BFF `@Order(3)` + retrait exclusions Security/OAuth2**
  - **Outcome :** chaîne BFF complète ; TODO 32 partiel.
  - **Depends on :** T1–T6
  - **Validation :** IT BFF + boot tests.
  - **Tests liés :** BffSecurityTest.

### M4 — Harness tests + IT

- [x] **T8. Stub `ClientRegistrationRepository` + config test**
  - **Outcome :** `OidcClientTestConfiguration` + `application.properties` test.
  - **Depends on :** T7
  - **Validation :** contexte Spring sans discovery.
  - **Tests liés :** IT stub ; DomainConfig / TenantConfig imports.

- [x] **T9. Tests MockMvc BFF (mock OIDC)**
  - **Outcome :** 401/200/403/CSRF/logout/registration id couverts.
  - **Depends on :** T7, T8
  - **Validation :** `BffSecurityTest` vert.
  - **Tests liés :** Integration Tests.

### M5 — Preuves & docs

- [x] **T10. Suite complète + ArchUnit + Jacoco**
  - **Outcome :** `./mvnw test` + `verify` verts ; Jacoco ≥ 80 %.
  - **Depends on :** T2–T5, T8, T9
  - **Validation :** 50 tests ; Jacoco check OK (≥ 80 % lignes).
  - **Tests liés :** ArchUnit ; coverage.

- [x] **T11. Documentation implementation + contrat SPA**
  - **Outcome :** doc implementation + contrat SPA Axios/`oidc`.
  - **Depends on :** T7
  - **Validation :** lint feature OK.
  - **Tests liés :** Manual Testing.

## Traceability — testing → tasks

| Scénario (testing doc) | Task(s) | Statut |
|---|---|---|
| Mapper `realm_access` → `ROLE_*` / edge | T3 | done |
| TenantResolutionFilter skip / 403+invalidate / OK | T5, T9 | done |
| SecurityCookieProperties défaut / override | T2 | done |
| ApiAuthenticationEntryPoint API vs hors API | T4, T9 | done |
| `/me` anonyme 401 body vide | T4, T7, T9 | done |
| `/me` oidcLogin 200 + roles sans préfixe | T3, T6, T9 | done |
| tenant mismatch / absent → 403 + session | T5, T9 | done |
| CSRF mutant | T7, T9 | done |
| Logout URL smoke | T7, T9 | done |
| Stub ClientRegistrationRepository | T8, T9 | done |
| Régression DomainConfig / Tenant* / workflow | T8, T10 | done |
| ArchUnit domaine ↛ Security | T10 | done |
| Jacoco ≥ 80 % | T10 | done |
| Manual yaml / SPA / forward-headers | T1, T7, T11 | done |
| E2E Keycloak | — | N/A hors scope |

## Dependencies
**What needs to happen in what order?**

Exécuté : T1→T2 ; T3∥T4∥T5 ; T6→T7→T8→T9→T10+T11.

## Timeline & Estimates
**When will things be done?**

Phase 5 complétée (2026-07-31). Pas d’échéances calendaires.

## Risks & Mitigation
**What could go wrong?**

| Risque | Mitigation / issue |
|---|---|
| Boot 4 MockMvc package move | Ajout `spring-boot-starter-webmvc-test` |
| Discovery OIDC en tests | Stub `ClientRegistrationRepository` |
| JDK 21 vs 25 | Temurin 25 (`JAVA_HOME`) |

## Resources Needed
**What do we need to succeed?**

- Worktree : `/workspace/.worktrees/feature-authentification`
- JDK 25 + `./mvnw`

## Next action

**Phase 9 terminée** — prêt à merger. Suivi ops : mapper IdP `realm_access` sur
ID token ; features aval M2M / frontend / autorisation.

## Progress summary

Lifecycle A+ complet (phases 1–5, 7–9). Correctif phase 9 : désactivation
enregistrement servlet de `TenantResolutionFilter`. Suite verte ; Jacoco OK.
