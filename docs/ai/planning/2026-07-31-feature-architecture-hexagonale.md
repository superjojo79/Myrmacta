---
phase: planning
title: Project Planning & Task Breakdown
description: Break down work into actionable tasks and estimate timeline
status: ready
created: 2026-07-31
---

# Project Planning & Task Breakdown

## Milestones
**What are the major checkpoints?**

- [ ] **M1 — Socle packages** : rename `com.flowforge`, arborescence complète, exclusions autoconfig
- [ ] **M2 — Tranche workflow** : modèle, ports, `StartWorkflowUseCaseImpl`, adaptateurs in-memory, `DomainConfig`
- [ ] **M3 — Preuves & docs** : tests unitaires + ArchUnit verts, doc implementation / javadoc câblage

## Task Breakdown
**What specific work needs to be done?**

Statuts : `[ ]` not started · `[~]` in progress · `[x]` done · `[!]` blocked

### M1 — Socle packages

- [ ] **T1. Rename `com.monapp` → `com.flowforge`**
  - **Outcome :** sources, tests, `package-info`, ArchUnit sur `com.flowforge` ; plus aucune référence Java `com.monapp`.
  - **Depends on :** —
  - **Validation :** `rg 'com\\.monapp' src` vide ; `./mvnw test` compile/passe (ArchUnit packages vides OK).
  - **Tests liés :** ArchUnit (packages `com.flowforge`).

- [ ] **T2. Compléter l’arborescence infrastructure**
  - **Outcome :** package `infrastructure.config` (+ `package-info`) ; hors workflow toujours `package-info` only.
  - **Depends on :** T1
  - **Validation :** arbre conforme success criteria requirements.
  - **Tests liés :** — (structure).

- [ ] **T3. Exclusions autoconfig temporaires**
  - **Outcome :** `application.yml` exclut DataSource, JPA, Liquibase, Security, OAuth2, Mail, Flowable (design phase 3).
  - **Depends on :** —
  - **Validation :** fichier présent ; commentaire « temporaire — retirer feature par feature ».
  - **Tests liés :** smoke `DomainConfig` optionnel (T10).

### M2 — Tranche workflow (TDD)

- [ ] **T4. Modèle domaine workflow**
  - **Outcome :** `WorkflowStatus`, `StartWorkflowCommand`, `WorkflowInstance` (fabrique `create`, `complete`), `WorkflowStartedEvent`, `InvalidStateException` en `domain.workflow.model`.
  - **Depends on :** T1
  - **Validation :** tests modèle verts (voir T8) ; aucun import Spring/JPA/Flowable.
  - **Tests liés :** Unit — WorkflowInstance (create / complete OK / complete invalide).

- [ ] **T5. Ports workflow**
  - **Outcome :** `StartWorkflowUseCase` (`port.in`) ; `WorkflowRepository`, `WorkflowEventPublisher` (`port.out`).
  - **Depends on :** T4
  - **Validation :** interfaces compilent ; utilisées par T6.
  - **Tests liés :** couverts via T6/T8.

- [ ] **T6. `StartWorkflowUseCaseImpl`**
  - **Outcome :** impl dans `usecase` ; ctor ports out ; `start` = `create` → `save` → `publish`.
  - **Depends on :** T5
  - **Validation :** tests use case sans Spring (T8) ; ArchUnit naming `*Impl`.
  - **Tests liés :** Unit — StartWorkflowUseCaseImpl (start OK, propagation champs, pas de publish si save échoue).

- [ ] **T7. Adaptateurs in-memory + `DomainConfig`**
  - **Outcome :**
    - `InMemoryWorkflowRepository` → `infrastructure.persistence.workflow` (`ConcurrentHashMap`)
    - `InMemoryWorkflowEventPublisher` → `infrastructure.flowable`
    - `DomainConfig` → `infrastructure.config` (`@Bean` repo, publisher, use case via interface)
  - **Depends on :** T6, T2, T3
  - **Validation :** beans câblés ; javadoc sur `DomainConfig` ; tests adaptateurs (T9).
  - **Tests liés :** Unit — InMemory* ; Integration optionnelle DomainConfig (T10).

### M3 — Preuves & docs

- [ ] **T8. Tests unitaires modèle + use case**
  - **Outcome :** tests JUnit5 **sans** `@SpringBootTest` ; mocks ou in-memory pour ports.
  - **Depends on :** T4, T6
  - **Validation :** scénarios testing doc Unit — WorkflowInstance + StartWorkflowUseCaseImpl tous `[x]` côté exécution.
  - **Tests liés :** Unit StartWorkflowUseCaseImpl ; Unit WorkflowInstance.

