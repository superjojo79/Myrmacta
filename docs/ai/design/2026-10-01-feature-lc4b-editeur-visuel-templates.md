---
phase: design
title: Design — LC4b
status: reviewed
created: 2026-10-01
feature: lc4b-editeur-visuel-templates
---

# Design — LC4b

- **TipTap v3** (`@tiptap/vue-3`, starter-kit, text-align, table) dans `frontend/src/templateEditor/` : `extensions.ts` (nœuds personnalisés Variable `span[th:text]`, ConditionalBlock `div[th:if|th:unless]`, RepeatBlock `div[th:each]`, PageBreak, RepeatableRow `tr[th:each]`), `htmlSubset.ts` (analyseur : le template est-il dans le sous-ensemble ?), `variableCatalog.ts` (variables d'exemple → chemins, listes, champs d'élément).
- Règles de lecture portables (`tag` + `getAttrs`) : jsdom ne gère pas les sélecteurs d'attribut à deux-points échappés.
- Allowlist d'expressions sans opérateur : `th:unless` pour la négation, `#strings.equals(chemin,'valeur')` pour l'égalité ; valeur sans apostrophe/antislash/accolade/`$`.
- `TemplateVisualEditor.vue` : `v-model` = HTML Thymeleaf ; bascule visuel/source ; un template incompatible démarre en source avec la liste des raisons.
- Fixture `src/test/resources/template-editor/editor-output.html` partagée : testée en Vitest (aller-retour) et rendue par `TemplateEditorOutputRenderingTest` (backend).
- MCP : `DefineDocumentTemplateTool` (création / mise à jour du brouillon / nouvelle version si active, jamais d'activation, métadonnées seules) ; `PreviewDocumentTool` (HTML en direct, jamais par `templateKey`). Mode DESIGN dans `McpToolModes`.
