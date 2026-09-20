---
phase: testing
title: Testing Strategy
description: Define testing approach, test cases, and quality assurance
status: planned
created: 2026-09-20
updated: 2026-09-20
feature: headers-http-globaux
lot: A4
phase3: 2026-09-20
phase4: 2026-09-20
---

# Testing Strategy — `headers-http-globaux` (lot A4)

Scénarios dérivés des success criteria requirements. Cases à cocher
pendant Phase 5/8. Playwright **obligatoire** (CSP SPA).

## Test Coverage Goals

- Backend : nouvelles branches helper / kill switch / HSTS-par-profil
  à 100 % LINE+BRANCH sur les classes ajoutées ou touchées du lot.
- Jacoco bundle existant (LINE ≥ 85 %, BRANCH ≥ 75 %) tenu.
- Frontend unit : pas de nouveau composant ; régression Vitest
  `npm run test`.
- Frontend E2E : `npm run test:e2e` — au moins un parcours SPA réel
  + preview documents.

## Unit Tests

### Properties / bind

- [ ] `headers.enabled` défaut `true` (T-PROP-DEFAULT)
- [ ] `headers.enabled=false` bind depuis l’environnement (T-PROP-OFF)

### `SecurityHeadersCustomizer`

- [ ] Construit la CSP D-CSP-STRING = A (T-CSP-VALUE) :
      `default-src 'self'; script-src 'self'; style-src 'self' 'unsafe-inline'; img-src 'self' data: blob:; font-src 'self' data:; connect-src 'self'; frame-src 'self' blob:; worker-src 'self' blob:; frame-ancestors 'none'; object-src 'none'; base-uri 'self'`
- [ ] HSTS writer absent si profil `local` (T-HSTS-LOCAL)
- [ ] HSTS writer absent si profil `test` (T-HSTS-TEST)
- [ ] HSTS `max-age=31536000; includeSubDomains` sans `preload` hors
      ces profils (T-HSTS-PROD)
- [ ] Kill switch `false` : Customizer no-op / pas de writers A4
      (T-SWITCH-OFF)

## Integration Tests

MockMvc **vraie** chaîne Spring Security (pas `standaloneSetup`) —
même doctrine que `WebhookAndCallbackRoutesSecurityTest`.

- [ ] BFF `GET /api/auth/me` authentifié : CSP, XFO=DENY, nosniff,
      Referrer-Policy (T-CHAIN-BFF) — couvre D-HEADERS-CHAINS
- [ ] M2M `POST /api/m2m/mcp` (ou GET health M2M existant) : mêmes
      en-têtes hors HSTS sous `test` (T-CHAIN-M2M)
- [ ] Webhook anonyme `POST /api/webhooks/{uuid}` : mêmes en-têtes,
      statut contrôleur inchangé (404 instance inconnue)
      (T-CHAIN-WEBHOOK)
- [ ] Callback anonyme `POST /api/callbacks/{uuid}` : idem
      (T-CHAIN-CALLBACK)
- [ ] Actuator `GET /actuator/health` sur port management : CSP/XFO
      présents (T-CHAIN-ACTUATOR) — si le test actuel bind déjà le
      port
- [ ] Profil test : **pas** de `Strict-Transport-Security`
      (T-HSTS-ABSENT-TEST)
- [ ] `headers.enabled=false` : assertions d’absence du paquet A4
      (T-CHAIN-OFF)
- [ ] `DocumentPreviewApiTest` : CSP M13
      `default-src 'none'; sandbox` inchangée (T-PREVIEW-M13)

## End-to-End Tests

Playwright sert le SPA via Vite `:4173` (`playwright.config.ts`), pas
Spring. **A-E2E-CSP-INJECT** (planning Phase 4) : les tests HDR
ajoutent la CSP D-CSP-STRING sur les réponses HTML document, pour
simuler la chaîne BFF.

- [ ] E2E-HDR-1 : un utilisateur authentifié ouvre une vue SPA
      existante (supervision **ou** `/admin/bpmn-editor`) — page
      rendue, **aucune** violation CSP `blocked` dans la console
      (T-E2E-SPA)
- [ ] E2E-HDR-2 : preview HTML/PDF template admin (réutiliser le
      setup d’`E2E-20`) — iframe `sandbox` / blob toujours visible
      (T-E2E-PREVIEW)
- [ ] Régression complète `npx playwright test` (parcours existants)

## Test Data

- `OidcClientTestConfiguration` existant.
- Profil Spring `test` (HSTS off).
- Un test `@SpringBootTest` avec
  `@TestPropertySource(properties = "app.security.headers.enabled=false")`.
- Pas de Kafka / Compose (hors lot).

## Test Reporting & Coverage

- `./mvnw verify -Dmaven.compiler.release=21` (ou release du `pom`)
- `cd frontend && npm run test`
- `cd frontend && npm run test:e2e`
- Jacoco classe par classe sur les fichiers du diff (Phase 8)

## Manual Testing

Hors cycle si E2E + MockMvc couvrent. Smoke HTTPS derrière proxy :
HSTS visible (`curl -I` avec `X-Forwarded-Proto: https`) — nice-to-have
ops, pas un gate Phase 8.

## Performance Testing

Non applicable (HeaderWriter).

## Bug Tracking

Écarts CSP découverts en E2E → ajuster D-CSP-STRING en Phase 5,
documenter dans le design, **pas** passer en Report-Only sans
rejouer D-CSP-POLICY.
