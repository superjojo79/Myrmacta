---
phase: requirements
title: Requirements — D2 (recherche sur champs dynamiques)
status: reviewed
created: 2026-10-01
feature: d2-recherche-champs-dynamiques
lot: D2
todo_ref: TODO.md §81 · analyse 33
---

# Requirements — D2

## Problème
La recherche de dossiers ne filtre que sur `status`, `dossierTypeCode` et le préfixe de `reference` (`DossierSpecifications`). On ne peut pas retrouver « le sinistre de ce contrat » ou « les dossiers de plus de 5 000 € » : les champs
dynamiques sont dans une colonne `json` (pas `jsonb`), donc sans index possible. Au-delà de quelques milliers de dossiers par tenant, la recherche devient impraticable pour un gestionnaire.

## Objectifs
- G1. Filtrer sur des champs dynamiques **déclarés cherchables** : égalité, appartenance (`SELECT`), intervalle (nombre, date), préfixe (texte).
- G2. Aucun champ **sensible** cherchable (un filtre sur un champ masqué est un oracle : on devinerait la valeur par essais).
- G3. Performances tenues : index GIN sur `fields` (PostgreSQL), nombre de filtres borné, critère de cadrage obligatoire (type, statut ou préfixe de référence) pour toute recherche sur champ dynamique.
- G4. Même contrat côté REST et MCP (`search_dossiers`) ; pagination et ACL de type inchangées (les filtres s'appliquent après le périmètre autorisé).
- G5. Édition de la définition : case « cherchable » dans l'éditeur de types ; refus d'un champ cherchable sensible / calculé non typé comme il faut.

## Hors périmètre
Plein texte (`tsvector`, `pg_trgm`) — nécessite des extensions et des privilèges de création absents de la stack figée (D-RECH-FTS : reporté) ; recherche dans les lignes d'un groupe répétable ; tri sur un champ dynamique ; recherche sur `MONEY`.

## Décisions tranchées (DECISIONS.md)
D-RECH-SCOPE (opt-in), D-RECH-SENSITIVE (exclu), D-RECH-FTS (reporté), D-RECH-SYNTAX (paramètres typés), D-RECH-STORAGE (`jsonb` + GIN, PostgreSQL seulement), D-RECH-GUARD (cadrage obligatoire, 5 filtres).

## Critères d'acceptation
- `GET /api/dossiers?dossierTypeCode=SINISTRE&f.contrat=C-42&f.montant.gte=5000` ne renvoie que les dossiers du type dont le champ cherchable `contrat` vaut `C-42` et `montant` ≥ 5000, dans le périmètre ACL de l'appelant.
- Un champ non déclaré cherchable, sensible, de type non cherchable ou inconnu : **400** explicite (jamais un résultat vide silencieux).
- Plus de 5 filtres, ou aucun critère de cadrage : 400.
- Sur H2 (tests, mono-poste), une recherche sur champ dynamique est refusée explicitement (501-like 400 « base non supportée ») ; les tests de la requête tournent sur PostgreSQL (Testcontainers en CI, serveur local ici).
