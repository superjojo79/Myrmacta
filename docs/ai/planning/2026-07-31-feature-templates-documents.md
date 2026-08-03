---
phase: planning
title: Project Planning & Task Breakdown
description: Break down work into actionable tasks and estimate timeline
status: completed
created: 2026-08-03
updated: 2026-08-03
design_aligned: 2026-08-03
phase4: 2026-08-03
phase5: 2026-08-03
phase6: 2026-08-03
---

# Project Planning & Task Breakdown

Plan **phase 4–6** — dérivé des docs `requirements` / `design`
(`reviewed`) et `testing` (`draft` design_aligned).

**Phase 5–6 done** — T1–T10. Traçage `task` CLI : **indisponible**.
Preuve T9 : `./mvnw verify` → **315 / 0**, Jacoco instructions **87,60 %**.

## Milestones
**What are the major checkpoints?**

- [x] **M1 — Domaine & ports** : models, exceptions, ports admin/resolve/checker
- [x] **M2 — Persistence** : Liquibase 004 dual PG/H2 + JPA adapter + ranked query
- [x] **M3 — Use cases & checker** : CRUD lifecycle + Thymeleaf syntax checker
- [x] **M4 — HTTP API** : controller admin + resolve, security, ProblemDetail
- [x] **M5 — Preuves & docs** : MockMvc/IT, Jacoco ≥ 80 %, notes implementation

## Task Breakdown
**What specific work needs to be done?**

Statuts : `[ ]` not started · `[~]` in progress · `[x]` done · `[!]` blocked

### M1 — Domaine & ports

- [x] **T1. Models domaine document**
  - **Outcome :** models + enums + exceptions.
  - **Validation :** `DocumentTemplateModelsTest` 9/0.

- [x] **T2. Ports in/out**
  - **Outcome :** repository, syntax checker, ports in admin/resolve.
  - **Validation :** `DocumentTemplatePortsContractTest` 1/0.

### M2 — Persistence

- [x] **T3. Liquibase `004-document-template-tables`**
  - **Validation :** `DocumentTemplateSchemaIT` 3/0.

- [x] **T4. JPA entity + adapter + ranked resolve**
  - **Validation :** MapperTest 2/0 ; PersistenceIT 3/0.

### M3 — Use cases & checker

- [x] **T5. ThymeleafTemplateSyntaxChecker**
  - **Validation :** `ThymeleafTemplateSyntaxCheckerTest` 3/0.

- [x] **T6. Use cases admin lifecycle**
  - **Validation :** `DocumentTemplateUseCasesTest` (partie lifecycle).

- [x] **T7. TemplateResolver + Resolve use case**
  - **Validation :** UseCasesTest resolve + PersistenceIT ranked.

### M4 — HTTP API

- [x] **T8. DocumentTemplateController**
  - **Validation :** `DocumentTemplateApiTest` 4/0.

### M5 — Preuves & docs

- [x] **T9. IT sécurité + régression**
  - **Validation :** `./mvnw verify` 315/0 ; Jacoco 87,60 % ; ArchUnit OK.

- [x] **T10. Docs implementation + sync planning**
  - **Validation :** lint feature OK.

## Traceability testing → tasks

| Scénario testing | Tâche(s) | Statut |
|---|---|---|
| Resolver scores 1–4 | T4, T7 | done |
| SyntaxChecker | T5 | done |
| Activate/archive/new-version | T6, T8 | done |
| Liste includeContent | T6, T8 | done |
| Security 401/403/CSRF | T8, T9 | done |
| Jacoco ≥ 80 % | T9 | done |

## Progress summary

Feature **templates-documents** implémentée bout-en-bout (CRUD versionné +
resolve ranked + parse activate). Prochaine phase lifecycle : **7 — Check
Implementation**.
