---
phase: design
title: Design — LC4a
status: reviewed
created: 2026-10-01
feature: lc4a-concepteur-formulaires
---

# Design — LC4a

- Domaine : `FieldDefinition` gagne `helpText` (≤ 500 car., texte brut, sans caractère de contrôle hors saut de ligne) et `width` (`HALF` ; `FULL` est normalisé en absence de valeur). Un sous-champ de groupe peut porter une aide, pas une largeur. Constructeurs historiques conservés.
- Portail : `PortalTaskResponse.Field` expose `helpText` et `width` (jamais plus).
- SPA : `FormDesigner.vue` = `FieldDefinitionDefsEditor` (glisser-déposer natif, liste des sections existantes, largeur, aide) + aperçu `DynamicFields` ; `DynamicFields` et `PortalQuestionForm` rendent une grille à deux colonnes (une en étroit) et l'aide en `<small>` (interpolation Vue : texte).
- Aucune migration : les définitions sont du JSON.
- Décisions : D-FORMED-LIBRARY (maison), D-FORMED-LAYOUT, D-FORMED-SCOPE, D-FORMED-MCP.
