---
phase: planning
title: Planning — D1 modèle de champs enrichi
description: Jalons et état d'avancement
status: done
created: 2026-09-30
updated: 2026-09-30
feature: d1-modele-champs-enrichi
lot: D1
requirements_ref: docs/ai/requirements/2026-09-30-feature-d1-modele-champs-enrichi.md
design_ref: docs/ai/design/2026-09-30-feature-d1-modele-champs-enrichi.md
testing_ref: docs/ai/testing/2026-09-30-feature-d1-modele-champs-enrichi.md
---

# Planning — `d1-modele-champs-enrichi`

Taille **L**. TDD à chaque jalon.

| Jalon | Contenu | État |
|---|---|---|
| M1 | Domaine : `FieldType`, `FieldDefinition` (règles de définition), `DossierFieldValidator` (validation + normalisation), `FieldValidationError.invalidValue` | done |
| M2 | Branchements : création, mise à jour (normalisation avant la détection des champs sensibles), questions de tâche (types refusés) | done |
| M3 | Exports : `DossierFieldCsv` partagé par l'export des dossiers et l'export BI | done |
| M4 | Preuves de non-régression : modèles PDF, compatibilité 409, masquage, OpenAPI additif | done |
| M5 | API de bout en bout (`RichFieldTypesApiTest`) ; outil MCP `define_dossier_type` documenté | done |
| M6 | SPA : `fieldValues`, saisie, lecture (texte, tableau), diff, éditeur d'administration, validation miroir | done |
| M7 | Playwright `E2E-RICH-FIELDS-1..4` ; Vitest | done |
| M8 | Documentation (guide utilisateur, guide de conception, analyse 32, TODO §76, CHANGELOG) | done |
| M9 | Revue adversariale (Phase 10) | voir implémentation |
