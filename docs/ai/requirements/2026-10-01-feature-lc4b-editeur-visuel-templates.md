---
phase: requirements
title: Requirements — LC4b (éditeur visuel de templates de documents)
status: reviewed
created: 2026-10-01
feature: lc4b-editeur-visuel-templates
lot: LC4b
todo_ref: TODO.md §84 · analyse 43
---

# Requirements — LC4b

## Problème
Un template de document est du HTML Thymeleaf saisi dans un textarea. Seul un développeur le rédige ; un gestionnaire ne peut ni mettre en forme, ni insérer une variable, une condition ou une liste répétée sans connaître la syntaxe.

## Objectifs
- G1. Éditeur **visuel** (gras/italique/souligné, titres, listes, alignement, tableaux, ligne, saut de page) qui produit un HTML rendu à l'identique par le backend (Thymeleaf + Flying Saucer, CSS 2.1).
- G2. Insertion guidée de **variables**, **blocs conditionnels** (si / sauf / égal à), **blocs répétés** et **lignes de tableau répétées**, alimentée par les variables d'exemple de l'aperçu.
- G3. Aucune expression hors allowlist ne peut être produite ; une valeur d'égalité dangereuse est refusée dans le formulaire.
- G4. Un template qui sort du sous-ensemble reste **en mode source**, intact, avec la raison ; rien n'est altéré en silence.
- G5. Le HTML Thymeleaf reste le contrat de stockage (aucun format propre à l'éditeur) ; le mode source reste disponible.
- G6. MCP : `define_document_template` (brouillon uniquement) et `preview_document` (HTML en direct uniquement).

## Hors périmètre
Images, polices, CSS libre, activation depuis le MCP, co-édition.

## Critères d'acceptation
- Un template produit par l'éditeur se rend côté backend (test partagé avec la SPA).
- Un template incompatible n'est jamais modifié sans action de l'utilisateur.
- Le MCP ne modifie jamais une version active, n'active jamais, ne renvoie jamais le corps HTML.
