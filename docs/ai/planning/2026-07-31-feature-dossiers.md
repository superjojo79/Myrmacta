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

- [x] **M1 — Domaine pur (TDD)** : model, validator, policy, transitions, ports
- [x] **M2 — Persistence** : H2 dep, Liquibase, JPA, JdbcTemplate seq, levée exclusions
- [x] **M3 — Use cases** : types (create/update), dossiers, statut, ACL
- [x] **M4 — API REST** : controllers, guards TENANT_ADMIN, ProblemDetail
- [x] **M5 — Preuves & docs** : IT H2, ArchUnit, Jacoco, doc implementation

## Task Breakdown
**What specific work needs to be done?**

Statuts : `[ ]` not started · `[~]` in progress · `[x]` done · `[!]` blocked

### M1 — Domaine pur (TDD)

- [x] **T1. Model records + enums**
  - **Outcome :** `Dossier` (dont `version`, `currentWorkflowLinkId` null),
    `DossierType`, `FieldDefinition` (record), `FieldType`, `DossierStatus`,
    `AccessContext`, `DossierPermission`, `DossierTypeAclEntry` ;
    helpers `withStatus` / copies immuables.
  - **Depends on :** —
  - **Validation :** compile ; ArchUnit domaine pur.
  - **Tests liés :** ArchUnit.

- [x] **T2. `DossierFieldValidator` + application des defaults + tests**
  - **Outcome :** required / type / select / unknown / FILE string ;
    helper appliquant `defaultValue` si clé absente avant validate.
  - **Depends on :** T1
  - **Validation :** tests unitaires verts.
  - **Tests liés :** Unit — DossierFieldValidator (+ defaults).

- [x] **T3. `DossierAccessPolicy` + tests**
  - **Outcome :** System bypass ; TENANT_ADMIN / WORKFLOW_ADMIN bypass ;
    VIEW/EDIT (`EDIT` ⇒ `VIEW`) ; Service refus ; sans grant refus.
  - **Depends on :** T1
  - **Validation :** tests policy verts.
  - **Tests liés :** Unit — DossierAccessPolicy.

- [x] **T4. `DossierStatusTransitions` + tests**
  - **Outcome :** graphe design ; exception conflit si illégal ; ARCHIVE
    terminal ; règles `closed*` documentées pour le use case.
  - **Depends on :** T1
  - **Validation :** tests transitions verts.
  - **Tests liés :** Unit — UpdateDossierStatus rules.

- [x] **T5. Ports in/out (interfaces)**
  - **Outcome :** interfaces use cases (dont `UpdateDossierTypeUseCase`) +
    repositories + `DossierReferenceGenerator` + exceptions métier
    (`DossierFieldValidationException`, `AccessDeniedException`,
    `InactiveDossierTypeException`, conflit transition/version…).
  - **Depends on :** T1
  - **Validation :** compile.
  - **Tests liés :** —

### M2 — Persistence

- [x] **T6. Dépendance H2 test + config test datasource/schema**
  - **Outcome :** `com.h2database:h2` (test) ; URL
    `MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE` ; `CREATE SCHEMA` /
    `SET SCHEMA` pour `tenant_dev` ; OIDC stub inchangé.
  - **Depends on :** —
  - **Validation :** pom + properties test.
  - **Tests liés :** IT boot.

- [x] **T7. Liquibase changelog tables dossiers**
  - **Outcome :** master changelog + changeset :
    `dossier_type`, `dossier`, `dossier_type_acl`, `dossier_reference_seq`
    (jsonb, UNIQUE, version) ; schéma non créé par Liquibase.
  - **Depends on :** T6 (pour valider sur H2)
  - **Validation :** Liquibase appliqué en IT.
  - **Tests liés :** IT persistence schema.

- [x] **T8. `*JpaEntity` + Spring Data + adapters (`SqlTypes.JSON`)**
  - **Outcome :** mappers bi-directionnels ; repos out ; `@Version` sur
    dossier ; JSON fields / fieldDefinitions.
  - **Depends on :** T5, T7
  - **Validation :** IT save/load type + dossier.
  - **Tests liés :** IT persistence.

