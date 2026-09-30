---
phase: requirements
title: Requirements — LC2 langage d'expression métier borné
description: Un langage d'expression sûr, hors JUEL, pour conditions, champs calculés et mapping de réponse
status: reviewed
created: 2026-09-30
updated: 2026-09-30
feature: lc2-langage-expression-metier
lot: LC2
todo_ref: TODO.md §75 · analysis doc/40-langage-expression-metier.md · ROADMAP.md LC2
---

# Requirements — `lc2-langage-expression-metier`

## Phase 1 — problème

La sécurité du BPMN a été obtenue en **retirant** de l'expressivité à JUEL (TODO §57 H1, §69) : une condition de passerelle
n'accepte ni point ni appel, donc « `montant > 5000` et `pays = FR` » sur les champs du dossier est **inexprimable**, comme
tout calcul ou mapping JSON. D0 = C (TODO §70) promet une couche low-code : sans langage d'expression, l'utilisateur métier
retombe sur du Java. LC2 est le prérequis des champs calculés (D1), du mapping de réponse de connecteur (D3b) et du
concepteur de parcours (LC5).

**Utilisateurs** : concepteur de process (`TENANT_ADMIN`, `WORKFLOW_ADMIN`), agents MCP (`API_SERVICE`).

## Objectifs

- G1. Exprimer conditions, calculs et extractions JSON sur le dossier et le résultat d'un connecteur.
- G2. **Aucune nouvelle surface d'injection** : l'expression n'est jamais évaluée ni concaténée en JUEL ; aucun accès
  fichier, réseau ou classe Java ; bornes de temps, de mémoire et de taille.
- G3. Un brique BPMN utilisable dans l'éditeur (LC1) qui range le résultat dans une variable comparée par une passerelle.
- G4. Validation au déploiement (syntaxe, constructions permises) ; essai à blanc par API et MCP **avec les mêmes bornes**.
- G5. Un moteur réutilisable (port) pour D1 (champs calculés) et D3b (mapping) sans le réécrire.

## Non-objectifs

Champs calculés et visibilité conditionnelle (D1, lot suivant), mapping de réponse vers les champs du dossier (D3b), éditeur
d'expression avec autocomplétion (LC4a/LC5), scripts JS/TS (LC6, conditionnel), expression dans une `conditionExpression`
libre (jamais).

## Critères de succès

- S1. `dossier.fields.montant > 5000 and dossier.fields.pays = "FR"` s'évalue, dans un process réel, vers un booléen qui route
  une passerelle à condition sûre.
- S2. Refus à la validation : syntaxe invalide (avec position), regex littérale, `$eval`, `$random`, `$now`, `$millis`, `${`/`#{`,
  caractères de contrôle, arbre trop gros ou trop profond, expression trop longue.
- S3. Refus à l'exécution : dépassement de délai, de mémoire, de profondeur d'évaluation, de taille d'entrée/sortie,
  `$pad` démesuré, contexte non JSON ou cyclique.
- S4. Un champ `sensitive` n'est jamais visible d'une expression ; une variable de process ne remplace jamais la formule.
- S5. Un résultat indéfini échoue explicitement ; un `null` explicite reste distinct d'un résultat absent.
- S6. API `POST /api/expressions/{validate,evaluate}` et outils MCP `validate_expression` / `evaluate_expression`.

## Décisions ouvertes tranchées (mandat « roadmap complète »)

D-EXPR-LANGUAGE, D-EXPR-EVAL-SITE, D-EXPR-CONTEXT, D-EXPR-LIMITS (ouvertes dans l'analyse 40) puis D-EXPR-REGEX, D-EXPR-FUNCTIONS,
D-EXPR-LITERAL-ONLY, D-EXPR-UNDEFINED, D-EXPR-RESULT, D-EXPR-ATTR-NAME, D-EXPR-AST-BRIDGE, D-EXPR-ALLOC-GUARD, D-EXPR-API —
voir `docs/roadmap-goal/DECISIONS.md`.
