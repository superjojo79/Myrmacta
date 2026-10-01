---
phase: implementation
title: Implementation — LC4a
status: reviewed
created: 2026-10-01
feature: lc4a-concepteur-formulaires
---

# Implementation — LC4a

- Backend : `FieldDefinition` (17 composants, constructeur à 15 conservé), `PortalTaskResponse`, descriptions MCP de `define_dossier_type` et `define_task_question_schema`, contrat OpenAPI régénéré.
- Frontend : `components/dossier/{FormDesigner,FieldDefinitionDefsEditor,DynamicFields}.vue`, `portal/{PortalQuestionForm.vue,portal.css,api.ts}`, `dossier/validateFieldDefinitions.ts`, `api/types.ts` ; `DossierTypesAdminView` et `TaskQuestionSchemasAdminView` utilisent `FormDesigner`.
