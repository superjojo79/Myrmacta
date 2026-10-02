---
phase: requirements
title: Requirements — LC5 concepteur de parcours
status: reviewed
created: 2026-10-01
feature: parcours-concepteur
lot: LC5
todo_ref: TODO.md §88 · analyse 44
---

# Requirements — LC5 concepteur de parcours

## Problème
Un métier qui conçoit un processus doit lire et écrire du BPMN (bpmn-js ou XML). LC1 (briques) et LC2 (expressions) rendent chaque
étape typée, mais l'enchaînement reste un graphe.

## Objectifs
- G1. Décrire un parcours **par étapes** (liste ordonnée, chacune avec sa brique et ses paramètres) et obtenir un **BPMN déterministe**.
- G2. Le BPMN généré passe **le même parseur et la même liste blanche** qu'un BPMN écrit à la main : aucune exception, aucune dérogation.
- G3. La compilation dépose un **brouillon** (provenance `PARCOURS`) relu dans Admin → Brouillons ; elle ne publie jamais.
- G4. Un parcours **enregistré est toujours compilable** : l'incohérence est refusée à l'enregistrement, avec l'étape nommée.
- G5. Exposition MCP dans le même lot (mode conception) : `list_parcours`, `get_parcours`, `define_parcours`, `compile_parcours`.
- G6. Avertissements non bloquants (variable lue jamais écrite, champ de schéma inconnu).

## Hors périmètre (journalisé D-PARCOURS-SCOPE / D-PARCOURS-ROUNDTRIP)
Aller-retour BPMN → parcours ; passerelles parallèles, signature, dossier enfant, escalade/minuterie de bordure ; éditeur graphique.

## Critères d'acceptation
- Même définition → mêmes octets et même empreinte SHA-256.
- Définition hostile (id, libellé, valeur de branche, paramètre) : refusée ou échappée, jamais exécutable.
- Version attendue périmée → 409 ; clé inconnue → 404 ; incohérence → 400 avec l'étape.
- `TENANT_ADMIN` et `WORKFLOW_ADMIN` seulement.
