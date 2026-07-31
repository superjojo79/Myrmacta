---
phase: planning
title: Project Planning & Task Breakdown
description: Break down work into actionable tasks and estimate timeline
status: planned
created: 2026-07-31
---

# Project Planning & Task Breakdown

## Milestones
**What are the major checkpoints?**

- [ ] **M1 — Validator tenant** : package `infrastructure.tenant`, regex, tests unitaires
- [ ] **M2 — Config instance** : `TenantProperties`, `TenantConfig`, runner, yaml, fixtures
- [ ] **M3 — Preuves & docs** : régression suite / ArchUnit, doc implementation + reverse-proxy

## Task Breakdown
**What specific work needs to be done?**

Statuts : `[ ]` not started · `[~]` in progress · `[x]` done · `[!]` blocked

### M1 — Validator tenant (TDD)

- [ ] **T1. `InvalidTenantIdException` + `TenantIdValidator`**
  - **Outcome :** package `com.flowforge.infrastructure.tenant` (+ `package-info`) ;
    exception runtime ; validator statique regex `^[a-z][a-z0-9_]{2,62}$`.
  - **Depends on :** —
  - **Validation :** classes compilent ; aucun import domaine workflow ; pas de Spring
    dans le validator.
  - **Tests liés :** Unit — TenantIdValidator (T2).
  - **TDD :** écrire T2 en rouge puis implémenter T1 jusqu’au vert.

- [ ] **T2. Tests unitaires `TenantIdValidator`**
  - **Outcome :** `TenantIdValidatorTest` couvre OK (`tenant_a`, `tenant_dev`, `abc`,
    63 chars) et KO (`null`, blank, `tenant-a`, majuscules, `1tenant`, `ab`, >63).
  - **Depends on :** T1 (vert avec T1)
  - **Validation :** scénarios testing doc Unit — TenantIdValidator tous exécutables ;
    couverture lignes validator = 100 %.
  - **Tests liés :** Unit — TenantIdValidator.

### M2 — Config instance

- [ ] **T3. `TenantProperties` + `application.yml`**
  - **Outcome :** `record TenantProperties(@NotBlank String id)` dans
    `infrastructure.config` ; `app.tenant.id: tenant_dev` actif ; blocs
    `context-path` + `connection-init-sql` **commentés** (modèle cible).
  - **Depends on :** —
  - **Validation :** yaml relu (manual) ; binding possible en smoke T5 ; exclusions
    autoconfig existantes **conservées** ; path non actif.
  - **Tests liés :** Config binding / absence id (T5) ; Manual yaml.

- [ ] **T4. `TenantConfig` + `TenantIdValidationRunner`**
  - **Outcome :** `@Configuration` + `@EnableConfigurationProperties` ;
    `ApplicationRunner` appelle `TenantIdValidator.validate(properties.id())` ;
    `DomainConfig` **inchangé**.
  - **Depends on :** T1, T3
  - **Validation :** beans présents ; démarrage avec `tenant_dev` OK ; id invalide
    fait échouer le runner (T5).
  - **Tests liés :** TenantIdValidationRunner ; TenantConfig / Properties smoke.

- [ ] **T5. Tests config + runner**
  - **Outcome :**
    - runner unitaire : id valide OK ; `tenant-a` → `InvalidTenantIdException` ;
    - smoke Spring (léger) : binding `tenant_dev` ; absence `app.tenant.id` → échec
      contexte (`@NotBlank`) ;
    - `DomainConfigTest` toujours vert.
  - **Depends on :** T4
  - **Validation :** scénarios testing « TenantProperties + TenantConfig / runner »
    + régression DomainConfig.
  - **Tests liés :** TenantProperties/TenantConfig ; TenantIdValidationRunner ;
    Integration régression DomainConfigTest.

- [ ] **T6. Fixtures workflow `tenant_a`**
  - **Outcome :** remplacer `tenant-a` → `tenant_a` dans
    `StartWorkflowUseCaseImplTest`, `WorkflowInstanceTest`, et tout autre test touché.
  - **Depends on :** —
  - **Validation :** `rg 'tenant-a' src/test` vide (hors cas volontaire d’id invalide
    dans tests tenant) ; tests workflow verts.
  - **Tests liés :** Régression workflow ; Test Data fixtures.

### M3 — Preuves & docs

