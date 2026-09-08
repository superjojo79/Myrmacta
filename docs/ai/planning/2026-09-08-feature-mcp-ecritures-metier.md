---
phase: planning
title: Project Planning & Task Breakdown
description: Break down work into actionable tasks and estimate timeline
status: completed
feature: mcp-ecritures-metier
---

# Project Planning & Task Breakdown — `mcp-ecritures-metier`

## Milestones

- [x] **M1** Helpers `McpArgs` (`requireUuid`, `optionalMap`)
- [x] **M2** `create_dossier`
- [x] **M3** `add_dossier_element`
- [x] **M4** `start_workflow`
- [x] **M5** `generate_document`
- [x] **M6** IT MCP (`tools/list` 20, create, add)
- [x] **M7** Docs utilisateur (`GETTING-STARTED` §12, FILE-ATTENTE, TODO, analyse 15)
- [x] **M8** Vérification globale `./mvnw verify`

Séquencement : M1 → M2/M3 (indépendants après M1) → M4 → M5 (réutilise add + DossierMcpView)
→ M6 → M7 → M8. TDD à chaque tâche.

## Task Breakdown

### M1 — Helpers

- [x] **T1.1** `McpArgs.requireUuid` / `optionalMap` + tests (`T-ARGS-*`)
  - Validation : `McpArgsTest` rouge puis vert
  - Scénarios : T-ARGS-UUID, T-ARGS-MAP

### M2 — create_dossier

- [x] **T2.1** `CreateDossierTool` TDD (`T-CREATE-*`)
  - Outcome : tool enregistré `@McpTool(name="create_dossier")`
  - Dépend : T1.1
  - Scénarios : T-CREATE-OK, T-CREATE-DEFAULT-FIELDS, T-CREATE-BLANK, T-CREATE-DRY*

### M3 — add_dossier_element

- [x] **T3.1** `AddDossierElementTool` TDD (`T-ADD-*`)
  - Dépend : T1.1
  - Scénarios : T-ADD-NOTE, T-ADD-REJECT-BINARY, T-ADD-BAD-TYPE, T-ADD-DRY

### M4 — start_workflow

- [x] **T4.1** `StartWorkflowTool` TDD (`T-START-*`)
  - Dépend : T1.1
  - Scénarios : T-START-OK, T-START-NO-KEY, T-START-RESERVED, T-START-ACL, T-START-REPLAY, T-START-DRY

### M5 — generate_document

- [x] **T5.1** `GenerateDocumentTool` TDD (`T-GEN-*`)
  - Dépend : T3.1 (même `AddDossierElementUseCase`), D-FIELDS-READ
  - Scénarios : T-GEN-OK, T-GEN-TEMPLATE, T-GEN-VARS, T-GEN-DRY, T-GEN-ADDED-BY, T-GEN-VIEW

### M6 — IT

- [x] **T6.1** `McpEndpointApiTest.EXPECTED_TOOLS` +4
- [x] **T6.2** `McpToolCallIntegrationTest` create + add NOTE + erreur type inconnu
  - Scénarios : IT-LIST-20, IT-CREATE, IT-ADD, IT-CREATE-ERROR

### M7 — Docs

- [x] **T7.1** `GETTING-STARTED.md` §12 : 16 → 20 tools + mention écritures métier
- [x] **T7.2** `FILE-ATTENTE.md` / `DEFERRED-FEATURES.md` §6 / `TODO.md` §59 /
  `analysis doc/15-serveur-mcp.md` backlog : lot A3 en cours puis en revue Phase 9
- [x] **T7.3** `CHANGELOG.md` [Non publié] à l'ouverture de PR (PR #118)

### M8 — Qualité

- [ ] **T8.1** `./mvnw verify` 0 échec, Jacoco tenu, ArchUnit tenu (preuve Phase 8, cette session)
- [x] **T8.2** `git diff --stat` : pas de `frontend/**`, pas de `SecurityConfig`, pas de Compose

## Progress summary (Phase 6)

Toutes les tâches M1–M8 sont livrées. Écart d’implémentation : constructeur unique
`CreateDossierTool` (Spring AI MCP). Surface exclusive respectée. Prochain à
l’origine : Phases 7–9 ; exécutées dans la même PR (#118).

## Dependencies

- Interne : M1 avant M2–M5 ; M5 après compréhension M3 ; M6 après M2–M5.
- Externe : aucun. Vague A1/A2 surfaces disjointes.
- Interdit : modifier `CreateDossierUseCase*` (import batch Vague B).

## Risks & Mitigation

| Risque | Mitigation |
|---|---|
| Appel `Dossier.fields()` hors allowlist | Uniquement via `DossierMcpView` (D-FIELDS-READ) |
| `start_workflow` sans ACL (orchestrateur nu) | `GetDossierUseCase` + `assertCanEdit` dans le tool |
| PDF Base64 / fuite `storageRef` | `DossierElementView` uniquement |
| IT start/generate trop lourds | Unitaires stricts ; IT limités à create/add/list |
| Conflit A1/A2 | Ne pas toucher Compose / `ConnectorsAdminView` / `SecurityConfig` |

## Resources Needed

- JDK imposé par `pom.xml` (25, wrapper éventuellement `release=21`).
- Pas de Docker pour cette feature.
- Pas de Playwright.
