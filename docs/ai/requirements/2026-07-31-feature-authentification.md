---
phase: requirements
title: Requirements & Problem Understanding
description: Clarify the problem space, gather requirements, and define success criteria
status: reviewed
reviewed: 2026-07-31
updated: 2026-07-31
---

# Requirements & Problem Understanding

## Problem Statement
**What problem are we solving?**

FlowForge doit authentifier les utilisateurs humains via OAuth2/OIDC sans exposer
de JWT au browser, et garantir que la session OIDC appartient bien au tenant de
**cette** instance (`TenantProperties.id()`, décisions n° 13 / 14 / 15).

- **Affectés :** utilisateurs finaux (login / session), développeurs features aval
  (`frontend`, `autorisation-conformite`, APIs), ops (IdP Keycloak, reverse-proxy).
- **Situation actuelle :** socle hexagonal + multi-tenant config livrés ; exclusions
  autoconfig **Security / OAuth2 client** encore actives (`application.yml`) ; aucun
  `SecurityFilterChain`, pas de `/api/auth/me`, pas de garde claim `tenant_id`.
  Analyse : `analysis doc/03-authentification.md` + décisions n° 14 / 15 / TODO 32.
- **UX :** pas d’écran login maison — redirection vers l’IdP ; la SPA (hors scope)
  consommera `/api/auth/me` et initiera `/oauth2/authorization/oidc`.

## Goals & Objectives
**What do we want to achieve?**

### Primary goals

- Activer Spring Security + OAuth2 Client (retirer les exclusions Security/OAuth2
  listées TODO 32 — **pas** Flowable/JPA/Mail).
- Livrer une **chaîne BFF unique** : `oauth2Login`, session cookie `httpOnly`,
  CSRF double-submit (`CookieCsrfTokenRepository` + eager-load filter), logout
  RP-initiated (`OidcClientInitiatedLogoutSuccessHandler` sur `/api/auth/logout`).
- Exposer `GET /api/auth/me` → `name`, `email`, `roles` (noms realm **sans** préfixe
  `ROLE_`).
- Non authentifié : **401** sur `/api/**` ; redirection OAuth pour le reste.
- `registrationId` Spring = **`oidc`** → URL `/oauth2/authorization/oidc`
  (**écart** vs analyse qui citait `keycloak` — provider réel via `issuer-uri`).
- Mapper `realm_access.roles` → autorités Spring `ROLE_*`.
- `TenantResolutionFilter` : claim OIDC `tenant_id` présent **et** égal à
  `TenantProperties.id()`, sinon **403**.
- Activer `server.forward-headers-strategy: framework` ; laisser `context-path`
  commenté.
- Cookie session/CSRF : `SameSite=Lax` ; `secure` configurable
  (`app.security.cookie.secure`, défaut `true`, `false` en local HTTP).
- Tests sans IdP réel (mock OIDC / `oidcLogin()`).

### Secondary goals

- Documenter le contrat SPA (cookie, CSRF Axios, login URL `oidc`) pour
  `frontend`.
- Préparer le terrain pour M2M / webhooks (chaînes `@Order` futures) sans les
  implémenter.

### Non-goals

- Flux `client_credentials` / `spring-boot-starter-oauth2-resource-server`.
- Chaîne de sécurité webhooks (`/api/webhooks/**`).
- Store Pinia / pages Vue (feature `frontend`).
- `@PreAuthorize`, ACL dossier, politiques RBAC (feature `autorisation-conformite`).
- Spring Session Redis/JDBC (multi-répliques).
- Keycloak Testcontainers / docker-compose obligatoire pour `./mvnw test`.
- Activation `context-path` / datasource / retrait exclusions Flowable-JPA.
- Provisioning clients IdP (ops / point 31).

## User Stories & Use Cases
**How will users interact with the solution?**

- En tant qu’**utilisateur**, je m’authentifie via l’IdP et obtients une session
  cookie sur FlowForge, sans manipuler de JWT.
- En tant que **SPA** (future), j’appelle `GET /api/auth/me` : 401 si anonyme,
  sinon identité + rôles ; je redirige vers `/oauth2/authorization/oidc` si besoin.
- En tant qu’**utilisateur**, je me déconnecte via `/api/auth/logout` (session
  locale + RP-initiated logout IdP).
- En tant que **ops / sécurité**, je veux qu’un token dont le `tenant_id` ≠
  instance soit rejeté en 403.
- En tant que **développeur**, je veux `./mvnw test` vert sans Keycloak réel.

### Scénarios clés

1. Anonyme `GET /api/auth/me` → **401** (pas de redirect HTML).
2. Session OIDC valide + `tenant_id` = `app.tenant.id` → **200** avec name/email/roles.
3. Session OIDC avec `tenant_id` absent / mismatch → **403**.
4. Mutating request sans header CSRF → **403** ; avec cookie+header XSRF → OK.
5. Logout → invalidation session + redirection fin de session IdP.
6. Boot tests avec mock OIDC / propriétés test → contexte OK ; suite existante non régressée.

### Edge cases

- Claim `tenant_id` blank → 403.
- Cookie `secure=false` en profil local uniquement.
- Requête hors `/api/**` non authentifiée → redirect login OAuth (entry point).

## Success Criteria
**How will we know when we're done?**

