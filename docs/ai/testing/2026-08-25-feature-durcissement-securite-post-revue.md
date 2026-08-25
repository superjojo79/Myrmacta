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

## Phase 8: Write tests

TDD obligatoire (skill `tdd`). Playwright = **régression** SPA non modifiée,
pas un contournement.

## Contrôles

- [ ] **T-SSTI** `ThymeleafHtmlTemplateProcessorTest` : SpEL `T(java.lang.Runtime)`
      non exécuté ; `${title}` OK.
- [ ] **T-GRANT** `TenantRoleIsolationIT` (Testcontainers, SKIPPED sans Docker) :
      rôle schéma A, `SELECT` `schemaB.dossier` → SQLException permission.
- [ ] **T-MGMT** yaml / propriété : address 127.0.0.1, port défaut 8090 ;
      `ObservabilityActuatorIT` inchangé (MANAGEMENT_PORT=0).
- [ ] **T-SECRETS** test EPP : environment sans profil local/test + password
      `flowforge` → IllegalStateException ; profil `test` passe.
- [ ] **T-PIN** resolver invoqué une fois ; URL passée au sender = IP.
- [ ] **T-HMAC** EPP : `M2M_JWT_SECRET` posé hors local/test → fail.

## Coverage target

Nouvelles classes : branches critiques (SpEL, denylist secrets, pin, GRANT)
exercées. Bundle Jacoco existant tenu.

## Frontend

Aucun fichier `frontend/**` attendu. Phase 8 : `npm run test` + Playwright
pour non-régression.

## Manual Testing

GETTING-STARTED : Keycloak `:8081` et actuator `:8090` (curl localhost).
Volume PG neuf pour rejouer l'init SQL.
