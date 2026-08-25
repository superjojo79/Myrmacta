---
phase: planning
title: Project Planning & Task Breakdown
description: Break down work into actionable tasks
status: draft
created: 2026-08-25
updated: 2026-08-25
feature: durcissement-securite-post-revue
---

# Planning — `durcissement-securite-post-revue`

> **Hors phase.** Brouillon prématuré (avant Phase 1/2). Non opposable.
> Phase 4 n'est pas ouverte. Les cases ci-dessous ne sont pas un plan
> d'exécution autorisé.

## Phase 4: Create Initial Plan — non commencée

## Milestones

- [ ] **M1** SSTI Thymeleaf (D-40)
- [ ] **M2** Pin IP outbound (D-44)
- [ ] **M3** Actuator bind/port (D-42)
- [ ] **M4** Secrets fail-fast + HMAC (D-43)
- [ ] **M5** Rôle PG + runbook (D-41)
- [ ] **M6** Docs analysis / GETTING-STARTED + clôture TODO §40–44
- [ ] **M7** `./mvnw verify` + régression FE

## Task Breakdown

### M1 — SSTI

- [ ] **T1.1** Test rouge : payload `${T(java.lang.Runtime)}` encore évaluable
      aujourd'hui (ou contenu dangereux). Validation : T-SSTI.
- [ ] **T1.2** Remplacer `SpringTemplateEngine` par `TemplateEngine`.
      `th:text="${title}"` vert. Validation : T-SSTI.

### M2 — SSRF pin

- [ ] **T2.1** `PinnedEndpoint` + test rebinding (resolver 2e appel ≠ 1er).
      Validation : T-PIN.
- [ ] **T2.2** `WebhookRestAdapter` / `HttpRestServiceConnector` utilisent
      l'URI pinnée. Redirects `NEVER` inchangé.

### M3 — Actuator

- [ ] **T3.1** `application.yml` : `address: ${MANAGEMENT_ADDRESS:127.0.0.1}`,
      `port: ${MANAGEMENT_PORT:8090}`. Validation : T-MGMT.
- [ ] **T3.2** GETTING-STARTED / `.env.example` / note observabilite.

### M4 — Secrets

- [ ] **T4.1** `SecretsEnvironmentPostProcessor` + tests (profil vide vs
      `test`). Validation : T-SECRETS.
- [ ] **T4.2** HMAC M2M : EPP refuse `jwt-secret` hors local/test.

### M5 — GRANT

- [ ] **T5.1** Init SQL rôle `flowforge_tenant_dev` + GRANTs.
- [ ] **T5.2** IT Testcontainers : user A ne `SELECT` pas schéma B.
      Validation : T-GRANT.
- [ ] **T5.3** `application.yml` username défaut + compose / `.env.example`.

### M6 — Docs clôture

- [ ] **T6.1** `08-templates-documents.md`, `09-notifications.md`,
      `02-multi-tenant.md` (rôle PG).
- [ ] **T6.2** TODO §40–44 `[x]` ; FILE-ATTENTE ; DEFERRED ; revue §7.

### M7 — Preuve

- [ ] **T7.1** `./mvnw verify`.
- [ ] **T7.2** `cd frontend && npm run test` + `npx playwright test`
      (régression ; SPA non touchée).

## Dependencies

M1 ∥ M2 ∥ M3 ∥ M4 (fichiers surtout disjoints) → M5 (compose/yml) → M6 → M7.

M3/M4 touchent `application.yml` : fusionner dans le même commit de yaml.

## Risks

- Thymeleaf sans SpEL casse un template qui utilisait `#dates` / beans Spring
  — grep `th:` métier = interpolation simple.
- Init SQL ne rejoue pas sur volume PG existant — documenter `down -v`.
- Pin TLS hostname vs IP : tests unitaires sur l'URI envoyée ; handshake TLS
  réel hors bac si pas de serveur HTTPS.

## Timeline

N/A — une passe TDD.
