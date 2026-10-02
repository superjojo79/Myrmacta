---
phase: design
title: Design — D2 (recherche sur champs dynamiques)
status: reviewed
created: 2026-10-01
feature: d2-recherche-champs-dynamiques
lot: D2
---

# Design — D2

## Modèle
- `FieldDefinition.searchable` (15ᵉ composante, défaut `false`) : STRING/NUMBER/DATE/BOOLEAN/SELECT, jamais sensible, jamais sous-champ de groupe.
- `FieldCondition(field, op, values)` — `EQ`, `IN`, `GTE`, `LTE`, `PREFIX` ; plafonds : 5 critères/recherche, 20 valeurs/`IN`, 200 caractères/valeur. `FieldConditionParser` lit les paramètres `f.*` (REST et MCP).
- `SearchDossiersQuery.fieldConditions` ; `DossierSearchPort.Filter.fieldPredicates` (`FieldPredicate` typé : `TEXT`/`NUMBER`/`BOOLEAN`).

## Cas d'usage (`SearchDossiersUseCaseImpl`)
Valide **avant** le périmètre ACL (un filtre invalide est un 400 pour tout appelant) : type de dossier obligatoire et connu, champ déclaré, cherchable et non sensible, opérateur permis pour le type (STRING : eq/in/prefix ; SELECT : eq/in ; NUMBER, DATE : eq/gte/lte ; BOOLEAN : eq), valeur convertie (décimal strict ≤ 30+15 chiffres, date ISO, option déclarée, `true`/`false`).

## Persistance (PostgreSQL)
- Migration 030 : `fields` `json` → `jsonb`, index `idx_dossier_fields_gin` (`jsonb_path_ops`).
- Égalité/appartenance : `fields @> {"champ": valeur}` (fonction HQL `flow_jsonb_contains`, servie par le GIN — vérifié par `EXPLAIN`, y compris sous la forme `(… ) = true` que Hibernate génère). Intervalles : `flow_jsonb_number` (le nombre JSON, `null` sinon : jamais de cast qui échoue) / `flow_jsonb_text` (dates ISO). Préfixe : `lower(flow_jsonb_text) like 'préfixe%' escape '\'`.
- Toutes les valeurs sont des paramètres liés ; le nom du champ vient de la définition validée.
- H2 : `JpaDossierSearchAdapter` refuse (400) toute recherche avec critères.

## Surfaces
REST `GET /api/dossiers?dossierTypeCode=…&f.montant.gte=5000` · MCP `search_dossiers.fieldFilters` · SPA : filtres générés par `fieldFilterInputs` dans `SupervisionView`, reflétés dans l'URL ; case « Cherchable » dans l'éditeur de types.

## Alternatives écartées
Expression libre (injection, coût) ; index B-tree par champ (DDL dynamique) ; plein texte (extensions) — voir D-RECH-* dans `docs/roadmap-goal/DECISIONS.md`.