- [ ] **T9. Tests unitaires adaptateurs in-memory**
  - **Outcome :** save/findById ; publish observable / ordre.
  - **Depends on :** T7
  - **Validation :** scénarios testing InMemory* verts.
  - **Tests liés :** Unit InMemoryWorkflowRepository ; Unit InMemoryWorkflowEventPublisher.

- [ ] **T10. ArchUnit + smoke config (optionnel)**
  - **Outcome :** `ArchitectureRulesTest` sur `com.flowforge` ; retirer `allowEmptyShould(true)` sur règle `usecase` ; smoke `@SpringBootTest`/`@Import(DomainConfig)` **optionnel** sans datasource.
  - **Depends on :** T6, T7, T1
  - **Validation :** 3 règles ArchUnit vertes ; `./mvnw test` exit 0.
  - **Tests liés :** Architecture Tests ; Integration DomainConfig (si fait).

- [ ] **T11. Documentation implementation**
  - **Outcome :** `docs/ai/implementation/2026-07-31-feature-architecture-hexagonale.md` décrit setup, structure, pattern `DomainConfig`, exclusions autoconfig.
  - **Depends on :** T7
  - **Validation :** success criterion « pattern documenté » ; lint feature OK.
  - **Tests liés :** Manual testing checklist (revue structure).

## Traceability — testing → tasks

| Scénario (testing doc) | Task(s) |
|---|---|
| Use case : start RUNNING + save + publish | T6, T8 |
| Use case : propagate processKey/tenantId | T6, T8 |
| Use case : pas de publish si save échoue | T6, T8 |
| Modèle : create RUNNING + id | T4, T8 |
| Modèle : complete OK / invalide | T4, T8 |
| InMemory repo save/find / empty | T7, T9 |
| InMemory publisher ordre | T7, T9 |
| Integration DomainConfig (optionnel) | T10 |
| ArchUnit 3 règles + allowEmptyShould | T10 |
| E2E N/A | — |
| Manual revue packages / DomainConfig | T1, T11 |

## Dependencies
**What needs to happen in what order?**

```text
T1 ──┬── T2 ──┐
     │        ├── T7 ── T9
     ├── T4 ── T5 ── T6 ── T8
     │              │
T3 ──┴──────────────┴── T10
T7 ── T11
```

- **Déjà fait (hors checklist, à ne pas refaire) :** `pom.xml` mono-module, dépendances stack, Maven Wrapper, ArchUnit initial sous `com.monapp`, arborescence packages quasi complète sous `com.monapp`.
- **Externe :** aucun service (in-memory only).
- **Docs amont :** requirements + design **reviewed** ; testing draft utilisable.

## Timeline & Estimates
**When will things be done?**

Pas d’échéances calendaires. Ordre d’exécution recommandé : **T1 → T3 → T2 → T4 → T5 → T6 → T8 → T7 → T9 → T10 → T11**.

| Task | Envergure technique |
|---|---|
| T1 | Transversal rename (mécanique, large surface fichiers) |
| T2–T3 | Petits (packages / yaml) |
| T4–T6 | Cœur domaine (TDD) |
| T7 | Câblage + stubs concurrent |
| T8–T10 | Preuves |
| T11 | Docs |

## Risks & Mitigation
**What could go wrong?**

| Risque | Mitigation |
|---|---|
| Autoconfig Boot 4 / Flowable résiste aux exclusions | Vérifier FQCN d’auto-config Flowable 8 ; ajuster `application.yml` ; smoke T10 |
| ArchUnit `allowEmptyShould` / package-info | Retirer seulement sur règle `usecase` après `*Impl` |
| Rename incomplet (`com.monapp` résiduel) | `rg` gate dans validation T1 |
| Scope creep REST / JPA | Rappeler non-goals ; refuser dans revue PR |
| Publisher in-memory difficile à inspecter | Préférer mocks sur le port en T8 ; T9 teste l’adaptateur directement |

## Resources Needed
**What do we need to succeed?**

- Worktree : `/workspace/.worktrees/feature-architecture-hexagonale`
- JDK 25 + `./mvnw`
- Docs : requirements / design / testing `2026-07-31-feature-architecture-hexagonale`
- Analyse : `analysis doc/01-architecture-hexagonale.md` (règles ; package racine = `com.flowforge` dans le code)

## Next action

Démarrer **phase 5 (`dev-implementation`)** par **T1** (rename), puis T3 / T4 en TDD.