- [x] **T9. `SequentialReferenceGenerator` (JdbcTemplate)**
  - **Outcome :** upsert année + `UPDATE … RETURNING` ; format
    `DOS-{TYPE}-{YYYY}-{seq:06d}` ; `Year.now(UTC)`.
  - **Depends on :** T7, T8
  - **Validation :** unit format + IT incrément / unicité.
  - **Tests liés :** Unit format ; IT référence.

- [x] **T10. Activer JDBC/JPA/Liquibase + yaml runtime**
  - **Outcome :** exclusions DataSource/JPA/Liquibase retirées ;
    `ddl-auto: none` ; datasource env +
    `connection-init-sql: SET search_path TO ${app.tenant.id}, public` ;
    Flowable/Mail restent exclus ; boot tests verts.
  - **Depends on :** T6, T7
  - **Validation :** contexte Spring IT ; BffSecurityTest non régressé.
  - **Tests liés :** IT boot ; régression auth.

### M3 — Use cases

- [x] **T11. Use cases DossierType (create / update / get / list)**
  - **Outcome :** `Create*` / `UpdateDossierTypeUseCase` (code immuable) /
    `Get*` / `List*` avec filtre ACL ; type `active=false` géré.
  - **Depends on :** T2, T3, T5, T8
  - **Validation :** tests use case / IT API types.
  - **Tests liés :** IT API types.

- [x] **T12. Use cases ACL grant / revoke**
  - **Outcome :** grant idempotent ; revoke ; `DossierTypeAclRepository`.
  - **Depends on :** T3, T5, T8
  - **Validation :** IT ACL.
  - **Tests liés :** IT grant/revoke.

- [x] **T13. `CreateDossierUseCase` / `GetDossierUseCase`**
  - **Outcome :** policy + type actif (sinon `InactiveDossierTypeException`)
    + defaults + validation + ref + save `DRAFT` ;
    `tenantId` fourni par appelant (= `TenantProperties`) ;
    get id / ref + policy.
  - **Depends on :** T2, T3, T8, T9
  - **Validation :** tests create/get.
  - **Tests liés :** IT API dossiers ; defaults ; inactive 409.

- [x] **T14. `UpdateDossierStatusUseCase`**
  - **Outcome :** transitions T4 ; `updated*` ; `closed*` sur CLOTURE ;
    `expectedVersion` + `@Version` → conflit.
  - **Depends on :** T4, T8, T13
  - **Validation :** tests statut + 409 version.
  - **Tests liés :** Unit transitions ; IT PATCH status.

- [x] **T15. Câblage Spring (`DossierConfig` / `DomainConfig`)**
  - **Outcome :** beans use cases + adapters + `JdbcTemplate` seq.
  - **Depends on :** T11–T14
  - **Validation :** injection IT.
  - **Tests liés :** IT boot.

### M4 — API REST

- [x] **T16. Helpers web — `AccessContextFactory` + `TenantAdminGuard`**
  - **Outcome :** `OidcUser` → `AccessContext.User` (sub + rôles sans
    `ROLE_`) ; gate `ROLE_TENANT_ADMIN` → 403 sinon ; **pas** de
    `@EnableMethodSecurity`.
  - **Depends on :** —
  - **Validation :** unit helper / IT 403 WORKFLOW_ADMIN sur POST type.
  - **Tests liés :** IT admin gate.

- [x] **T17. Controllers + DTOs surface API**
  - **Outcome :** tous les endpoints design/requirements ; CSRF hérité BFF ;
    `tenantId` depuis `TenantProperties` ; label requis côté request.
  - **Depends on :** T15, T16
  - **Validation :** MockMvc scénarios authz.
  - **Tests liés :** IT API MockMvc.

- [x] **T18. `ProblemDetail` exception handler**
  - **Outcome :** 400 `errors[]` / label ; 403 ACL/admin ; 404 ; 409
    (transition, version, unique, type inactif).
  - **Depends on :** T17
  - **Validation :** corps ProblemDetail assertés.
  - **Tests liés :** IT erreurs.

### M5 — Preuves & docs

