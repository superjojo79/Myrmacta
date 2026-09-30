---
phase: design
title: Design — D1 modèle de champs enrichi
description: Trois types structurés portés par FieldDefinition, validateur, normalisation, exports, SPA
status: reviewed
created: 2026-09-30
updated: 2026-09-30
feature: d1-modele-champs-enrichi
lot: D1
---

# Design — `d1-modele-champs-enrichi`

## Phase 3 — faits vérifiés dans le code

- `DossierFieldValidator` est appelé par **tous** les chemins d'écriture (création, mise à jour, `CheckDossierTypeSchemaCompatibility`, MCP `create_dossier`,
  réponses de tâche `UserQuestionAnswers`, `CompleteTask`) : l'étendre couvre tout sans dupliquer.
- `FieldDefinition` est (dé)sérialisé **tel quel** par les DTO REST, l'outil MCP (`convertValue`) et la JPA : des composants optionnels
  ajoutés au record sont portés partout sans rupture (Jackson renseigne `null`).
- L'évaluateur de modèles PDF (`SafeThymeleafExpressionSupport`) accepte déjà les chemins de propriétés sur des `Map` et `th:each` :
  `dossierFields.montant.amount` et l'itération sur les lignes fonctionnent **sans modification** (test ajouté).
- Les deux exports CSV (`DossierExportController`, `BiExportCsvRenderer`) faisaient `String.valueOf(value)` : un montant sortait `{amount=…}`.
- `CsvWriter` préfixe d'une apostrophe toute cellule commençant par `-` (test existant `-42` → `'-42`) : décision conservée.
- `Dossier` interdit les valeurs nulles (`Map.copyOf`) : une valeur structurée absente est une clé absente.
- Le formulaire de tâche et le portail ont leur propre rendu et rangent les réponses en variables **sans normalisation**.

## Modèle

| Type | `options` | Valeur stockée |
|---|---|---|
| `MONEY` | devises autorisées (ISO 4217, facultatif) | `{amount: "1234.50", currency: "EUR"}` — montant en **texte décimal** |
| `EXTERNAL_REF` | systèmes autorisés (facultatif) | `{system, id, label?}` |
| `REPEATABLE_GROUP` | — | liste de lignes `[{sous-champ: valeur}]` ; `fields`, `minRows`, `maxRows` sur la définition |

`FieldDefinition` : 3 composants optionnels (`fields`, `minRows`, `maxRows`), règles de définition dans le constructeur (un champ structuré
invalide n'existe jamais).

## Flux

```mermaid
flowchart LR
  IN[valeurs JSON] --> V[DossierFieldValidator.validate<br/>chemins lignes[i].champ]
  V -->|ok| N[normalize : montant à l'échelle de la devise, référence nettoyée]
  N --> S[(Dossier.fields)]
  S --> X[DossierFieldCsv : colonnes stables]
  S --> R[DossierFieldRedactor : groupe entier]
  S --> T[modèles PDF : propriétés + th:each]
  S --> E[BusinessExpressionContext : champs déclarés non sensibles]
```

## Décisions de design

Journal `docs/roadmap-goal/DECISIONS.md` : D-CHAMPS-TYPES, -REPEAT, -MONEY, -EXPORT, -STORAGE, -SENSITIVE, -NORMALIZE, -USERQ, -REF ; écarts relevés ci-dessus.
