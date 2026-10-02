---
phase: design
title: Design — D1 règles de champs
status: reviewed
created: 2026-10-01
feature: d1-regles-champs
---

# Design — D1 règles de champs

Faits vérifiés : `FieldDefinition` est un record sérialisé en JSON (colonne du type, API, MCP) ; Jackson y voit toute méthode `isXxx()` comme propriété (un premier jet `isComputed()` a cassé la relecture JSON — d'où `hasFormula()`) ;
`ExpressionEnginePort` (LC2) valide et évalue sans accès serveur ; `CreateDossierUseCaseImpl` / `UpdateDossierFieldsUseCaseImpl` appellent le validateur puis normalisent ; la vérification de compatibilité de schéma rejoue le validateur sur
les dossiers existants.

## Modèle
`FieldDefinition` gagne `formula`, `visibleWhen`, `requiredWhen` (chaînes ≤ 2000 car., nullables ; constructeurs historiques conservés). Contraintes : un champ calculé n'est ni `required`, ni sensible, ni valeur par défaut, ni groupe/fichier, sans autre règle ;
un sous-champ de groupe ne porte pas de règle ; `TaskQuestionSchema` refuse toute règle.

## `DossierFieldRules` (domaine, `domain.dossier`)
Évalue sur `{dossier: {fields: …}}` limité aux champs **déclarés, simples, non sensibles** (pas de chaînage ⇒ ordre sans importance, pas de cycle). Ordre : (1) visibilité et obligation sur les champs fournis, (2) retrait des champs sans objet
(erreur `NOT_APPLICABLE` s'ils viennent d'être fournis), (3) calcul sur ce qui reste ; erreurs `COMPUTED_READ_ONLY`, `RULE_FAILED`. Renvoie les champs fusionnés, les définitions « effectives » (obligations ajustées, passées au validateur existant),
les erreurs, `hidden` et `requiredByRule`. `validateDefinitions` : syntaxe/constructions (moteur), références `fields.x` aux seuls champs lisibles, ≤ 40 règles ; sans moteur, toute règle est refusée.

## Intégration
`CreateDossier`, `UpdateDossierFields` (explicites = clés du corps / du patch), `Create/UpdateDossierType` (validation de définition), compatibilité de schéma (les règles comptent), `EvaluateDossierRulesUseCase` (aperçu), REST `POST /api/dossier-types/{code}/evaluate-rules`,
MCP `evaluate_dossier_rules` + dry-run de `define_dossier_type` qui valide aussi les règles. Aucune migration (JSON existant), aucune variable d'environnement.

## SPA
`DynamicFields` (prop `typeCode`) évalue côté serveur, anti-rebond 300 ms, réponses périmées ignorées, retire la saisie d'un champ devenu sans objet, affiche le calculé en lecture seule ; éditeur de définitions : trois champs dans un bloc repliable
(champs de dossier seulement) ; validation miroir ; `fieldRules.ts` (helpers purs).
