---
phase: requirements
title: Requirements — D1 règles de champs (calculés, visibilité, obligation conditionnelle)
status: reviewed
created: 2026-10-01
feature: d1-regles-champs
lot: D1 (suite, sur LC2)
todo_ref: TODO.md §80 · analyse 32 (« Champs calculés », « Visibilité / obligation conditionnelle »)
---

# Requirements — D1 règles de champs

## Problème
Le modèle de champs (D1, §76) ne sait ni dériver une valeur d'autres champs (TTC = HT × (1 + taux)), ni n'afficher un champ que dans certains cas (SIRET seulement si pays = FR), ni
rendre un champ obligatoire sous condition (justificatif si montant > 1 000). Ces règles vivent aujourd'hui dans la tête des utilisateurs ou dans des contrôles aval. LC2 (§75) fournit désormais
le moteur d'expressions borné qui rend la chose possible sans nouvelle surface d'injection.

## Objectifs
- G1. `formula` : champ **calculé**, valeur dérivée, recalculée à chaque enregistrement, en lecture seule.
- G2. `visibleWhen` : champ applicable seulement si la règle est vraie (sinon non obligatoire ; valeur fournie refusée ; valeur stockée devenue sans objet retirée).
- G3. `requiredWhen` : champ obligatoire si la règle est vraie, en plus de `required`.
- G4. **Une seule sémantique**, côté serveur (même moteur, mêmes bornes) pour la création, la mise à jour, l'aperçu pendant la saisie (SPA), le MCP — pas de second moteur JavaScript.
- G5. Aucun champ sensible lisible par une règle (D-EXPR-CONTEXT) ; valeurs jamais recopiées dans les messages d'erreur.
- G6. Exposition MCP dans le même lot : `define_dossier_type` accepte les règles (validation en dry-run), `evaluate_dossier_rules` les prévisualise.

## Hors périmètre
Chaînage de formules (un champ calculé qui lit un autre champ calculé), règles sur les sous-champs d'un groupe, règles dans les questions de tâche et le portail, règles lisant les champs sensibles ou le statut du dossier,
recalcul rétroactif des dossiers existants (les valeurs se recalculent à leur prochaine modification).

## Décisions tranchées (DECISIONS.md)
D-CHAMPS-COMPUTED-STORAGE, -CONTEXT, -CHAIN, -WRITE, -FAILURE ; D-CHAMPS-VISIBILITY ; D-CHAMPS-RULES-EVAL ; D-CHAMPS-RULES-DEFINITION.

## Critères d'acceptation
- Création : un TTC calculé est stocké ; SIRET obligatoire seulement pour pays = FR ; justificatif obligatoire seulement si HT > 1 000.
- Mise à jour d'un HT : TTC recalculé ; un pays qui devient « DE » retire le SIRET stocké ; une valeur fournie pour TTC (ou pour SIRET quand il est sans objet) est refusée (`COMPUTED_READ_ONLY`, `NOT_APPLICABLE`).
- Définition d'un type refusée si : syntaxe invalide, construction interdite (`$eval`), règle lisant un champ inconnu / sensible / calculé, champ calculé obligatoire / sensible / avec défaut, plus de 40 règles.
- Aperçu `POST /api/dossier-types/{code}/evaluate-rules` : mêmes droits que la création d'un dossier du type ; champs sensibles ignorés ; aucune écriture.
- SPA : champ sans objet masqué (et sa saisie retirée), valeur calculée affichée en lecture seule, astérisque d'obligation à jour ; lecture seule = valeurs stockées, sans appel d'aperçu.
