---
phase: planning
title: Project Planning & Task Breakdown
description: Break down work into actionable tasks and estimate timeline
status: implemented
created: 2026-07-31
updated: 2026-07-31
---

# Project Planning & Task Breakdown

## Milestones
**What are the major checkpoints?**

- [x] **M1 — Validator tenant** : package `infrastructure.tenant`, regex, tests unitaires
- [x] **M2 — Config instance** : `TenantProperties`, `TenantConfig`, runner, yaml, fixtures
- [x] **M3 — Preuves & docs** : régression suite / ArchUnit, doc implementation + reverse-proxy

## Task Breakdown
**What specific work needs to be done?**

Statuts : `[ ]` not started · `[~]` in progress · `[x]` done · `[!]` blocked

### M1 — Validator tenant (TDD)

- [x] **T1. `InvalidTenantIdException` + `TenantIdValidator`**
  - **Outcome :** package `com.flowforge.infrastructure.tenant` (+ `package-info`) ;
    exception runtime ; validator statique regex `^[a-z][a-z0-9_]{2,62}$`.
  - **Depends on :** —
  - **Validation :** classes compilent ; aucun import domaine workflow ; pas de Spring
    dans le validator.
  - **Tests liés :** Unit — TenantIdValidator (T2).

- [x] **T2. Tests unitaires `TenantIdValidator`**
  - **Outcome :** `TenantIdValidatorTest` couvre OK / KO (regex).
  - **Depends on :** T1
  - **Validation :** 13 cas verts.
  - **Tests liés :** Unit — TenantIdValidator.

### M2 — Config instance

- [x] **T3. `TenantProperties` + `application.yml`**
  - **Outcome :** record `@NotBlank` ; `app.tenant.id: tenant_dev` ; path / search_path
    commentés.
  - **Depends on :** —
  - **Validation :** yaml + smoke T5.
  - **Tests liés :** Config binding.

- [x] **T4. `TenantConfig` + `TenantIdValidationRunner`**
  - **Outcome :** enable properties + `ApplicationRunner` ; `DomainConfig` inchangé.
  - **Depends on :** T1, T3
  - **Validation :** beans + T5.
  - **Tests liés :** runner / TenantConfig.

- [x] **T5. Tests config + runner**
  - **Outcome :** runner unitaire ; smoke binding ; fail missing/blank ; DomainConfig OK.
  - **Depends on :** T4
  - **Validation :** `TenantConfigTest` + `TenantIdValidationRunnerTest` verts.
  - **Tests liés :** Config + runner + DomainConfigTest.

- [x] **T6. Fixtures workflow `tenant_a`**
  - **Outcome :** fixtures valides `tenant_a` ; `tenant-a` réservé aux cas d’invalidité.
  - **Depends on :** —
  - **Validation :** `rg` + suite workflow verte.
  - **Tests liés :** Régression workflow.

### M3 — Preuves & docs

- [x] **T7. Suite complète + ArchUnit**
  - **Outcome :** `./mvnw test` + `./mvnw verify` verts.
  - **Depends on :** T2, T5, T6
  - **Validation :** 33 tests, 0 failure ; Jacoco ≥ 80 % (96.7 % lignes).
  - **Tests liés :** ArchUnit ; coverage.

- [x] **T8. Documentation implementation**
  - **Outcome :** doc implementation + reverse-proxy + pattern TenantConfig.
  - **Depends on :** T4, T3
  - **Validation :** lint feature OK.
  - **Tests liés :** Manual checklist.

## Traceability — testing → tasks

| Scénario (testing doc) | Task(s) | Statut |
|---|---|---|
| Validator OK / KO | T1, T2 | done |
| Binding `tenant_dev` / absence id | T3, T5 | done |
| Runner valide / invalide | T4, T5 | done |
| DomainConfigTest non régressé | T4, T5, T7 | done |
| Fixtures `tenant_a` | T6, T7 | done |
| ArchUnit + Jacoco | T7 | done |
| Manual yaml / reverse-proxy | T3, T8 | done |

## Dependencies
**What needs to happen in what order?**

Exécuté : T1→T2 puis T3/T6 en parallèle, T4→T5, T7+T8.

## Timeline & Estimates
**When will things be done?**

Phase 5 complétée (2026-07-31). Pas d’échéances calendaires.

## Risks & Mitigation
**What could go wrong?**

| Risque | Mitigation / issue |
|---|---|
| JDK agent 21 vs Java 25 | Contourné : Temurin 25 installé en local agent (`~/java/jdk-25…`) |
| Scope creep OIDC/DB | Respecté — non livré |

## Resources Needed
**What do we need to succeed?**

- Worktree : `/workspace/.worktrees/feature-multi-tenant`
- JDK 25 + `./mvnw`

## Next action

Lifecycle **terminé** pour la tranche A — PR #9 prête à merger.

## Progress summary

T1–T8 livrés. Phases 7–9 OK (2026-07-31).
Evidence phase 9 : `./mvnw test` **36** OK ; `./mvnw verify` Jacoco 96.7 % ;
lint OK ; aucun finding bloquant. **Prêt à merger.**
