---
phase: testing
title: Testing — LC4a
status: reviewed
created: 2026-10-01
feature: lc4a-concepteur-formulaires
---

# Testing — LC4a

| Niveau | Fichier | Couvre |
|---|---|---|
| Backend | `FieldLayoutTest` | largeur (défaut, normalisation, refus), aide (bornes, contrôle), sous-champs, copies, aller-retour JSON, relecture d'une définition ancienne |
| Backend | `PortalTaskResponseTest` | la disposition atteint le portail |
| Vitest | `FormDesigner.spec.ts`, `PortalQuestionForm.spec.ts`, `validateFieldDefinitions.spec.ts` | aperçu (sections, demi-largeur, aide), glisser-déposer, sections proposées, aide en texte brut sur le portail, plafond de 500 caractères |
| Playwright | E2E-FORMED-1 | conception d'un schéma avec section, largeur et aide ; envoi en `fieldDefinitions` simples |
