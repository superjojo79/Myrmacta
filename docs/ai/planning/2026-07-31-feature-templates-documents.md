---
phase: planning
title: Project Planning & Task Breakdown
description: Break down work into actionable tasks and estimate timeline
status: draft
created: 2026-08-03
updated: 2026-08-03
design_aligned: 2026-08-03
phase4: 2026-08-03
---

# Project Planning & Task Breakdown

Plan **phase 4** — dérivé des docs `requirements` / `design` (`reviewed`)
et `testing` (`draft` design_aligned).

Traçage `task` CLI : **indisponible** (`unknown command 'task'`).

## Milestones
**What are the major checkpoints?**

- [ ] **M1 — Domaine & ports** : models, exceptions, ports admin/resolve/checker
- [ ] **M2 — Persistence** : Liquibase 004 dual PG/H2 + JPA adapter + ranked query
- [ ] **M3 — Use cases & checker** : CRUD lifecycle + Thymeleaf syntax checker
- [ ] **M4 — HTTP API** : controller admin + resolve, security, ProblemDetail
- [ ] **M5 — Preuves & docs** : MockMvc/IT, Jacoco ≥ 80 %, notes implementation

## Task Breakdown
**What specific work needs to be done?**

Statuts : `[ ]` not started · `[~]` in progress · `[x]` done · `[!]` blocked

### M1 — Domaine & ports

- [ ] **T1. Models domaine document**
  - **Outcome :** `DocumentTemplate` record, `TemplateStatus`,
    `TemplateResolutionRequest`, exceptions métier
    (`TemplateNotFoundException`, `InvalidTemplateStateException`,
    `InvalidTemplateSyntaxException`, `TemplateValidationException`).
  - **Depends on :** —
  - **Validation :** tests unit models / enums.
  - **Tests liés :** Mapper round-trip (prépare T4) ; validation create.

- [ ] **T2. Ports in/out**
  - **Outcome :** `DocumentTemplateRepository` (save, findById, list,
    maxVersion, `findBestActive` ranked) ; `TemplateSyntaxChecker` ;
    ports in create/update/activate/archive/new-version/list/get/resolve.
  - **Depends on :** T1
  - **Validation :** compilation + contrats ports (smoke test optionnel).
  - **Tests liés :** Resolver ; SyntaxChecker ; use cases.

### M2 — Persistence

- [ ] **T3. Liquibase `004-document-template-tables`**
  - **Outcome :** table `document_template` ; include master changelog ;
    **DD-ACTIVE-UQ** / **DD-VER-UQ** (PG) ; **DD-H2-UQ** generated columns
    + unique (pattern 002/003).
  - **Depends on :** T1
  - **Validation :** schema IT migrate ; insert double ACTIVE → violation.
  - **Tests liés :** Liquibase / contrainte.

- [ ] **T4. JPA entity + adapter + ranked resolve**
  - **Outcome :** `DocumentTemplateJpaEntity` + mapper null-safe ;
    adapter CRUD ; `findBestActive` **une** requête ranked scores 1–4
    (**DD-RESOLVE-SQL**) ; map `DataIntegrityViolation` → 409 côté web/UC.
  - **Depends on :** T2, T3
  - **Validation :** unit mapper ; IT persistence / ranked (1 query).
  - **Tests liés :** Mapper domaine↔JPA ; TemplateResolver ranked ;
    Liquibase contrainte.

### M3 — Use cases & checker

- [ ] **T5. ThymeleafTemplateSyntaxChecker**
  - **Outcome :** adaptateur `TemplateSyntaxChecker` via
    `StringTemplateResolver` ; **syntaxe seule** (vars métier absentes OK) ;
    bean infra.
  - **Depends on :** T2
  - **Validation :** unit SyntaxChecker (valide / invalide / vars absentes).
  - **Tests liés :** TemplateSyntaxChecker ; activate invalid HTML.

- [ ] **T6. Use cases admin lifecycle**
  - **Outcome :** create (blank/taille 5 MiB, version max+1, DRAFT) ;
    update DRAFT only ; activate (checker → TX archive+activate) ;
    archive ; new-version ; normalisation blank→null (**A7**) ;
    `updatedBy` OIDC/`system`.
  - **Depends on :** T2, T4, T5
  - **Validation :** unit use cases activate/archive/new-version/create/patch.
  - **Tests liés :** Activate/archive/new-version ; Update/create validation.