- [ ] Exclusions Security / OAuth2 client retirées ; JPA/Flowable/Mail restent exclus.
- [ ] `SecurityFilterChain` BFF câblée (oauth2Login, CSRF, logout OIDC).
- [ ] `GET /api/auth/me` conforme ; 401 anonyme sur `/api/**`.
- [ ] `registrationId` = `oidc` documenté et configuré.
- [ ] Converter `realm_access.roles` → `ROLE_*` ; JSON `roles` sans préfixe.
- [ ] `TenantResolutionFilter` 403 si claim absent / mismatch.
- [ ] `forward-headers-strategy: framework` actif ; `context-path` commenté.
- [ ] `app.security.cookie.secure` (défaut true) appliqué session + CSRF.
- [ ] `./mvnw test` vert **sans** IdP réel ; ArchUnit / Jacoco OK.
- [ ] Docs requirements / design / testing alignés sur tranche A+.

**Performance benchmarks :** N/A — chemin auth session, pas d’IO métier nouveau.

## Constraints & Assumptions
**What limitations do we need to work within?**

### Technical constraints

- Java 25 · Spring Boot 4.1 · Security 7 · deps déjà dans `pom.xml`
  (`spring-boot-starter-security`, `oauth2-client`, `spring-security-test`).
- Mono-module ; domaine **sans** Spring — filtre / security en `infrastructure`.
- Décisions n° 13, 14, 15, 27, TODO 32 font foi (sauf `registrationId` → `oidc`).
- Pas de resource-server dans cette tranche.

### Business / product

- Browser ne manipule jamais de JWT (BFF).
- Provider interchangeable via `issuer-uri` ; URL login stable (`oidc`).

### Time / budget / rollout

- Branche `feature-authentification` / PR ; pas de feature flag.
- Keycloak réel = env local/ops, pas CI obligatoire.

### Assumptions nommées

- `analysis doc/03-authentification.md` + revue pré-implémentation (n° 15) font
  foi sauf écarts actés (`registrationId=oidc`).
- L’IdP émet un claim custom `tenant_id` et `realm_access.roles` (Keycloak).
- `TenantProperties` / `TenantIdValidator` déjà livrés (multi-tenant).
- La SPA Axios (`withCredentials`, XSRF) sera câblée dans `frontend`.
- M2M / webhooks ajouteront des `SecurityFilterChain` `@Order` plus tard.
- Session in-memory suffit (pas de scale multi-pod V1).

## Questions & Open Items
**What do we still need to clarify?**

- Aucune question bloquante pour rédiger design/testing de la tranche A+.
- **Différé :** M2M, webhooks security chain, SPA, RBAC annotations, Spring Session,
  clients IdP provisioning, Testcontainers Keycloak.

## Alternatives brainstormées

| Approche | Trade-off | Verdict |
|---|---|---|
| A — BFF humain only | Plus petit, retarde garde tenant | Écarté |
| B — BFF + garde tenant (A+) | Ferme contrat multi-tenant sans M2M | **Retenu** |
| C — Analyse complète (+ M2M) | Dépend connecteurs / resource-server | Écarté pour V1 feature |
| JWT direct SPA | XSS / token exposé | Écarté (décision produit) |
| `registrationId=keycloak` | Aligné analyse, vendor dans l’URL | Écarté — `oidc` retenu |
| Toujours redirect OAuth | Casse bootstrap SPA `/me` | Écarté — 401 sur `/api/**` |
| Testcontainers Keycloak | Fidèle, CI lourde | Écarté — mock OIDC |
| `secure` cookie toujours true | Casse HTTP local | Écarté — propriété configurable |
| Mapping rôles différé | `/me.roles` vide / inutile | Écarté — converter dans tranche |
| Autorités sans `ROLE_` | Incompatible `hasRole` futur | Écarté |

## Clarifications actées

1. **Périmètre :** option B — BFF + CSRF + logout + `/me` + converter rôles +
   `TenantResolutionFilter` ; sans M2M / webhooks / SPA.
2. **Non-auth :** 401 JSON sur `/api/**`, redirect OAuth ailleurs.
3. **Tests / local IdP :** mock OIDC ; Keycloak hors CI.
4. **Cookies :** `SameSite=Lax` ; `app.security.cookie.secure` (défaut true).
5. **Proxy :** `forward-headers-strategy: framework` actif ; `context-path` commenté.
6. **Login URL :** `/oauth2/authorization/oidc` (`registrationId=oidc`).
7. **Rôles :** `realm_access.roles` → `ROLE_*` ; JSON `/me.roles` sans préfixe.

## Review notes (phase 2)

| Point | Résolution |
|---|---|
| Conformité template README | Sections présentes + UX / perf / rollout / assumptions / alternatives |
| Périmètre A vs A+ vs complet | **A+** confirmé (BFF + garde tenant) |
| Non-auth API | **401** sur `/api/**` acté |
| IdP en CI | **Mock OIDC** acté |
| Cookie `secure` | Propriété `app.security.cookie.secure` actée |
| `forward-headers` | **Actif** ; `context-path` commenté |
| `registrationId` | **`oidc`** (écart analyse `keycloak` documenté) |
| Mapping rôles | Converter `realm_access` + préfixe `ROLE_` ; JSON sans préfixe |
| Questions ouvertes bloquantes | Aucune |
| Design / testing drafts | Alignés sur les clarifications ; revue design = phase 3 |

**Prochaine phase :** `dev-design` (phase 3 — revue design contre requirements).
