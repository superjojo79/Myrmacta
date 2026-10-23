---
phase: design
title: Réserver generatedDocumentId — conception
description: Extension de WorkflowVariables.RESERVED, comme connectorResult (§78)
---

# Conception — `document-genere-reserve`

- `WorkflowVariables.GENERATED_DOCUMENT_ID` rejoint `RESERVED`. `assertNoReservedKeys` couvre toutes les entrées
  utilisateur recensées :
  - `ManualStartOrchestrator` (dialogue, API REST, dossier enfant) ;
  - `OutboxRelayScheduler` ;
  - MCP `StartWorkflowTool` et `GenerateDocumentTool` ;
  - `CompleteTaskUseCaseImpl` ;
  - `SubmitPortalTaskUseCaseImpl` ;
  - `CallbackConnectorRuntimeImpl` ;
  - `FlowableSignalConnector`.

  Les autres écritures viennent du serveur ou du concepteur : DMN, délégués, réconciliation.
- `isReservedOrKnownKey` inclut donc ce nom : un champ de questionnaire ne peut plus s'appeler `generatedDocumentId`.
- Le SPA (`startVariables.ts`) refuse le nom avant l'envoi ; le serveur reste l'autorité.
- **D-DOC-GENERATED-RESERVED** ⚠ — option 2 de l'issue retenue, parce qu'elle a le même mécanisme et le même précédent que
  `connectorResult` (§78). Options écartées :
  - (1) provenance enregistrée côté serveur : nouvelle table, coût sans gain tant que toutes les entrées sont gardées ;
  - (3) refus d'un objet déjà rattaché ailleurs : requête sur `storage_ref` non indexée, et ne couvre pas un objet encore
    jamais rattaché.
- Changement de comportement : une entrée qui portait `generatedDocumentId` est refusée (400) au lieu d'être acceptée.
  Pour MCP `generate_document`, la variable était déjà ignorée par le filtre des modèles.
