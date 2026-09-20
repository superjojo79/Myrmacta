---
phase: implementation
title: Implementation Guide
description: Technical implementation notes, patterns, and code guidelines
status: in-progress
created: 2026-09-20
updated: 2026-09-20
feature: headers-http-globaux
lot: A4
phase5: 2026-09-20
---

# Implementation Guide — `headers-http-globaux` (lot A4)

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

Preuves Phase 5 (session) :

- `./mvnw verify -Dskip.frontend=true` : **1898 tests**, 0 échec, Jacoco bundle LINE 93.9 % / BRANCH 77.9 % (seuils tenus).
- Classes du lot : `SecurityHeadersCustomizer` LINE+BRANCH **100 %** ; `SecurityHeadersProperties` LINE **100 %** ; `SecurityConfig` LINE 95.7 % (branches JWT M2M pré-existantes).
- `cd frontend && npm run test` : **351** Vitest.
- `cd frontend && npm run test:e2e` : **73/73** Playwright, dont E2E-HDR-1/2.

### Écarts / trouvailles Phase 5

- `Binder.bind()` sur un préfixe vide reste unbound même avec `@DefaultValue` ; le test de défaut utilise `bindOrCreate`.
- `EvaluateDossierRetentionUseCaseImplTest` avait `NOW` figé au `2026-08-19` alors que `evaluate()` lit `Instant.now()` : au 2026-09-20 le cas « pas encore expiré » archivait (rétention 30 j). Hors lot A4 ; `NOW = Instant.now()` pour que les offsets restent valides. Pas un bug produit.

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
