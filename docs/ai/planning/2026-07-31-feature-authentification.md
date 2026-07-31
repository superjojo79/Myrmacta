---
phase: planning
title: Project Planning & Task Breakdown
description: Break down work into actionable tasks and estimate timeline
status: planned
created: 2026-07-31
updated: 2026-07-31
---

# Project Planning & Task Breakdown

## Milestones
**What are the major checkpoints?**

- [ ] **M1 — Config & propriétés** : yaml OIDC/`forward-headers`/cookies ; `SecurityCookieProperties`
- [ ] **M2 — Pièces security (TDD)** : mapper rôles, entry point 401, `TenantResolutionFilter`
- [ ] **M3 — BFF câblé** : `SecurityConfig` `@Order(3)`, `AuthController`, retrait exclusions
- [ ] **M4 — Harness tests + IT** : stub `ClientRegistrationRepository`, MockMvc OIDC
- [ ] **M5 — Preuves & docs** : suite / ArchUnit / Jacoco ; doc implementation + contrat SPA

## Task Breakdown
**What specific work needs to be done?**

Statuts : `[ ]` not started · `[~]` in progress · `[x]` done · `[!]` blocked

Ordre recommandé : **T1 → T2** puis **T3 ∥ T4 ∥ T5** → **T6 → T7 → T8 → T9 → T10 → T11**.

### M1 — Config & propriétés

- [ ] **T1. YAML sécurité + OIDC client `oidc`**
  - **Outcome :** `forward-headers-strategy: framework` actif ; `context-path` reste
    commenté ; session/CSRF cookies SameSite=Lax + `secure` lié à
    `app.security.cookie.secure` (défaut `true`) ; registration/provider **`oidc`**
    avec `${OIDC_ISSUER_URI}` / `${OIDC_CLIENT_SECRET}` **sans défaut** ;
    `OIDC_CLIENT_ID` défaut `mon-app` OK. Exclusions Security/OAuth2 **pas encore
    retirées** (retrait en T7) pour ne pas casser le boot intermédiaire.
  - **Depends on :** —
  - **Validation :** relecture yaml ; placeholders présents ; JPA/Flowable toujours exclus.
  - **Tests liés :** Manual yaml ; Config cookie (T2).

- [ ] **T2. `SecurityCookieProperties` + tests**
  - **Outcome :** record/`@ConfigurationProperties(prefix=app.security.cookie)` ;
    enable via config security ; défaut `secure=true` ; override testable.
  - **Depends on :** T1 (préfixe yaml)
  - **Validation :** tests unitaires / binding verts.
  - **Tests liés :** Unit — SecurityCookieProperties.

### M2 — Pièces security (TDD)

- [ ] **T3. `RealmAccessAuthoritiesMapper` + tests**
  - **Outcome :** `GrantedAuthoritiesMapper` dans `infrastructure.security.oidc` ;
    `realm_access.roles` → `ROLE_*` ; claim absent → pas d’autorités rôle ;
    rôles déjà préfixés gérés sans double `ROLE_`.
  - **Depends on :** —
  - **Validation :** tests unitaires mapper verts.
  - **Tests liés :** Unit — RealmAccessAuthoritiesMapper.

- [ ] **T4. `ApiAuthenticationEntryPoint` + tests**
  - **Outcome :** `/api/**` → **401 body vide** ; hors API → délègue au entry point
    login OAuth (redirect).
  - **Depends on :** —
  - **Validation :** tests unitaires / mock request-response.
  - **Tests liés :** Unit — ApiAuthenticationEntryPoint ; IT 401 (T9).

- [ ] **T5. `TenantResolutionFilter` + tests**
  - **Outcome :** skip si anonyme ; si `OidcUser` : claim `tenant_id` vs
    `TenantProperties.id()` ; absent/blank/mismatch → **`session.invalidate()` +
    403 body vide** ; match → `chain.doFilter`.
  - **Depends on :** —
  - **Validation :** tests unitaires filtre (mock filter chain / session).
  - **Tests liés :** Unit — TenantResolutionFilter ; IT 403 (T9).

### M3 — BFF câblé

- [ ] **T6. `AuthController` + `AuthUserResponse`**
  - **Outcome :** `GET /api/auth/me` → `{name,email,roles}` avec rôles **sans**
    préfixe `ROLE_` (strip depuis authorities) ; package `infrastructure.web.auth`.
  - **Depends on :** —
  - **Validation :** compile ; comportement couvert en T9 (et smoke unitaire éventuel).
  - **Tests liés :** IT `/me` 200 (T9).

- [ ] **T7. `SecurityConfig` BFF `@Order(3)` + retrait exclusions Security/OAuth2**
  - **Outcome :** chaîne BFF : CSRF double-submit + eager-load filter ; `oauth2Login`
    + mapper T3 ; logout `/api/auth/logout` + `OidcClientInitiatedLogoutSuccessHandler` ;
    entry point T4 ; filtre T5 après auth ; cookies secure via T2. Retirer du yaml
    les exclusions Security / OAuth2 client (TODO 32 partiel) — **garder**
    JPA/Flowable/Mail. Aucune chaîne M2M/webhooks.
  - **Depends on :** T1–T6
  - **Validation :** classes présentes ; yaml exclusions Security absentes ;
    boot réel exige OIDC env (hors tests).
  - **Tests liés :** IT stub contexte (T8/T9) ; Manual yaml.

### M4 — Harness tests + IT

