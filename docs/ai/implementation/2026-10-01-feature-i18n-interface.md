---
phase: implementation
title: Implementation — D7 internationalisation de l'interface
status: done
created: 2026-10-01
feature: i18n-interface
lot: D7
todo_ref: TODO.md §90
---

# Implementation — D7
Voir `design`. Points d'attention : `t` ne doit jamais être masqué dans un template (`v-for (turn, i)` et non `(t, i)`) ; les messages avec accolades littérales s'écrivent `{'{'}` ; un message avec balises utilise `<i18n-t>` (jamais `v-html`) ; une constante de module ne doit pas contenir de texte traduit (calcul dans une fonction ou un `computed`).
