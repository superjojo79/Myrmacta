---
phase: requirements
title: Requirements — D1 modèle de champs enrichi (types structurés)
description: MONEY, REPEATABLE_GROUP et EXTERNAL_REF pour les champs dynamiques des dossiers
status: reviewed
created: 2026-09-30
updated: 2026-09-30
feature: d1-modele-champs-enrichi
lot: D1
todo_ref: TODO.md §76 · analysis doc/32-modele-champs-enrichi.md · ROADMAP.md D1
---

# Requirements — `d1-modele-champs-enrichi`

## Phase 1 — problème

Les champs dynamiques se limitent à `STRING, NUMBER, DATE, BOOLEAN, SELECT, FILE` : pas de montant avec devise, pas de lignes
répétables, pas de référence vers un système externe. Un sinistre (postes de préjudice), une subvention (lignes budgétaires) ou
un onboarding fournisseur (compte ERP) doivent détourner `STRING` ou `NUMBER` — avec des montants en `double` et des lignes
en texte libre. D0 = C (intégrateurs + low-code) rend ce manque bloquant.

## Objectifs

- G1. `MONEY` : montant décimal **exact** + devise ISO 4217, jamais arrondi en silence.
- G2. `REPEATABLE_GROUP` : lignes typées, bornées, un niveau.
- G3. `EXTERNAL_REF` : système + identifiant (+ libellé mis en cache), sans résolution distante.
- G4. Tous les consommateurs du schéma restent corrects : validation (création, mise à jour, compatibilité 409, MCP), exports CSV
  et BI, modèles PDF, masquage des champs sensibles, formulaire SPA, éditeur d'administration, contrat OpenAPI.
- G5. Aucune rupture pour l'existant : un JSON de définition sans les nouveaux composants se charge comme avant.

## Non-objectifs

Champs calculés et visibilité conditionnelle (lot suivant, s'appuient sur LC2), résolution des références (D3), recherche sur ces
champs (D2), types structurés dans les **questions de tâche** et le portail (LC4a), migration `json` → `jsonb` (D2).

## Critères de succès

- S1. Un montant `12.5 EUR` est accepté et stocké `"12.50"` ; `1.234 EUR` et `12.5 JPY` sont refusés (`INVALID_VALUE`) ; `1.250 KWD` est accepté.
- S2. Une erreur de ligne nomme le chemin (`lignes[1].poste`) ; les bornes de lignes, sous-champs inconnus et champs obligatoires sont contrôlés.
- S3. Une définition invalide est refusée à la définition (groupe vide, sous-champ sensible ou imbriqué, devise hors ISO, valeur par défaut sur un type structuré).
- S4. Un groupe marqué sensible est masqué pour un non-administrateur et absent de l'export BI ; aucun sous-champ ne peut être sensible.
- S5. Un changement de type rendant des dossiers invalides est refusé (409) ; le JSON ancien se charge ; l'OpenAPI est additif.
- S6. Export CSV : montant et référence aplatis en colonnes stables, groupe en une colonne JSON.
- S7. La SPA saisit, édite, affiche (texte, tableau) et n'envoie que les changements réels (« 12.5 » = « 12.50 » ⇒ aucun patch).

## Décisions ouvertes tranchées (mandat « roadmap complète »)

D-CHAMPS-TYPES, D-CHAMPS-REPEAT, D-CHAMPS-MONEY, D-CHAMPS-EXPORT, D-CHAMPS-STORAGE (ouvertes dans l'analyse 32) puis D-CHAMPS-SENSITIVE,
D-CHAMPS-NORMALIZE, D-CHAMPS-USERQ, D-CHAMPS-REF — voir `docs/roadmap-goal/DECISIONS.md`.