- [ ] **T8. Stub `ClientRegistrationRepository` + config test**
  - **Outcome :** `@TestConfiguration` / `src/test/resources` permettant
    `@SpringBootTest` / MockMvc **sans** discovery HTTP ni `OIDC_ISSUER_URI` réel ;
    registration id `oidc`.
  - **Depends on :** T7
  - **Validation :** contexte Spring de test démarre ; DomainConfigTest / Tenant*
    adaptés si besoin (import stub).
  - **Tests liés :** IT « stub repository » ; régression DomainConfig / Tenant*.

- [ ] **T9. Tests MockMvc BFF (mock OIDC)**
  - **Outcome :** couverture IT :
    - `/api/auth/me` anonyme → 401 body vide (pas 302)
    - `oidcLogin()` + `tenant_id=tenant_dev` + rôles → 200, `roles` sans `ROLE_`
    - `tenant_id` other / absent → 403 + session invalidée (2ᵉ `/me` → 401)
    - POST sans CSRF → 403 ; avec CSRF → pas d’échec CSRF
    - smoke logout URL configurée
  - **Depends on :** T7, T8
  - **Validation :** tests IT verts.
  - **Tests liés :** Integration Tests (testing doc).

### M5 — Preuves & docs

- [ ] **T10. Suite complète + ArchUnit + Jacoco**
  - **Outcome :** `./mvnw test` et `./mvnw verify` verts ; ArchUnit : domaine sans
    Spring Security / `infrastructure.security` ; Jacoco bundle ≥ 80 %.
  - **Depends on :** T2–T5, T8, T9
  - **Validation :** evidence fraîche commandes + rapport Jacoco.
  - **Tests liés :** ArchUnit ; coverage ; régression workflow.

- [ ] **T11. Documentation implementation + contrat SPA**
  - **Outcome :** doc `implementation` : packages, yaml, stub tests, contrat SPA
    (`/oauth2/authorization/oidc`, Axios CSRF, 401/403) ; checklist manuelle cochable.
  - **Depends on :** T7
  - **Validation :** `lint --feature authentification` OK ; relecture manuelle.
  - **Tests liés :** Manual Testing.

## Traceability — testing → tasks

| Scénario (testing doc) | Task(s) | Statut |
|---|---|---|
| Mapper `realm_access` → `ROLE_*` / edge | T3 | todo |
| TenantResolutionFilter skip / 403+invalidate / OK | T5, T9 | todo |
| SecurityCookieProperties défaut / override | T2 | todo |
| ApiAuthenticationEntryPoint API vs hors API | T4, T9 | todo |
| `/me` anonyme 401 body vide | T4, T7, T9 | todo |
| `/me` oidcLogin 200 + roles sans préfixe | T3, T6, T9 | todo |
| tenant mismatch / absent → 403 + session | T5, T9 | todo |
| CSRF mutant | T7, T9 | todo |
| Logout URL smoke | T7, T9 | todo |
| Stub ClientRegistrationRepository | T8, T9 | todo |
| Régression DomainConfig / Tenant* / workflow | T8, T10 | todo |
| ArchUnit domaine ↛ Security | T10 | todo |
| Jacoco ≥ 80 % | T10 | todo |
| Manual yaml / SPA / forward-headers | T1, T7, T11 | todo |
| E2E Keycloak | — | N/A hors scope |

## Dependencies
**What needs to happen in what order?**

```text
T1 → T2
T3, T4, T5 (parallélisables, TDD)
T6
T7 (câblage + retrait exclusions)  ← après T1–T6
T8 (stub tests) → T9 (IT)
T10 (preuves) ; T11 (docs, peut démarrer dès T7)
```

**Externe :** JDK 25 ; pas de Keycloak CI. Dépend de multi-tenant livré
(`TenantProperties`).

## Timeline & Estimates
**When will things be done?**

Pas d’échéances calendaires. Envergure relative :

| Task | Envergure |
|---|---|
| T1 | Petite (yaml) |
| T2–T5 | Petites (TDD unitaire) |
| T6 | Petite |
| T7 | Moyenne (SecurityFilterChain + yaml) |
| T8–T9 | Moyenne (harness + IT — risque Boot 4 / Security 7) |
| T10–T11 | Petite–moyenne |

## Risks & Mitigation
**What could go wrong?**

| Risque | Mitigation |
|---|---|
| Discovery OIDC casse `@SpringBootTest` | T8 stub repository **avant** d’exiger la suite verte ; jamais d’issuer réel en CI |
| Retrait exclusions trop tôt | Exclusions Security retirées seulement en T7 avec config prête |
| `oidcLogin()` + claims `realm_access` / `tenant_id` | Fixtures IT explicites ; doc test data |
| CSRF eager-load / XOR handler | Suivre design (handler non-XOR + filtre eager) ; IT CSRF |
| Régression DomainConfig / Tenant tests | Adapter imports stub en T8 ; T10 gate |
| Scope creep M2M / webhooks / SPA | Non-goals ; BFF `@Order(3)` seulement |
| JDK agent ≠ 25 | `JAVA_HOME` Temurin 25 (comme multi-tenant) |
| Placeholder OIDC casse run local oublié | Documenté ; fail-fast voulu hors tests |

## Resources Needed
**What do we need to succeed?**

- Worktree : `/workspace/.worktrees/feature-authentification`
- Branche : `feature-authentification`
- JDK 25 + `./mvnw`
- Docs : requirements / design / testing `2026-07-31-feature-authentification` (reviewed / reviewed / draft)
- Analyse : `analysis doc/03-authentification.md` (écarts design font foi)

## Next action

**Phase 4 terminée** — enchaîner **phase 5** (`dev-implementation`) en démarrant par **T1**
(puis T2, puis T3–T5 en TDD).

## Progress summary

Plan initial créé (2026-07-31) : 11 tâches, 5 milestones, traçabilité testing
complète. Aucune tâche démarrée.
