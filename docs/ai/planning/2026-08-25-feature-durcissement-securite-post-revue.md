---
phase: planning
title: Project Planning & Task Breakdown
description: Break down work into actionable tasks
status: reviewed
created: 2026-08-25
updated: 2026-08-25
feature: durcissement-securite-post-revue
phase4: 2026-08-25
phase5: 2026-08-25
phase7: 2026-08-25
phase8: 2026-08-25
requirements_ref: docs/ai/requirements/2026-08-25-feature-durcissement-securite-post-revue.md
design_ref: docs/ai/design/2026-08-25-feature-durcissement-securite-post-revue.md
testing_ref: docs/ai/testing/2026-08-25-feature-durcissement-securite-post-revue.md
---

# Project Planning & Task Breakdown — `durcissement-securite-post-revue`

## Phase 4: Create Initial Plan

Plan **reviewed** 2026-08-25. Phase 5 **done** : M1–M7 (TDD + preuves).

## Milestones
**What are the major checkpoints?**

- [x] **M1** SSTI B2 + fail 400 (DD-THYME-B2 = E, DD-SSTI-FAIL = C)
- [x] **M2** Pin IP outbound (D-44 = P, A-PIN-CALLERS)
- [x] **M3** Actuator localhost:8090 (D-42 = L)
- [x] **M4** Secrets fail-fast + denylist L + HMAC (D-43 = F)
- [x] **M5** Rôle PG LOGIN + IT + Compose (D-41 = A/S)
- [x] **M6** Docs T-DOCS + clôture TODO §40–44
- [x] **M7** Preuve `./mvnw verify` + régression FE

## Task Breakdown
**What specific work needs to be done?**

TDD : test rouge → code → vert. Validation = scénario testing doc.

### M1 — SSTI B2 + C

