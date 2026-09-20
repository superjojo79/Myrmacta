---
phase: testing
title: Testing Strategy
description: Define testing approach, test cases, and quality assurance
status: verified
created: 2026-09-20
updated: 2026-09-20
feature: headers-http-globaux
lot: A4
phase3: 2026-09-20
phase4: 2026-09-20
phase5: 2026-09-20
phase8: 2026-09-20
verified: 2026-09-20
---

# Testing Strategy — `headers-http-globaux` (lot A4)

Scénarios dérivés des success criteria requirements. Playwright **obligatoire** (CSP SPA).

## Phase 8 — résultats (session 2026-09-20)

| Commande | Exit | Résultat |
|---|---|---|
| `./mvnw verify -Dskip.frontend=true` | 0 | Surefire **1898** tests, 0 failure, 0 error |
| Jacoco bundle | — | LINE **93,9 %** (9187/9782) BRANCH **77,9 %** (2995/3845) |
| `SecurityHeadersCustomizer` | — | LINE **100 %** (21/21) BRANCH **100 %** (6/6) |
| `SecurityHeadersProperties` | — | LINE **100 %** (1/1) |
| `SecurityConfig` | — | LINE **95,7 %** (67/70) BRANCH **50 %** (4/8, JWT M2M pré-existant) |
| `cd frontend && npm run test` | 0 | **351** Vitest |
| `cd frontend && npx playwright test` | 0 | **75/75** (dont E2E-HDR-1–4) |

Gap refermé en Phase 8 : E2E-HDR-3 (éditeur BPMN) et E2E-HDR-4 (éditeur DMN)
sous CSP injectée — **aucune** violation, **pas** d’`unsafe-eval`. D-CSP-STRING A
tenu. Flake pré-existant E2E-EDIT-BPMN-1/3 (`putCalled` immédiat) : `expect.poll`
comme E2E-EDIT-DMN-1.

## Test Coverage Goals

- Backend : classes **ajoutées** (`SecurityHeadersProperties`, `SecurityHeadersCustomizer`) à 100 % LINE+BRANCH — **tenu**.
- `SecurityConfig` touché : LINE 95,7 % ; branches manquantes = JWT M2M pré-existantes.
- Jacoco bundle LINE ≥ 85 %, BRANCH ≥ 75 % — **tenu**.
- Frontend : Vitest régression + Playwright obligatoire — **tenu**.

## Unit Tests

Fichiers :

- `src/test/java/com/flowforge/infrastructure/config/SecurityHeadersPropertiesTest.java`
- `src/test/java/com/flowforge/infrastructure/security/SecurityHeadersCustomizerTest.java`

### Properties / bind

- [x] `headers.enabled` défaut `true` (T-PROP-DEFAULT)
- [x] `headers.enabled=false` bind depuis l’environnement (T-PROP-OFF)

### `SecurityHeadersCustomizer`

- [x] Construit la CSP D-CSP-STRING = A (T-CSP-VALUE)
- [x] HSTS writer absent si profil `local` (T-HSTS-LOCAL)
- [x] HSTS writer absent si profil `test` (T-HSTS-TEST)
- [x] HSTS `max-age=31536000; includeSubDomains` sans `preload` hors
      ces profils (T-HSTS-PROD)
- [x] Kill switch `false` : Customizer no-op / pas de writers A4
      (T-SWITCH-OFF)
- [x] `shouldWriteHsts` : secure + pair loopback/RFC1918 ; unwrap
      `X-Forwarded-For` ; refus pair public / HTTP clair / adresse invalide

## Integration Tests

Fichiers :

- `src/test/java/com/flowforge/infrastructure/security/SecurityHeadersApiTest.java`
- `src/test/java/com/flowforge/infrastructure/security/SecurityHeadersDisabledApiTest.java`
- `src/test/java/com/flowforge/infrastructure/web/observability/ObservabilityActuatorIT.java`
- `src/test/java/com/flowforge/infrastructure/web/document/DocumentPreviewApiTest.java` (inchangé, M13)
- `src/test/java/com/flowforge/infrastructure/security/SecurityHeadersHstsApiTest.java`

- [x] BFF `GET /api/auth/me` authentifié (T-CHAIN-BFF)
- [x] M2M `POST /api/m2m/mcp` (T-CHAIN-M2M)
- [x] Webhook anonyme (T-CHAIN-WEBHOOK)
- [x] Callback anonyme (T-CHAIN-CALLBACK)
- [x] Actuator liveness port management (T-CHAIN-ACTUATOR)
- [x] Profil test : pas de HSTS (T-HSTS-ABSENT-TEST)
- [x] `headers.enabled=false` (T-CHAIN-OFF)
- [x] Preview M13 `default-src 'none'; sandbox` (T-PREVIEW-M13)
- [x] A4-HSTS-001 `SecurityHeadersHstsApiTest` (customizer prod `@Primary`,
      hors effet du profil `test`) :
      loopback + `X-Forwarded-Proto: https` → HSTS ;
      pair RFC1918 + proto → HSTS ;
      pair public + proto spoofé → pas de HSTS ;
      HTTP clair loopback → pas de HSTS ;
      `X-Forwarded-For` client public + pair TCP loopback → HSTS (unwrap)

## End-to-End Tests

Fichier : `frontend/e2e/pilotage.spec.ts` (`A4_CSP`, `injectDocumentCsp`, `attachCspProbe`).

- [x] E2E-HDR-1 Supervision (T-E2E-SPA)
- [x] E2E-HDR-2 preview HTML/PDF (T-E2E-PREVIEW)
- [x] E2E-HDR-3 éditeur BPMN sous CSP (Phase 8)
- [x] E2E-HDR-4 éditeur DMN sous CSP (Phase 8)
- [x] Régression `npx playwright test` **75/75**

## Test Data

- `OidcClientTestConfiguration` existant.
- Profil Spring `test` (HSTS off).
- `@TestPropertySource(properties = "app.security.headers.enabled=false")`.

## Test Reporting & Coverage

- `./mvnw verify -Dskip.frontend=true`
- `cd frontend && npm run test`
- `cd frontend && npm run test:e2e` (équivalent `npx playwright test`)

## Manual Testing

Hors cycle. Smoke HTTPS derrière proxy : `curl -I` + `X-Forwarded-Proto: https`
depuis loopback. Vérifier l’absence de HSTS si le port applicatif est
atteint hors pair de confiance.

## Performance Testing

Non applicable (HeaderWriter).

## Bug Tracking

Aucune violation CSP E2E. D-CSP-STRING A inchangé. Flake BPMN save → `expect.poll`.