- [ ] **T7. Suite complète + ArchUnit**
  - **Outcome :** `./mvnw test` vert (validator, config, workflow, ArchUnit) ;
    `./mvnw verify` respecte Jacoco ≥ 80 %.
  - **Depends on :** T2, T5, T6
  - **Validation :** exit 0 ; pas de nouvelle dépendance domaine → infra.
  - **Tests liés :** ArchUnit ; Reporting coverage ; régression globale.

- [ ] **T8. Documentation implementation**
  - **Outcome :**
    `docs/ai/implementation/2026-07-31-feature-multi-tenant.md` décrit setup,
    structure packages, `TenantConfig` / runner, yaml commenté, contrat reverse-proxy,
    règle aval (`TenantProperties.id()` dans les commandes).
  - **Depends on :** T4, T3
  - **Validation :** success criteria doc reverse-proxy + docs alignés ; lint feature OK.
  - **Tests liés :** Manual — yaml / reverse-proxy / pas de Security filter.

## Traceability — testing → tasks

| Scénario (testing doc) | Task(s) |
|---|---|
| Validator OK : `tenant_a`, `tenant_dev`, `abc`, max 63 | T1, T2 |
| Validator KO : null, blank, tiret, majuscule, digit, `ab`, >63 | T1, T2 |
| Binding `tenant_dev` | T3, T5 |
| Absence `app.tenant.id` → fail binding | T3, T5 |
| Runner id valide OK | T4, T5 |
| Runner `tenant-a` → exception | T4, T5 |
| DomainConfigTest non régressé | T4, T5, T7 |
| Régression suite workflow + fixtures `tenant_a` | T6, T7 |
| ArchUnit domaine pur | T7 |
| E2E N/A | — |
| Manual yaml commenté / reverse-proxy / no Security | T3, T8 |
| Jacoco ≥ 80 % | T7 |

## Dependencies
**What needs to happen in what order?**

```text
T1 ── T2
T3 ──┐
T1 ──┼── T4 ── T5 ──┐
T6 ─────────────────┼── T7
T3, T4 ── T8        │
                    └── (T8 parallélisable avec T7 après T4)
```

Ordre recommandé : **T1 → T2 → T3 → T4 → T5 → T6 → T7 → T8** (T6 parallélisable dès le début).

- **Externe :** aucun (pas de PostgreSQL / Keycloak / proxy réel).
- **Docs amont :** requirements + design **reviewed** ; testing draft utilisable.
- **Prérequis code :** socle `architecture-hexagonale` déjà sur la branche de base.

## Timeline & Estimates
**When will things be done?**

Pas d’échéances calendaires. Envergure technique :

| Task | Envergure |
|---|---|
| T1–T2 | Petit — pur Java + tests table-driven |
| T3 | Petit — yaml + record properties |
| T4 | Petit — config Spring dédiée |
| T5 | Moyen — smoke contexte + unitaires runner |
| T6 | Trivial — remplacement fixtures |
| T7 | Vérification globale |
| T8 | Docs |

## Risks & Mitigation
**What could go wrong?**

| Risque | Mitigation |
|---|---|
| JDK agent = 21 vs projet Java 25 | Compiler/tests dans worktree ; si échec toolchain, documenter et viser CI JDK 25 |
| `@ConfigurationProperties` record + `@NotBlank` Boot 4 | Activer validation properties (`@EnableConfigurationProperties` + starter-validation) ; prouver via T5 |
| `ApplicationRunner` échoue aussi sur `tenant_dev` mal formé | Garder défaut yaml conforme ; tests runner avec ids contrôlés |
| Activation accidentelle `context-path` | T3/T8 checklist ; ne jamais décommenter dans cette feature |
| Scope creep (Liquibase, filtre OIDC) | Non-goals requirements ; refuser en revue PR |
| Jacoco sous 80 % si nouvelles classes peu testées | T2+T5 couvrent validator/runner ; smoke binding |

## Resources Needed
**What do we need to succeed?**

- Worktree : `/workspace/.worktrees/feature-multi-tenant`
- Branche : `feature-multi-tenant`
- JDK cible 25 + `./mvnw`
- Docs : requirements / design / testing `2026-07-31-feature-multi-tenant`
- Analyse : `analysis doc/02-multi-tenant.md` (modèle cible ; tranche A seulement)

## Next action

**Phase 5** — exécuter le plan (`dev-implementation`), en TDD à partir de **T1/T2**.

## Progress summary

Plan initial créé (2026-07-31). 0/8 tâches faites. Aucun bloqueur. Prêt pour implémentation tranche A.