- [ ] **T7. TemplateResolver + Resolve use case**
  - **Outcome :** délègue `findBestActive` ; throw
    `TemplateNotFoundException` ; ignore non-ACTIVE via query.
  - **Depends on :** T4
  - **Validation :** unit resolver scores 1–4 + not found.
  - **Tests liés :** TemplateResolver / ranked query.

### M4 — HTTP API

- [ ] **T8. DocumentTemplateController**
  - **Outcome :** surface API requirements ; path littéral `/resolve` ;
    `{id}` UUID ; list `?includeContent=` + `templateKey` ;
    `@PreAuthorize TENANT_ADMIN` ; CSRF mutateurs ; ProblemDetail mapping
    400/404/409.
  - **Depends on :** T6, T7
  - **Validation :** MockMvc ApiTest (CRUD, lifecycle, resolve, security).
  - **Tests liés :** MockMvc admin DocumentTemplate*ApiTest.

### M5 — Preuves & docs

- [ ] **T9. IT sécurité + régression**
  - **Outcome :** 401 anonyme ; 403 non-admin ; CSRF manquant ;
    `./mvnw verify` vert ; ArchUnit OK ; Jacoco INSTRUCTION ≥ 80 %.
  - **Depends on :** T8
  - **Validation :** output `./mvnw verify` frais.
  - **Tests liés :** MockMvc security ; Success criteria Jacoco/ArchUnit.

- [ ] **T10. Docs implementation + sync planning**
  - **Outcome :** notes `docs/ai/implementation/…-templates-documents.md`
    (fichiers, écarts, evidence) ; cocher tâches planning ; testing
    scenarios alignés si besoin.
  - **Depends on :** T9
  - **Validation :** `npx ai-devkit lint --feature templates-documents`.
  - **Tests liés :** — (meta).

## Traceability testing → tasks

| Scénario testing | Tâche(s) |
|---|---|
| Resolver scores 1–4 / not found / ignore DRAFT | T4, T7 |
| Ranked = une requête | T4 |
| SyntaxChecker valide / invalide / vars OK | T5 |
| Activate / archive / new-version | T6 |
| Create/patch validation + 5 MiB | T6 |
| Mapper null/locale | T4 |
| MockMvc CRUD / list includeContent / resolve | T8 |
| Activate HTML invalide 400 | T5, T6, T8 |
| Security 401/403/CSRF | T8, T9 |
| Liquibase + unique ACTIVE | T3, T9 |
| Jacoco ≥ 80 % / ArchUnit | T9 |

## Dependencies
**What needs to happen in what order?**

```text
T1 → T2 → T5
T1 → T3 → T4 → T6 → T8 → T9 → T10
       T2 ↗        T5 ↗
              T4 → T7 ↗
```

- **Externe :** `spring-boot-starter-thymeleaf` déjà au pom — pas d’ajout
  Maven attendu.
- **Bloquant :** aucun ; PDF/Flowable hors scope.

## Risks & Mitigation

| Risque | Mitigation |
|---|---|
| H2 generated columns COALESCE / ACTIVE | Copier pattern exact 002/003 ; IT contrainte tôt (T3) |
| Ranked SQL JPQL vs native | Native OK si scores 1–4 respectés (O9 design) |
| Parse Thymeleaf trop strict (vars) | DD-THYMELEAF-SCOPE ; tests vars absentes (T5) |
| Payload 5 MiB MockMvc | Fixtures petites ; unit taille seule pour borne |
| Collision path `/resolve` | Mapping littéral avant `{id}` UUID (T8) |

## Resources Needed

- Worktree `.worktrees/feature-templates-documents`
- JDK 25 + `./mvnw`
- Analyse `08-templates-documents.md` ; design `reviewed`
- Patterns Liquibase connecteurs / workflow

## Sequencing notes

1. Enchaîner **T1 → T3** tôt pour valider les index H2.
2. **T5** peut paralléliser avec **T3/T4**.
3. Ne pas démarrer **T8** sans T6+T7 stables.
4. Après chaque tâche done → phase 6 update planning (coche + notes).

**Phase 4 close** — plan initial prêt.
**Phase 5** — exécuter T1 (`dev-implementation` + TDD).
