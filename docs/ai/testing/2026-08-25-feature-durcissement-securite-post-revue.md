---
phase: testing
title: Testing Strategy
description: Define testing approach, test cases, and quality assurance
status: planned
created: 2026-08-25
updated: 2026-08-25
feature: durcissement-securite-post-revue
---

# Testing Strategy — `durcissement-securite-post-revue`

Scénarios dérivés des success criteria (Phase 2) et du design (Phase 3).
Phase 5 TDD **done**. Preuves M7 (cette session) : `./mvnw verify`
1173 tests, 0 échec, Jacoco BRANCH 75,03 % ; Vitest 199 ; Playwright 47.
Phase 8 lifecycle formelle **non commencée**.

TDD obligatoire (skill `tdd`). Playwright = **régression** SPA (A-NO-SPA).

## Contrôles

- [x] **T-SSTI-PROC** Processor : `${title}` OK ; `#vars` / `#dates` /
      `#numbers` / `#strings` / `#lists` (méthode déclarée) OK ;
      `T()` / `#beans` / `new` / request / `getClass` →
      `TemplateProcessingException`.
- [x] **T-SSTI-CHECK** Checker : hors allowlist →
      `InvalidTemplateSyntaxException`.
- [x] **T-SSTI-HTTP** Generate/preview **et** enregistrement → **400**
      (preview live ; **activation** — le POST draft reste 201, checker
      métier existant à l'activation).
- [x] **T-SSTI-EACH** `th:each` + `th:text` non régression.
- [x] **T-PIN-GUARD** Un lookup ; URI client = IP validée ; rebinding.
- [x] **T-PIN-WH** Webhook : URI IP + `Host` + TLS hostname d'origine.
- [x] **T-PIN-SK** Skeleton SERVICE pinné, non enregistré.
- [x] **T-PIN-ADMIN** `assertSafe` à l'enregistrement, pas de connect.
- [x] **T-MGMT** Address `127.0.0.1`, port défaut 8090 ;
      `ObservabilityActuatorIT` (`MANAGEMENT_PORT=0`).
- [x] **T-SECRETS** Hors local/test : denylist L (vide, `flowforge`,
      `postgres`, `admin`, `password`, casse ignorée), master-key vide,
      OIDC secret vide → fail-fast ; profil `test` OK.
- [x] **T-HMAC** `M2M_JWT_SECRET` posé hors local/test → fail-fast.
- [x] **T-GRANT** Rôle app SELECT son schéma OK ; autre schéma denied
      (Testcontainers, SKIPPED sans Docker).
- [x] **T-DOCS** GETTING-STARTED / `.env.example` / `02` / `08` / `09` /
      TODO §40–44.
- [x] **T-VERIFY** `./mvnw verify` vert, Jacoco tenu.
- [x] **T-FE** Vitest + Playwright régression.

## Coverage target

Nouvelles classes : branches allowlist B2, denylist L, pin, GRANT.
Bundle Jacoco existant tenu.

## Frontend

Aucun fichier `frontend/**`. Preuve régression Phase 5 M7 : `npm run test`
199/199 ; `npm run test:e2e` 47/47.

## Manual Testing

GETTING-STARTED : Keycloak `:8081`, actuator `:8090`, profil `local`.
Volume PG neuf (`compose down -v`) pour l'init SQL.
