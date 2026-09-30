---
phase: implementation
title: D1 — modèle de champs enrichi (implémentation)
---

# Implémentation — D1 (TODO §76)

## Backend
- `FieldType` : `MONEY`, `EXTERNAL_REF`, `REPEATABLE_GROUP`. `FieldDefinition` porte `fields` (sous-champs) ; règles de définition
  (un niveau, ≤ 20 sous-champs plats non sensibles, 0–500 lignes, sensibilité sur le groupe entier).
- `DossierFieldValidator` : validation et normalisation, erreurs avec chemin (`lignes[1].poste`). Montant `{amount:"1234.50",currency}` :
  texte décimal exact, refusé (jamais arrondi) au-delà des décimales de la devise, normalisé à l'échelle de la devise. Les décimaux JSON
  lus en `Double` sont refusés au-delà de 15 chiffres significatifs (perte de précision). Bornes : cellule 10 000, groupe 1 000 000,
  `fields` 1 048 576 caractères.
- `DossierFieldCsv` (exports dossiers et BI) : `.amount/.currency`, `.system/.id/.label`, groupe = une colonne JSON ; `DossierFieldRedactor`
  masque le groupe sensible entier.
- Branchements : création/mise à jour (use cases), MCP (`define_dossier_type`, `define_task_question_schema`), OpenAPI additif.
  Types structurés refusés dans `TaskQuestionSchema` (D-CHAMPS-USERQ). Aucune migration.

## Frontend
- `fieldValues.ts` (vide/blanc, forme canonique, comparaison `12.5 == 12.50`, payload), `diffDossierFields.ts` (aucun faux patch),
  `validateFieldDefinitions.ts` (miroir serveur), `DynamicFieldInput/DynamicFields/SensitiveValue`, `FieldDefinitionDefsEditor`
  (colonnes de groupe), `CreateDossierView`, `TaskQuestionSchemasAdminView` (mode tâche : types structurés absents).
- Un groupe dont toutes les lignes sont vides est vide (retiré) ; une cellule blanche ne compte pas.

## Revue adversariale (Phase 10) — 1 passe
0 Critique / 0 Haute. Constats Moyens et Bas corrigés avec tests de non-régression : précision `double` des montants, plafonds de taille
(DoS `fields`), devises/échelle, comparaison décimale canonique côté SPA, sémantique « groupe vide », rôle ARIA du groupe, miroir éditeur
de définitions. **Résidu ⚠ (M4)** : le plafond de corps HTTP global n'est pas spécifique aux `fields` ; il est borné par la limite de
requête existante et par `FIELDS_TEXT_MAX`. Passe 2 non requise (aucun Critique/Haut).