- [x] **T1.1** Tests processor : `${title}` OK ; `#vars` / `#dates` /
      `#numbers` / `#strings` / `#lists` évalués (méthode déclarée sur
      l'objet Thymeleaf) ; `${T(...)}` et `#beans` / `new` / request
      → `TemplateProcessingException` ; `Object.getClass` refusé
      (A-EXPR-METHODS). **Deps :** aucune. **Scénarios :** T-SSTI-PROC.
- [x] **T1.2** Évaluateur B2 (étendre l'ébauche, ne pas livrer le regex
      `${ident.prop}` seul) + même `SafeHtmlTemplateEngineFactory` pour
      processor. **Deps :** T1.1. **Scénarios :** T-SSTI-PROC.
- [x] **T1.3** Checker : expressions hors allowlist →
      `InvalidTemplateSyntaxException` (pas parse HTML seul). **Deps :**
      T1.2. **Scénarios :** T-SSTI-CHECK.
- [x] **T1.4** Preuve HTTP : generate/preview et enregistrement → **400**
      (handlers existants, DD-SSTI-FAIL = C) — test API ou handler.
      **Deps :** T1.2, T1.3. **Scénarios :** T-SSTI-HTTP.
- [x] **T1.5** `th:each` / `th:text` non régression. **Deps :** T1.2.
      **Scénarios :** T-SSTI-EACH.

### M2 — Pin IP

- [x] **T2.1** `resolveSafe` : un lookup ; `PinnedEndpoint` ; test
      rebinding (2e résolution ≠ 1re, client utilise la 1re). **Deps :**
      aucune. **Scénarios :** T-PIN-GUARD.
- [x] **T2.2** `WebhookRestAdapter` : POST URI IP, header `Host` =
      hostname d'origine, TLS verify hostname d'origine, redirects
      `NEVER`. **Deps :** T2.1. **Scénarios :** T-PIN-WH.
- [x] **T2.3** `HttpRestServiceConnector` (skeleton) : même pin, pas
      d'enregistrement SERVICE. **Deps :** T2.1. **Scénarios :** T-PIN-SK.
- [x] **T2.4** Admin webhook reste `assertSafe` (pas de connect).
      **Deps :** T2.1. **Scénarios :** T-PIN-ADMIN.

### M3 — Actuator

- [x] **T3.1** `management.server.address` `${MANAGEMENT_ADDRESS:127.0.0.1}`,
      port `${MANAGEMENT_PORT:8090}` ; `permitAll` inchangé.
      **Deps :** aucune. **Scénarios :** T-MGMT.
- [x] **T3.2** `ObservabilityActuatorIT` vert (`MANAGEMENT_PORT=0`).
      **Deps :** T3.1. **Scénarios :** T-MGMT.

### M4 — Secrets

- [x] **T4.1** EPP : hors `local`/`test`, denylist L (vide + `flowforge` /
      `postgres` / `admin` / `password`, trim, casse ignorée) ;
      master-key vide ; `OIDC_CLIENT_SECRET` vide → fail-fast.
      **Deps :** aucune. **Scénarios :** T-SECRETS.
- [x] **T4.2** HMAC M2M non vide hors `local`/`test` → fail-fast ; profil
      `test` inchangé. **Deps :** T4.1. **Scénarios :** T-HMAC.
- [x] **T4.3** `application-local.yml` + doc profil `local` (A-LOCAL-DOC,
      partie GETTING-STARTED reportée M6). **Deps :** T4.1. **Scénarios :**
      T-SECRETS.

### M5 — GRANT PG

- [x] **T5.1** Init SQL : rôle `flowforge_tenant_dev` LOGIN, GRANT schéma
      `tenant_dev` seul, REVOKE PUBLIC. **Deps :** aucune. **Scénarios :**
      T-GRANT.
- [x] **T5.2** Compose : `POSTGRES_USER` superuser ≠ `DB_USERNAME` app.
      **Deps :** T5.1. **Scénarios :** T-GRANT.
- [x] **T5.3** `TenantRoleIsolationIT` : SELECT propre schéma OK ; autre
      schéma → permission denied ; SKIPPED sans Docker. **Deps :** T5.1.
      **Scénarios :** T-GRANT.

### M6 — Docs

- [x] **T6.1** GETTING-STARTED : profil `local`, actuator `:8090`, Keycloak
      `:8081`, rôle PG, `compose down -v`. `.env.example`. **Deps :**
      M3–M5 yaml. **Scénarios :** T-DOCS.
- [x] **T6.2** `02-multi-tenant.md` (rôle PG), `08-templates-documents.md`
      (B2, pas « sans SpEL » au sens A), `09-notifications.md` (pin).
      **Deps :** M1, M2, M5. **Scénarios :** T-DOCS.
- [x] **T6.3** TODO §40–44 `[x]` ; FILE-ATTENTE ; note design observabilite
      port 8090. **Deps :** T6.1, T6.2. **Scénarios :** T-DOCS.

### M7 — Preuve

- [x] **T7.1** `./mvnw verify` (Jacoco bundle tenu). **Deps :** M1–M5.
      **Scénarios :** T-VERIFY.
- [x] **T7.2** `cd frontend && npm run test` + Playwright (A-NO-SPA,
      régression). **Deps :** aucune (SPA non touchée). **Scénarios :**
      T-FE.

## Dependencies
**What needs to happen in what order?**

M1 ∥ M2 ∥ M3 ∥ M4 (fichiers surtout disjoints). M3+M4+M5 touchent
`application.yml` / Compose : fusionner yaml dans un commit cohérent.
M5 après ou parallèle M4 (username défaut). M6 après M1–M5. M7 dernier.

T1.4 peut être un test MVC existant generate/template plutôt qu'un nouvel
endpoint.

## Timeline & Estimates
**When will things be done?**

Pas d'estimation calendaire. Une passe TDD Phase 5, une tâche à la fois
(Phase 6 après chaque tâche done).

## Risks & Mitigation
**What could go wrong?**

| Risque | Mitigation |
|---|---|
| `#dates.format(...)` parsing sans SpEL/OGNL | T1.1 rouge d'abord ; parser/dispatch dans l'évaluateur |
| `th:each` casse | T1.5 |
| Volume PG existant | T6.1 `down -v` |
| Header `Host` (JDK HttpClient) | garder `HttpURLConnection` côté webhook (design) |
| IT GRANT SKIPPED ici | compilé + CI Docker |
| Ébauche A/B1 livrée par erreur | M1 ne coche pas T1.2 tant que `#dates` n'est pas vert |

## Resources Needed
**What do we need to succeed?**

- JDK 21 d'itération si 25 absent (`-Dmaven.compiler.release=21`,
  `-Dskip.frontend=true` hors T7.2)
- Docker pour T5.3 en CI
- Skills `tdd` + `verify` en Phase 5/7

## Traceability (scénario → tâche)

| Scénario testing | Tâches |
|---|---|
| T-SSTI-PROC | T1.1, T1.2 |
| T-SSTI-CHECK | T1.3 |
| T-SSTI-HTTP | T1.4 |
| T-SSTI-EACH | T1.5 |
| T-PIN-GUARD | T2.1 |
| T-PIN-WH | T2.2 |
| T-PIN-SK | T2.3 |
| T-PIN-ADMIN | T2.4 |
| T-MGMT | T3.1, T3.2 |
| T-SECRETS | T4.1, T4.3 |
| T-HMAC | T4.2 |
| T-GRANT | T5.1, T5.2, T5.3 |
| T-DOCS | T6.1, T6.2, T6.3 |
| T-VERIFY | T7.1 |
| T-FE | T7.2 |

Phase 5 **done** (2026-08-25). Phase 7 **checked** (2026-08-25). Phase 8
**verified** (2026-08-25) : couverture classe par classe + generate HTTP
`T()` 400. Preuves : `./mvnw verify` **1185** tests, 0 échec, Jacoco
LINE 93,52 % / BRANCH 75,57 % ; Vitest 199/199 ; Playwright 47/47.
Suite lifecycle : Phase 9 (`dev-review`) — **non commencée**.
