---
phase: implementation
title: Implementation — LC4b
status: reviewed
created: 2026-10-01
feature: lc4b-editeur-visuel-templates
---

# Implementation — LC4b

- Frontend : `templateEditor/{extensions,htmlSubset,variableCatalog}.ts`, `components/document/TemplateVisualEditor.vue`, intégration dans `TemplatesAdminView.vue` (remplace le textarea) et `TemplatePreviewPanel.vue` (variables d'exemple partagées).
- Backend : `infrastructure/mcp/tool/{DefineDocumentTemplateTool,PreviewDocumentTool}.java`, entrées DESIGN dans `McpToolModes`. Aucun changement de domaine ni de schéma.
- Pièges : sélecteurs `[th\:text]` non pris en charge par jsdom (règles par `getAttrs`) ; `{{ }}` dans un template Vue (entités HTML) ; `width:` contient « th: » dans les assertions (chercher « ␠th: »).
