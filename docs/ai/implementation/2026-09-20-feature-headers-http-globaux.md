---
phase: implementation
title: Implementation Guide
description: Technical implementation notes, patterns, and code guidelines
status: reviewed
created: 2026-09-20
updated: 2026-09-20
feature: headers-http-globaux
lot: A4
phase5: 2026-09-20
phase7: 2026-09-20
phase8: 2026-09-20
phase9: 2026-09-20
checked: 2026-09-20
reviewed: 2026-09-20
---

# Implementation Guide — `headers-http-globaux` (lot A4)

## Phase 7 — Check implementation

Revue fichier par fichier contre
[design](../design/2026-09-20-feature-headers-http-globaux.md) et
[requirements](../requirements/2026-09-20-feature-headers-http-globaux.md).
**Alignement : conforme.** Aucune déviation bloquante. Pas de retour
Phase 3/5.

Task tracing indisponible : `npx ai-devkit@latest task list --name headers-http-globaux --json`
→ `error: unknown command 'task'`. `lint --feature` OK sur les docs ;
`[MISS]` branche `feature-headers-http-globaux` ignoré (branche cloud
`cursor/headers-http-globaux-ec9a`).

### Décisions design vs code

| Décision | Code |
|---|---|
| D-MECHANISM B | `SecurityHeadersCustomizer.apply(HttpSecurity)` injecté dans les 4 `@Bean` filter chains ; pas de `OncePerRequestFilter` |
| D-CSP-STRING A | Constante publique `CONTENT_SECURITY_POLICY` identique au design (égalité testée) |
| D-SWITCH-OFF A | `enabled=false` → `return` avant `http.headers()` |
| D-HSTS B | `hstsEnabled()` = `!environment.matchesProfiles("local", "test")` ; max-age 31536000, includeSubDomains, preload false |
| D-CONFIG C / D-LOCAL-HEADERS A | `app.security.headers.enabled: ${APP_SECURITY_HEADERS_ENABLED:true}` ; **pas** d’override dans `application-local.yml` |
| D-HEADERS-CHAINS B | actuator `@Order(0)`, M2M, webhooks/callbacks, BFF |
| A-PREVIEW | `DocumentGenerationController.PREVIEW_CSP` inchangé (`default-src 'none'; sandbox`) |
| A-NO-META | pas de meta CSP dans `frontend/index.html` |
| A-NO-GUIDE-UI | `GUIDE-UTILISATEUR.md` hors diff |
| Surfaces interdites | pas Compose, pas CSRF, pas `context-path`, pas `ConnectorCatalog` |

### Revue fichier par fichier

| Fichier | Verdict |
|---|---|
| `SecurityHeadersProperties` | Record miroir `SecurityCookieProperties`, préfixe `app.security.headers`, `@DefaultValue("true")` |
| `SecurityHeadersCustomizer` | Flux design (kill switch → writers → HSTS profil). API HeadersConfigurer Spring Security 7 telle que prévue |
| `SecurityConfig` | `@EnableConfigurationProperties` étendu ; `apply(http)` avant chaque `http.build()` |
| `application.yml` | Kill switch à côté de `app.security.cookie` |
| `application-local.yml` | Inchangé (HSTS coupé par profil, pas par kill switch) |
| Tests properties / customizer / MockMvc / disabled / actuator | Couvrent T-PROP-*, T-CSP-VALUE, T-HSTS-*, T-SWITCH-OFF, T-CHAIN-*, T-HSTS-ABSENT-TEST, T-CHAIN-OFF |
| `DocumentPreviewApiTest` | T-PREVIEW-M13 : header exact M13 toujours asserté |
| `frontend/e2e/pilotage.spec.ts` | E2E-HDR-1 Supervision + E2E-HDR-2 preview iframes ; A-E2E-CSP-INJECT |
| `.env.example` / GETTING-STARTED / DEPLOIEMENT / CHANGELOG | T5.1–T5.4 |

### Écarts (non bloquants)

| Sévérité | Écart | Traitement |
|---|---|---|
| Info | E2E-HDR-1 ouvre **Supervision**, pas `/admin/bpmn-editor` | Autorisé par requirements (« supervision **ou** éditeur »). Risque résiduel `unsafe-eval` bpmn-js : follow-up Phase 8 si un test éditeur est ajouté |
| Info | HSTS jamais observé en MockMvc (profil `test`) | Prévu D-HSTS / T2.2 ; lambda prod couverte unitairement |
| Info | `DocumentPreviewApiTest` lit `getHeader()` (première valeur) | M13 non affaiblie (égalité exacte). Dual-header AND navigateur (A-PREVIEW) non asserté séparément |
| Info | `SecurityConfig` BRANCH 50 % | Branches JWT M2M **pré-existantes**, hors paquet A4 |
| Hors lot | `EvaluateDossierRetentionUseCaseImplTest.NOW = Instant.now()` | Horloge figée vs `evaluate()` ; pas un bug produit A4 |
| Info | Test défaut properties : `bindOrCreate` | `Binder.bind()` sur préfixe vide reste unbound malgré `@DefaultValue` |

