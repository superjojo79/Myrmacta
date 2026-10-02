---
phase: testing
title: Tests — D1 règles de champs
---

- Unitaires : `DossierFieldRulesTest` (20, vrai moteur JSONata), `FieldDefinitionRulesTest`, `TaskQuestionSchemaTest`, `CreateDossierToolTest` (aperçu avec règles).
- API / MCP : `FieldRulesApiTest` (8), `McpToolCallIntegrationTest`, `DefineDossierTypeToolTest`.
- Architecture : `ArchitectureRulesTest` (allowlist de lecture de `defaultValue`).
- Frontend : Vitest `fieldRules`, `validateFieldDefinitions`, composants ; e2e Playwright `E2E-RULES-1/2`.
- Régressions de revue : `_reviewD1M1`, `_reviewD1M2`, `_reviewD1M3`, `_reviewD1L2`.
