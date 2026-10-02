---
phase: implementation
title: Implémentation — D1 règles de champs (champs calculés, visibilité, obligation conditionnelle)
---

# Implémentation (TODO §80)

- **Domaine** : `DossierFieldRules` (évaluation : visibilité → obligation → calcul ; `validateDefinitions` à la définition), `FieldDefinition` (`formula`, `visibleWhen`, `requiredWhen`, `hasFormula()`, `withRequired`, `withHidden`), `EvaluateDossierRulesUseCase`, erreurs `COMPUTED_READ_ONLY` / `NOT_APPLICABLE` / `RULE_FAILED`.
- **Branchement** : création, mise à jour des champs, vérification de compatibilité d'un type, aperçu `dryRun` MCP.
- **Surfaces** : `POST /api/dossier-types/{code}/evaluate-rules`, outil MCP `evaluate_dossier_rules`, `define_dossier_type` (validation en dry-run), SPA (`fieldRules.ts`, `DynamicFields.vue`, éditeur de définitions).
- **Pièges** : un getter `isComputed()` était vu par Jackson comme propriété `computed` → relecture JSON refusée ; renommé `hasFormula()`.

## Revue adversariale (1 passe, 0 Critical/High)

| Constat | Traitement |
|---|---|
| M1 groupe masqué avec `minRows>0` impossible à enregistrer | `withHidden()` neutralise `minRows` + test |
| M2 aperçu `dryRun` MCP sans règles | `CreateDossierTool` applique `DossierFieldRules` + test |
| M3 règles lisant un champ masquable : résultat dépendant de l'ordre | refus à la définition + test |
| L2 `null` explicite sur un champ calculé refusé | ignoré + test |
| L1/L3/L4 (SPA : filtrage à l'envoi, coût de compatibilité, affichage) | acceptés, consignés (TODO) |
