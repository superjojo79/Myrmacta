---
phase: testing
title: D1 — modèle de champs enrichi (tests)
---

# Tests — D1 (TODO §76)

| Couche | Preuves |
|---|---|
| Domaine | `RichFieldTypesTest`, `DossierFieldCsvTest` (validation, chemins d'erreur, normalisation, bornes, précision, masquage, CSV) |
| API | `RichFieldTypesApiTest` (création/mise à jour/409/masquage/OpenAPI), `BiExportCsvRendererTest`, `ThymeleafHtmlTemplateProcessorTest` (PDF sans modification), `TaskQuestionSchemaTest` |
| SPA (Vitest) | `fieldValues.spec`, `diffDossierFields.spec`, `DynamicFields.spec`, `FieldDefinitionDefsEditor.spec`, `validateFieldDefinitions.spec` — 539/539 |
| E2E (Playwright) | `E2E-RICH-FIELDS-1..4` (édition montant + ligne, absence de faux patch, lecture texte/tableau, définition d'un type avec groupe) |

Résultats finaux : voir ligne du lot 3 de `docs/roadmap-goal/AVANCEMENT.md`.
Environnement : JDK 21 (`-Dmaven.compiler.release=21`), Playwright avec `PLAYWRIGHT_CHROMIUM_EXECUTABLE=/opt/pw-browsers/chromium-1194/chrome-linux/chrome`.
