---
phase: planning
title: Project Planning & Task Breakdown
description: Break down work into actionable tasks and estimate timeline
status: draft
created: 2026-07-31
updated: 2026-07-31
---

# Project Planning & Task Breakdown

## Milestones
**What are the major checkpoints?**

- [ ] **M1 — Domaine pur (TDD)** : model, validator, policy, transitions, ports
- [ ] **M2 — Persistence** : Liquibase, JPA entities/adapters, référence, levée exclusions
- [ ] **M3 — Use cases** : types, dossiers, statut, ACL câblés
- [ ] **M4 — API REST** : controllers, ProblemDetail, sécurité rôles/CSRF
- [ ] **M5 — Preuves & docs** : IT H2, ArchUnit, Jacoco, doc implementation

## Task Breakdown
**What specific work needs to be done?**

Statuts : `[ ]` not started · `[~]` in progress · `[x]` done · `[!]` blocked

### M1 — Domaine pur (TDD)

- [ ] **T1. Model records + enums**
  - **Outcome :** `Dossier`, `DossierType`, `FieldDefinition` (record),
    `DossierStatus`, `ElementType` non requis ; `AccessContext`,
    `DossierPermission`, `DossierTypeAclEntry` ; `version` sur `Dossier`.
  - **Depends on :** —
  - **Validation :** compile ; ArchUnit domaine pur.
  - **Tests liés :** — (structure).

- [ ] **T2. `DossierFieldValidator` + tests**
  - **Outcome :** validation required/type/select/unknown selon
    `allowUnknownFields`.
  - **Depends on :** T1
  - **Validation :** tests unitaires validator verts.
  - **Tests liés :** Unit — DossierFieldValidator.

- [ ] **T3. `DossierAccessPolicy` + tests**
  - **Outcome :** System / admins bypass ; VIEW/EDIT ; Service refus.
  - **Depends on :** T1
  - **Validation :** tests policy verts.
  - **Tests liés :** Unit — DossierAccessPolicy.

- [ ] **T4. Règles de transition de statut + tests**
  - **Outcome :** graphe design ; exception conflit si illégal.
  - **Depends on :** T1
  - **Validation :** tests transitions verts.
  - **Tests liés :** Unit — UpdateDossierStatus rules.

- [ ] **T5. Ports in/out (interfaces)**
  - **Outcome :** use case interfaces + repositories +
    `DossierReferenceGenerator` déclarés.
  - **Depends on :** T1
  - **Validation :** compile.
  - **Tests liés :** —

### M2 — Persistence

- [ ] **T6. Liquibase changelog tables dossiers**
  - **Outcome :** `dossier_type`, `dossier`, `dossier_type_acl`,
    `dossier_reference_seq` + contraintes/index.
  - **Depends on :** —
  - **Validation :** appliqué sur H2 MODE=PostgreSQL.
  - **Tests liés :** IT persistence schema.

- [ ] **T7. `*JpaEntity` + Spring Data + adapters**
  - **Outcome :** mappers bi-directionnels ; repos out implémentés ;
    JSON conversions fields / fieldDefinitions.
  - **Depends on :** T5, T6
  - **Validation :** IT save/load.
  - **Tests liés :** IT persistence.

- [ ] **T8. `SequentialReferenceGenerator`**
  - **Outcome :** upsert + `UPDATE … RETURNING` ; format UTC ; unique ref.
  - **Depends on :** T6, T7
  - **Validation :** IT séquence + unit format.
  - **Tests liés :** Unit format ; IT référence.

- [ ] **T9. Activer JDBC/JPA/Liquibase + yaml datasource/search_path**
  - **Outcome :** exclusions retirées (pas Flowable/Mail) ; config runtime +
    test H2 ; suite boot OK avec OIDC stub.
  - **Depends on :** T6
  - **Validation :** contexte Spring IT vert ; auth non régressée.
  - **Tests liés :** IT boot ; BffSecurityTest régression.

### M3 — Use cases