### Phase 8

Preuves fraîches : verify 1898 / Vitest 351 / Playwright **75/75**.
E2E-HDR-3/4 : éditeurs BPMN et DMN sans violation CSP (pas d’`unsafe-eval`).
Flake E2E-EDIT-BPMN-1/3 : `expect.poll` sur `putCalled`.

### Phase 9 — revue holistique

Stance : findings d’abord. **Aucun blocking.**

| Sévérité | Fichier | Issue | Reco |
|---|---|---|---|
| — | — | Pas de finding bloquant | Merger PR #128 |
| Low | `pilotage.spec.ts` `A4_CSP` | Copie de la constante Java (A-E2E-CSP-INJECT) | Commentaire de synchro ajouté ; MockMvc reste la preuve d’émission Spring |
| Low | `DocumentPreviewApiTest` | `getHeader()` = première CSP | M13 exacte prouve non-affaiblissement ; AND dual-header non asserté |
| Low | `SecurityConfig` | BRANCH 50 % | Branches JWT M2M pré-existantes, hors A4 |
| Info | `ObservabilityActuatorIT` | FQCN du customizer | Cosmétique |
| Info | E2E Vite `:4173` | N’atteint pas Spring | Attendu ; émission = MockMvc + actuator IT |

Checklist finale : design match ; 4 `apply(http)` ; kill switch no-op ;
HSTS profil ; CSRF `HttpOnly=false` intact ; pas Compose / GUIDE-UI ;
CHANGELOG Sécurité PR #128 ; rollback `APP_SECURITY_HEADERS_ENABLED=false`.
Callers : customizer seulement depuis `SecurityConfig` + tests. Pas de
nouvelle dépendance. Pas de migration. HSTS sans `preload` (réversible).

## Development Setup

- JDK 25 (`JAVA_HOME` Temurin 25). `./mvnw verify -Dskip.frontend=true` pour le backend.
- Frontend : `cd frontend && npm run test` + `npm run test:e2e`.
- Variable : `APP_SECURITY_HEADERS_ENABLED` (défaut `true`). Pas d’override dans `application-local.yml`.

## Code Structure

| Fichier | Rôle |
|---|---|
| `infrastructure.config.SecurityHeadersProperties` | Record `@ConfigurationProperties(prefix = "app.security.headers")`, kill switch `enabled` `@DefaultValue("true")` |
| `infrastructure.security.SecurityHeadersCustomizer` | `@Component` `apply(HttpSecurity)` — CSP / XFO DENY / nosniff / Referrer-Policy / HSTS conditionnel |
| `infrastructure.security.SecurityConfig` | Injecte le customizer dans les 4 `@Bean` filter chains ; `@EnableConfigurationProperties` étendu |

Pas de changement `domain/**`. `DocumentGenerationController.PREVIEW_CSP` inchangé.

## Implementation Notes

- **D-MECHANISM B** : customizer par chain, pas de `OncePerRequestFilter` (raterait l’actuator `:8090`).
- **D-CSP-STRING A** : constante `CONTENT_SECURITY_POLICY` unique (tests + writers).
- **D-SWITCH-OFF A** : `enabled=false` → `return` immédiat, Spring defaults (XFO/nosniff) restent.
- **HSTS** : `!environment.matchesProfiles("local", "test")` ; `max-age=31536000`, `includeSubDomains`, `preload=false`. Sous profil `test`, MockMvc n’émet pas HSTS (prouvé).
- **A-E2E-CSP-INJECT** : Playwright Vite `:4173` n’atteint pas Spring. `E2E-HDR-1/2` injectent la CSP A4 sur les réponses `document`.

Preuves Phase 5 (session précédente, à reconfirmer Phase 8) :

- `./mvnw verify -Dskip.frontend=true` : **1898 tests**, 0 échec, Jacoco bundle LINE 93.9 % / BRANCH 77.9 %.
- Classes du lot : `SecurityHeadersCustomizer` LINE+BRANCH **100 %** ; `SecurityHeadersProperties` LINE **100 %** ; `SecurityConfig` LINE 95.7 % (branches JWT M2M pré-existantes).
- `cd frontend && npm run test` : **351** Vitest.
- `cd frontend && npm run test:e2e` : **73/73** Playwright, dont E2E-HDR-1/2.

## Integration Points

- `application.yml` : `app.security.headers.enabled: ${APP_SECURITY_HEADERS_ENABLED:true}` à côté de `app.security.cookie`.
- Docs ops : `.env.example`, GETTING-STARTED, DEPLOIEMENT (HSTS applicatif vs proxy).

## Error Handling

Kill switch ops : `APP_SECURITY_HEADERS_ENABLED=false` sans rebuild. Pas de log dédié (en-têtes silencieux).

## Performance Considerations

HeaderWriter Spring — négligeable.

## Security Notes

- CSP enforce SPA ; preview M13 `default-src 'none'; sandbox` conservée (AND navigateur).
- Pas de CSRF / cookies (S-8). Pas de `unsafe-eval`. Si Playwright révèle une violation réelle : élargir D-CSP-STRING dans le cycle, pas Report-Only.
