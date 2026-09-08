---
phase: implementation
title: Implementation Guide
description: Technical implementation notes, patterns, and code guidelines
status: in-progress
feature: mcp-ecritures-metier
---

# Implementation Guide — `mcp-ecritures-metier`

Lot A3. Adaptateurs MCP uniquement (`infrastructure.mcp.tool`). TDD M1–M8.

## Code Structure

```
infrastructure/mcp/tool/
  CreateDossierTool.java
  AddDossierElementTool.java
  StartWorkflowTool.java
  GenerateDocumentTool.java
  McpArgs.java          # requireUuid, optionalMap
  DossierMcpView.java   # existant — generate lit fields() ici
```

## Notes

- Ne pas modifier `CreateDossierUseCase*` (Vague B).
- Ne pas appeler `Dossier.fields()` hors `DossierMcpView`.
- `start_workflow` : ACL dans le tool (`GetDossierUseCase` + `assertCanEdit`).
- `generate_document` : store + `PRODUCED_DOC` ; sortie `DossierElementView`.

## Design deviations

(à remplir en Phase 5/7)
