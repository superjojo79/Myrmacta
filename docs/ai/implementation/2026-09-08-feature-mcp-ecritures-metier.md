---
phase: implementation
title: Implementation Guide
description: Technical implementation notes, patterns, and code guidelines
status: checked
feature: mcp-ecritures-metier
---

# Implementation Guide — `mcp-ecritures-metier`

Lot A3. Adaptateurs MCP uniquement (`infrastructure.mcp.tool`). TDD M1–M8.
Phase 7 (check implementation) : aligné sur le design, écarts ci-dessous.

## Code Structure

```
infrastructure/mcp/tool/
  CreateDossierTool.java
  AddDossierElementTool.java
  StartWorkflowTool.java
  GenerateDocumentTool.java
  McpArgs.java          # requireNonBlank, requireUuid, optionalMap (+ helpers existants)
  DossierMcpView.java   # existant — generate lit fields() ici
```

Aucune table, migration, bean Security, ni modification de `CreateDossierUseCase*`.

## Notes

- Ne pas modifier `CreateDossierUseCase*` (Vague B).
- Ne pas appeler `Dossier.fields()` hors `DossierMcpView`.
- `start_workflow` : ACL dans le tool (`GetDossierUseCase` + `assertCanEdit`).
- `generate_document` : store + `PRODUCED_DOC` ; sortie `DossierElementView`.
- `addedBy` / `createdBy` / `startedBy` côté commande = sujet JWT (`actor.userId()`).
- `CreateDossierTool` : **un seul constructeur public** (4 args). Spring AI MCP
  refuse deux constructeurs (`No default constructor found`).
  `DossierFieldValidator` est instancié dans ce constructeur (classe domaine, pas un bean).

## Design deviations

| Écart | Sévérité | Traitement |
|---|---|---|
| Un constructeur public unique sur `CreateDossierTool` (pas de ctor test-only injectant le validator) | Basse | Contrainte runtime Spring AI MCP, documentée. Tests utilisent le ctor 4-args. |
| `GETTING-STARTED.md` §12 (compteur 16 → 20) | Basse | Surface exclusive A1 sur ce fichier ; seule la section MCP a été touchée. Conflit de merge possible si A1 réécrit le même fichier. |
| `StartWorkflowTool.Result` expose aussi `startedAt` / `startedBy` | Basse | Additive vs le design ; parité avec le lien domaine. |
| `DocumentGenerationRateLimiter` non partagé | Accepté | D-RATE-LIMIT : classe package-private hors surface A3. |
| dryRun `add_dossier_element` ne valide pas le schéma metadata (NOTE.text, etc.) | Accepté | Design : VIEW + EDIT seulement, pas d’appel `add`. |

Aucun écart bloquant. `CreateDossierUseCase*` non modifié. `git diff --stat`
vs `main` : `infrastructure/mcp/**` + tests MCP + docs (dont §12 GETTING-STARTED).

## File-by-file (Phase 7)

| Fichier | Alignement design |
|---|---|
| `CreateDossierTool` | Délègue `CreateDossierCommand` ; dryRun = type + validator, pas de persist. |
| `AddDossierElementTool` | NOTE/LINK/INFORMATION ; PRODUCED_DOC/ATTACHMENT rejetés avant get ; `workflowStepId`/`storageRef` null. |
| `StartWorkflowTool` | `ManualStartOrchestrator` ; ACL avant start ; dryRun sans orchestrateur ; `idempotencyKey` obligatoire. |
| `GenerateDocumentTool` | Template listé sur le type du dossier ; overlay via `DossierMcpView.fields()` ; store + PRODUCED_DOC ; pas de PDF Base64. |
| `McpArgs` | `requireUuid` / `optionalMap` / `requireNonBlank`. |
| `McpEndpointApiTest` | 20 tools nommés. |
| `McpToolCallIntegrationTest` | create persisté, add NOTE, type inconnu → `isError`. |

## Edge cases handled

- Type/label/UUID/idempotency blank → IAE, ports non appelés.
- Variables overlay avec clés réservées (`tenantId`, …) → IAE.
- Type dossier inactif / champs invalides en dryRun create → exceptions domaine.
- Template absent du type → `AccessDeniedException`, pas de generate/store/add.
- Replay idempotence start exposé (`replay=true`).

## Follow-ups (non bloquants)

- A1 propriétaire de `GETTING-STARTED.md` : résoudre le conflit de merge si les deux lots avancent.
- Rate-limit generate MCP : hors surface ; réévaluer si le canal M2M est exposé au-delà de l’outillage interne.
