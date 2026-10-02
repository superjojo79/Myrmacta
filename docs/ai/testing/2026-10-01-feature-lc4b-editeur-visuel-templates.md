---
phase: testing
title: Testing — LC4b
status: reviewed
created: 2026-10-01
feature: lc4b-editeur-visuel-templates
---

# Testing — LC4b

| Niveau | Fichier | Couvre |
|---|---|---|
| Vitest | `variableCatalog.spec.ts`, `htmlSubset.spec.ts`, `extensions.spec.ts`, `TemplateVisualEditor.spec.ts`, `adminActions.spec.ts` | catalogue, sous-ensemble, aller-retour de chaque nœud, modes visuel/source, refus d'un template incompatible |
| Backend | `TemplateEditorOutputRenderingTest` | la sortie de l'éditeur est rendue (Thymeleaf + Flying Saucer) |
| Backend | `DocumentTemplateToolsTest`, `McpEndpointApiTest`, `McpToolModesTest` | brouillon / nouvelle version / jamais d'activation, aperçu en direct seul, classement des outils |
| Playwright | E2E-20, E2E-21 (`pilotage.spec.ts`) | édition visuelle d'un template, bascule source |
