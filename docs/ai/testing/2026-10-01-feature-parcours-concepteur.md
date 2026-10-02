---
phase: testing
title: Testing — LC5 concepteur de parcours
status: done
created: 2026-10-01
feature: parcours-concepteur
lot: LC5
todo_ref: TODO.md §88
---

# Testing — LC5

- `ParcoursCompilerTest` (26) : déterminisme, XML bien formé + diagramme, liste blanche, chaque type d'étape, boucles, refus (valeurs de branche, mots réservés, clés, durées, caractères XML, collisions, variables du moteur), avertissements.
- `ParcoursApiTest` (9) : cycle de vie, 404/400/409, aperçu qui rend `problem`, suppression versionnée, rôles. `ParcoursToolsTest` (4). `McpEndpointApiTest` (liste attendue), `McpToolModesTest`, ArchUnit.
- Front : `ParcoursView.spec.ts` (10), E2E-PARCOURS-1.
- Régressions de revue : chaque finding a son test (`aBranchValueTheConditionGrammarCannotWriteIsRefusedAtDesignTimeNamingTheStep`, `trueAndFalseAreAlwaysQuoted…`, `charactersOutsideXml10AndLoneSurrogates…`…).
