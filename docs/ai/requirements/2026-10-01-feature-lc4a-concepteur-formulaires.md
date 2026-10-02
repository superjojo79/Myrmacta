---
phase: requirements
title: Requirements — LC4a (concepteur visuel de formulaires)
status: reviewed
created: 2026-10-01
feature: lc4a-concepteur-formulaires
lot: LC4a
todo_ref: TODO.md §85 · analyse 42
---

# Requirements — LC4a

## Problème
Les formulaires (questions de tâche, champs d'un type de dossier, portail externe) se définissent par une liste brute de champs : ni aperçu, ni sections lisibles, ni aide contextuelle, ni mise en page.

## Objectifs
- G1. Concevoir un formulaire avec un **aperçu en direct** rendu par le vrai moteur de saisie.
- G2. **Sections** (le `group` existant), **largeur** (pleine ou demi) et **aide contextuelle** par champ ; réordonner par **glisser-déposer** (boutons ↑ ↓ conservés au clavier).
- G3. Même disposition dans le back-office et dans le **portail externe**.
- G4. `fieldDefinitions` reste la **seule source** : aucun format propre au concepteur ; anciennes définitions relues sans migration.
- G5. MCP : `define_task_question_schema` et `define_dossier_type` acceptent la mise en page.

## Hors périmètre
Colonnes imbriquées, ordre des sections indépendant des champs, aide en texte riche, `preview_form` MCP (voir D-FORMED-MCP).

## Critères d'acceptation
- Un champ `HALF` occupe une demi-ligne ; l'aide est du texte brut jamais interprété (y compris sur le portail).
- Une aide de plus de 500 caractères ou avec caractères de contrôle est refusée (serveur et SPA).
- Les définitions enregistrées avant le lot se relisent sans erreur.