- [x] **T19. Suite complète + ArchUnit + Jacoco**
  - **Outcome :** `./mvnw test` + `verify` verts ; couverture ≥ 80 % ;
    régression auth/workflow/tenant.
  - **Depends on :** T2–T4, T9, T17, T18
  - **Validation :** CI locale verte.
  - **Tests liés :** ArchUnit ; coverage ; BffSecurityTest.

- [x] **T20. Doc implementation + notes écarts H2/SQL**
  - **Outcome :** `docs/ai/implementation/2026-07-31-feature-dossiers.md`
    rempli ; contrat REST résumé ; écarts H2 documentés si découverts.
  - **Depends on :** T17–T19
  - **Validation :** lint feature OK.
  - **Tests liés :** Manual doc.

## Traceability — testing → tasks

| Scénario (testing doc) | Task(s) |
|---|---|
| Validator required / type / select / unknown / OK | T2 |
| Defaults champs absents | T2, T13 |
| Policy System / admins / VIEW / EDIT / Service | T3 |
| Transitions légales / illégales / ARCHIVE | T4, T14 |
| Format référence UTC | T9 |
| Liquibase 4 tables + contraintes | T7 |
| Save/load type JSON | T8 |
| Référence incrémentale / unicité | T9, T13 |
| `@Version` stale | T8, T14 |
| UNIQUE tenant+ref / tenant+code | T7, T8 |
| Anonyme 401 | T17 |
| TENANT_ADMIN create/PATCH type 201/200 | T11, T16, T17 |
| Non-admin / WORKFLOW_ADMIN create type 403 | T16, T17 |
| Grant EDIT + create DRAFT + tenantId instance | T12, T13, T17 |
| Type `active=false` → 409 | T13, T18 |
| Champs invalides / FILE blank → 400 | T2, T13, T18 |
| Create sans label → 400 | T17, T18 |
| Get VIEW / bypass WORKFLOW_ADMIN | T3, T13, T17 |
| Get by ref | T13, T17 |
| PATCH status légal / illégal / version | T14, T17, T18 |
| Revoke + re-grant idempotent | T12, T17 |
| CSRF manquant 403 | T17 (régression BFF) |
| Régression BFF | T10, T19 |
| ArchUnit domaine / JPA / usecase Impl | T1, T19 |
| Jacoco ≥ 80 % | T19 |
| E2E navigateur / PG réel | N/A hors scope |

## Dependencies
**What needs to happen in what order?**

```text
T1 → T2∥T3∥T4∥T5
T6 → T7 → T10
T5+T7 → T8 → T9
T2+T3+T8 → T11∥T12
T2+T3+T8+T9 → T13 → T14
T11–T14 → T15
T16∥T15 → T17 → T18 → T19 → T20
```

Auth BFF déjà sur `main` — ne pas casser stub OIDC. Features aval non bloquantes.

## Timeline & Estimates
**When will things be done?**

Pas d’estimation calendaire. Ordre : M1 → M2 (T6/T7/T10 tôt) → M3 → M4 → M5.

## Risks & Mitigation
**What could go wrong?**

| Risque | Mitigation |
|---|---|
| H2 ≠ PG (`RETURNING`, jsonb, ON CONFLICT) | SQL minimal ; noter écarts T20 ; smoke PG manuel |
| Levée JPA casse boot auth | T6/T10 tôt ; garder OIDC stub |
| Gate admin oubliée (fuite WORKFLOW_ADMIN) | T16 + IT explicite 403 |
| Jacoco < 80 % | T2/T3/T4 unitaires + IT ciblées |
| Schéma tenant absent en prod | Doc ops : schema pré-existe (décision 13) |

## Resources Needed
**What do we need to succeed?**

- Worktree : `/workspace/.worktrees/feature-dossiers`
- Docs requirements/design/testing `reviewed` / alignés
- JDK 25 + `./mvnw` ; pas de PG obligatoire en CI

## Next action

**Phase 7 — Check Implementation**, puis phase 8 testing / phase 9 review.

## Progress summary

Phase 5 terminée : T1–T20 livrés. `./mvnw verify` — 113 tests verts, Jacoco
≥ 80 % (~83 %). Suite : check implementation + revue.
