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
Phase 8 (exécution / preuve) **non commencée**. TDD en Phase 5.

TDD obligatoire (skill `tdd`). Playwright = **régression** SPA (A-NO-SPA).

## Contrôles

- [x] **T-SSTI-PROC** Processor : `${title}` OK ; `#vars` / `#dates` /
      `#numbers` / `#strings` / `#lists` (méthode déclarée) OK ;
      `T()` / `#beans` / `new` / request / `getClass` →
      `TemplateProcessingException`.
- [x] **T-SSTI-CHECK** Checker : hors allowlist →
      `InvalidTemplateSyntaxException`.
- [ ] **T-SSTI-HTTP** Generate/preview **et** enregistrement → **400**.
- [x] **T-SSTI-EACH** `th:each` + `th:text` non régression.
- [x] **T-PIN-GUARD** Un lookup ; URI client = IP validée ; rebinding.
- [x] **T-PIN-WH** Webhook : URI IP + `Host` + TLS hostname d'origine.
- [x] **T-PIN-SK** Skeleton SERVICE pinné, non enregistré.
- [x] **T-PIN-ADMIN** `assertSafe` à l'enregistrement, pas de connect.
- [ ] **T-MGMT** Address `127.0.0.1`, port défaut 8090 ;
      `ObservabilityActuatorIT` (`MANAGEMENT_PORT=0`).
- [x] **T-SECRETS** Hors local/test : denylist L (vide, `flowforge`,
      `postgres`, `admin`, `password`, casse ignorée), master-key vide,
      OIDC secret vide → fail-fast ; profil `test` OK.
- [x] **T-HMAC** `M2M_JWT_SECRET` posé hors local/test → fail-fast.
- [x] **T-GRANT** Rôle app SELECT son schéma OK ; autre schéma denied
      (Testcontainers, SKIPPED sans Docker).
- [x] **T-DOCS** GETTING-STARTED / `.env.example` / `02` / `08` / `09` /
      TODO §40–44.
- [ ] **T-VERIFY** `./mvnw verify` vert, Jacoco tenu.
- [ ] **T-FE** Vitest + Playwright régression.

## Coverage target

Nouvelles classes : branches allowlist B2, denylist L, pin, GRANT.
Bundle Jacoco existant tenu.

## Frontend

Aucun fichier `frontend/**`. Phase 8 : `npm run test` + Playwright.

## Manual Testing

GETTING-STARTED : Keycloak `:8081`, actuator `:8090`, profil `local`.
Volume PG neuf (`compose down -v`) pour l'init SQL.