- [ ] **T10. Use cases DossierType + ACL grant/revoke**
  - **Outcome :** create/get/list/update(`active`…) ; grant/revoke ;
    enforce `TENANT_ADMIN` côté web (use case reçoit contexte).
  - **Depends on :** T2–T5, T7
  - **Validation :** tests use case / IT.
  - **Tests liés :** IT API types/ACL.

- [ ] **T11. `CreateDossierUseCase` / `GetDossierUseCase`**
  - **Outcome :** validation + policy + ref + save `DRAFT` ; get id/ref + policy.
  - **Depends on :** T2, T3, T7, T8
  - **Validation :** tests create/get.
  - **Tests liés :** IT API dossiers.

- [ ] **T12. `UpdateDossierStatusUseCase`**
  - **Outcome :** transitions + `updatedBy`/`closed*` + optimistic version.
  - **Depends on :** T4, T7, T11
  - **Validation :** tests statut + 409 version.
  - **Tests liés :** Unit transitions ; IT PATCH status.

- [ ] **T13. Câblage Spring (`DomainConfig` ou `DossierConfig`)**
  - **Outcome :** beans use cases + adapters enregistrés.
  - **Depends on :** T10–T12
  - **Validation :** injection IT.
  - **Tests liés :** IT boot.

### M4 — API REST

- [ ] **T14. Controllers + DTOs surface option 1**
  - **Outcome :** endpoints design (types, dossiers, status, ACL) ;
    mapping `AccessContext.User` depuis `OidcUser`.
  - **Depends on :** T13
  - **Validation :** MockMvc scénarios authz.
  - **Tests liés :** IT API MockMvc.

- [ ] **T15. `ProblemDetail` exception handler**
  - **Outcome :** 400 errors[] ; 403 ; 404 ; 409 (transition/version/unique).
  - **Depends on :** T14
  - **Validation :** corps ProblemDetail assertés.
  - **Tests liés :** IT erreurs.

### M5 — Preuves & docs

- [ ] **T16. Suite complète + ArchUnit + Jacoco**
  - **Outcome :** `./mvnw test` + `verify` verts ; couverture ≥ 80 %.
  - **Depends on :** T2–T4, T8, T14, T15
  - **Validation :** CI locale verte.
  - **Tests liés :** ArchUnit ; coverage ; régression auth.

- [ ] **T17. Doc implementation + notes écarts H2**
  - **Outcome :** `docs/ai/implementation/…-dossiers.md` rempli ;
    contrat REST résumé.
  - **Depends on :** T14–T16
  - **Validation :** lint feature OK.
  - **Tests liés :** Manual doc.

## Dependencies
**What needs to happen in what order?**

- M1 → M3 (domaine avant use cases).
- M2 T6/T9 tôt pour débloquer IT ; T7/T8 avant T11.
- Auth BFF déjà sur `main` — ne pas casser stub OIDC / cookies.
- Features aval (`workflow-engine`, etc.) **non bloquantes** pour B.

## Timeline & Estimates
**When will things be done?**

Pas d’estimation calendaire. Ordre technique : T1–T5 → T6/T9 → T7/T8 →
T10–T13 → T14–T15 → T16–T17.

## Risks & Mitigation
**What could go wrong?**

| Risque | Mitigation |
|---|---|
| H2 ≠ PG (`RETURNING`, JSON) | Garder SQL simple ; noter écarts ; PG smoke manuel |
| Levée JPA casse boot auth | Profil test H2 + stub OIDC dès T9 |
| Surface ACL sous-estimée | T3/T10 tôt ; tests 403 exhaustifs |
| Jacoco sous 80 % avec nouveau code | Tests validator/policy/IT ciblés |

## Resources Needed
**What do we need to succeed?**

- Analyse `04-dossiers.md`, `12-autorisation-conformite.md`.
- Branche worktree `feature-dossiers`.
- Maven wrapper ; pas de PG obligatoire en CI.

## Summary

Plan initial tranche **B** : domaine + JPA/Liquibase + ACL + API option 1 +
tests H2. Aucune tâche démarrée. Prochaine étape lifecycle : **phase 2 —
revue requirements**, puis phase 3 design, puis exécution M1 T1.
